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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tvtools.app.R
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

    LabScaffold(stringResource(R.string.hotspot_mode), onBack) {
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
                        stringResource(R.string.control_your_tv_without_a_wi_fi),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.your_phone_becomes_the_network_the_tv),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.steps), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Step(1, stringResource(R.string.turn_on_the_phones_wi_fi_hotspot), stringResource(R.string.open_the_system_settings_below_set_a))
            Step(2, stringResource(R.string.on_the_tv_join_that_hotspot), stringResource(R.string.settings_network_internet_wi_fi_pick_your))
            Step(3, stringResource(R.string.enable_adb_on_the_tv), stringResource(R.string.wireless_debugging_android_11_or_adb_debugging))
            Step(4, stringResource(R.string.come_back_and_refresh), stringResource(R.string.the_tv_usually_appears_as_192_168))

            Spacer(Modifier.height(20.dp))
            Button(onClick = { openHotspotSettings() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_hotspot_settings))
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.tip_some_tvs_drop_wi_fi_when),
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
