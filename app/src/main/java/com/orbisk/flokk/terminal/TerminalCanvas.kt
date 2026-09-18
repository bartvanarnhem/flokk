package com.orbisk.flokk.terminal

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbisk.flokk.ui.theme.Tui
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

private const val TRUECOLOR_MASK = -0x1000000 // top byte 0xFF => already a resolved ARGB color

/** A request to coast; a class rather than a data class so each one is a distinct instance. */
private class FlingRequest(val velocity: Float)

/** A cell coordinate into the visible screen buffer, in the same (row, col) space as [drawTerminalFrame]. */
private data class CellPos(val row: Int, val col: Int)

private fun CellPos.orderedWith(other: CellPos): Pair<CellPos, CellPos> =
    if (row < other.row || (row == other.row && col <= other.col)) this to other else other to this

/** A URL detected on one visible row, in cell columns, so both drawing and tap hit-testing
 *  can use the exact same span. */
private data class TerminalLink(val row: Int, val startCol: Int, val endColExclusive: Int, val url: String)

private val URL_REGEX = Regex("""(?:https?://|www\.)[^\s<>"']+""")

/** Trims characters unlikely to be part of the link itself — a sentence's closing period, a
 *  wrapping paren/quote — from the end of a raw regex match. */
private fun trimTrailingPunctuation(match: String): String {
    var end = match.length
    while (end > 0 && match[end - 1] in ".,;:!?)]}'\"") end--
    return match.substring(0, end)
}

private fun findLinksInRow(text: String): List<Pair<IntRange, String>> =
    URL_REGEX.findAll(text).mapNotNull { m ->
        val trimmed = trimTrailingPunctuation(m.value)
        if (trimmed.isEmpty()) null else (m.range.first..(m.range.first + trimmed.length - 1)) to trimmed
    }.toList()

/**
 * Renders a [RemoteTerminalSession]'s screen buffer, reproducing the color/attribute
 * decoding Termux's own `TerminalRenderer` performs, so a herdr pane looks the same here
 * as it would attached to directly in Termux.
 *
 * Simplification: each buffer column is treated as exactly one monospace cell (no
 * double-width CJK/emoji handling), which covers the shells/CLIs/editors a coding-agent
 * pane typically runs.
 */
