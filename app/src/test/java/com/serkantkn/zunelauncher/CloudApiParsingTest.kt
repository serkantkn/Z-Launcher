package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.data.model.CloudAccount
import com.serkantkn.zunelauncher.data.model.CloudService
import com.serkantkn.zunelauncher.data.repository.GoogleDriveApi
import com.serkantkn.zunelauncher.data.repository.OneDriveApi
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
        val items = GoogleDriveApi.parse(response)

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
        assertTrue(GoogleDriveApi.parse(JSONObject("{}")).isEmpty())
        assertTrue(GoogleDriveApi.parse(JSONObject("""{"files":[]}""")).isEmpty())
    }

    @Test
    fun oneDriveListsFoldersFirst() {
        val response = JSONObject(
            """
            {"value":[
              {"id":"a","name":"rapor.docx","size":1024,"lastModifiedDateTime":"2026-09-04T12:00:00Z",
               "file":{"mimeType":"application/vnd.openxmlformats-officedocument.wordprocessingml.document"}},
              {"id":"b","name":"Belgeler","lastModifiedDateTime":"2026-09-01T12:00:00Z","folder":{"childCount":3}}
            ]}
            """.trimIndent()
        )
        val items = OneDriveApi.parse(response)

        assertEquals(2, items.size)
        assertTrue(items[0].isFolder)
        assertEquals("Belgeler", items[0].name)
        assertFalse(items[1].isFolder)
        assertEquals(1024L, items[1].size)
        assertTrue(items[1].mimeType.contains("wordprocessingml"))
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
        assertNull(CloudAccount.fromJson(JSONObject("""{"service":"onedrive"}""")))
        assertEquals(CloudService.ONEDRIVE, CloudService.fromId("onedrive"))
        assertNull(CloudService.fromId("nope"))
    }
}
