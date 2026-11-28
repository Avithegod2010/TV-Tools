package com.tvtools.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tvtools.app.AppServices
import com.tvtools.app.R
import com.tvtools.app.data.AppBackup
import com.tvtools.app.data.BackupManager
import com.tvtools.app.TvToolsApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupsUiState(
    val backups: List<AppBackup> = emptyList(),
    val busy: Boolean = false,
    val busyLabel: String = "",
)

class BackupsViewModel : ViewModel() {

    private val manager = BackupManager(TvToolsApp.instance)
    private val adb = AppServices.adb

    private val _ui = MutableStateFlow(BackupsUiState())
    val ui = _ui.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.value = _ui.value.copy(backups = manager.list())
        }
    }

    fun restore(backup: AppBackup) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, busyLabel = TvToolsApp.instance.getString(R.string.backup_installing, backup.label))
            try {
                val out = adb.installSplitApk(backup.apkFiles)
                _messages.emit(
                    if (out.contains("Success", true)) TvToolsApp.instance.getString(R.string.backup_restored, backup.label)
                    else out.trim().ifBlank { TvToolsApp.instance.getString(R.string.restore_failed) }
                )
            } catch (e: Exception) {
                _messages.emit(TvToolsApp.instance.getString(R.string.backup_restore_error, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    fun delete(backup: AppBackup) {
        viewModelScope.launch(Dispatchers.IO) {
            manager.delete(backup)
            _ui.value = _ui.value.copy(backups = manager.list())
            _messages.emit(TvToolsApp.instance.getString(R.string.backup_deleted, backup.label))
        }
    }

    fun restoreData(backup: AppBackup) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, busyLabel = TvToolsApp.instance.getString(R.string.backup_restoring_data, backup.label))
            try {
                val ok = manager.restoreDataBackup(adb, backup)
                _messages.emit(if (ok) TvToolsApp.instance.getString(R.string.backup_data_restored, backup.label) else TvToolsApp.instance.getString(R.string.no_data_archive_for_this_app))
            } catch (e: Exception) {
                _messages.emit(TvToolsApp.instance.getString(R.string.backup_data_restore_error, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    fun exportEncrypted(passphrase: String, share: (java.io.File) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, busyLabel = TvToolsApp.instance.getString(R.string.encrypting_backups))
            try {
                val out = java.io.File(
                    com.tvtools.app.TvToolsApp.instance.cacheDir,
                    "tv-tools-backup-${System.currentTimeMillis()}.tvtools"
                )
                withContext(Dispatchers.IO) {
                    com.tvtools.app.data.BackupCrypto.export(manager.rootDir(), out, passphrase.toCharArray())
                }
                _messages.emit(TvToolsApp.instance.getString(R.string.encrypted_backup_created))
                share(out)
            } catch (e: Exception) {
                _messages.emit(TvToolsApp.instance.getString(R.string.backup_export_error, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    fun importEncrypted(uri: android.net.Uri, passphrase: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, busyLabel = TvToolsApp.instance.getString(R.string.decrypting_backup))
            try {
                val context = com.tvtools.app.TvToolsApp.instance
                val tmp = java.io.File(context.cacheDir, "import.tvtools")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    }
                }
                val count = withContext(Dispatchers.IO) {
                    com.tvtools.app.data.BackupCrypto.import(tmp, manager.rootDir(), passphrase.toCharArray())
                }
                tmp.delete()
                _messages.emit(TvToolsApp.instance.getString(R.string.backup_imported_count, count))
                refresh()
            } catch (e: Exception) {
                _messages.emit(TvToolsApp.instance.getString(R.string.backup_import_error, e.message.orEmpty()))
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }
}
