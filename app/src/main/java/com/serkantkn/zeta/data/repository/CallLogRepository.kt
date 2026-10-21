package com.serkantkn.zeta.data.repository

import com.serkantkn.zeta.util.ZuneLog
import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.serkantkn.zeta.data.model.CallLogModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    suspend fun getRecentCalls(): List<CallLogModel> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogModel>()
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.NUMBER_PRESENTATION
            )

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val idIndex = it.getColumnIndex(CallLog.Calls._ID)
                val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
                val presentationIndex = it.getColumnIndex(CallLog.Calls.NUMBER_PRESENTATION)

                while (it.moveToNext()) {
                    val id = it.getString(idIndex) ?: continue
                    val name = it.getString(nameIndex)
                    // A withheld number is stored as null; the row is still a call that happened.
                    val number = it.getString(numberIndex).orEmpty()
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)
                    val type = it.getInt(typeIndex)
                    val presentation = if (presentationIndex >= 0 && !it.isNull(presentationIndex)) {
                        it.getInt(presentationIndex)
                    } else {
                        CallLog.Calls.PRESENTATION_ALLOWED
                    }

                    callLogs.add(
                        CallLogModel(
                            id = id,
                            name = name,
                            number = number,
                            dateMillis = date,
                            durationSeconds = duration,
                            type = type,
                            presentation = presentation
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
            ZuneLog.e("CallLogRepository", "getRecentCalls failed", e)
            throw e
        } catch (e: Exception) {
            ZuneLog.e("CallLogRepository", "getRecentCalls failed", e)
            throw e
        }
        return@withContext callLogs
    }

    /**
     * Looking at the history is what makes a missed call seen. The log keeps its own "new" flag,
     * which the phone's badge and the system's missed-call notification both read; clearing it
     * here is what stops the badge counting calls the user has already looked at. Needs the
     * write permission, which is asked for alongside the read one; without it this does nothing.
     */
    suspend fun markMissedCallsRead(): Boolean = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext false
        }
        try {
            context.contentResolver.update(
                CallLog.Calls.CONTENT_URI,
                ContentValues().apply {
                    put(CallLog.Calls.NEW, 0)
                    put(CallLog.Calls.IS_READ, 1)
                },
                "${CallLog.Calls.TYPE} = ? AND (${CallLog.Calls.NEW} = 1 OR ${CallLog.Calls.IS_READ} = 0)",
                arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            )
            true
        } catch (e: Exception) {
            ZuneLog.w("CallLogRepository", "the missed calls would not be marked seen", e)
            false
        }
    }

    suspend fun getCallLogsForContact(phoneNumbers: List<String>): List<CallLogModel> = withContext(Dispatchers.IO) {
        if (phoneNumbers.isEmpty()) return@withContext emptyList()
        val allLogs = try {
            getRecentCalls()
        } catch (e: Exception) {
            ZuneLog.w("CallLogRepository", "getCallLogsForContact: call log unavailable", e)
            return@withContext emptyList()
        }
        val cleanNumbers = phoneNumbers.map { it.replace(Regex("[^0-9+]"), "") }.filter { it.isNotEmpty() }

        allLogs.filter { log ->
            val cleanLogNumber = log.number.replace(Regex("[^0-9+]"), "")
            cleanLogNumber.isNotEmpty() && cleanNumbers.any { cleanNumber ->
                cleanLogNumber == cleanNumber ||
                (cleanLogNumber.length >= 7 && cleanNumber.length >= 7 &&
                 (cleanLogNumber.endsWith(cleanNumber.takeLast(7)) || cleanNumber.endsWith(cleanLogNumber.takeLast(7))))
            }
        }
    }
}
