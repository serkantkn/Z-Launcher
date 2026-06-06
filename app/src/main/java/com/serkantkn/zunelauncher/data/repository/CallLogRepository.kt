package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import android.provider.CallLog
import com.serkantkn.zunelauncher.data.model.CallLogModel
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
                CallLog.Calls.TYPE
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

                while (it.moveToNext()) {
                    val id = it.getString(idIndex)
                    val name = it.getString(nameIndex)
                    val number = it.getString(numberIndex)
                    val date = it.getLong(dateIndex)
                    val duration = it.getLong(durationIndex)
                    val type = it.getInt(typeIndex)

                    callLogs.add(
                        CallLogModel(
                            id = id,
                            name = name,
                            number = number,
                            dateMillis = date,
                            durationSeconds = duration,
                            type = type
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext callLogs
    }
}
