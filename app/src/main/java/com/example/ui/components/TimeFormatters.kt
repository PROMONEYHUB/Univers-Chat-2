package com.example.ui.components

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatRelativeTime(timestamp: Timestamp?): String {
    if (timestamp == null) return ""
    val date = timestamp.toDate()
    val diffMs = System.currentTimeMillis() - date.time
    val minutes = diffMs / 60_000L
    val hours = diffMs / 3_600_000L
    val days = diffMs / 86_400_000L

    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m"
        hours < 24L -> "${hours}h"
        days < 7L -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

fun formatMessageClock(timestamp: Timestamp?): String {
    val date: Date = timestamp?.toDate() ?: return "Sending…"
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
}

fun formatDayHeader(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return "Today"
    val cal = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
        cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

fun isSameCalendarDay(first: Timestamp?, second: Timestamp?): Boolean {
    if (first == null && second == null) return true
    if (first == null || second == null) return false
    val c1 = Calendar.getInstance().apply { time = first.toDate() }
    val c2 = Calendar.getInstance().apply { time = second.toDate() }
    return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
        c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
}
