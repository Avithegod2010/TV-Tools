package com.tvtools.app.adb

import io.github.muntashirakon.adb.AdbStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal implementation of the ADB `sync:` protocol (v1) on top of an [AdbStream].
 * Supports LIST / STAT / RECV / SEND — enough for a full file manager.
 *
 * Protocol notes (all integers little-endian):
 *  - Request : [id:4][length:4][payload]
 *  - LIST    : stream of DENT (id,mode,size,mtime,namelen,name) terminated by DONE + 16 zero bytes
 *  - RECV    : stream of (DATA,size,bytes) terminated by DONE, or FAIL
 *  - SEND    : DATA chunks (<= 64K), then DONE+mtime; answered by OKAY or FAIL
 */
class AdbSync(private val stream: AdbStream) {

    private val input: InputStream = BufferedInputStream(stream.openInputStream(), 64 * 1024)
    private val output: OutputStream = BufferedOutputStream(stream.openOutputStream(), 64 * 1024)

    data class RemoteEntry(
        val name: String,
        val mode: Int,
        val size: Long,
        val mtime: Long,
    ) {
        val isDirectory: Boolean get() = (mode and S_IFMT) == S_IFDIR
    }

    data class Stat(val mode: Int, val size: Long, val mtime: Long) {
        val exists: Boolean get() = mode != 0
        val isDirectory: Boolean get() = (mode and S_IFMT) == S_IFDIR
    }

    // ---------------- LIST ----------------

    fun list(path: String): List<RemoteEntry> {
        sendRequest(ID_LIST, path)
        val entries = ArrayList<RemoteEntry>()
        while (true) {
            val id = readId()
            if (id == ID_DONE) {
                skipFully(16) // 4 zero fields
                break
            }
            if (id != ID_DENT) throw IOException("Unexpected sync response: ${idString(id)}")
            val mode = readIntLe()
            val size = readIntLe()
            val mtime = readIntLe()
            val nameLen = readIntLe()
            val name = String(readFully(nameLen), Charsets.UTF_8)
            entries.add(RemoteEntry(name, mode, size.toLong() and 0xFFFFFFFFL, mtime.toLong() and 0xFFFFFFFFL))
        }
        return entries
    }

    // ---------------- STAT ----------------

    fun stat(path: String): Stat {
        sendRequest(ID_STAT, path)
        val id = readId()
        if (id != ID_STAT) {
            if (id == ID_FAIL) throw IOException("stat failed: ${readFailMessage()}")
            throw IOException("Unexpected sync response: ${idString(id)}")
        }
        val mode = readIntLe()
        val size = readIntLe()
        val mtime = readIntLe()
        return Stat(mode, size.toLong() and 0xFFFFFFFFL, mtime.toLong() and 0xFFFFFFFFL)
    }

    // ---------------- RECV (pull) ----------------

    fun pull(remotePath: String, localFile: File, expectedSize: Long = -1,
             onProgress: ((bytesDone: Long) -> Unit)? = null) {
        sendRequest(ID_RECV, remotePath)
        var done = 0L
        FileOutputStream(localFile).buffered(64 * 1024).use { fos ->
            while (true) {
                val id = readId()
                when (id) {
                    ID_DATA -> {
                        val size = readIntLe()
                        var remaining = size
                        val buf = ByteArray(minOf(64 * 1024, remaining))
                        while (remaining > 0) {
                            val n = input.read(buf, 0, minOf(buf.size, remaining))
                            if (n < 0) throw IOException("Unexpected EOF in sync DATA")
                            fos.write(buf, 0, n)
                            remaining -= n
                            done += n
                        }
                        onProgress?.invoke(done)
                    }
                    ID_DONE -> {
                        readIntLe() // length, should be 0
                        return
                    }
                    ID_FAIL -> throw IOException("pull failed: ${readFailMessage()}")
                    else -> throw IOException("Unexpected sync response: ${idString(id)}")
                }
            }
        }
    }

    // ---------------- SEND (push) ----------------

    fun push(localFile: File, remotePath: String, mode: Int = 420, // 0644 octal
             onProgress: ((bytesDone: Long) -> Unit)? = null) {
        val payload = "$remotePath,${Integer.toString(mode, 10)}"
        sendRequest(ID_SEND, payload)
        val buf = ByteArray(64 * 1024)
        var done = 0L
        FileInputStream(localFile).buffered(64 * 1024).use { fis ->
            while (true) {
                val n = fis.read(buf)
                if (n < 0) break
                writeId(ID_DATA)
                writeIntLe(n)
                output.write(buf, 0, n)
                output.flush()
                done += n
                onProgress?.invoke(done)
            }
        }
        writeId(ID_DONE)
        writeIntLe((System.currentTimeMillis() / 1000).toInt())
        output.flush()

        val id = readId()
        when (id) {
            ID_OKAY -> readIntLe() // length 0
            ID_FAIL -> throw IOException("push failed: ${readFailMessage()}")
            else -> throw IOException("Unexpected sync response: ${idString(id)}")
        }
    }

    // ---------------- wire helpers ----------------

    private fun sendRequest(id: Int, payload: String) {
        val data = payload.toByteArray(Charsets.UTF_8)
        writeId(id)
        writeIntLe(data.size)
        output.write(data)
        output.flush()
    }

    private fun writeId(id: Int) {
        output.write(id and 0xFF)
        output.write((id shr 8) and 0xFF)
        output.write((id shr 16) and 0xFF)
        output.write((id shr 24) and 0xFF)
    }

    private fun writeIntLe(v: Int) = writeId(v)

    private fun readId(): Int = readIntLe()

    private fun readIntLe(): Int {
        val b0 = input.read(); val b1 = input.read(); val b2 = input.read(); val b3 = input.read()
        if (b3 < 0) throw IOException("Unexpected EOF in sync stream")
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8) or ((b2 and 0xFF) shl 16) or ((b3 and 0xFF) shl 24)
    }

    private fun readFully(n: Int): ByteArray {
        val out = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(out, off, n - off)
            if (r < 0) throw IOException("Unexpected EOF in sync stream")
            off += r
        }
        return out
    }

    private fun skipFully(n: Int) {
        var left = n
        while (left > 0) {
            val skipped = input.skip(left.toLong())
            if (skipped <= 0) {
                if (input.read() < 0) throw IOException("Unexpected EOF in sync stream")
                left--
            } else left -= skipped.toInt()
        }
    }

    private fun readFailMessage(): String {
        val len = readIntLe()
        return String(readFully(len), Charsets.UTF_8)
    }

    private fun idString(id: Int): String =
        String(byteArrayOf((id and 0xFF).toByte(), ((id shr 8) and 0xFF).toByte(),
            ((id shr 16) and 0xFF).toByte(), ((id shr 24) and 0xFF).toByte()))

    companion object {
        private const val S_IFMT = 61440   // 0170000 octal
        private const val S_IFDIR = 16384  // 0040000 octal

        private fun idOf(s: String): Int =
            (s[0].code) or (s[1].code shl 8) or (s[2].code shl 16) or (s[3].code shl 24)

        private val ID_STAT = idOf("STAT")
        private val ID_LIST = idOf("LIST")
        private val ID_SEND = idOf("SEND")
        private val ID_RECV = idOf("RECV")
        private val ID_DENT = idOf("DENT")
        private val ID_DONE = idOf("DONE")
        private val ID_DATA = idOf("DATA")
        private val ID_OKAY = idOf("OKAY")
        private val ID_FAIL = idOf("FAIL")
    }
}
