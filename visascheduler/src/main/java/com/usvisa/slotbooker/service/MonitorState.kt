package com.usvisa.slotbooker.service

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData

/** Process-wide observable state shared between the monitor service and the UI. */
object MonitorState {

    private val _running = MutableLiveData(false)
    val running: LiveData<Boolean> = _running

    private val _status = MutableLiveData("Idle")
    val status: LiveData<String> = _status

    private val _log = MutableLiveData<List<String>>(emptyList())
    val log: LiveData<List<String>> = _log

    fun setRunning(value: Boolean) = _running.postValue(value)

    fun setStatus(text: String) = _status.postValue(text)

    fun append(line: String) {
        val stamped = "${android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis())}  $line"
        val current = _log.value ?: emptyList()
        _log.postValue((listOf(stamped) + current).take(MAX_LINES))
    }

    private const val MAX_LINES = 60
}
