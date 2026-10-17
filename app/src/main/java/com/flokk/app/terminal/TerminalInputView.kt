package com.flokk.app.terminal

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
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

    fun showKeyboard() {
        requestFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
    }
}
