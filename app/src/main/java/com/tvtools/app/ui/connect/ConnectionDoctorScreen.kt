package com.tvtools.app.ui.connect

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.tvtools.app.AppServices
import com.tvtools.app.R
import com.tvtools.app.TvToolsApp
import com.tvtools.app.adb.AdbClient
import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import com.tvtools.app.ui.labs.LabScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.Socket

private enum class CheckState { PENDING, RUNNING, PASS, WARN, FAIL }

private data class DoctorCheck(
    val title: String,
    val state: CheckState,
    val detail: String,
)

/**
 * Step-by-step connection wizard: phone network → TCP reachability → mDNS
 * advertisements → ADB handshake → pairing state, with concrete fix suggestions
 * and an inline pairing form when the TV asks for a code.
 */
@Composable
fun ConnectionDoctorScreen(onBack: () -> Unit, onConnected: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var checks by remember { mutableStateOf<List<DoctorCheck>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var adverts by remember { mutableStateOf<List<TvDevice>>(emptyList()) }
    var pairHost by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var pairStatus by remember { mutableStateOf<String?>(null) }
    var pairing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val last = AppServices.prefs.loadLastDevice()
        if (last != null) {
            host = last.host
            port = last.port.toString()
            pairHost = last.host
        }
    }

    fun update(index: Int, check: DoctorCheck) {
        checks = checks.mapIndexed { i, old -> if (i == index) check else old }
    }

    fun runChecks() {
        if (running) return
        val targetHost = host.trim()
        val targetPort = port.toIntOrNull() ?: 5555
        if (targetHost.isBlank()) {
            pairStatus = context.getString(R.string.enter_the_tvs_ip_address_first)
            return
        }
        running = true
        checks = listOf(
            DoctorCheck(context.getString(R.string.phone_network), CheckState.RUNNING, context.getString(R.string.checking_wi_fi)),
            DoctorCheck(context.getString(R.string.tcp_reachability), CheckState.PENDING, context.getString(R.string.waiting)),
            DoctorCheck(context.getString(R.string.mdns_advertisements), CheckState.PENDING, context.getString(R.string.waiting)),
            DoctorCheck(context.getString(R.string.adb_handshake), CheckState.PENDING, context.getString(R.string.waiting)),
            DoctorCheck(context.getString(R.string.pairing_state), CheckState.PENDING, context.getString(R.string.waiting)),
        )
        scope.launch {
            // 1. Phone network
            val ip = localIpv4()
            val gateway = localGateway()
            checks = checks.toMutableList().also {
                it[0] = if (ip == null)
                    DoctorCheck(context.getString(R.string.phone_network), CheckState.FAIL, context.getString(R.string.not_on_wi_fi_join_the_same))
                else
                    DoctorCheck(
                        context.getString(R.string.phone_network),
                        CheckState.PASS,
                        context.getString(R.string.doctor_phone_ip, ip) + (gateway?.let {
                            " · " + context.getString(R.string.doctor_router, it)
                        } ?: ""),
                    )
            }

            // 2. TCP reachability + common ports
            val started = System.currentTimeMillis()
            val directOpen = tcpProbe(targetHost, targetPort)
            val latency = System.currentTimeMillis() - started
            val altPorts = listOf(5555, 5554).distinct().filter { it != targetPort }
            val openAlt = withContext(Dispatchers.IO) {
                altPorts.filter { tcpProbe(targetHost, it) }
            }
            checks = checks.toMutableList().also {
                it[1] = when {
                    directOpen -> DoctorCheck(
                        context.getString(R.string.tcp_reachability),
                        CheckState.PASS,
                        context.getString(R.string.doctor_port_open, targetPort, latency),
                    )
                    openAlt.isNotEmpty() -> DoctorCheck(
                        context.getString(R.string.tcp_reachability),
                        CheckState.WARN,
                        context.getString(R.string.doctor_alternate_ports, targetPort, openAlt.joinToString(", ")),
                    )
                    else -> DoctorCheck(
                        context.getString(R.string.tcp_reachability),
                        CheckState.FAIL,
                        context.getString(R.string.no_adb_port_answered_the_tv_may),
                    )
                }
            }

            // 3. mDNS advertisements
            var seen: List<TvDevice> = emptyList()
            withTimeoutOrNull(4_000) {
                AppServices.discovery.discover(scanSubnetToo = false).collect { seen = it.devices }
            }
            val relevant = seen.filter { it.host == targetHost }
            adverts = seen
            checks = checks.toMutableList().also {
                it[2] = when {
                    relevant.any { it.pairing } -> DoctorCheck(
                        context.getString(R.string.mdns_advertisements),
                        CheckState.WARN,
                        context.getString(R.string.doctor_pairing_advert, relevant.first { it.pairing }.port),
                    )
                    relevant.isNotEmpty() -> DoctorCheck(
                        context.getString(R.string.mdns_advertisements),
                        CheckState.PASS,
                        relevant.joinToString(" · ") { "${it.name}:${it.port}" },
                    )
                    seen.isNotEmpty() -> DoctorCheck(
                        context.getString(R.string.mdns_advertisements),
                        CheckState.WARN,
                        context.getString(R.string.doctor_no_matching_advert, seen.size, targetHost),
                    )
                    else -> DoctorCheck(
                        context.getString(R.string.mdns_advertisements),
                        CheckState.WARN,
                        context.getString(R.string.no_mdns_adverts_some_tvs_dont_broadcast),
                    )
                }
            }

            // Suggest a pairing port from the advert or memory.
            val pairingAdvert = relevant.firstOrNull { it.pairing }
            val rememberedPairPort = AppServices.prefs.loadPairingPort(targetHost)
            if (pairPort.isBlank()) {
                pairPort = (pairingAdvert?.port ?: rememberedPairPort)?.toString().orEmpty()
            }

            // 4. ADB handshake
            val connectPort = when {
                directOpen -> targetPort
                openAlt.isNotEmpty() -> openAlt.first()
                else -> targetPort
            }
            val result = if (directOpen || openAlt.isNotEmpty()) {
                AppServices.adb.connect(targetHost, connectPort)
            } else null
            checks = checks.toMutableList().also {
                it[3] = when (result) {
                    is AdbClient.ConnectResult.Success -> {
                        val model = runCatching {
                            AppServices.adb.exec("getprop ro.product.model", timeoutMs = 8_000).trim()
                        }.getOrNull().orEmpty().ifBlank { targetHost }
                        val device = TvDevice(model, targetHost, connectPort, DeviceProto.TCP)
                        AppServices.prefs.saveLastDevice(device)
                        AppServices.prefs.saveDevice(device)
                        onConnected(model)
                        DoctorCheck(context.getString(R.string.adb_handshake), CheckState.PASS, context.getString(R.string.doctor_connected, model, connectPort))
                    }
                    AdbClient.ConnectResult.PairingRequired -> DoctorCheck(
                        context.getString(R.string.adb_handshake),
                        CheckState.WARN,
                        context.getString(R.string.tv_refused_the_key_pairing_required_use),
                    )
                    is AdbClient.ConnectResult.Error -> DoctorCheck(
                        context.getString(R.string.adb_handshake),
                        CheckState.FAIL,
                        context.getString(R.string.doctor_adb_failed, result.message),
                    )
                    null -> DoctorCheck(
                        context.getString(R.string.adb_handshake),
                        CheckState.FAIL,
                        context.getString(R.string.skipped_no_open_port_to_talk_to),
                    )
                }
            }

            // 5. Pairing state
            checks = checks.toMutableList().also {
                it[4] = when {
                    pairingAdvert != null -> DoctorCheck(
                        context.getString(R.string.pairing_state),
                        CheckState.WARN,
                        context.getString(R.string.doctor_enter_pairing_code, pairingAdvert.port),
                    )
                    rememberedPairPort != null -> DoctorCheck(
                        context.getString(R.string.pairing_state),
                        CheckState.PASS,
                        context.getString(R.string.doctor_remembered_port, rememberedPairPort),
                    )
                    result is AdbClient.ConnectResult.Success -> DoctorCheck(
                        context.getString(R.string.pairing_state),
                        CheckState.PASS,
                        context.getString(R.string.already_paired_the_tv_accepted_this_phones),
                    )
                    else -> DoctorCheck(
                        context.getString(R.string.pairing_state),
                        CheckState.WARN,
                        context.getString(R.string.no_pairing_port_seen_enable_wireless_debugging),
                    )
                }
            }

            if (pairHost.isBlank()) pairHost = targetHost
            running = false
        }
    }

    fun pairNow() {
        val h = pairHost.trim().ifBlank { host.trim() }
        val p = pairPort.toIntOrNull() ?: 0
        if (h.isBlank() || p <= 0 || pairCode.length != 6) {
            pairStatus = context.getString(R.string.enter_the_tv_ip_pairing_port_and)
            return
        }
        pairing = true
        pairStatus = context.getString(R.string.pairing)
        scope.launch {
            val result = AppServices.adb.pair(h, p, pairCode)
            if (result.isFailure) {
                pairing = false
                pairStatus = context.getString(R.string.doctor_pairing_failed, result.exceptionOrNull()?.message ?: context.getString(R.string.wrong_pairing_code))
                return@launch
            }
            AppServices.prefs.savePairingPort(h, p)
            pairStatus = context.getString(R.string.paired_looking_for_the_connect_port)
            var connectPort = adverts.firstOrNull { it.host == h && !it.pairing }?.port
            var waited = 0
            while (connectPort == null && waited < 8_000) {
                delay(700)
                waited += 700
                var latest: List<TvDevice> = emptyList()
                withTimeoutOrNull(1_500) {
                    AppServices.discovery.discover(scanSubnetToo = false).collect { latest = it.devices }
                }
                connectPort = latest.firstOrNull { it.host == h && !it.pairing }?.port
            }
            val finalPort = connectPort ?: 5555
            val connected = AppServices.adb.connect(h, finalPort)
            pairing = false
            if (connected == AdbClient.ConnectResult.Success) {
                val model = runCatching {
                    AppServices.adb.exec("getprop ro.product.model", timeoutMs = 8_000).trim()
                }.getOrNull().orEmpty().ifBlank { h }
                val device = TvDevice(model, h, finalPort, DeviceProto.TCP)
                AppServices.prefs.saveLastDevice(device)
                AppServices.prefs.saveDevice(device)
                pairStatus = context.getString(R.string.doctor_connected_to, model)
                onConnected(model)
            } else {
                pairStatus = context.getString(R.string.doctor_connect_failed_after_pairing, finalPort)
            }
        }
    }

    LabScaffold(stringResource(R.string.connection_doctor), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.checks_the_whole_path_from_this_phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it.trim() },
                    label = { Text(stringResource(R.string.tv_address)) },
                    placeholder = { Text("192.168.1.50") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.port)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(90.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { runChecks() }, enabled = !running, modifier = Modifier.fillMaxWidth()) {
                if (running) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (running) context.getString(R.string.running_checks) else context.getString(R.string.run_checks))
            }

            checks.forEach { check ->
                val color = when (check.state) {
                    CheckState.PASS -> MaterialTheme.colorScheme.primary
                    CheckState.WARN -> MaterialTheme.colorScheme.tertiary
                    CheckState.FAIL -> MaterialTheme.colorScheme.error
                    CheckState.RUNNING -> MaterialTheme.colorScheme.secondary
                    CheckState.PENDING -> MaterialTheme.colorScheme.outline
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 4.dp)
                                .size(10.dp)
                                .background(color, CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                check.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                check.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (checks.isNotEmpty() && checks.any { it.state == CheckState.WARN || it.state == CheckState.FAIL }) {
                Spacer(Modifier.height(18.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(stringResource(R.string.pair_with_the_tv), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.on_the_tv_developer_options_wireless_debugging),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = pairHost,
                                onValueChange = { pairHost = it.trim() },
                                label = { Text("IP") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = pairPort,
                                onValueChange = { pairPort = it.filter(Char::isDigit) },
                                label = { Text(stringResource(R.string.port)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(90.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pairCode,
                            onValueChange = { pairCode = it.filter(Char::isDigit).take(6) },
                            label = { Text(stringResource(R.string.six_digit_code)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { pairNow() },
                                enabled = !pairing && pairCode.length == 6,
                            ) { Text(if (pairing) context.getString(R.string.pairing) else context.getString(R.string.pair_connect)) }
                            Spacer(Modifier.width(10.dp))
                            pairStatus?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun tcpProbe(host: String, port: Int): Boolean = try {
    Socket().use { it.connect(InetSocketAddress(host, port), 900) }
    true
} catch (_: Exception) {
    false
}

private fun localIpv4(): String? = try {
    val cm = TvToolsApp.instance.getSystemService(ConnectivityManager::class.java)
    cm.activeNetwork?.let { cm.getLinkProperties(it) }
        ?.linkAddresses
        ?.firstOrNull { it.address is Inet4Address }
        ?.address?.hostAddress
} catch (_: Exception) {
    null
}

private fun localGateway(): String? = try {
    val cm = TvToolsApp.instance.getSystemService(ConnectivityManager::class.java)
    cm.activeNetwork?.let { cm.getLinkProperties(it) }
        ?.routes
        ?.firstOrNull { it.isDefaultRoute }
        ?.gateway?.hostAddress
} catch (_: Exception) {
    null
}
