package com.tvtools.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSessionTest {

    private val netflixDump = """
        MEDIA SESSION SERVICE (dumpsys media_session)

          MediaSessionRecord{id=1}
            package=com.netflix.ninja
            PlaybackState {state=3, position=60000, speed=1.0, updated=123}
            metadata: size=3, description=Stranger Things, artist=Kyle Dixon, duration=3000000
    """.trimIndent()

    @Test
    fun `parses a playing session with metadata and duration`() {
        val track = parseMediaSession(netflixDump)

        requireNotNull(track)
        assertEquals("com.netflix.ninja", track.packageName)
        assertEquals("Stranger Things", track.title)
        assertEquals("Kyle Dixon", track.artist)
        assertEquals(3, track.state)
        assertEquals(60_000L, track.positionMs)
        assertEquals(3_000_000L, track.durationMs)
        assertTrue(track.isPlaying)
    }

    @Test
    fun `position advances with playback speed`() {
        val track = parseMediaSession(netflixDump)
        requireNotNull(track)

        val tenSecondsLater = track.currentPositionMs(track.updatedAtMs + 10_000)
        assertEquals(70_000L, tenSecondsLater)
    }

    @Test
    fun `paused session does not advance`() {
        val paused = netflixDump.replace("state=3", "state=2")
        val track = parseMediaSession(paused)
        requireNotNull(track)

        assertFalse(track.isPlaying)
        assertEquals(60_000L, track.currentPositionMs(track.updatedAtMs + 10_000))
    }

    @Test
    fun `prefers the playing session when several are listed`() {
        val dump = """
            MediaSessionRecord{id=1}
              package=com.spotify.tv.android
              PlaybackState {state=2, position=1000, speed=1.0}
              metadata: size=1, description=Background song, artist=Someone

            MediaSessionRecord{id=2}
              package=com.google.android.youtube.tv
              PlaybackState {state=3, position=5000, speed=1.0}
              metadata: size=1, description=Video title, artist=Channel
        """.trimIndent()

        val track = parseMediaSession(dump)
        requireNotNull(track)
        assertEquals("com.google.android.youtube.tv", track.packageName)
        assertEquals("Video title", track.title)
        assertTrue(track.isPlaying)
    }

    @Test
    fun `returns null when there are no sessions`() {
        assertNull(parseMediaSession("MEDIA SESSION SERVICE\n\n  0 sessions listeners:"))
        assertNull(parseMediaSession(""))
    }

    @Test
    fun `falls back to the package name when the title is missing`() {
        val dump = """
            MediaSessionRecord{id=9}
              package=com.example.player
              PlaybackState {state=3, position=0, speed=1.0}
        """.trimIndent()

        val track = parseMediaSession(dump)
        requireNotNull(track)
        assertEquals("com.example.player", track.title)
    }
}
