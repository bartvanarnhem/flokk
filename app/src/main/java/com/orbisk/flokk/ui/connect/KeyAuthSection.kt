package com.orbisk.flokk.ui.connect

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orbisk.flokk.ssh.GeneratedSshKey
import com.orbisk.flokk.ssh.SshKeyGenerator
import com.orbisk.flokk.ui.theme.Tui
import com.orbisk.flokk.ui.theme.TuiBox
import com.orbisk.flokk.ui.theme.TuiButton
import com.orbisk.flokk.ui.theme.TuiOutlineButton
import com.orbisk.flokk.ui.theme.TuiSegmentedRow
import com.orbisk.flokk.ui.theme.TuiTextField

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
    canInstall: Boolean,
    onInstall: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    TuiBox {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TuiSegmentedRow(
                options = listOf("use existing", "generate new"),
                selectedIndex = if (keyMode == KeyMode.EXISTING) 0 else 1,
                onSelect = { onKeyMode(if (it == 0) KeyMode.EXISTING else KeyMode.GENERATE) },
            )

            if (keyMode == KeyMode.EXISTING) {
                TuiTextField(
                    value = privateKey,
                    onValueChange = { onPrivateKey(it); onGenerated(null) },
                    label = "private key",
                    placeholder = "-----BEGIN OPENSSH PRIVATE KEY-----",
                    singleLine = false,
                    minLines = 3,
                )
                TuiTextField(
                    value = passphrase,
                    onValueChange = onPassphrase,
                    label = "passphrase (optional)",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "type", style = Tui.label, modifier = Modifier.padding(bottom = 4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Tui.inputBackground, RoundedCornerShape(5.dp))
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Text(text = "ed25519", style = Tui.body)
                        }
                    }
                    TuiTextField(
                        value = keyName,
                        onValueChange = onKeyName,
                        label = "name",
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onProtectWithPassphrase(!protectWithPassphrase) },
                    ),
                ) {
                    Text(
                        text = if (protectWithPassphrase) "[x] " else "[ ] ",
                        style = Tui.body.copy(color = Tui.accentGreen),
                    )
                    Text(text = "protect with passphrase", style = Tui.body.copy(color = Tui.textDim))
                }
                if (protectWithPassphrase) {
                    TuiTextField(
                        value = passphrase,
                        onValueChange = onPassphrase,
                        label = "passphrase",
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "[x] ", style = Tui.body.copy(color = Tui.accentGreen))
                    Text(
                        text = "stored encrypted in the device keystore",
                        style = Tui.body.copy(color = Tui.textDim),
                    )
                }

                TuiButton(
                    text = "generate keypair",
                    background = Tui.accentPurple,
                    contentColor = Tui.background,
                    onClick = {
                        val g = SshKeyGenerator.generateEd25519(
                            comment = keyName.trim().ifBlank { "flokk-android" },
                        )
                        onPrivateKey(g.privateKeyPem)
                        if (!protectWithPassphrase) onPassphrase("")
                        onGenerated(g)
                    },
                )

                generated?.let { g ->
                    TuiBox(borderColor = Tui.border) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "✓ generated ${keyName.ifBlank { "key" }} (ed25519)",
                                style = Tui.label.copy(color = Tui.accentGreen),
                            )
                            Text(text = g.fingerprint, style = Tui.label)
                            Text(
                                text = g.publicKeyLine,
                                style = Tui.label.copy(color = Tui.textFaint),
                                maxLines = 3,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                TuiOutlineButton(
                                    text = "copy pubkey",
                                    modifier = Modifier.weight(1f),
                                    onClick = { clipboard.setText(AnnotatedString(g.publicKeyLine)) },
                                )
                                TuiOutlineButton(
                                    text = "share",
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, g.publicKeyLine)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share public key"))
                                    },
                                )
                            }
                        }
                    }

                    TuiBox(borderColor = Tui.border) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(text = "install on host", style = Tui.label)
                            Text(
                                text = "authorize once with a password, then herdr appends the " +
                                    "pubkey to authorized_keys.",
                                style = Tui.label.copy(color = Tui.textDim),
                            )
                            TuiOutlineButton(
                                text = "install via one-time password auth",
                                enabled = canInstall,
                                onClick = onInstall,
                            )
                        }
                    }
                }
            }
        }
    }
}
