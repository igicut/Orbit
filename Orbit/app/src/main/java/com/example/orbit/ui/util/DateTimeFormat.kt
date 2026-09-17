package com.example.orbit.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pomocne funkcije za datum; SimpleDateFormat zbog minSdk 24 */

fun formatEventDateTime(millis: Long): String =
    SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault()).format(millis)

fun formatEventDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(millis)

/** Kraci oblik, da pocetak i kraj stanu u jedan red */
fun formatEventDateTimeShort(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(millis)

fun formatEventTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(millis)

/** Spaja datum iz DatePicker-a (UTC) sa satom u lokalnoj zoni */
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
