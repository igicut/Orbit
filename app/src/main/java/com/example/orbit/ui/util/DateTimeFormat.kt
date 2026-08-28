package com.example.orbit.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Date helpers.
 *
 * We use SimpleDateFormat/Calendar rather than the nicer java.time API because this app's
 * minSdk is 24, and java.time needs API 26 (or extra build setup called desugaring).
 */

fun formatEventDateTime(millis: Long): String =
    SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(millis)

fun formatEventDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(millis)

fun formatEventTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(millis)

/**
 * Combines a date coming out of Material's DatePicker with an hour and minute.
 *
 * The subtlety: DatePicker hands back midnight UTC for the chosen day. If you just add
 * hours to it you get the wrong day for anyone not on UTC. So we read the year/month/day
 * back out *in UTC*, then rebuild the timestamp in the phone's own timezone.
 */
fun combineDateAndTime(dateMillisUtc: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = dateMillisUtc
    }

    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
