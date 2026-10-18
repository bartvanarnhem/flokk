package com.flokk.app.terminal

/** Longest base name (extension excluded) an attachment's on-screen label shows before eliding. */
private const val LABEL_BASE_MAX = 12

/**
 * A file uploaded to the host and inserted into the prompt by its full [remotePath]. The host
 * (and so the agent) only ever sees that path; the canvas draws [label] in its place.
 */
class Attachment(val remotePath: String) {
    val label: String = shortLabel(remotePath.substringAfterLast('/'))

    /** Whether an agent will likely turn its pasted path into an `[Image N]` placeholder. */
    val isImage: Boolean = remotePath.substringAfterLast('.').lowercase() in IMAGE_EXTENSIONS
}

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp")

/** `[name.ext]`, with a base name past [LABEL_BASE_MAX] chars elided but the extension kept. */
internal fun shortLabel(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val (base, ext) = if (dot > 0) fileName.substring(0, dot) to fileName.substring(dot) else fileName to ""
    val shownBase = if (base.length > LABEL_BASE_MAX) base.take(LABEL_BASE_MAX - 1) + "…" else base
    return "[$shownBase$ext]"
}

/** Keeps only characters that need no quoting anywhere, so the inserted path is one plain token. */
internal fun sanitizeFileName(name: String): String {
    val cleaned = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.', '-').take(100)
    return cleaned.ifEmpty { "file" }
}
