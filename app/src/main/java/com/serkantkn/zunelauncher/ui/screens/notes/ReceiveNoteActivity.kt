package com.serkantkn.zunelauncher.ui.screens.notes

import android.content.Context
import com.serkantkn.zunelauncher.util.AppLocale
import com.serkantkn.zunelauncher.util.ZuneLog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.serkantkn.zunelauncher.MainActivity
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.repository.NotesBridge
import com.serkantkn.zunelauncher.di.appContainer
import kotlinx.coroutines.launch

/**
 * Target of the system share sheet ("paylaş → Notlar") and of "a new note" from anywhere on the
 * phone, as its notes app. Saves the shared text as a new note,
 * then brings the launcher to the front with that note open. Has no UI of its own.
 */
class ReceiveNoteActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // "A new note", as the phone's notes app: an empty note, open and ready to be written.
        if (intent?.action == ACTION_CREATE_NOTE) {
            NotesBridge.newNote(content = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty())
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(MainActivity.EXTRA_OPEN_NOTES, true)
                }
            )
            finish()
            return
        }

        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = intent?.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()

        if (text.isEmpty() && subject.isEmpty()) {
            finish()
            return
        }

        val store = appContainer.notesDataStore
        lifecycleScope.launch {
            try {
                val note = Note(title = subject, content = text)
                store.update { current -> current + note }
                NotesBridge.open(note.id)
            } catch (e: Exception) {
                ZuneLog.e("ReceiveNoteActivity", "onCreate failed", e)
            }
            val launcherIntent = Intent(this@ReceiveNoteActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_NOTES, true)
            }
            startActivity(launcherIntent)
            finish()
        }
    }
}

/** Intent.ACTION_CREATE_NOTE, which the platform has from Android 14 on. */
private const val ACTION_CREATE_NOTE = "android.intent.action.CREATE_NOTE"
