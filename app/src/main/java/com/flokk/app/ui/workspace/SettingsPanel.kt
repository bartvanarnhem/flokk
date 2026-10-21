package com.flokk.app.ui.workspace

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TableRows
import androidx.compose.material.icons.rounded.ViewWeek
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flokk.app.data.AppSettings
import com.flokk.app.data.FontSizePreset
import com.flokk.app.data.TabLayout
import com.flokk.app.ui.theme.Flokk
import com.flokk.app.ui.theme.FlokkCard
import com.flokk.app.ui.theme.LabelledField
import com.flokk.app.ui.theme.Segment
import com.flokk.app.ui.theme.SegmentedControl

/**
 * App settings, opened in place under the workspace header. Every change applies immediately,
 * so there's nothing to confirm or cancel; the header's tune button closes it again.
 */
@Composable
fun SettingsPanel(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlokkCard(modifier = modifier, spacing = 10.dp) {
        LabelledField(label = "Font size") {
            val fontSizes = FontSizePreset.entries
            SegmentedControl(
                options = fontSizes.map { preset ->
                    // A sample "A" at a size that grows with the preset, like a type-size picker.
                    val sampleSize = when (preset) {
                        FontSizePreset.SMALL -> 12
                        FontSizePreset.STANDARD -> 15
                        FontSizePreset.BIG -> 18
                    }
                    Segment(preset.label, leading = { color ->
                        Text(
                            "A",
                            style = TextStyle(fontFamily = Flokk.sans, fontWeight = FontWeight.SemiBold, fontSize = sampleSize.sp, color = color),
                        )
                    })
                },
                selectedIndex = fontSizes.indexOf(settings.fontSize),
                onSelect = { i -> onChange { it.copy(fontSize = fontSizes[i]) } },
                track = Flokk.background,
            )
        }
        LabelledField(label = "Workspace tabs") {
            val layouts = TabLayout.entries
            SegmentedControl(
                options = layouts.map { layout ->
                    Segment(
                        layout.label,
                        icon = when (layout) {
                            TabLayout.SINGLE_ROW -> Icons.Rounded.ViewWeek
                            TabLayout.GROUP_BY_HOST -> Icons.Rounded.TableRows
                        },
                    )
                },
                selectedIndex = layouts.indexOf(settings.tabLayout),
                onSelect = { i -> onChange { it.copy(tabLayout = layouts[i]) } },
                track = Flokk.background,
            )
        }
    }
}
