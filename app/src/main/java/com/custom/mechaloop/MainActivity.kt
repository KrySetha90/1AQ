package com.custom.mechaloop

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private var r = 0
    private var g = 240
    private var b = 255

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = getSharedPreferences("mecha_prefs", Context.MODE_PRIVATE)
        r = prefs.getInt("color_r", 0)
        g = prefs.getInt("color_g", 240)
        b = prefs.getInt("color_b", 255)

        val previewBar = findViewById<View>(R.id.ledPreviewBar)
        val tvHex = findViewById<TextView>(R.id.tvHexValue)
        val seekR = findViewById<SeekBar>(R.id.seekRed)
        val seekG = findViewById<SeekBar>(R.id.seekGreen)
        val seekB = findViewById<SeekBar>(R.id.seekBlue)
        val btnApply = findViewById<Button>(R.id.btnApplyColor)
        val switchAutoMusic = findViewById<Switch>(R.id.switchAutoMusic)
        val btnToggleVis = findViewById<Button>(R.id.btnToggleVisualizer)
        val btnNotifAccess = findViewById<Button>(R.id.btnGrantNotifAccess)
        val btnOpenXos = findViewById<Button>(R.id.btnOpenXosMecha)
        val tvLog = findViewById<TextView>(R.id.tvProbeLog)
        val tvSyncStatus = findViewById<TextView>(R.id.tvSyncStatus)

        seekR.progress = r
        seekG.progress = g
        seekB.progress = b
        switchAutoMusic.isChecked = prefs.getBoolean("auto_music_sync", true)

        fun updatePreview() {
            val hex = String.format("#%02X%02X%02X", r, g, b)
            tvHex.text = "CUSTOM COLOR: $hex"
            previewBar.setBackgroundColor(Color.rgb(r, g, b))
        }

        updatePreview()
        tvLog.text = MechaHardwareController.probeHardware()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                r = seekR.progress
                g = seekG.progress
                b = seekB.progress
                updatePreview()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        seekR.setOnSeekBarChangeListener(listener)
        seekG.setOnSeekBarChangeListener(listener)
        seekB.setOnSeekBarChangeListener(listener)

        btnApply.setOnClickListener {
            prefs.edit().putInt("color_r", r).putInt("color_g", g).putInt("color_b", b).apply()
            val result = MechaHardwareController.applyCustomColor(this, r, g, b, 255)
            tvLog.text = result
            Toast.makeText(this, "Applied $r, $g, $b to Mecha Loop", Toast.LENGTH_SHORT).show()
        }

        switchAutoMusic.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("auto_music_sync", isChecked).apply()
            tvSyncStatus.text = if (isChecked) {
                "Status: Armed (Will auto-pulse on YouTube Morphe / Music start)"
            } else {
                "Status: Auto-trigger disabled"
            }
        }

        btnToggleVis.setOnClickListener {
            requestRequiredPermissions()
            if (!MechaMusicSyncService.isRunning) {
                val intent = Intent(this, MechaMusicSyncService::class.java).apply {
                    action = MechaMusicSyncService.ACTION_START
                }
                ContextCompat.startForegroundService(this, intent)
                btnToggleVis.text = "STOP LIVE BEAT PULSE"
                tvSyncStatus.text = "Status: LIVE FFT BEAT SYNC RUNNING"
            } else {
                val intent = Intent(this, MechaMusicSyncService::class.java).apply {
                    action = MechaMusicSyncService.ACTION_STOP
                }
                startService(intent)
                btnToggleVis.text = "START LIVE BEAT PULSE NOW"
                tvSyncStatus.text = "Status: Stopped"
            }
        }

        btnNotifAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        btnOpenXos.setOnClickListener {
            val launched = MechaHardwareController.launchHiddenXosMechaSettings(this)
            if (!launched) {
                Toast.makeText(this, "XOS Lighting Activity not found on this ROM", Toast.LENGTH_SHORT).show()
            }
        }

        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }
}
