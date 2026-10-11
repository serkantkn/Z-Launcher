package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudService
import com.serkantkn.zunelauncher.data.repository.GoogleDriveApi
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudApiParsingTest {

    @Test
    fun driveFoldersFilesAndDocumentsAreToldApart() {
        val response = JSONObject(
            """
            {"files":[
              {"id":"1","name":"Belgeler","mimeType":"application/vnd.google-apps.folder","modifiedTime":"2026-09-01T10:15:30Z"},
              {"id":"2","name":"Rapor.pdf","mimeType":"application/pdf","size":"20480","modifiedTime":"2026-09-02T08:00:00Z"},
              {"id":"3","name":"Plan","mimeType":"application/vnd.google-apps.document","modifiedTime":"2026-09-03T09:00:00Z"},
              {"name":"kimliksiz","mimeType":"text/plain"}
            ]}
            """.trimIndent()
        )
        // parse now hands back a page, since a folder can arrive in several of them.
        val items = GoogleDriveApi.parse(response).items

        assertEquals(3, items.size)
        assertTrue(items[0].isFolder)
        assertEquals(0L, items[0].size)

        val pdf = items[1]
        assertFalse(pdf.isFolder)
        assertEquals(20480L, pdf.size)
        assertFalse(pdf.isGoogleDocument)
        assertTrue(pdf.lastModified > 0L)

        // A Google Doc has no bytes of its own, so it is exported as PDF when it is downloaded.
        val document = items[2]
        assertTrue(document.isGoogleDocument)
        assertEquals("Plan.pdf", GoogleDriveApi.downloadName(document))
        assertEquals("Rapor.pdf", GoogleDriveApi.downloadName(pdf))
    }

    @Test
    fun driveHandlesAnEmptyAnswer() {
        assertTrue(GoogleDriveApi.parse(JSONObject("{}")).items.isEmpty())
        assertTrue(GoogleDriveApi.parse(JSONObject("""{"files":[]}""")).items.isEmpty())
    }

    @Test
    fun anAccountSurvivesBeingStoredAndReadBack() {
        val account = CloudAccount(
            id = "google_drive:serkan@example.com",
            service = CloudService.GOOGLE_DRIVE,
            displayName = "Serkan",
            email = "serkan@example.com"
        )
        assertEquals(account, CloudAccount.fromJson(account.toJson()))
    }

    @Test
    fun anAccountOfAnUnknownServiceIsDropped() {
        assertNull(CloudAccount.fromJson(JSONObject("""{"id":"x","service":"dropbox"}""")))
        assertNull(CloudAccount.fromJson(JSONObject("""{"service":"dropbox"}""")))
        assertEquals(CloudService.GOOGLE_DRIVE, CloudService.fromId("google_drive"))
        assertNull(CloudService.fromId("nope"))
    }

    @Test
    fun shortcutsOpenWhatTheyPointAtAndUnexportableGoogleFilesOpenOnTheWeb() {
        val response = JSONObject(
            """
            {"files":[
              {"id":"s1","name":"Ortak klasör","mimeType":"application/vnd.google-apps.shortcut",
               "shortcutDetails":{"targetId":"f9","targetMimeType":"application/vnd.google-apps.folder"}},
              {"id":"s2","name":"Tablo","mimeType":"application/vnd.google-apps.shortcut",
               "shortcutDetails":{"targetId":"t7","targetMimeType":"application/vnd.google-apps.spreadsheet"}},
              {"id":"f1","name":"Anket","mimeType":"application/vnd.google-apps.form","webViewLink":"https://docs.google.com/forms/d/f1"},
              {"id":"p1","name":"Rapor.pdf","mimeType":"application/pdf"}
            ]}
            """.trimIndent()
        )
        val items = GoogleDriveApi.parse(response).items

        // A shortcut to a folder is a folder, listed by its target; renaming it stays with the shortcut.
        assertTrue(items[0].isFolder)
        assertEquals("f9", items[0].contentId)
        assertEquals("s1", items[0].id)

        // A shortcut to a spreadsheet exports the spreadsheet.
        assertTrue(items[1].isGoogleDocument)
        assertEquals("t7", items[1].contentId)

        // A form cannot be exported; it only opens on its page.
        assertTrue(items[2].isWebOnly)
        assertFalse(items[2].isGoogleDocument)
        assertEquals("https://docs.google.com/forms/d/f1", GoogleDriveApi.webUrlOf(items[2]))

        // An ordinary file is its own content, and a Google file downloads as a PDF.
        assertEquals("p1", items[3].contentId)
        assertNull(items[3].targetId)
        assertEquals("application/pdf", GoogleDriveApi.downloadMime(items[1]))
    }
}
