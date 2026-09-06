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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Target of the system share sheet ("paylaş → Notlar"). Saves the shared text as a new note,
 * then brings the launcher to the front with that note open. Has no UI of its own.
 */
class ReceiveNoteActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                val current = store.notesFlow.first()
                store.saveNotes(current + note)
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
