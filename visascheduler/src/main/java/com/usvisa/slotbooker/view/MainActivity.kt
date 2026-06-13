package com.usvisa.slotbooker.view

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.usvisa.slotbooker.data.ConfigStore
import com.usvisa.slotbooker.databinding.ActivityMainBinding
import com.usvisa.slotbooker.service.MonitorState
import com.usvisa.slotbooker.service.VisaMonitorService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermission()

        binding.configButton.setOnClickListener {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
        binding.loginButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.startStopButton.setOnClickListener { toggleMonitor() }

        MonitorState.running.observe(this) { running ->
            binding.startStopButton.text = if (running) "Stop monitoring" else "Start monitoring"
        }
        MonitorState.status.observe(this) { binding.statusText.text = it }
        MonitorState.log.observe(this) { binding.logText.text = it.joinToString("\n") }
    }

    private fun toggleMonitor() {
        val running = MonitorState.running.value == true
        if (running) {
            VisaMonitorService.stop(this)
            return
        }
        if (!ConfigStore(this).load().isComplete) {
            binding.statusText.text = "Open Config and fill in your details first."
            startActivity(Intent(this, ConfigActivity::class.java))
            return
        }
        VisaMonitorService.start(this)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
