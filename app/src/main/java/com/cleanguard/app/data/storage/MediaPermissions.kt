package com.cleanguard.app.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cleanguard.app.domain.storage.MediaAccess

/** Permissões de mídia adequadas para cada versão do Android. */
object MediaPermissions {

    /** Permissões a pedir em tempo de execução para a análise de armazenamento. */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        // Android 8/9: excluir mídia de outros apps exige WRITE_EXTERNAL_STORAGE.
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun access(context: Context): MediaAccess {
        fun granted(permission: String) =
            context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val images = granted(Manifest.permission.READ_MEDIA_IMAGES)
            val video = granted(Manifest.permission.READ_MEDIA_VIDEO)
            val audio = granted(Manifest.permission.READ_MEDIA_AUDIO)
            val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            when {
                images && video && audio -> MediaAccess.FULL
                images || video || audio || partial -> MediaAccess.PARTIAL
                else -> MediaAccess.NONE
            }
        } else {
            if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.NONE
        }
    }
}
