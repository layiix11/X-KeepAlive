package app.xkeepalive.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")

fun formatEventTime(timeMs: Long): String =
    Instant.ofEpochMilli(timeMs).atZone(ZoneId.systemDefault()).format(clock)

fun eventTitle(kind: String): String = when (kind) {
    "SERVICE_STARTED" -> "Service started"
    "SERVICE_STOPPED" -> "Service stopped"
    "PHASE" -> "Status"
    "PROCESS" -> "Process"
    "LIMIT" -> "Limit"
    "POLICY" -> "Policy"
    "SHIZUKU" -> "Shizuku"
    "BOOT" -> "Boot"
    "TEST" -> "On-device test"
    "PID" -> "PID"
    "ERROR" -> "Error"
    else -> kind
}
