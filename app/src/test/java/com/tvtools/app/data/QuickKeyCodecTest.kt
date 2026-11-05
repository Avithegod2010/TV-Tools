package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickKeyCodecTest {

    @Test
    fun `round trips an ordered list`() {
        val ids = listOf("back", "home", "play", "mute", "search")
        assertEquals(ids, decodeQuickKeyIds(encodeQuickKeyIds(ids)))
    }

    @Test
    fun `blank input decodes to an empty list`() {
        assertEquals(emptyList<String>(), decodeQuickKeyIds(""))
        assertEquals(emptyList<String>(), decodeQuickKeyIds("   "))
    }

    @Test
    fun `spaces around ids are tolerated`() {
        assertEquals(
            listOf("back", "home", "play"),
            decodeQuickKeyIds("back, home , play"),
        )
    }

    @Test
    fun `empty entries are dropped`() {
        assertEquals(listOf("back", "home"), decodeQuickKeyIds("back,,home,"))
    }

    @Test
    fun `every default id resolves to a catalog key`() {
        QuickKeys.defaultIds.forEach { id ->
            assertTrue("Missing catalog entry for $id", QuickKeys.byId(id) != null)
        }
    }

    @Test
    fun `catalog ids are unique`() {
        val ids = QuickKeys.catalog.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
}
