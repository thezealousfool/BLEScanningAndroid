package com.vivekroy.blescanning

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel

class DBService : Service() {

    private lateinit var db : AppDatabase

    companion object {
        val START_SERVICE = "START_SERVICE"
        val LOG_DATA = "LOG_DATA"
        val STOP_SERVICE = "STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "com.vivekroy.navcoglogging").build()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            START_SERVICE -> {
                val _intent = Intent(this, DBService::class.java)
                // add the data to the intent
                val pendingIntent = PendingIntent.getActivity(this, 0, _intent, 0)
                createNotificationChannel()
                val notification = NotificationCompat.Builder(this, "navcogDB")
                    .setContentTitle("Saving beacons to DB")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(pendingIntent)
                    .build()
                startForeground(1, notification)
            }
            LOG_DATA -> {
                // Log.d("vvk:DBS:", "major: ${intent.getIntExtra("major", -1)}  minor: ${intent.getIntExtra("minor", -1)}  rssi: ${intent.getIntExtra("rssi", -1)}  timestamp: ${intent.getLongExtra("timestamp", 0)}")
                CoroutineScope(IO).launch {
                    db.beaconDao().insertBeacons(
                        BeaconEntity(
                            "F7826DA6-4FA2-4E98-8024-BC5B71E0893E",
                            intent.getStringExtra("major"),
                            intent.getStringExtra("minor"),
                            intent.getIntExtra("rssi", -1),
                            intent.getLongExtra("timestamp", 0)
                        )
                    )
                }
            }
            STOP_SERVICE -> {
                CoroutineScope(IO).launch { commitDatabase() }
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel("navcogDB", "navcogDBChannel",
                NotificationManager.IMPORTANCE_HIGH)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }

    private fun commitDatabase() {
        CoroutineScope(IO).launch {
            try {

                val dbFile = applicationContext.getDatabasePath("com.vivekroy.navcoglogging") as File
                val backupFile = applicationContext.getDatabasePath("com.vivekroy.navcoglogging" + System.nanoTime()) as File

                if (dbFile.exists()) {
                    val src = FileInputStream(dbFile).channel as FileChannel
                    val dst = FileOutputStream(backupFile).channel as FileChannel
                    dst.transferFrom(src, 0, src.size())
                    src.close()
                    dst.close()
                    db.clearAllTables()
                }

            } catch (e: Exception) {
                Log.e("vvk:", e.message)
            }
        }
    }


}