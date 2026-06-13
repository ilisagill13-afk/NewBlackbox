package com.usvisa.slotbooker.core

/** An available appointment slot the bot intends to book. */
data class Slot(
    val date: String,  // "yyyy-MM-dd"
    val time: String   // "HH:mm"
)

/** Raised when the AIS session is no longer valid and the user must log in again. */
class SessionExpiredException : Exception("Visa session expired — please log in again.")
