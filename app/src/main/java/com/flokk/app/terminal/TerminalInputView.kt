package com.flokk.app.terminal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager

/**
 * A focusable, zero-size [View] that exists only to own IME focus for the terminal, with a
 * custom [InputConnection] forwarding every commit/delete straight to callbacks — no backing
 * text buffer at all.
 *
 * The alternative (what this replaces) is a Compose `BasicTextField` holding a sentinel
 * character, diffed and reset on every keystroke to fake an "empty" field a backspace can
 * still fire on. That works, but it means every single character round-trips a full
 * IME-to-app text resync (the IME has to re-observe the field's contents after we yank them
 * back to the sentinel) — real terminal apps (Termux, JuiceSSH, ConnectBot) all avoid this
 * exact overhead the same way: a raw `View.onCreateInputConnection` with no text model to
 * keep in sync, so a keystroke is just one direct callback with nothing to resync.
 */
class TerminalInputView(context: Context) : View(context) {

    var onText: (String) -> Unit = {}
    var onBackspace: () -> Unit = {}
    var onEnter: () -> Unit = {}
    var onKey: (ByteArray) -> Unit = {}

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_ACTION_SEND
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                onText(text.toString())
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { onBackspace() }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> {
                            onEnter()
                            return true
                        }
                        KeyEvent.KEYCODE_DEL -> {
                            onBackspace()
                            return true
                        }
                    }
                }
                return super.sendKeyEvent(event)
            }

            // No text model to report — every IME that asks is treated as if the field is
            // always empty, which is the whole point: nothing to keep in sync.
            override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence = ""
            override fun getTextAfterCursor(n: Int, flags: Int): CharSequence = ""
            override fun getSelectedText(flags: Int): CharSequence? = null
        }
    }

    /**
     * Hardware keys (a Bluetooth/USB keyboard) arrive here directly whenever no IME is
     * bound — which is the normal case, since the soft keyboard is blocked until toggled on.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { onEnter(); return true }
            KeyEvent.KEYCODE_DEL -> { onBackspace(); return true }
        }
        val special = when (keyCode) {
            KeyEvent.KEYCODE_FORWARD_DEL -> TermKeys.DELETE
            KeyEvent.KEYCODE_TAB -> TermKeys.TAB
            KeyEvent.KEYCODE_ESCAPE -> TermKeys.ESC
            KeyEvent.KEYCODE_DPAD_UP -> TermKeys.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> TermKeys.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> TermKeys.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> TermKeys.RIGHT
            KeyEvent.KEYCODE_MOVE_HOME -> TermKeys.HOME
            KeyEvent.KEYCODE_MOVE_END -> TermKeys.END
            KeyEvent.KEYCODE_PAGE_UP -> TermKeys.PAGE_UP
            KeyEvent.KEYCODE_PAGE_DOWN -> TermKeys.PAGE_DOWN
            else -> null
        }
        if (special != null) {
            onKey(special)
            return true
        }
        val modifiers = KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK
        val char = event.getUnicodeChar(event.metaState and modifiers.inv())
        if (char == 0 || char and KeyCharacterMap.COMBINING_ACCENT != 0) {
            return super.onKeyDown(keyCode, event)
        }
        val text = String(Character.toChars(char))
        when {
            event.isCtrlPressed && text.length == 1 -> onKey(TermKeys.ctrl(text[0]))
            event.isAltPressed -> onKey(TermKeys.alt(text[0]))
            else -> onText(text)
        }
        return true
    }

    private val imm get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager

    private val window: Window?
        get() {
            var ctx = context
            while (ctx is ContextWrapper) {
                if (ctx is Activity) return ctx.window
                ctx = ctx.baseContext
            }
            return null
        }

    /**
     * Turns the soft keyboard on or off. Off sets [WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM],
     * so the system keyboard can't pop up on its own (e.g. on focus, or as soon as a key is
     * typed) — keys then come straight to [onKeyDown] instead. Same mechanism Termux uses for
     * its "disable soft keyboard" toggle.
     */
    fun setSoftKeyboardEnabled(enabled: Boolean) {
        requestFocus()
        if (enabled) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            // The window has to re-bind to the IME after the flag clears before a show can land.
            post {
                imm?.restartInput(this)
                imm?.showSoftInput(this, 0)
            }
        } else {
            imm?.hideSoftInputFromWindow(windowToken, 0)
            window?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    override fun onDetachedFromWindow() {
        // The flag lives on the activity's window: don't leave the other screens' text fields
        // without a keyboard.
        window?.clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        super.onDetachedFromWindow()
    }
}
