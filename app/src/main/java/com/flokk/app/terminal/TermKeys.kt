package com.flokk.app.terminal

/** VT100/xterm byte sequences for the extra-keys row, matching Termux's default key set. */
object TermKeys {
    private fun esc(rest: String): ByteArray = byteArrayOf(0x1b) + rest.toByteArray(Charsets.US_ASCII)

    val ESC = esc("")
    val TAB = byteArrayOf(0x09)
    val ENTER = byteArrayOf(0x0d)
    val BACKSPACE = byteArrayOf(127)
    val UP = esc("[A")
    val DOWN = esc("[B")
    val RIGHT = esc("[C")
    val LEFT = esc("[D")
    val HOME = esc("[H")
    val END = esc("[F")
    val PAGE_UP = esc("[5~")
    val PAGE_DOWN = esc("[6~")
    val DELETE = esc("[3~")

    fun ctrl(letter: Char): ByteArray {
        val upper = letter.uppercaseChar()
        val code = (upper.code - 'A'.code + 1) and 0x1f
        return byteArrayOf(code.toByte())
    }

    fun alt(letter: Char): ByteArray = byteArrayOf(0x1b) + letter.toString().toByteArray(Charsets.UTF_8)
}
