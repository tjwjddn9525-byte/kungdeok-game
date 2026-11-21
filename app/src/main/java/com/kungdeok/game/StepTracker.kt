package com.kungdeok.game

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 걸음 수 센서(TYPE_STEP_COUNTER)를 듣고 15초마다 최신 값을 저장한다.
 * 앱 화면과 오버레이 서비스가 각자 켜도 직전 값과의 차이만 더하므로 두 번 세지 않는다.
 */
class StepTracker(private val context: Context, private val scope: CoroutineScope) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private var latest = -1L
    private var saved = -1L
    private var job: Job? = null

    fun start() {
        if (sensor == null || !hasPermission(context) || job != null) return
        sm?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        val repo = (context.applicationContext as KungdeokApp).repository
        job = scope.launch {
            while (true) {
                if (latest >= 0 && latest != saved) {
                    saved = latest
                    repo.recordSteps(latest)
                }
                delay(15_000)
            }
        }
    }

    fun stop() {
        sm?.unregisterListener(this)
        job?.cancel()
        job = null
        if (latest >= 0 && latest != saved) {
            val value = latest
            saved = value
            val repo = (context.applicationContext as KungdeokApp).repository
            scope.launch { repo.recordSteps(value) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        latest = event.values[0].toLong()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        fun available(context: Context): Boolean =
            (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
                ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < 29 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED
    }
}
