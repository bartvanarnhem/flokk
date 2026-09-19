package com.flokk.app.ui.workspace

import com.termux.terminal.TerminalEmulator

/** One choice detected in a live prompt: [send] is the literal keystroke(s) to answer with. */
data class DetectedChoice(val send: String, val label: String)

data class DetectedPrompt(val choices: List<DetectedChoice>)

private val NUMBERED_LINE = Regex("""^[\s│|]*[❯>➤▸*•]?\s*(\d{1,2})[.):]\s+(.{1,40}?)\s*$""")
private val LETTER_CHOICE = Regex("""\(([A-Za-z0-9])\)\s?([A-Za-z][\w '/-]{0,20})""")
private val YN_BRACKET = Regex("""[(\[]\s*[Yy]\s*/\s*[Nn]\s*[)\]]""")

/**
 * Looks for a coding agent's own interactive prompt in the terminal's *currently visible*
 * screen — never the scrollback, since a question only matters while it's still live — and
 * turns it into real, tappable choices. Deliberately agent-agnostic: it pattern-matches
 * plain text (numbered menus, "(Y)es/(N)o"-style shortcuts, bare "(y/n)"), the same way a
 * person reads the screen, rather than special-casing Claude/Cursor/etc. Returns null when
 * nothing recognizable is there — no fallback banner, no guessing.
 */
fun detectPrompt(screenLines: List<String>): DetectedPrompt? {
    val tail = screenLines.takeLast(12).filter { it.isNotBlank() }
    if (tail.isEmpty()) return null

    // 1) A numbered menu, e.g. "❯ 1. Yes" / "  2. No" — two or more such lines together.
    val numbered = tail.mapNotNull { line ->
        NUMBERED_LINE.find(line)?.let { m -> DetectedChoice(send = m.groupValues[1], label = m.groupValues[2].trim()) }
    }
    if (numbered.size >= 2) return DetectedPrompt(numbered.take(5))

    // 2) Lettered shortcuts inline on one line, e.g. "(Y)es (N)o (A)ll".
    val lastLine = tail.last()
    val letters = LETTER_CHOICE.findAll(lastLine)
        .map { m -> DetectedChoice(send = m.groupValues[1], label = m.groupValues[1] + m.groupValues[2].trim()) }
        .toList()
    if (letters.size >= 2) return DetectedPrompt(letters.take(5))

    // 3) A bare yes/no prompt, e.g. "Continue? (y/n)".
    if (tail.any { YN_BRACKET.containsMatchIn(it) }) {
        return DetectedPrompt(listOf(DetectedChoice("y", "yes"), DetectedChoice("n", "no")))
    }

    return null
}

/** The emulator's live screen (not scrollback), one trimmed string per row, top to bottom. */
fun TerminalEmulator.currentScreenLines(): List<String> {
    val cols = mColumns
    return (0 until mRows).map { row ->
        val lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(row))
        val used = lineObject.spaceUsed.coerceIn(0, cols)
        String(lineObject.mText, 0, used).trimEnd()
    }
}
