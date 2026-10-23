package com.saidjte.schoolradio

import org.json.JSONArray
import org.json.JSONObject

/** فقرة إذاعية واحدة (تسجيل صوتي قابل للتشغيل) */
data class Segment(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val durationSec: Int,
    val audioUrl: String,
    val order: Int
)

/** سطر من الجدول الأسبوعي للإذاعة المدرسية */
data class ScheduleItem(
    val day: String,
    val time: String,
    val title: String,
    val presenter: String
)

/** معلومات البث المباشر (إن وُجد) */
data class LiveInfo(
    val enabled: Boolean,
    val title: String,
    val url: String
)

/** الكتالوج الكامل: فقرات + جدول + بث مباشر */
data class Catalog(
    val school: String,
    val version: Int,
    val segments: List<Segment>,
    val schedule: List<ScheduleItem>,
    val live: LiveInfo
) {
    companion object {
        fun fromJson(obj: JSONObject): Catalog {
            val segments = mutableListOf<Segment>()
            val segArr: JSONArray = obj.optJSONArray("segments") ?: JSONArray()
            for (i in 0 until segArr.length()) {
                val s = segArr.getJSONObject(i)
                segments.add(
                    Segment(
                        id = s.optString("id", "seg$i"),
                        title = s.optString("title", "بدون عنوان"),
                        description = s.optString("description", ""),
                        category = s.optString("category", "عام"),
                        durationSec = s.optInt("duration_sec", 0),
                        audioUrl = s.optString("audio", ""),
                        order = s.optInt("order", i)
                    )
                )
            }

            val schedule = mutableListOf<ScheduleItem>()
            val schArr: JSONArray = obj.optJSONArray("schedule") ?: JSONArray()
            for (i in 0 until schArr.length()) {
                val s = schArr.getJSONObject(i)
                schedule.add(
                    ScheduleItem(
                        day = s.optString("day", ""),
                        time = s.optString("time", ""),
                        title = s.optString("title", ""),
                        presenter = s.optString("presenter", "")
                    )
                )
            }

            val liveObj = obj.optJSONObject("live") ?: JSONObject()
            val live = LiveInfo(
                enabled = liveObj.optBoolean("enabled", false),
                title = liveObj.optString("title", "البث المباشر"),
                url = liveObj.optString("url", "")
            )

            return Catalog(
                school = obj.optString("school", "الإذاعة المدرسية"),
                version = obj.optInt("catalog_version", 1),
                segments = segments.sortedBy { it.order },
                schedule = schedule,
                live = live
            )
        }
    }
}
