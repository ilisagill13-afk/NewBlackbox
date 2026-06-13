package com.usvisa.slotbooker.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Persists [VisaConfig] in EncryptedSharedPreferences so credentials are stored encrypted at rest.
 */
class ConfigStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "visa_config",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun save(config: VisaConfig) {
        prefs.edit()
            .putString(KEY_LOCALE, config.locale)
            .putString(KEY_EMAIL, config.email)
            .putString(KEY_PASSWORD, config.password)
            .putString(KEY_SCHEDULE_ID, config.scheduleId)
            .putString(KEY_CONSULATE, config.consulateFacilityId)
            .putString(KEY_ASC, config.ascFacilityId)
            .putString(KEY_MIN_DATE, config.minDate)
            .putString(KEY_MAX_DATE, config.maxDate)
            .putInt(KEY_POLL, config.pollIntervalMinutes)
            .putString(KEY_API_KEY, config.anthropicApiKey)
            .apply()
    }

    fun load(): VisaConfig {
        val poll = prefs.getInt(KEY_POLL, VisaConfig.DEFAULT_POLL_MINUTES)
            .coerceAtLeast(VisaConfig.MIN_POLL_MINUTES)
        return VisaConfig(
            locale = prefs.getString(KEY_LOCALE, "en-in") ?: "en-in",
            email = prefs.getString(KEY_EMAIL, "") ?: "",
            password = prefs.getString(KEY_PASSWORD, "") ?: "",
            scheduleId = prefs.getString(KEY_SCHEDULE_ID, "") ?: "",
            consulateFacilityId = prefs.getString(KEY_CONSULATE, "") ?: "",
            ascFacilityId = prefs.getString(KEY_ASC, "") ?: "",
            minDate = prefs.getString(KEY_MIN_DATE, "") ?: "",
            maxDate = prefs.getString(KEY_MAX_DATE, "") ?: "",
            pollIntervalMinutes = poll,
            anthropicApiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        )
    }

    private companion object {
        const val KEY_LOCALE = "locale"
        const val KEY_EMAIL = "email"
        const val KEY_PASSWORD = "password"
        const val KEY_SCHEDULE_ID = "schedule_id"
        const val KEY_CONSULATE = "consulate_facility"
        const val KEY_ASC = "asc_facility"
        const val KEY_MIN_DATE = "min_date"
        const val KEY_MAX_DATE = "max_date"
        const val KEY_POLL = "poll_minutes"
        const val KEY_API_KEY = "anthropic_api_key"
    }
}
