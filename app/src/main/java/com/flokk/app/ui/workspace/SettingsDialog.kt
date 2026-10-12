package com.flokk.app.ui.workspace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.flokk.app.data.AppSettings
import com.flokk.app.data.FontSizePreset
import com.flokk.app.data.TabLayout
import com.flokk.app.ui.theme.Tui
import com.flokk.app.ui.theme.TuiSegmentedRow

/** App settings; every change applies immediately, so there's nothing to confirm or cancel. */
@Composable
fun SettingsDialog(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("font size", style = Tui.label)
                val fontSizes = FontSizePreset.entries
                TuiSegmentedRow(
                    options = fontSizes.map { it.label },
                    selectedIndex = fontSizes.indexOf(settings.fontSize),
                    onSelect = { i -> onChange { it.copy(fontSize = fontSizes[i]) } },
                )
                Text("workspace tabs", style = Tui.label)
                val layouts = TabLayout.entries
                TuiSegmentedRow(
                    options = layouts.map { it.label },
                    selectedIndex = layouts.indexOf(settings.tabLayout),
                    onSelect = { i -> onChange { it.copy(tabLayout = layouts[i]) } },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}
