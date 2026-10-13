package com.serkantkn.zunelauncher.data.repository

import android.content.Context
import com.serkantkn.zunelauncher.data.model.ReadingItem
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

/**
 * The reading list: pages kept to be read later, from the browser or from the news hub, and
 * the copy of each one's text kept on the phone so it can be read without a connection.
 *
 * The copy is the page's story only - what reading view keeps of it, set out the same way - so it
 * is a few kilobytes rather than the whole site, and it reads the same offline as on.
 */
class ReadingListRepository(context: Context) {

    private val dir = File(context.filesDir, "reading").apply { mkdirs() }
    private val listFile = File(context.filesDir, "reading_list.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _items = MutableStateFlow<List<ReadingItem>>(emptyList())
    val items: StateFlow<List<ReadingItem>> = _items.asStateFlow()

    init {
        scope.launch {
            val loaded = try {
                if (!listFile.exists()) emptyList() else {
                    val array = JSONArray(listFile.readText())
                    (0 until array.length()).mapNotNull { runCatching { ReadingItem.fromJson(array.getJSONObject(it)) }.getOrNull() }
                }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "reading list unreadable", e)
                emptyList()
            }
            _items.update { current -> (current + loaded).distinctBy { it.url } }
        }
    }

    fun contains(url: String): Boolean = _items.value.any { it.url == url }

    /** Keeps [item] at the top of the list; one already there moves up and keeps its copy. */
    fun add(item: ReadingItem) {
        _items.update { current ->
            val before = current.firstOrNull { it.url == item.url }
            listOf(item.copy(offline = before?.offline ?: false)) + current.filterNot { it.url == item.url }
        }
        persist()
    }

    fun remove(url: String) {
        _items.update { current -> current.filterNot { it.url == url } }
        scope.launch { fileOf(url).delete() }
        persist()
    }

    fun markRead(url: String, read: Boolean = true) {
        _items.update { current -> current.map { if (it.url == url && it.read != read) it.copy(read = read) else it } }
        persist()
    }

    /** Keeps the page's text on the phone, set out as a reading view page. */
    fun keepCopy(url: String, title: String, source: String, bodyHtml: String) {
        scope.launch {
            try {
                fileOf(url).writeText(offlinePage(title, source, bodyHtml))
                _items.update { current -> current.map { if (it.url == url) it.copy(offline = true) else it } }
                persist()
            } catch (e: Exception) {
                ZuneLog.w(TAG, "copy of $url not kept", e)
            }
        }
    }

    /** The kept copy of [url], or null when there is none. */
    fun copyOf(url: String): String? = fileOf(url).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    private fun fileOf(url: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".html")
    }

    private fun persist() {
        val snapshot = _items.value
        scope.launch {
            try {
                val array = JSONArray()
                snapshot.forEach { array.put(it.toJson()) }
                val temp = File(listFile.parentFile, listFile.name + ".tmp")
                temp.writeText(array.toString())
                if (!temp.renameTo(listFile)) {
                    listFile.writeText(array.toString())
                    temp.delete()
                }
            } catch (e: Exception) {
                ZuneLog.w(TAG, "reading list not written", e)
            }
        }
    }

    private companion object {
        const val TAG = "ReadingList"

        /** The kept copy as a page of its own, in reading view's type, light or dark with the phone. */
        fun offlinePage(title: String, source: String, body: String): String {
            fun escape(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            return """<!DOCTYPE html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>${escape(title)}</title>
<style>
html,body{margin:0;padding:0;background:#fdfdfb;color:#1a1a1a}
@media (prefers-color-scheme: dark){html,body{background:#121212;color:#e8e8e8}a{color:#7fb2ff}}
#zune-reader{max-width:38em;margin:0 auto;padding:28px 20px 64px;font-family:-apple-system,Segoe UI,Roboto,sans-serif;font-size:18px;line-height:1.65}
h1{font-weight:300;font-size:30px;line-height:1.25;margin:0 0 6px}
.source{color:#888;font-size:15px;margin:0 0 22px}
p{margin:0 0 18px} img{max-width:100%;height:auto;display:block;margin:18px auto}
figcaption,small{color:#888;font-size:15px} *{max-width:100%}
</style></head><body><div id="zune-reader">
<h1>${escape(title)}</h1><p class="source">${escape(source)}</p>
$body
</div></body></html>"""
        }
    }
}
