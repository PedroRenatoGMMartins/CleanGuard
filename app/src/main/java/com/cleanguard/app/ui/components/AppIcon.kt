package com.cleanguard.app.ui.components

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Cache em memória dos ícones dos apps (carregados sob demanda, fora da thread principal). */
object AppIconCache {
    private const val ICON_PX = 108
    private val cache = LruCache<String, ImageBitmap>(250)

    fun get(packageName: String): ImageBitmap? = cache.get(packageName)

    fun load(context: Context, packageName: String): ImageBitmap? = try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawable.toBitmap(ICON_PX, ICON_PX).asImageBitmap().also { cache.put(packageName, it) }
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AppIcon(
    packageName: String,
    isDemo: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = if (isDemo) null else AppIconCache.get(packageName), packageName, isDemo) {
        if (value == null && !isDemo) {
            value = withContext(Dispatchers.IO) { AppIconCache.load(context, packageName) }
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val image = bitmap
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size).clip(shape))
    } else {
        Box(
            modifier = modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isDemo) Icons.Filled.Science else Icons.Filled.Android,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}
