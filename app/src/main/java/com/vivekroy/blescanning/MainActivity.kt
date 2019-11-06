package com.vivekroy.blescanning

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.ReceiveChannel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.*

class MainActivity() : AppCompatActivity() {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private val bluetoothScanner = bluetoothAdapter.bluetoothLeScanner
    private val requestCode = 0
    private var isScanning = false
    private var curTimestamp = 0L
    private val queue = Channel<ScanResult>(Channel.UNLIMITED)
    private lateinit var db : AppDatabase
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            enableDisableButton()
        }
    }
    private val scanCallback = object: ScanCallback(), CoroutineScope by MainScope() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result != null)
                launch {
                    queue.send(result)
                }
        }
    }
    private lateinit var scanSettings : ScanSettings
    private lateinit var scanFilter: ScanFilter

    private fun setScanFilter() {
        val uuid = UUID.fromString("F7826DA6-4FA2-4E98-8024-BC5B71E0893E")
        val bb = ByteBuffer.allocate(16)
        bb.putLong(uuid.mostSignificantBits)
        bb.putLong(uuid.leastSignificantBits)
        val uuidBytes = bb.array()
        val manufacturerData = ByteBuffer.allocate(23)
        val manufacturerDataMask = ByteBuffer.allocate(23)
        manufacturerData.put(0, 2)  // 0x02
        manufacturerData.put(1, 21) // 0x15
        for (i in 2..17) {
            manufacturerData.put(i, uuidBytes[i-2])
        }
        for (i in 0..17) {
            manufacturerDataMask.put(i,1)
        }
        val scanFilterBuilder = ScanFilter.Builder()
        scanFilterBuilder.setManufacturerData(76, manufacturerData.array(), manufacturerDataMask.array())
        scanFilter = scanFilterBuilder.build()
    }

    private fun setScanSettings() {
        val scanSettingBuilder = ScanSettings.Builder()
        scanSettingBuilder.setReportDelay(0)
        scanSettingBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        scanSettings = scanSettingBuilder.build()
    }

    private fun enableDisableButton() {
        scanningButton.isEnabled = !(!bluetoothAdapter.isEnabled or
                (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED))
    }

    private fun initScanSettings() {
        setScanSettings()
        setScanFilter()
    }

    private fun initDb() {
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "com.vivekroy.navcoglogging").build()
    }

    private fun initConsumer() {
        CoroutineScope(Dispatchers.Default).launch {
            while(true) {
                while(!queue.isEmpty) {
                    val result = queue.receive()
                    val manufacturerData = result?.scanRecord?.getManufacturerSpecificData(76)
                    val majorMinor = ByteBuffer.wrap(manufacturerData, 18, 4).asShortBuffer()
                    val major = majorMinor[0].toUShort().toString()
                    val minor = majorMinor[1].toUShort().toString()
                    db.beaconDao().insertBeacons(
                        BeaconEntity(
                            "F7826DA6-4FA2-4E98-8024-BC5B71E0893E",
                            major,
                            minor,
                            result.rssi,
                            result.timestampNanos
                        ))
                }
                for (x in 1..10) {
                    delay(300)
                    if (!queue.isEmpty)
                        break
                }
                if (queue.isEmpty)
                    break
            }
            commitDatabase()
        }
    }

    private fun commitDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {

                val dbFile = applicationContext.getDatabasePath("com.vivekroy.navcoglogging") as File
                val backupFile = applicationContext.getDatabasePath("com.vivekroy.navcoglogging_" + System.nanoTime()) as File

                if (dbFile.exists()) {
                    val src = FileInputStream(dbFile).channel as FileChannel
                    val dst = FileOutputStream(backupFile).channel as FileChannel
                    dst.transferFrom(src, 0, src.size())
                    src.close()
                    dst.close()
                    db.clearAllTables()
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Database saved", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                Log.e("vvk:", e.message)
            }
        }
    }

    private fun startScanning() {
        isScanning = true
        scanningButton.text = "Stop"
        curTimestamp = System.nanoTime()
        bluetoothScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        initConsumer()
    }

    private fun stopScanning() {
         bluetoothScanner.stopScan(scanCallback)
        isScanning = false
        scanningButton.text = "Start"
    }

    fun onScanningClick(view: View) {
        if (isScanning) {
            stopScanning()
        } else {
            startScanning()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), requestCode)
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        initScanSettings()
        initDb()
    }

    override fun onResume() {
        super.onResume()
        enableDisableButton()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = enableDisableButton()
}
