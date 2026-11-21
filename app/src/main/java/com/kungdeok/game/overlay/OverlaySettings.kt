package com.kungdeok.game.overlay

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

enum class OverlaySize(val label: String, val scale: Float) {
    SMALL("작게", 0.75f),
    NORMAL("보통", 1.0f),
    LARGE("크게", 1.35f),
}

/** 오버레이 설정 (SPEC 8, 9). 게임 데이터가 아니라 기기 설정이라 SharedPreferences 에 둔다. */
class OverlaySettings(context: Context) {
    val prefs: SharedPreferences = context.getSharedPreferences("overlay", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** 알림창 버튼으로 잠깐 숨긴 상태 */
    var hidden: Boolean
        get() = prefs.getBoolean(KEY_HIDDEN, false)
        set(v) = prefs.edit().putBoolean(KEY_HIDDEN, v).apply()

    var size: OverlaySize
        get() = OverlaySize.entries.getOrElse(prefs.getInt(KEY_SIZE, 1)) { OverlaySize.NORMAL }
        set(v) = prefs.edit().putInt(KEY_SIZE, v.ordinal).apply()

    /** 0.3 – 1.0 */
    var opacity: Float
        get() = prefs.getFloat(KEY_OPACITY, 1f)
        set(v) = prefs.edit().putFloat(KEY_OPACITY, v.coerceIn(0.3f, 1f)).apply()

    companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HIDDEN = "hidden"
        const val KEY_SIZE = "size"
        const val KEY_OPACITY = "opacity"

        fun canDrawOverlays(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}
