package com.serkantkn.zunelauncher.data.model

import com.serkantkn.zunelauncher.util.ZuneLog
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/*
 * Shared org.json helpers for everything the DataStores persist.
 *
 * Convention: every stored list is a JSONArray written into a stringPreferencesKey. Each
 * element is either a plain string or a JSONObject produced by the model's `toJson()` and read
 * back by its companion `fromJson(JSONObject)`. A broken element is logged with ZuneLog.w and
 * skipped so one bad row never empties the whole list.
 */

/** True when the raw preference value is already in the JSON array format. */
internal fun String?.looksLikeJsonArray(): Boolean =
    this != null && trimStart().startsWith("[")

/**
 * Parses a JSON array of objects with [parse]. Returns an empty list for a null/blank string
 * or when the string is not a JSON array at all; per-element failures are skipped.
 */
internal fun <T> parseJsonObjectList(
    json: String?,
    tag: String,
    parse: (JSONObject) -> T
): List<T> {
    if (json.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(json)
    } catch (e: JSONException) {
        ZuneLog.w(tag, "stored value is not a json array, treating as empty", e)
        return emptyList()
    }
    val list = ArrayList<T>(array.length())
    for (i in 0 until array.length()) {
        try {
            list.add(parse(array.getJSONObject(i)))
        } catch (e: JSONException) {
            ZuneLog.w(tag, "skipping malformed element at index $i", e)
        } catch (e: IllegalArgumentException) {
            ZuneLog.w(tag, "skipping invalid element at index $i", e)
        }
    }
    return list
}

/** Serializes the list as a JSON array of the objects returned by [toJson]. */
internal fun <T> List<T>.toJsonArrayString(toJson: (T) -> JSONObject): String {
    val array = JSONArray()
    for (item in this) array.put(toJson(item))
    return array.toString()
}

/** Parses a JSON array of strings; non-string or empty elements are skipped. */
internal fun parseJsonStringList(json: String?, tag: String): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    val array = try {
        JSONArray(json)
    } catch (e: JSONException) {
        ZuneLog.w(tag, "stored value is not a json string array, treating as empty", e)
        return emptyList()
    }
    val list = ArrayList<String>(array.length())
    for (i in 0 until array.length()) {
        val value = array.optString(i, "")
        if (value.isNotEmpty()) list.add(value)
    }
    return list
}

/** Serializes the strings as a JSON array, preserving order. */
internal fun Collection<String>.toJsonStringArray(): String = JSONArray(this).toString()

/** Null for a missing, JSON-null or empty string value. */
internal fun JSONObject.optStringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }

/** Null for a missing or JSON-null number. */
internal fun JSONObject.optLongOrNull(key: String): Long? =
    if (isNull(key)) null else optLong(key)
