package com.orbisk.flokk.terminal

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import com.orbisk.flokk.ssh.AttachChannel
import com.orbisk.flokk.ssh.SshTransport
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

private const val SCROLLBACK_ROWS = 8000

/** Smallest screen [TerminalEmulator] accepts; it throws below this rather than clamping. */
private const val MIN_CELLS = 2

/**
 * Adapts a remote SSH PTY (attached to `herdr terminal attach <id>`) onto Termux's own
 * VT100/ANSI engine ([TerminalEmulator]), so escape-sequence handling, colors, and cursor
 * behavior match Termux exactly. Unlike a normal [TerminalSession], there is no local
 * subprocess: bytes flow to/from the SSH channel's streams instead of a JNI pty.
 */
class RemoteTerminalSession(
    private val transport: SshTransport,
    private val attachCommand: String,
) : TerminalOutput() {

    /** Bumped once per redrawn frame; read it inside a Canvas draw scope to react to new output. */
    var frame by mutableIntStateOf(0)
        private set

    var title by mutableStateOf("")
        private set

    var bellTick by mutableIntStateOf(0)
        private set

    var finished by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private val dirty = AtomicBoolean(true)

    lateinit var emulator: TerminalEmulator
        private set

    val isReady: Boolean get() = ::emulator.isInitialized

    /**
     * [TerminalEmulator] is not thread-safe, and here it is touched from two threads: the
     * reader appends remote bytes to it, while the UI resizes it and walks its buffer to
     * draw. Without this lock a resize landing mid-append (e.g. the keyboard opening while
     * output streams in) leaves the emulator addressing rows the buffer no longer has.
     */
    private val emulatorLock = Any()

    /** Runs everything that touches the SSH channel — never the caller's (usually main) thread. */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Keystrokes queued for the writer coroutine; writing to the channel is a blocking socket write. */
    private val outbound = Channel<ByteArray>(Channel.UNLIMITED)

    private var channel: AttachChannel? = null
    private var readJob: Job? = null

    // Termux-style "sticky" modifiers: tapping CTRL/ALT arms it (and lights the key up) until
    // the very next keystroke, which is sent combined with that modifier instead of raw —
    // exactly how Termux's own extra-keys CTRL/ALT behave, since a soft keyboard has no real
    // modifier keys to hold down.
    var ctrlArmed by mutableStateOf(false)
    var altArmed by mutableStateOf(false)

    /** Routes typed text from the system keyboard through any armed CTRL/ALT modifier. */
    fun sendText(text: String) {
        if (text.isEmpty()) return
        if (text.length == 1 && (ctrlArmed || altArmed)) {
            val bytes = if (ctrlArmed) TermKeys.ctrl(text[0]) else TermKeys.alt(text[0])
            write(bytes, 0, bytes.size)
            ctrlArmed = false
            altArmed = false
        } else {
            val bytes = text.toByteArray(Charsets.UTF_8)
            write(bytes, 0, bytes.size)
        }
    }

    private val sessionClient = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {}
        override fun onTitleChanged(changedSession: TerminalSession) {}
        override fun onSessionFinished(finishedSession: TerminalSession) {}
        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession) {}
        override fun onColorsChanged(session: TerminalSession) {}
        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int? = null
        override fun logError(tag: String?, message: String?) {}
        override fun logWarn(tag: String?, message: String?) {}
        override fun logInfo(tag: String?, message: String?) {}
        override fun logDebug(tag: String?, message: String?) {}
        override fun logVerbose(tag: String?, message: String?) {}
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
        override fun logStackTrace(tag: String?, e: Exception?) {}
    }

    /**
     * Attaches on the first call and only resizes afterwards, so a session kept alive in the
     * background can be handed back to a canvas without opening a second channel for it.
     */
    fun startOrResize(cols: Int, rows: Int) {
        if (isReady) resize(cols, rows) else start(cols, rows)
    }

    private fun start(requestedCols: Int, requestedRows: Int) {
        val cols = requestedCols.coerceAtLeast(MIN_CELLS)
        val rows = requestedRows.coerceAtLeast(MIN_CELLS)
        emulator = TerminalEmulator(this, cols, rows, SCROLLBACK_ROWS, sessionClient)
        ioScope.launch {
            try {
                val ch = transport.openAttach(attachCommand, cols, rows)
                channel = ch
                readJob = launch { pump(ch) }
                drainOutbound(ch)
            } catch (t: Throwable) {
                error = t.message ?: t.javaClass.simpleName
                finished = true
            }
        }
    }

    private fun pump(ch: AttachChannel) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = ch.inputStream.read(buffer)
                if (n < 0) break
                synchronized(emulatorLock) { emulator.append(buffer, n) }
                dirty.set(true)
            }
        } catch (_: IOException) {
            // Channel closed, normal on detach.
        } finally {
            finished = true
        }
    }

    private suspend fun drainOutbound(ch: AttachChannel) {
        for (chunk in outbound) {
            try {
                ch.outputStream.write(chunk)
                ch.outputStream.flush()
            } catch (_: IOException) {
                finished = true
                return
            }
        }
    }

    /**
     * How a swipe should be turned into scrolling for whatever is currently running.
     *
     * Full-screen programs (an agent TUI, less, vim) run on the alternate screen, which has no
     * transcript at all, so scrolling our own buffer there moves nothing — which is why a swipe
     * used to appear to do nothing. Those get the swipe forwarded instead, the way Termux does
     * it: wheel events for a program that asked to track the mouse, arrow keys for one that
     * didn't.
     */
    enum class ScrollMode {
        /** Forward wheel events; the program scrolls its own view. */
        WHEEL,

        /** Forward arrow keys; a full-screen program with no mouse tracking. */
        KEYS,

        /** Ordinary scrollback: the caller scrolls our transcript locally. */
        TRANSCRIPT,
    }

    fun scrollMode(): ScrollMode = withEmulator { emulator ->
        when {
            emulator.isMouseTrackingActive() -> ScrollMode.WHEEL
            emulator.isAlternateBufferActive() -> ScrollMode.KEYS
            else -> ScrollMode.TRANSCRIPT
        }
    } ?: ScrollMode.TRANSCRIPT

    /** Forwards [steps] scroll steps (positive = back into history) per [scrollMode]. */
    fun scrollRemotely(steps: Int, column: Int, row: Int) {
        withEmulator { emulator ->
            val count = abs(steps)
            when {
                emulator.isMouseTrackingActive() -> {
                    val button = if (steps > 0) {
                        TerminalEmulator.MOUSE_WHEELUP_BUTTON
                    } else {
                        TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                    }
                    // Termux's own 1-based screen coordinates.
                    repeat(count) { emulator.sendMouseEvent(button, column + 1, row + 1, true) }
                }
                emulator.isAlternateBufferActive() -> {
                    val key = if (steps > 0) TermKeys.UP else TermKeys.DOWN
                    repeat(count) { write(key, 0, key.size) }
                }
            }
        }
    }

    /** Types clipboard text, via the emulator so bracketed-paste-aware programs see it as a paste. */
    fun paste(text: String) {
        if (text.isEmpty()) return
        withEmulator { it.paste(text) }
    }

    /** Reads the emulator under [emulatorLock]; null until [start] has built one. */
    fun <T> withEmulator(block: (TerminalEmulator) -> T): T? {
        if (!isReady) return null
        return synchronized(emulatorLock) { block(emulator) }
    }

    /** Call once per display frame; cheaply no-ops unless new bytes arrived since the last call. */
    fun pumpFrameIfDirty() {
        if (dirty.compareAndSet(true, false)) frame++
    }

    fun resize(cols: Int, rows: Int) {
        if (cols < MIN_CELLS || rows < MIN_CELLS || !::emulator.isInitialized) return
        synchronized(emulatorLock) { emulator.resize(cols, rows) }
        val ch = channel ?: return
        ioScope.launch { ch.resize(cols, rows) }
    }

    fun stop() {
        readJob?.cancel()
        outbound.close()
        val ch = channel
        channel = null
        ioScope.launch {
            ch?.close()
            ioScope.cancel()
        }
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        // Copied because the caller (and Termux's emulator) reuses its buffer, and the write
        // itself happens later, on [ioScope].
        outbound.trySend(data.copyOfRange(offset, offset + count))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        title = newTitle ?: ""
    }

    override fun onCopyTextToClipboard(text: String?) {}

    override fun onPasteTextFromClipboard() {}

    override fun onBell() {
        bellTick++
    }

    override fun onColorsChanged() {
        dirty.set(true)
    }
}
