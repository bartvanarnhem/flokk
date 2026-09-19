package com.flokk.app.ssh

import net.schmizz.sshj.connection.channel.direct.Session
import java.io.InputStream
import java.io.OutputStream

/**
 * A live interactive PTY channel produced by [SshTransport.openAttach]: bytes written to
 * [outputStream] are keystrokes sent to the remote `herdr terminal attach` process, bytes
 * read from [inputStream] are its rendered terminal output.
 */
class AttachChannel internal constructor(
    private val session: Session,
    private val shell: Session.Shell,
    private val sessionLock: Any,
    private val onClose: () -> Unit = {},
) {
    val inputStream: InputStream get() = shell.inputStream
    val outputStream: OutputStream get() = shell.outputStream
    val isOpen: Boolean get() = shell.isOpen

    fun resize(cols: Int, rows: Int) {
        runCatching { shell.changeWindowDimensions(cols, rows, 0, 0) }
    }

    /** Synchronized on the same lock [SshTransport] opens channels under — see its comment. */
    fun close() {
        synchronized(sessionLock) { runCatching { session.close() } }
        onClose()
    }
}
