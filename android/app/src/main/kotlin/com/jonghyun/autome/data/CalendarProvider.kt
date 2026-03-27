package com.jonghyun.autome.data

import android.content.Context
import android.provider.CalendarContract
import android.text.format.DateFormat
import java.util.*

class CalendarProvider(private val context: Context) {

    data class CalendarEvent(
        val title: String,
        val startTime: Long,
        val endTime: Long,
        val description: String? = null,
        val isAllDay: Boolean = false
    )

    fun getTodayEvents(): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        
        // 1. 모든 캘린더 계정 정보 확인 로그
        try {
            val calCursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.VISIBLE
                ),
                null, null, null
            )
            calCursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val name = it.getString(1)
                    val account = it.getString(2)
                    val visible = it.getInt(3)
                    android.util.Log.d("CalendarProvider", "Check Calendar: ID=$id, Name=$name, Account=$account, Visible=$visible")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarProvider", "Error checking calendars", e)
        }

        val calendar = Calendar.getInstance()
        // 30일 전
        calendar.add(Calendar.DAY_OF_YEAR, -30)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startMillis = calendar.timeInMillis
        
        // 30일 뒤
        calendar.add(Calendar.DAY_OF_YEAR, 60)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        val endMillis = calendar.timeInMillis

        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID
        )

        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        android.content.ContentUris.appendId(builder, startMillis)
        android.content.ContentUris.appendId(builder, endMillis)

        try {
            val cursor = context.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )

            cursor?.use {
                val titleIdx = it.getColumnIndex(CalendarContract.Instances.TITLE)
                val startIdx = it.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = it.getColumnIndex(CalendarContract.Instances.END)
                val descIdx = it.getColumnIndex(CalendarContract.Instances.DESCRIPTION)
                val allDayIdx = it.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                val calIdIdx = it.getColumnIndex(CalendarContract.Instances.CALENDAR_ID)

                while (it.moveToNext()) {
                    val title = it.getString(titleIdx) ?: "제목 없음"
                    val start = it.getLong(startIdx)
                    val calId = it.getLong(calIdIdx)
                    
                    // 디버깅 로그: 가져오고 있는 모든 일정의 캘린더 ID 확인
                    android.util.Log.d("CalendarProvider", "Instance Found: $title at ${Date(start)} (Calendar ID: $calId)")
                    
                    events.add(CalendarEvent(
                        title = title,
                        startTime = start,
                        endTime = it.getLong(endIdx),
                        description = it.getString(descIdx),
                        isAllDay = it.getInt(allDayIdx) == 1
                    ))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CalendarProvider", "Error querying events", e)
        }

        return events
    }

    fun getTodayEventsSummary(): String {
        val events = getTodayEvents()
        if (events.isEmpty()) return "최근 1개월간 등록된 일정이 없습니다."

        val sb = StringBuilder("사용자 일정 (전후 1개월):\n")
        events.forEach { event ->
            val dateStr = DateFormat.format("MM/dd", event.startTime).toString()
            if (event.isAllDay) {
                sb.append("- $dateStr [하루 종일]: ${event.title}\n")
            } else {
                val startTimeStr = DateFormat.format("HH:mm", event.startTime).toString()
                sb.append("- $dateStr $startTimeStr: ${event.title}\n")
            }
        }
        return sb.toString()
    }
}
