package com.tvtools.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream

data class ShellUiState(
    val output: String = "",
    val input: String = "",
    val connected: Boolean = false,
)

class ShellViewModel : ViewModel() {

    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(ShellUiState())
    val ui: StateFlow<ShellUiState> = _ui.asStateFlow()

    private var stream: AdbStream? = null
    private var writer: OutputStream? = null

    init {
        openShell()
    }

    private fun openShell() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val s = adb.openShell()
                stream = s
                writer = s.openOutputStream()
                _ui.value = _ui.value.copy(connected = true)
                append("Connected. Type a command and press Send.\n\n")
                val input = s.openInputStream()
                val buf = ByteArray(8 * 1024)
                while (true) {
                    val n = try {
                        input.read(buf)
                    } catch (e: IOException) {
                        if (e.message?.contains("closed", true) == true) break else throw e
                    }
                    if (n < 0) break
                    if (n > 0) append(String(buf, 0, n, Charsets.UTF_8))
                }
                append("\n[stream closed]\n")
            } catch (e: Exception) {
                append("[shell error: ${e.message}]\n")
            }
            _ui.value = _ui.value.copy(connected = false)
        }
    }

    @Synchronized
    private fun append(text: String) {
        val current = _ui.value.output + text
        // Cap at ~160KB to avoid unbounded growth
        val trimmed = if (current.length > 160_000) current.takeLast(120_000) else current
        _ui.value = _ui.value.copy(output = trimmed)
    }

    fun setInput(text: String) {
        _ui.value = _ui.value.copy(input = text)
    }

    fun send() {
        val cmd = _ui.value.input
        if (cmd.isBlank()) return
        _ui.value = _ui.value.copy(input = "")
        viewModelScope.launch(Dispatchers.IO) {
            val w = writer
            if (w == null || stream?.isClosed == true) {
                append("$ $cmd\n[not connected]\n")
                return@launch
            }
            try {
                append("$ $cmd\n")
                w.write((cmd + "\n").toByteArray(Charsets.UTF_8))
                w.flush()
            } catch (e: Exception) {
                append("[write failed: ${e.message}]\n")
            }
        }
    }

    fun ctrlC() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                writer?.write(3)
                writer?.flush()
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        _ui.value = _ui.value.copy(output = "")
    }

    fun reconnect() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                stream?.close()
            } catch (_: Exception) {
            }
            openShell()
        }
    }

    override fun onCleared() {
        try {
            stream?.close()
        } catch (_: Exception) {
        }
    }
}
