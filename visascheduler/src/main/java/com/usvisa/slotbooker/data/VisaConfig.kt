package com.usvisa.slotbooker.data

/**
 * User-supplied configuration for the auto-booker. Dates are ISO "yyyy-MM-dd".
 */
data class VisaConfig(
    val locale: String,        // e.g. "en-in", "en-ca"
    val email: String,
    val password: String,
    val scheduleId: String,    // id of the existing (paid) appointment
    val consulateFacilityId: String,
    val ascFacilityId: String, // biometrics/ASC facility; "" if not required
    val minDate: String,       // earliest acceptable date (inclusive)
    val maxDate: String,       // latest acceptable date (inclusive)
    val pollIntervalMinutes: Int,
    val anthropicApiKey: String // optional; enables the real-AI scheduler when set
) {
    val isComplete: Boolean
        get() = locale.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
                scheduleId.isNotBlank() && consulateFacilityId.isNotBlank() &&
                minDate.isNotBlank() && maxDate.isNotBlank() && minDate <= maxDate

    val aiEnabled: Boolean
        get() = anthropicApiKey.isNotBlank()

    companion object {
        /** Enforced floor so we never hammer the site / trip a ban. */
        const val MIN_POLL_MINUTES = 2
        const val DEFAULT_POLL_MINUTES = 4
    }
}
