package com.tvtools.app.ui.connect

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.ui.labs.LabScaffold

/**
 * No router? Let the TV join the phone's hotspot. This screen explains the
 * steps and opens the system hotspot settings directly.
 */
@Composable
fun HotspotGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    fun openHotspotSettings() {
        val candidates = listOf(
            Intent("android.settings.TETHER_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (_: Exception) {
            }
        }
    }

    LabScaffold("Hotspot mode", onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Icon(
                        Icons.Default.WifiTethering,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Control your TV without a Wi-Fi router",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your phone becomes the network. The TV connects to it, and TV Tools reaches the TV over the hotspot.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Step(1, "Turn on the phone's Wi-Fi hotspot", "Open the system settings below. Set a name and password.")
            Step(2, "On the TV, join that hotspot", "Settings → Network & Internet → Wi-Fi → pick your phone's hotspot.")
            Step(3, "Enable ADB on the TV", "Wireless debugging (Android 11+) or ADB debugging (Fire TV).")
            Step(4, "Come back and refresh", "The TV usually appears as 192.168.43.x or 192.168.1.x in the device list.")

            Spacer(Modifier.height(20.dp))
            Button(onClick = { openHotspotSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text("Open hotspot settings")
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "Tip: some TVs drop Wi-Fi when the hotspot uses 5 GHz only. Switch the hotspot to 2.4 GHz if the TV can't see it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(number: Int, title: String, body: String) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(26.dp)
                .padding(top = 2.dp),
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
