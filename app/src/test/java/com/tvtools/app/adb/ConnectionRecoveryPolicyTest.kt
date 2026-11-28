package com.tvtools.app.adb

import com.tvtools.app.data.DeviceProto
import com.tvtools.app.data.TvDevice
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionRecoveryPolicyTest {
    @Test
    fun identifiesSameLanAfterDhcpRenewal() {
        assertTrue(matches(endingAddress = "192.168.1.95", sameHandle = true))
    }

    @Test
    fun identifiesSameRouterAfterWifiDisconnectAndReturn() {
        assertTrue(matches(endingAddress = "192.168.1.95", sameHandle = false))
    }

    @Test
    fun changedSubnetAndChangedGatewayAreDistinctLinks() {
        assertFalse(matches(endingAddress = "192.168.31.95", sameHandle = true))
        assertFalse(matches(endingAddress = "192.168.1.95", endingGateway = "192.168.1.254"))
    }

    @Test
    fun gatewaylessLanRequiresTheSameHandle() {
        val old = setOf(address("10.0.0.30") to 24)
        val renewed = setOf(address("10.0.0.42") to 24)

        assertTrue(sameLan("wlan0", renewed, emptySet(), "wlan0", old, emptySet(), true))
        assertFalse(sameLan("wlan0", renewed, emptySet(), "wlan0", old, emptySet(), false))
    }

    @Test
    fun exactAdvertCanMoveButDisplayNameAndPairingAdvertDoNotIdentifyIt() {
        val last = TvDevice("Living room", "192.168.1.20", 37000, DeviceProto.TLS, discoveryId = "adb-guid")
        val sameLabel = last.copy(host = "192.168.1.22", discoveryId = "adb-other")
        val pairing = last.copy(host = "192.168.1.23", pairing = true)
        val moved = last.copy(host = "192.168.1.24", port = 47000)

        assertNull(matchingAdvert(last, listOf(sameLabel, pairing)))
        assertEquals(moved, matchingAdvert(last, listOf(sameLabel, pairing, moved)))
        assertNull(matchingAdvert(last, listOf(moved, moved.copy(host = "192.168.1.25"))))
    }

    private fun matches(
        endingAddress: String,
        endingGateway: String = "192.168.1.1",
        sameHandle: Boolean = false,
    ): Boolean = sameLan(
        "wlan0", setOf(address(endingAddress) to 24), setOf(address(endingGateway)),
        "wlan0", setOf(address("192.168.1.53") to 24), setOf(address("192.168.1.1")), sameHandle,
    )

    private fun address(value: String): InetAddress = InetAddress.getByName(value)
}
