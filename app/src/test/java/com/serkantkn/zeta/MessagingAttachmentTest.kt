package com.serkantkn.zeta

import com.serkantkn.zeta.data.model.AttachmentKind
import com.serkantkn.zeta.data.model.MmsAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagingAttachmentTest {

    @Test
    fun `what came with a message is told apart by its type`() {
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.of("image/jpeg"))
        assertEquals(AttachmentKind.IMAGE, AttachmentKind.of("IMAGE/PNG"))
        assertEquals(AttachmentKind.VIDEO, AttachmentKind.of("video/3gpp"))
        assertEquals(AttachmentKind.AUDIO, AttachmentKind.of("audio/amr"))
        assertEquals(AttachmentKind.AUDIO, AttachmentKind.of("application/ogg"))
        assertEquals(AttachmentKind.CONTACT, AttachmentKind.of("text/x-vCard"))
        assertEquals(AttachmentKind.CONTACT, AttachmentKind.of("text/vcard; charset=utf-8"))
        assertEquals(AttachmentKind.OTHER, AttachmentKind.of("application/pdf"))
    }

    @Test
    fun `the words and the layout are not counted as attachments`() {
        assertFalse(AttachmentKind.isContent("text/plain"))
        assertFalse(AttachmentKind.isContent("application/smil"))
        assertFalse(AttachmentKind.isContent(""))
        assertTrue(AttachmentKind.isContent("image/gif"))
        assertTrue(AttachmentKind.isContent("text/x-vcard"))
    }

    @Test
    fun `a part is read from the message store by its number`() {
        assertEquals("content://mms/part/42", MmsAttachment(42L, "image/jpeg").uri)
    }
}
