package com.example.lab_week_08.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.lab_week_08.MainActivity
import com.example.lab_week_08.R

class NotificationService : Service() {

    // Notification builder yang akan kita update untuk countdown
    private lateinit var notificationBuilder: NotificationCompat.Builder

    // Handler untuk menjalankan tugas di thread terpisah
    private lateinit var serviceHandler: Handler

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Mempersiapkan notification builder dan startForeground
        notificationBuilder = startForegroundServiceNotification()

        // Buat HandlerThread agar pekerjaan notifikasi tidak di main thread
        val handlerThread = HandlerThread("ServiceThread").apply { start() }
        serviceHandler = Handler(handlerThread.looper)
    }

    private fun startForegroundServiceNotification(): NotificationCompat.Builder {
        val pendingIntent = getPendingIntent()
        val channelId = createNotificationChannel()
        val nb = getNotificationBuilder(pendingIntent, channelId)

        // Start foreground dengan notification yang sudah dibuat
        startForeground(NOTIFICATION_ID, nb.build())
        return nb
    }

    private fun getPendingIntent(): PendingIntent {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else 0

        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(this, 0, intent, flag)
    }

    private fun createNotificationChannel(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "001"
            val channelName = "001 Channel"
            val channelPriority = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, channelPriority)
            val service = requireNotNull(ContextCompat.getSystemService(this, NotificationManager::class.java))
            service.createNotificationChannel(channel)
            channelId
        } else {
            "" // untuk API < 26
        }
    }

    private fun getNotificationBuilder(pendingIntent: PendingIntent, channelId: String) =
        NotificationCompat.Builder(this, channelId)
            .setContentTitle("Second worker process is done")
            .setContentText("Check it out!")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setTicker("Second worker process is done, check it out!")
            .setOngoing(true)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val returnValue = super.onStartCommand(intent, flags, startId)

        // Ambil channel id yang dikirim dari MainActivity
        val id = intent?.getStringExtra(EXTRA_ID)
            ?: throw IllegalStateException("Channel ID must be provided")

        // Post task ke handler agar berjalan di thread terpisah
        serviceHandler.post {
            // Lakukan countdown (10 -> 0) dan update notification tiap detik
            countDownFromTenToZero(notificationBuilder)

            // Notify MainActivity bahwa proses service telah selesai (kirimkan channel id)
            notifyCompletion(id)

            // Hentikan foreground dan service
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return returnValue
    }

    private fun countDownFromTenToZero(notificationBuilder: NotificationCompat.Builder) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        for (i in 10 downTo 0) {
            try {
                Thread.sleep(1000L)
            } catch (e: InterruptedException) {
                // ignore
            }
            notificationBuilder.setContentText("$i seconds until last warning")
                .setSilent(true)
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private fun notifyCompletion(id: String) {
        Handler(Looper.getMainLooper()).post {
            mutableID.value = id
        }
    }

    companion object {
        const val NOTIFICATION_ID = 0xCA7
        const val EXTRA_ID = "Id"

        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}
