package com.cleanguard.app.data.storage

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.cleanguard.app.domain.storage.DeviceStorage

/** Espaço total/livre do armazenamento interno (sem permissões especiais). */
class DeviceStorageProvider(private val context: Context) {

    fun read(): DeviceStorage = try {
        val ssm = context.getSystemService(StorageStatsManager::class.java)
            ?: throw IllegalStateException("StorageStatsManager indisponível")
        DeviceStorage(
            totalBytes = ssm.getTotalBytes(StorageManager.UUID_DEFAULT),
            freeBytes = ssm.getFreeBytes(StorageManager.UUID_DEFAULT),
        )
    } catch (e: Exception) {
        val stat = StatFs(Environment.getDataDirectory().path)
        DeviceStorage(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }
}
