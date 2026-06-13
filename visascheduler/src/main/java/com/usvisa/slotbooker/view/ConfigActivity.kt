package com.usvisa.slotbooker.view

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.usvisa.slotbooker.data.ConfigStore
import com.usvisa.slotbooker.data.VisaConfig
import com.usvisa.slotbooker.databinding.ActivityConfigBinding

/** Form to capture/edit the booking configuration (credentials, ids, date range, interval). */
class ConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfigBinding
    private lateinit var store: ConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = ConfigStore(this)

        bind(store.load())
        binding.saveButton.setOnClickListener { save() }
    }

    private fun bind(c: VisaConfig) = with(binding) {
        localeInput.setText(c.locale)
        emailInput.setText(c.email)
        passwordInput.setText(c.password)
        scheduleInput.setText(c.scheduleId)
        consulateInput.setText(c.consulateFacilityId)
        ascInput.setText(c.ascFacilityId)
        minDateInput.setText(c.minDate)
        maxDateInput.setText(c.maxDate)
        pollInput.setText(c.pollIntervalMinutes.toString())
        apiKeyInput.setText(c.anthropicApiKey)
    }

    private fun save() {
        val poll = binding.pollInput.text.toString().toIntOrNull()
            ?: VisaConfig.DEFAULT_POLL_MINUTES
        val config = VisaConfig(
            locale = binding.localeInput.text.toString().trim(),
            email = binding.emailInput.text.toString().trim(),
            password = binding.passwordInput.text.toString(),
            scheduleId = binding.scheduleInput.text.toString().trim(),
            consulateFacilityId = binding.consulateInput.text.toString().trim(),
            ascFacilityId = binding.ascInput.text.toString().trim(),
            minDate = binding.minDateInput.text.toString().trim(),
            maxDate = binding.maxDateInput.text.toString().trim(),
            pollIntervalMinutes = poll.coerceAtLeast(VisaConfig.MIN_POLL_MINUTES),
            anthropicApiKey = binding.apiKeyInput.text.toString().trim()
        )

        if (!config.isComplete) {
            Toast.makeText(this, "Please fill all fields (and ensure min date ≤ max date).", Toast.LENGTH_LONG).show()
            return
        }
        store.save(config)
        Toast.makeText(this, "Saved.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