@Composable
fun TerminalCanvasView(
    session: RemoteTerminalSession,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val decaySpec = remember(density) { splineBasedDecay<Float>(density) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val paint = remember {
        Paint().apply {
            typeface = Typeface.MONOSPACE
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }
    }
    var selectionAnchor by remember(session) { mutableStateOf<CellPos?>(null) }
    var selectionCursor by remember(session) { mutableStateOf<CellPos?>(null) }
    var copiedTick by remember(session) { mutableIntStateOf(0) }
    var showCopiedBadge by remember(session) { mutableStateOf(false) }
    var linkErrorTick by remember(session) { mutableIntStateOf(0) }
    var showLinkError by remember(session) { mutableStateOf(false) }

    // How far back into history we are, in *pixels* rather than rows: 0 = pinned to the live
    // screen. A one-finger swipe (distinct from the long-press-then-drag used for text
    // selection, below) walks this back and forth through the emulator's existing scrollback
    // buffer — no new state to fetch, `TerminalBuffer` already keeps the transcript. Tracking
    // pixels and drawing the sub-row remainder as an offset is what lets the text follow the
    // finger continuously; quantizing to whole rows made a slow drag lurch a row at a time.
    var scrollPx by remember(session) { mutableFloatStateOf(0f) }

    // Finger travel not yet converted into scroll steps for a program we forward to (the
    // non-transcript modes are inherently stepped: one wheel notch or arrow key at a time).
    var remoteScrollAccumPx by remember(session) { mutableFloatStateOf(0f) }

    // Non-null while a fling is meant to be running. A plain class, so every request is a
    // distinct instance and restarts the animation; setting it to null cancels one mid-flight.
    var fling by remember(session) { mutableStateOf<FlingRequest?>(null) }

    // Links detected on the frame just drawn, in cell coordinates, for tap hit-testing.
    // Plain mutable state (not a Compose State) deliberately: it's rebuilt every draw and
    // only ever read back from the same-thread gesture callback below, so there's nothing to
    // observe — turning it into a State would just cause pointless recomposition on redraw.
    val links = remember(session) { mutableListOf<TerminalLink>() }

    LaunchedEffect(session) {
        while (isActive) {
            withFrameNanos { }
            session.pumpFrameIfDirty()
        }
    }

    // Briefly flash a "copied" confirmation, then drop the highlight.
    LaunchedEffect(copiedTick) {
        if (copiedTick == 0) return@LaunchedEffect
        showCopiedBadge = true
        delay(900)
        showCopiedBadge = false
        selectionAnchor = null
        selectionCursor = null
    }

    // Same for a failed link-open, so a broken intent resolution is visible instead of a tap
    // that silently does nothing.
    LaunchedEffect(linkErrorTick) {
        if (linkErrorTick == 0) return@LaunchedEffect
        showLinkError = true
        delay(1500)
        showLinkError = false
    }

    BoxWithConstraints(modifier = modifier) {
        val fontSizePx = with(density) { fontSizeSp.sp.toPx() }
        paint.textSize = fontSizePx
        val cellWidth = max(1f, paint.measureText("X"))
        val fm = paint.fontMetrics
        val cellHeight = max(1f, fm.descent - fm.ascent + fm.leading)
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // Two is Termux's own floor: TerminalEmulator rejects anything smaller, and the box
        // really can get that short for a frame while the keyboard animates in.
        val cols = max(2, (widthPx / cellWidth).toInt())
        val rows = max(2, (heightPx / cellHeight).toInt())

        // Keyed on `session` too, not just `cols`/`rows`: switching workspace tabs swaps in a
        // different session without necessarily changing the terminal's pixel size, and an
        // as-yet-unattached one still has to be started — otherwise its emulator is never
        // initialized and the pane stays permanently blank (isReady never becomes true).
        LaunchedEffect(session, cols, rows) {
            session.startOrResize(cols, rows)
        }

        // The whole rows we're scrolled back, plus the leftover fraction of a row, which the
        // drawing below shifts everything down by.
        val scrollRows = floor(scrollPx / cellHeight).toInt()
        val subRowPx = scrollPx - scrollRows * cellHeight

        fun maxScrollPx(): Float =
            (session.withEmulator { it.screen.activeTranscriptRows } ?: 0) * cellHeight

        fun cellAt(offset: Offset): CellPos = CellPos(
            row = floor((offset.y - subRowPx) / cellHeight).toInt().coerceIn(0, rows - 1),
            col = (offset.x / cellWidth).toInt().coerceIn(0, cols - 1),
        )

        fun copySelection() {
            val anchor = selectionAnchor
            val cursor = selectionCursor
            if (anchor == null || cursor == null) return
            val (start, end) = anchor.orderedWith(cursor)
            // Screen rows are relative to the live screen, so scrolled-back rows are negative:
            // without this, selecting in history copied whatever sits at the same position on
            // the live screen instead.
            val text = session.withEmulator {
                it.screen.getSelectedText(start.col, start.row - scrollRows, end.col, end.row - scrollRows)
            }
            if (!text.isNullOrEmpty()) {
                clipboard.setText(AnnotatedString(text))
                copiedTick++
            } else {
                selectionAnchor = null
                selectionCursor = null
            }
        }

        fun linkAt(cell: CellPos): TerminalLink? =
            links.firstOrNull { it.row == cell.row && cell.col >= it.startCol && cell.col < it.endColExclusive }

        fun openLink(url: String) {
            val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
            val opened = runCatching {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(normalized))
                intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                context.startActivity(intent)
            }.isSuccess
            if (!opened) linkErrorTick++
        }

        // Coast after a flick, with the platform's own fling physics so it decelerates like
        // every other scrollable surface on the device.
        LaunchedEffect(fling) {
            val request = fling ?: return@LaunchedEffect
            val limit = maxScrollPx()
            AnimationState(initialValue = scrollPx, initialVelocity = request.velocity)
                .animateDecay(decaySpec) {
                    val clamped = value.coerceIn(0f, limit)
                    scrollPx = clamped
                    if (clamped != value) cancelAnimation() // hit the top or the live screen
                }
            fling = null
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    // The partially-scrolled rows above and below the viewport are drawn, not
                    // clipped to whole rows, so they must not bleed outside the terminal.
                    .clipToBounds()
                    .pointerInput(cellWidth, cellHeight, cols, rows) {
                        // One gesture recognizer deciding between three mutually-exclusive
                        // outcomes, instead of three independent detectors racing each other
                        // on the same touch stream: a plain tap-and-release (open a link /
                        // clear selection / jump to live), an early drag (scroll), or holding
                        // still past the long-press threshold (select). Two separate detectors
                        // — one for long-press-drag, one for a plain drag — sounds equivalent,
                        // but isn't: the plain-drag one has no minimum-movement threshold of
                        // its own, so it "wins" almost every long-press attempt on the tiny
                        // hand tremor a real finger can't help but make while holding still,
                        // making selection nearly impossible to trigger in practice.
                        detectTerminalGestures(
                            onSelectStart = { offset ->
                                val cell = cellAt(offset)
                                selectionAnchor = cell
                                selectionCursor = cell
                            },
                            onSelectDrag = { offset -> selectionCursor = cellAt(offset) },
                            onSelectEnd = { copySelection() },
                            onScroll = { dy, position ->
                                fling = null // a new drag takes over from any fling still running
                                when (session.scrollMode()) {
                                    RemoteTerminalSession.ScrollMode.TRANSCRIPT -> {
                                        scrollPx = (scrollPx + dy).coerceIn(0f, maxScrollPx())
                                    }
                                    // One step per row of finger travel: measured against the
                                    // agent CLIs, a wheel notch moves them about one line, the
                                    // same as an arrow key, so this tracks the finger roughly
                                    // 1:1. It can't be finer — both are whole-line steps.
                                    else -> {
                                        val cell = cellAt(position)
                                        remoteScrollAccumPx += dy
                                        val steps = (remoteScrollAccumPx / cellHeight).toInt()
                                        if (steps != 0) {
                                            remoteScrollAccumPx -= steps * cellHeight
                                            session.scrollRemotely(steps, cell.col, cell.row)
                                        }
                                    }
                                }
                            },
                            // Only our own transcript coasts: a fling forwarded as a burst of
                            // wheel notches would keep arriving — and scrolling — long after
                            // the finger left, since each one costs a remote round trip.
                            onScrollEnd = { velocityY ->
                                remoteScrollAccumPx = 0f
                                val coasts = session.scrollMode() ==
                                    RemoteTerminalSession.ScrollMode.TRANSCRIPT
                                if (coasts && abs(velocityY) > 1f) fling = FlingRequest(velocityY)
                            },
                            onTap = { offset ->
                                fling = null
                                val cell = cellAt(offset)
                                val link = linkAt(cell)
                                when {
                                    link != null -> openLink(link.url)
                                    scrollPx != 0f -> scrollPx = 0f
                                    else -> {
                                        selectionAnchor = null
                                        selectionCursor = null
                                    }
                                }
                            },
                        )
                    },
            ) {
                @Suppress("UNUSED_EXPRESSION")
                session.frame // establish a draw-phase read so this redraws when new bytes arrive
                if (!session.isReady) return@Canvas
                drawIntoCanvas { canvas ->
                    session.withEmulator { emulator ->
                        drawTerminalFrame(
                            canvas.nativeCanvas,
                            emulator,
                            paint,
                            cellWidth,
                            cellHeight,
                            scrollRows,
                            subRowPx,
                            links,
                        )
                    }
                }
                val anchor = selectionAnchor
                val cursor = selectionCursor
                if (anchor != null && cursor != null) {
                    drawSelectionHighlight(anchor.orderedWith(cursor), cols, cellWidth, cellHeight, subRowPx)
                }
            }

            // `isReady` only means the local emulator was constructed, not that the SSH
            // attach actually succeeded — [RemoteTerminalSession.start] sets it up front so
            // the canvas has somewhere to draw while the real attach happens asynchronously.
            // Without checking `error` too, a failed attach silently looks identical to an
            // empty-but-healthy pane: just a bare cursor, forever.
            val attachError = session.error
            if (attachError != null && session.finished) {
                Text(
                    text = "attach failed: $attachError",
                    style = Tui.label.copy(color = Tui.accentRed),
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            }

            if (showCopiedBadge) {
                Text(
                    text = "copied",
                    style = Tui.label.copy(color = Tui.background),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Tui.accentGreen, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else if (showLinkError) {
                Text(
                    text = "couldn't open link",
                    style = Tui.label.copy(color = Tui.background),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Tui.accentRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else if (scrollRows != 0) {
                Text(
                    text = "history -$scrollRows · tap to jump to live",
                    style = Tui.label.copy(color = Tui.background),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp)
                        .background(Tui.accentYellow, RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { fling = null; scrollPx = 0f },
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Disambiguates a single touch sequence into exactly one of: a tap, a scroll drag, or a
 * long-press-then-drag selection — see the call site for why this must be one combined
 * recognizer rather than several independent ones.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTerminalGestures(
    longPressTimeoutMillis: Long = 500L,
    onSelectStart: (Offset) -> Unit,
    onSelectDrag: (Offset) -> Unit,
    onSelectEnd: () -> Unit,
    onScroll: (dy: Float, position: Offset) -> Unit,
    onScrollEnd: (velocityY: Float) -> Unit,
    onTap: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        // Where the drag is considered to have begun: the point at which it crossed the slop
        // threshold, not the touch-down point, so the first frame of scrolling doesn't jump by
        // the slop distance the finger spent getting there.
        var dragOrigin = down.position

        val outcome = withTimeoutOrNull(longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull "up"
                if (!change.pressed) return@withTimeoutOrNull "up"
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > slop || abs(dy) > slop) {
                    dragOrigin = change.position
                    return@withTimeoutOrNull "moved"
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        }

        when (outcome) {
            null -> {
                // Timed out while the finger was still within slop: a genuine long press.
                onSelectStart(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    onSelectDrag(change.position)
                }
                onSelectEnd()
            }
            "moved" -> {
                val velocity = VelocityTracker()
                velocity.addPosition(down.uptimeMillis, dragOrigin)
                var lastY = dragOrigin.y
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    velocity.addPosition(change.uptimeMillis, change.position)
                    onScroll(change.position.y - lastY, change.position)
                    lastY = change.position.y
                }
                onScrollEnd(velocity.calculateVelocity().y)
            }
            else -> onTap(down.position)
        }
    }
}

/** Highlights the selected cell range with the flokk TUI's focused-yellow accent. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSelectionHighlight(
    selection: Pair<CellPos, CellPos>,
    columns: Int,
    cellWidth: Float,
    cellHeight: Float,
    pixelOffset: Float,
) {
    val (start, end) = selection
    // Termux's own long-press selection tint is a translucent blue, not an app accent color.
    val highlight = Color(0x6633B5E5)
    for (row in start.row..end.row) {
        val fromCol = if (row == start.row) start.col else 0
        val toColExclusive = if (row == end.row) end.col + 1 else columns
        if (toColExclusive <= fromCol) continue
        drawRect(
            color = highlight,
            topLeft = Offset(fromCol * cellWidth, row * cellHeight + pixelOffset),
            size = androidx.compose.ui.geometry.Size((toColExclusive - fromCol) * cellWidth, cellHeight),
        )
    }
}

private fun drawTerminalFrame(
    canvas: android.graphics.Canvas,
    emulator: TerminalEmulator,
    paint: Paint,
    cellWidth: Float,
    cellHeight: Float,
    scrollRows: Int = 0,
    pixelOffset: Float = 0f,
    linksOut: MutableList<TerminalLink>? = null,
) {
    val screen = emulator.screen
    val columns = emulator.mColumns
    val rows = emulator.mRows
    val palette = TerminalColors.COLOR_SCHEME.mDefaultColors
    val defaultBackground = palette[TextStyle.COLOR_INDEX_BACKGROUND]
    val fm = paint.fontMetrics
    val ascent = -fm.ascent
    val topRow = -scrollRows

    val rowChars = CharArray(columns)
    linksOut?.clear()

    // One row beyond the viewport at each end: when [pixelOffset] shifts everything down by
    // part of a row, those are the rows that fill the sliver left over at the top and bottom.
    for (row in -1..rows) {
        val externalRow = topRow + row
        if (externalRow < -screen.activeTranscriptRows || externalRow >= rows) continue
        val lineObject = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(externalRow))
        val used = lineObject.spaceUsed.coerceIn(0, columns)
        rowChars.fill(' ')
        System.arraycopy(lineObject.mText, 0, rowChars, 0, used)

        val top = row * cellHeight + pixelOffset
        val baseline = top + ascent

        var col = 0
        while (col < columns) {
            val style = lineObject.getStyle(col)
            var runEnd = col + 1
            while (runEnd < columns && lineObject.getStyle(runEnd) == style) runEnd++

            drawRun(canvas, paint, rowChars, col, runEnd, style, palette, defaultBackground, top, baseline, cellWidth, cellHeight)
            col = runEnd
        }

        if (linksOut != null) {
            for ((range, url) in findLinksInRow(String(rowChars, 0, used))) {
                linksOut.add(TerminalLink(row, range.first, range.last + 1, url))
                val underlineTop = top + cellHeight - max(1.5f, cellHeight * 0.06f)
                paint.color = Tui.accentBlue.toArgb()
                canvas.drawRect(
                    range.first * cellWidth,
                    underlineTop,
                    (range.last + 1) * cellWidth,
                    top + cellHeight,
                    paint,
                )
            }
        }
    }

    if (scrollRows == 0 && emulator.shouldCursorBeVisible()) {
        val cursorCol = emulator.cursorCol.coerceIn(0, columns - 1)
        val cursorRow = emulator.cursorRow.coerceIn(0, rows - 1)
        val left = cursorCol * cellWidth
        val top = cursorRow * cellHeight + pixelOffset
        paint.color = palette[TextStyle.COLOR_INDEX_CURSOR]
        paint.alpha = 160
        canvas.drawRect(left, top, left + cellWidth, top + cellHeight, paint)
        paint.alpha = 255
    }
}

private fun drawRun(
    canvas: android.graphics.Canvas,
    paint: Paint,
    rowChars: CharArray,
    startCol: Int,
    endCol: Int,
    style: Long,
    palette: IntArray,
    defaultBackground: Int,
    top: Float,
    baseline: Float,
    cellWidth: Float,
    cellHeight: Float,
) {
    val effect = TextStyle.decodeEffect(style)
    val bold = (effect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_BLINK)) != 0
    val underline = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
    val italic = (effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0
    val strikeThrough = (effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH) != 0
    val dim = (effect and TextStyle.CHARACTER_ATTRIBUTE_DIM) != 0
    val invisible = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE) != 0
    val inverse = (effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0

    var foreRaw = TextStyle.decodeForeColor(style)
    if (bold && foreRaw in 0..7) foreRaw += 8
    var foreColor = resolveColor(foreRaw, palette)
    var backColor = resolveColor(TextStyle.decodeBackColor(style), palette)

    if (inverse) {
        val tmp = foreColor
        foreColor = backColor
        backColor = tmp
    }
    if (dim) {
        val r = (0xFF and (foreColor shr 16)) * 2 / 3
        val g = (0xFF and (foreColor shr 8)) * 2 / 3
        val b = (0xFF and foreColor) * 2 / 3
        foreColor = -0x1000000 or (r shl 16) or (g shl 8) or b
    }

    val left = startCol * cellWidth
    val right = endCol * cellWidth

    if (backColor != defaultBackground) {
        paint.color = backColor
        canvas.drawRect(left, top, right, top + cellHeight, paint)
    }

    if (!invisible) {
        paint.color = foreColor
        paint.isFakeBoldText = bold
        paint.isUnderlineText = underline
        paint.isStrikeThruText = strikeThrough
        paint.textSkewX = if (italic) -0.35f else 0f
        if (isPlainAscii(rowChars, startCol, endCol)) {
            canvas.drawText(rowChars, startCol, endCol - startCol, left, baseline, paint)
        } else {
            drawCellByCell(canvas, paint, rowChars, startCol, endCol, baseline, cellWidth)
        }
    }
}

private fun isPlainAscii(rowChars: CharArray, startCol: Int, endCol: Int): Boolean {
    for (i in startCol until endCol) if (rowChars[i].code >= 0x80) return false
    return true
}

/**
 * Draws a run that contains non-ASCII glyphs one cell at a time, pinned to the cell grid and
 * scaled to exactly one cell wide.
 *
 * [Typeface.MONOSPACE] is only monospaced for the characters it actually covers: arrows, box
 * drawing, braille spinners and the like come from fallback fonts whose advance is its own
 * width, not the cell's (here 21px against a 17px cell). Letting one `drawText` lay out the
 * whole run therefore walks every following glyph off the grid, which shows up as the block
 * cursor sitting half over the character before it. Scaling rather than merely positioning
 * each glyph is what keeps box-drawing runs joined up instead of dashed.
 */
private fun drawCellByCell(
    canvas: android.graphics.Canvas,
    paint: Paint,
    rowChars: CharArray,
    startCol: Int,
    endCol: Int,
    baseline: Float,
    cellWidth: Float,
) {
    val baseScaleX = paint.textScaleX
    // Runs of one repeated glyph (border and block-drawing rows) are the common case here, and
    // the paint doesn't change within a run, so remembering the last advance avoids remeasuring
    // the same character for every cell it fills.
    var measuredChar = '\u0000'
    var measuredAdvance = 0f

    var col = startCol
    while (col < endCol) {
        val char = rowChars[col]
        val pairedSurrogate = Character.isHighSurrogate(char) &&
            col + 1 < endCol &&
            Character.isLowSurrogate(rowChars[col + 1])
        val charCount = if (pairedSurrogate) 2 else 1

        if (char != ' ') {
            val advance = if (charCount == 1 && char == measuredChar) {
                measuredAdvance
            } else {
                paint.measureText(rowChars, col, charCount).also {
                    if (charCount == 1) {
                        measuredChar = char
                        measuredAdvance = it
                    }
                }
            }
            val cells = if (pairedSurrogate) 2 else 1
            paint.textScaleX = if (advance > 0f) baseScaleX * (cellWidth * cells / advance) else baseScaleX
            canvas.drawText(rowChars, col, charCount, col * cellWidth, baseline, paint)
            paint.textScaleX = baseScaleX
        }
        col += charCount
    }
}

private fun resolveColor(raw: Int, palette: IntArray): Int =
    if ((raw and TRUECOLOR_MASK) == TRUECOLOR_MASK) raw else palette[raw.coerceIn(0, palette.size - 1)]
