package com.kungdeok.game.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/** assets/large, assets/small 의 스프라이트를 한 번만 읽어 둔다. */
object Sprites {
    private val cache = ConcurrentHashMap<String, Bitmap>()

    fun bitmap(context: Context, path: String): Bitmap =
        cache.getOrPut(path) {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        }
}

@Composable
fun rememberSprite(path: String): ImageBitmap {
    val context = LocalContext.current
    return remember(path) { Sprites.bitmap(context, path).asImageBitmap() }
}
