package com.example.data.local

import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object RoomConverters {

    fun timestampToMillis(timestamp: Timestamp?): Long? {
        return timestamp?.toDate()?.time
    }

    fun millisToTimestamp(millis: Long?): Timestamp? {
        if (millis == null || millis <= 0L) return null
        val seconds = millis / 1000L
        val nanos = ((millis % 1000L) * 1_000_000L).toInt().coerceIn(0, 999_999_999)
        return Timestamp(seconds, nanos)
    }

    fun encodeStringList(list: List<String>): String {
        val array = JSONArray()
        list.forEach { array.put(it) }
        return array.toString()
    }

    fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (i in 0 until array.length()) {
                    add(array.optString(i, ""))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encodeStringMap(map: Map<String, String>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, String>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optString(key, "")
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeLongMap(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeLongMap(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, Long>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optLong(key, 0L)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeBooleanMap(map: Map<String, Boolean>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    fun decodeBooleanMap(json: String?): Map<String, Boolean> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, Boolean>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = obj.optBoolean(key, false)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun encodeTimestampMap(map: Map<String, Timestamp>): String {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toDate().time) }
        return obj.toString()
    }

    fun decodeTimestampMap(json: String?): Map<String, Timestamp> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            val result = LinkedHashMap<String, Timestamp>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val ms = obj.optLong(key, 0L)
                if (ms > 0L) {
                    result[key] = Timestamp(Date(ms))
                }
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
