package com.orbisk.flokk.ssh

internal fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
