package com.tvtools.app.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.data.QuickKeys
import com.tvtools.app.data.RemoteLayout
import kotlin.math.roundToInt

/**
 * Per-TV remote customiser: column count, D-pad size, hidden sections,
 * one-handed dock and the order of the quick-key strip.
 *
 * Every change is pushed through [onPreview] so the sheet behind the dialog
 * updates live; [onSave] persists it for the current TV.
 */
@Composable
internal fun RemoteLayoutEditorDialog(
    layout: RemoteLayout,
    onPreview: (RemoteLayout) -> Unit,
    onDismiss: () -> Unit,
    onSave: (RemoteLayout) -> Unit,
) {
    var draft by remember { mutableStateOf(layout) }
    var addMenuOpen by remember { mutableStateOf(false) }

    fun update(next: RemoteLayout) {
        draft = next
        onPreview(next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Remote layout", fontWeight = FontWeight.Bold)
                Text(
                    "Saved for this TV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 470.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Columns", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(1, 2, 3).forEachIndexed { index, columns ->
                        SegmentedButton(
                            selected = draft.columns == columns,
                            onClick = { update(draft.copy(columns = columns)) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                        ) { Text("$columns") }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("D-pad size", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("${draft.dpadSize} dp", style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = draft.dpadSize.toFloat(),
                    onValueChange = { update(draft.copy(dpadSize = it.roundToInt())) },
                    valueRange = 200f..340f,
                    steps = 6,
                )

                SettingSwitch(
                    title = "One-handed mode",
                    subtitle = "Dock the D-pad at the bottom of the sheet",
                    checked = draft.oneHanded,
                    onChange = { update(draft.copy(oneHanded = it)) },
                )
                SettingSwitch(
                    title = "Expressive shapes",
                    subtitle = "Scalloped dial and springy press feedback",
                    checked = draft.expressive,
                    onChange = { update(draft.copy(expressive = it)) },
                )

                Spacer(Modifier.height(12.dp))
                Text("Show on the remote", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                RemoteLayout.sections.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { (id, label) ->
                            val shortLabel = when (id) {
                                "media" -> "Media"
                                "quick" -> "Quick keys"
                                "nav" -> "Navigation"
                                "app" -> "App keys"
                                "volume" -> "Volume"
                                else -> label.substringBefore(' ')
                            }
                            FilterChip(
                                selected = id !in draft.hidden,
                                onClick = {
                                    val hidden = if (id in draft.hidden) draft.hidden - id else draft.hidden + id
                                    update(draft.copy(hidden = hidden))
                                },
                                label = { Text(shortLabel) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Quick keys",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Box {
                        TextButton(onClick = { addMenuOpen = true }) {
                            Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Add")
                        }
                        DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                            QuickKeys.catalog
                                .filterNot { it.id in draft.quickOrder }
                                .forEach { key ->
                                    DropdownMenuItem(
                                        text = { Text(key.label) },
                                        onClick = {
                                            addMenuOpen = false
                                            update(draft.copy(quickOrder = draft.quickOrder + key.id))
                                        },
                                    )
                                }
                        }
                    }
                }
                if (draft.quickOrder.isEmpty()) {
                    Text(
                        "No quick keys — add a few for one-tap access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                draft.quickOrder.forEachIndexed { index, id ->
                    val key = QuickKeys.byId(id) ?: return@forEachIndexed
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(key.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val list = draft.quickOrder.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index - 1, item)
                                        update(draft.copy(quickOrder = list))
                                    }
                                },
                                enabled = index > 0,
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Move ${key.label} up", Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    if (index < draft.quickOrder.lastIndex) {
                                        val list = draft.quickOrder.toMutableList()
                                        val item = list.removeAt(index)
                                        list.add(index + 1, item)
                                        update(draft.copy(quickOrder = list))
                                    }
                                },
                                enabled = index < draft.quickOrder.lastIndex,
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Move ${key.label} down", Modifier.size(18.dp))
                            }
                            IconButton(onClick = {
                                update(draft.copy(quickOrder = draft.quickOrder - id))
                            }) {
                                Icon(Icons.Default.Close, "Remove ${key.label}", Modifier.size(16.dp))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        update(
                            RemoteLayout(
                                columns = 2,
                                dpadSize = 260,
                                quickOrder = QuickKeys.defaultIds,
                            ),
                        )
                    },
                ) {
                    Icon(Icons.Default.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Reset to default")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
