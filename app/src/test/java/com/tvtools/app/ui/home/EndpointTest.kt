package com.tvtools.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class EndpointTest {

    @Test
    fun `bare ipv4 keeps the default port`() {
        assertEquals("192.168.1.5" to 5555, parseHostPort("192.168.1.5", 5555))
    }

    @Test
    fun `ipv4 with port overrides the default`() {
        assertEquals("192.168.1.5" to 5037, parseHostPort("192.168.1.5:5037", 5555))
    }

    @Test
    fun `bare ipv6 keeps the default port`() {
        assertEquals("fe80::1" to 5555, parseHostPort("fe80::1", 5555))
        assertEquals("2001:db8::10" to 5555, parseHostPort("2001:db8::10", 5555))
    }

    @Test
    fun `bracketed ipv6 with port is split correctly`() {
        assertEquals("fe80::1" to 4321, parseHostPort("[fe80::1]:4321", 5555))
    }

    @Test
    fun `adb uri prefix and trailing slash are tolerated`() {
        assertEquals("10.0.0.2" to 5037, parseHostPort("adb://10.0.0.2:5037/", 5555))
        assertEquals("10.0.0.3" to 5555, parseHostPort("tcp://10.0.0.3/", 5555))
    }

    @Test
    fun `whitespace is trimmed`() {
        assertEquals("192.168.0.9" to 5555, parseHostPort("  192.168.0.9  ", 5555))
    }

    @Test
    fun `brackets are stripped when the closing bracket is missing`() {
        assertEquals("fe80::1" to 5555, parseHostPort("[fe80::1", 5555))
    }

    @Test
    fun `formatEndpoint brackets ipv6 only`() {
        assertEquals("192.168.1.5:5555", formatEndpoint("192.168.1.5", 5555))
        assertEquals("[fe80::1]:5555", formatEndpoint("fe80::1", 5555))
    }
}
