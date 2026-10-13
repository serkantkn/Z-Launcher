package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.ui.openwith.HubShortcutActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenWithTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun everyCategoryAliasLeadsToAHub() {
        val aliases = Regex("""<activity-alias android:name="\.(App\w+)"""").findAll(manifest).map { it.groupValues[1] }.toList()
        assertTrue("no category aliases in the manifest", aliases.size >= 10)
        aliases.forEach { alias -> assertTrue("$alias has no hub", HubShortcutActivity.hubOf(alias) != null) }
        assertEquals(HubType.PICTURES, HubShortcutActivity.hubOf("AppGallery"))
        assertEquals(HubType.MUSIC, HubShortcutActivity.hubOf("AppMusic"))
        assertNull(HubShortcutActivity.hubOf("Nothing"))
    }

    @Test
    fun theLauncherOffersItselfForPicturesSongsVideosAndNotes() {
        listOf(
            "android.intent.category.APP_GALLERY",
            "android.intent.category.APP_MUSIC",
            "android.intent.action.CREATE_NOTE",
            "android:name=\".ui.openwith.ImageViewerActivity\"",
            "android:name=\".ui.openwith.OpenAudioActivity\"",
            "android:mimeType=\"image/*\"",
            "android:mimeType=\"audio/*\"",
            "android:mimeType=\"video/*\""
        ).forEach { assertTrue("manifest lacks $it", it in manifest) }
    }
}
