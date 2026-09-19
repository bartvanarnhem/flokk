package com.flokk.app.ssh

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
