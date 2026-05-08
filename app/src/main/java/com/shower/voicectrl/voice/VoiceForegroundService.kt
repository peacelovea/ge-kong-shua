package com.shower.voicectrl.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.shower.voicectrl.MainActivity
import com.shower.voicectrl.R
import com.shower.voicectrl.bus.CommandBus
import com.shower.voicectrl.bus.Debouncer
import com.shower.voicectrl.config.AppConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

class VoiceForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var captureJob: Job? = null
    private var recognizer: VoskRecognizer? = null
    private val debouncer = Debouncer()
    private val lastCommandTime = AtomicLong(System.currentTimeMillis())
    private var idleCheckJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithNotification()
        running.set(true)
        lastCommandTime.set(System.currentTimeMillis())
        if (captureJob?.isActive != true) {
            captureJob = scope.launch { runCaptureLoop() }
        }
        if (idleCheckJob?.isActive != true) {
            idleCheckJob = scope.launch { runIdleCheck() }
        }
        return START_STICKY
    }

    private suspend fun runCaptureLoop() {
        recognizer = VoskRecognizer.create(applicationContext) { cmd ->
            if (debouncer.shouldEmit(cmd)) {
                lastCommandTime.set(System.currentTimeMillis())
                CommandBus.INSTANCE.emit(cmd)
            }
        }

        val bufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(SAMPLE_RATE / 2)

        // VOICE_COMMUNICATION：为"扬声器外放同时收音"场景设计（VoIP 通话），
        // 会自动启用回声消除（AEC）和降噪（NS），对抖音在后台放视频时抢麦很有用。
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufSize
        )

        // 显式启用 AEC / NS（如果设备支持），双保险
        if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
            android.media.audiofx.AcousticEchoCanceler.create(record.audioSessionId)?.enabled = true
        }
        if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
            android.media.audiofx.NoiseSuppressor.create(record.audioSessionId)?.enabled = true
        }

        val buffer = ShortArray(1024)
        try {
            record.startRecording()
            while (scope.isActive) {
                val n = record.read(buffer, 0, buffer.size)
                if (n > 0) recognizer?.acceptPcm(buffer, n)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "capture loop crashed", t)
        } finally {
            try { record.stop() } catch (_: Throwable) {}
            record.release()
        }
    }

    private suspend fun runIdleCheck() {
        val appConfig = AppConfig(applicationContext)
        val timeoutMinutes = appConfig.idleTimeoutMinutes.first()
        val timeoutMs = timeoutMinutes * 60 * 1000L

        while (scope.isActive) {
            delay(60_000) // 每分钟检查一次
            val idleTime = System.currentTimeMillis() - lastCommandTime.get()
            if (idleTime >= timeoutMs) {
                Log.i(TAG, "No command for ${idleTime / 60_000} minutes, auto-stopping")
                sendAutoStopNotification(timeoutMinutes)
                stopSelf()
                break
            }
        }
    }

    private fun sendAutoStopNotification(timeoutMinutes: Long) {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("隔空刷已自动停止")
            .setContentText("$timeoutMinutes 分钟无命令，已自动停止监听")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .build()
        mgr.notify(NOTIF_ID_AUTO_STOP, notification)
    }

    override fun onDestroy() {
        running.set(false)
        captureJob?.cancel()
        idleCheckJob?.cancel()
        recognizer?.close()
        recognizer = null
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("隔空刷 · 监听中")
            .setContentText("说 \"下一条\" / \"上一条\" / \"暂停\"")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun ensureChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "语音监听", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        const val ACTION_STOP = "com.shower.voicectrl.STOP"
        private const val CHANNEL_ID = "voice_listen"
        private const val NOTIF_ID = 1001
        private const val NOTIF_ID_AUTO_STOP = 1002
        private const val SAMPLE_RATE = 16_000
        private const val TAG = "VoiceFgService"
        private val running = AtomicBoolean(false)

        fun isRunning(): Boolean = running.get()

        fun start(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceForegroundService::class.java)
                .setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
