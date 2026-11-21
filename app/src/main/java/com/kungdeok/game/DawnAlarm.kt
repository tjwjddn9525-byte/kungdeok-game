package com.kungdeok.game

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kungdeok.game.logic.epochDay
import com.kungdeok.game.model.CatId
import com.kungdeok.game.overlay.OverlaySettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * 실제 버릇 (SPEC 10, 3단계): 새벽에 깨우기.
 * 매일 새벽 5~6시 사이 아무 때나 한 번, 고양이가 밥 달라고 깨우는 알림을 보낸다.
 * 정확한 알람 권한 없이 AlarmManager.setAndAllowWhileIdle 로 예약한다 (몇 분 늦을 수 있음).
 */
object DawnAlarm {
    const val START_HOUR = 5

    private val kungLines = listOf(
        "냐아아앙~! 냐앙! (밥그릇 앞에서 목청껏)",
        "쿵이 앞발로 얼굴을 톡톡 쳐요. 일어나라냥!",
        "쿵이 침대 위를 우다다 달리고 있어요…",
        "쿵: (귀에 대고) 냥. 냥. 냥. 냥.",
    )
    private val nodeokLines = listOf(
        "노덕이 가슴 위에 묵직하게 앉았어요. …밥.",
        "노덕이 반쯤 감긴 눈으로 빤히 쳐다보고 있어요.",
        "노덕: (한숨) …냥.",
        "노덕이 책상 위 물건을 하나씩 떨어뜨리고 있어요.",
    )

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        if (!OverlaySettings(context).dawnWake) {
            am.cancel(pi)
            return
        }
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        var day = LocalDate.now(zone)
        // 오늘 슬롯이 이미 지났으면 내일
        if (LocalTime.now(zone).hour >= START_HOUR) day = day.plusDays(1)
        val at = day.atTime(START_HOUR, Random.nextInt(60)).atZone(zone).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, maxOf(at, now + 60_000), pi)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 10, Intent(context, DawnReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun notify(context: Context, who: CatId) {
        if (!Notifications.canPost(context)) return
        val line = (if (who == CatId.KUNG) kungLines else nodeokLines).random()
        val n = NotificationCompat.Builder(context, Notifications.CHANNEL_HABIT)
            .setSmallIcon(R.drawable.ic_stat_paw)
            .setContentTitle("${who.catName}이 깨우러 왔어요 🌅")
            .setContentText(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(line))
            .setContentIntent(Notifications.openAppIntent(context))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(200, n)
        } catch (_: SecurityException) {
        }
    }
}

class DawnReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as KungdeokApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 더 배고픈 쪽이 깨우러 온다
                val first = app.repository.markDawn(epochDay(System.currentTimeMillis()))
                if (first != null && OverlaySettings(context).dawnWake) DawnAlarm.notify(context, first)
                DawnAlarm.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
