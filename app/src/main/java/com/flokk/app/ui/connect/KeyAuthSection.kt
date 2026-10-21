package com.flokk.app.ui.connect

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flokk.app.data.AuthMethod
import com.flokk.app.data.ConnectionProfile
import com.flokk.app.ssh.GeneratedSshKey
import com.flokk.app.ssh.SshKeyGenerator
import com.flokk.app.ui.theme.Chip
import com.flokk.app.ui.theme.ChipGroup
import com.flokk.app.ui.theme.Flokk
import com.flokk.app.ui.theme.FlokkCard
import com.flokk.app.ui.theme.FlokkTextField
import com.flokk.app.ui.theme.IconLine
import com.flokk.app.ui.theme.LabelledField
import com.flokk.app.ui.theme.Segment
import com.flokk.app.ui.theme.SegmentedControl
import com.flokk.app.ui.theme.TonalButton
import com.flokk.app.ui.theme.ToggleRow

internal enum class KeyMode { EXISTING, GENERATE }

@Composable
internal fun KeyAuthSection(
    keyMode: KeyMode,
    onKeyMode: (KeyMode) -> Unit,
    privateKey: String,
    onPrivateKey: (String) -> Unit,
    passphrase: String,
    onPassphrase: (String) -> Unit,
    keyName: String,
    onKeyName: (String) -> Unit,
    protectWithPassphrase: Boolean,
    onProtectWithPassphrase: (Boolean) -> Unit,
    generated: GeneratedSshKey?,
    onGenerated: (GeneratedSshKey?) -> Unit,
    /** Other saved connections that sign in with a key, offered for reuse. */
    reusableKeys: List<ConnectionProfile>,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    FlokkCard(spacing = 10.dp) {
        SegmentedControl(
            options = listOf(Segment("Use existing"), Segment("Generate new")),
            selectedIndex = if (keyMode == KeyMode.EXISTING) 0 else 1,
            onSelect = { onKeyMode(if (it == 0) KeyMode.EXISTING else KeyMode.GENERATE) },
            track = Flokk.background,
            height = 30.dp,
        )

        if (keyMode == KeyMode.EXISTING) {
            // Reusing another connection's key copies it (and its passphrase) into this one, so
            // several hosts that trust the same key don't each need it pasted in again.
            if (reusableKeys.isNotEmpty()) {
                LabelledField(label = "Reuse key from") {
                    ChipGroup {
                        reusableKeys.forEach { other ->
                            val key = other.auth as AuthMethod.PrivateKey
                            Chip(
                                text = other.displayName,
                                selected = privateKey.isNotBlank() && key.pem == privateKey,
                                onClick = {
                                    onPrivateKey(key.pem)
                                    onPassphrase(key.passphrase.orEmpty())
                                    onGenerated(null)
                                },
                            )
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FlokkTextField(
                    value = privateKey,
                    onValueChange = { onPrivateKey(it); onGenerated(null) },
                    label = "Private key",
                    placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                    mono = true,
                    singleLine = false,
                    minHeight = 80.dp,
                    background = Flokk.background,
                )
                IconLine(
                    icon = Icons.Rounded.ContentPaste,
                    text = "Paste from clipboard",
                    color = Flokk.accent,
                    onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                            onPrivateKey(it.trim() + "\n")
                            onGenerated(null)
                        }
                    },
                )
            }
            FlokkTextField(
                value = passphrase,
                onValueChange = onPassphrase,
                label = "Passphrase (optional)",
                background = Flokk.background,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabelledField(label = "Type", modifier = Modifier.width(110.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .background(Flokk.raised, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text("ed25519", style = TextStyle(fontFamily = Flokk.mono, fontSize = 14.sp, color = Flokk.text))
                    }
                }
                FlokkTextField(
                    value = keyName,
                    onValueChange = onKeyName,
                    label = "Key name",
                    mono = true,
                    background = Flokk.background,
                    modifier = Modifier.weight(1f),
                )
            }

            ToggleRow(
                title = "Protect with passphrase",
                checked = protectWithPassphrase,
                onCheckedChange = onProtectWithPassphrase,
                divider = false,
            )
            if (protectWithPassphrase) {
                FlokkTextField(
                    value = passphrase,
                    onValueChange = onPassphrase,
                    background = Flokk.background,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            IconLine(icon = Icons.Rounded.Lock, text = "Stored encrypted in the device keystore", color = Flokk.green)

            if (generated == null) {
                TonalButton(
                    text = "Generate key pair",
                    leadingIcon = Icons.Rounded.Key,
                    height = 36.dp,
                    fontSize = 15f,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val g = SshKeyGenerator.generateEd25519(comment = keyName.trim().ifBlank { "flokk-android" })
                        onPrivateKey(g.privateKeyPem)
                        if (!protectWithPassphrase) onPassphrase("")
                        onGenerated(g)
                    },
                )
            } else {
                FlokkCard(background = Flokk.greenBackground, radius = 10.dp, spacing = 10.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Flokk.green, modifier = Modifier.size(18.dp))
                        Text(
                            "Generated ${keyName.ifBlank { "key" }} (ed25519)",
                            style = Flokk.button.copy(color = Flokk.green),
                        )
                    }
                    val keyText = TextStyle(fontFamily = Flokk.mono, fontSize = 11.5.sp, lineHeight = 16.sp, color = Flokk.textSecondary)
                    Text(generated.fingerprint, style = keyText)
                    Text(generated.publicKeyLine, style = keyText.copy(color = Flokk.textFaint))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TonalButton(
                            text = "Copy public key",
                            leadingIcon = Icons.Rounded.ContentCopy,
                            iconSize = 17.dp,
                            height = 32.dp,
                            background = Flokk.background,
                            modifier = Modifier.weight(1f),
                            onClick = { clipboard.setText(AnnotatedString(generated.publicKeyLine)) },
                        )
                        TonalButton(
                            text = "Share",
                            leadingIcon = Icons.Rounded.Share,
                            iconSize = 17.dp,
                            height = 32.dp,
                            background = Flokk.background,
                            modifier = Modifier.width(96.dp),
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, generated.publicKeyLine)
                                }
                                context.startActivity(Intent.createChooser(send, "Share public key"))
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Installs a freshly generated public key on the host, authorizing once with a password. */
@Composable
internal fun InstallKeyCard(
    target: String,
    password: String,
    onPassword: (String) -> Unit,
    installing: Boolean,
    result: Result<Unit>?,
    canInstall: Boolean,
    onInstall: () -> Unit,
) {
    FlokkCard {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Install key on $target", style = Flokk.cardTitle)
            Text(
                "Authorize once with a password, then herdr appends the pubkey to authorized_keys.",
                style = Flokk.small,
            )
        }
        FlokkTextField(
            value = password,
            onValueChange = onPassword,
            label = "One-time password",
            helper = "Used only for this install, never saved.",
            background = Flokk.background,
            enabled = !installing,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        )
        TonalButton(
            text = if (installing) "Installing…" else "Install",
            leadingIcon = Icons.Rounded.Upload,
            height = 36.dp,
            fontSize = 15f,
            fontWeight = FontWeight.SemiBold,
            enabled = canInstall && password.isNotBlank() && !installing,
            modifier = Modifier.fillMaxWidth(),
            onClick = onInstall,
        )
        result?.fold(
            onSuccess = {
                IconLine(icon = Icons.Rounded.CheckCircle, text = "Installed — key appended to authorized_keys.", color = Flokk.green)
            },
            onFailure = {
                IconLine(
                    icon = Icons.Rounded.Error,
                    text = "Install failed: ${it.message ?: it.javaClass.simpleName}",
                    color = Flokk.red,
                )
            },
        )
    }
}
