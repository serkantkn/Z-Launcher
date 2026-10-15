package com.serkantkn.zunelauncher.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import com.serkantkn.zunelauncher.data.repository.MmsTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * What the phone says once it has sent a picture message or fetched one: passed to
 * [MmsTransport], which files the outcome.
 */
class MmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context?.applicationContext ?: return
        val action = intent?.action ?: return
        val row = intent.getLongExtra(EXTRA_ROW, -1L)
        val file = intent.getStringExtra(EXTRA_FILE)?.let { File(it) }
        val code = resultCode
        when (action) {
            ACTION_SENT -> {
                val response = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { MmsTransport.onSent(ctx, row, code, response, file) } finally { pending.finish() }
                }
            }
            ACTION_DOWNLOADED -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try { MmsTransport.onDownloaded(ctx, row, code, file) } finally { pending.finish() }
                }
            }
            ACTION_RECEIPT_SENT -> file?.delete()
        }
    }

    companion object {
        const val ACTION_SENT = "com.serkantkn.zunelauncher.MMS_SENT"
        const val ACTION_DOWNLOADED = "com.serkantkn.zunelauncher.MMS_DOWNLOADED"
        const val ACTION_RECEIPT_SENT = "com.serkantkn.zunelauncher.MMS_RECEIPT_SENT"
        const val EXTRA_ROW = "row"
        const val EXTRA_FILE = "file"
    }
}
