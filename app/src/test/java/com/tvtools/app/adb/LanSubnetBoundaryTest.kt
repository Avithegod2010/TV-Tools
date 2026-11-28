package com.tvtools.app.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanSubnetBoundaryTest {
    @Test
    fun distinguishesTwoDifferentLanPrefixes() {
        val first = LanSubnet("192.168.1.42", 24)
        val second = LanSubnet("192.168.31.42", 24)

        assertTrue(first.contains("192.168.1.200"))
        assertFalse(second.contains("192.168.1.200"))
        assertTrue(second.contains("192.168.31.200"))
    }

    @Test
    fun scanCrossesAnOctetBoundaryWhenThePrefixAllowsIt() {
        val subnet = LanSubnet("172.20.8.254", 23)
        val targets = subnet.scanHosts()

        assertTrue(targets.any { it.startsWith("172.20.9.") })
        assertTrue(targets.all(subnet::contains))
        assertFalse(subnet.contains("172.20.10.1"))
        assertFalse("172.20.8.254" in targets)
    }

    @Test
    fun smallNetworkExcludesTheLocalAndReservedAddresses() {
        val subnet = LanSubnet("10.0.0.5", 30)

        assertEquals(listOf("10.0.0.6"), subnet.scanHosts())
    }

    @Test
    fun pointToPointPrefixIncludesItsOnePeer() {
        assertEquals(listOf("10.0.0.9"), LanSubnet("10.0.0.8", 31).scanHosts())
        assertTrue(LanSubnet("10.0.0.8", 32).scanHosts().isEmpty())
    }

    @Test
    fun fallbackScanRemainsBoundedOnALargeLan() {
        val subnet = LanSubnet("10.70.10.5", 16)
        val targets = subnet.scanHosts()

        assertEquals(254, targets.size)
        assertTrue(targets.all(subnet::contains))
        assertEquals(targets.size, targets.toSet().size)
        assertFalse("10.70.10.5" in targets)
    }
}
