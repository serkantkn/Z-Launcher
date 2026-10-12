package com.serkantkn.zunelauncher

import com.serkantkn.zunelauncher.util.EmailHtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlTest {

    @Test
    fun `pictures from the internet are found however the address is written`() {
        assertTrue(EmailHtml.hasRemoteImages("""<img src="https://a.example/x.png">"""))
        assertTrue(EmailHtml.hasRemoteImages("""<img src='http://a.example/x.png'>"""))
        assertTrue(EmailHtml.hasRemoteImages("""<img src=http://a.example/x.png>"""))
        assertTrue(EmailHtml.hasRemoteImages("""<img src="//cdn.example/x.png">"""))
        assertTrue(EmailHtml.hasRemoteImages("""<img srcset="https://a.example/x.png 1x">"""))
        assertTrue(EmailHtml.hasRemoteImages("""<td background="http://a.example/bg.jpg">"""))
        assertTrue(EmailHtml.hasRemoteImages("""<div style="background-image:url('https://a.example/bg.jpg')">"""))
    }

    @Test
    fun `a mail with nothing to fetch offers no pictures`() {
        assertFalse(EmailHtml.hasRemoteImages("""<p>hello</p><a href="https://a.example">link</a>"""))
        assertFalse(EmailHtml.hasRemoteImages("""<img src="cid:logo@x">"""))
        assertFalse(EmailHtml.hasRemoteImages("""<img src="data:image/png;base64,AAAA">"""))
    }

    @Test
    fun `addresses without a scheme are given https`() {
        assertEquals(
            """<img src="https://cdn.example/x.png">""",
            EmailHtml.withSchemes("""<img src="//cdn.example/x.png">""")
        )
        assertEquals(
            """<div style="background:url(https://cdn.example/b.png)">""",
            EmailHtml.withSchemes("""<div style="background:url(//cdn.example/b.png)">""")
        )
        assertEquals(
            """<img srcset="https://a.example/1.png 1x, https://a.example/2.png 2x">""",
            EmailHtml.withSchemes("""<img srcset="//a.example/1.png 1x, //a.example/2.png 2x">""")
        )
        // A full address and words that only look like it are left alone.
        assertEquals("""<img src="http://a.example/x.png">""", EmailHtml.withSchemes("""<img src="http://a.example/x.png">"""))
        assertEquals("<p>a // b</p>", EmailHtml.withSchemes("<p>a // b</p>"))
    }

    @Test
    fun `plain http is tried over https, anything else is left`() {
        assertEquals("https://a.example/x.png?y=1", EmailHtml.httpsVersion("http://a.example/x.png?y=1"))
        assertEquals("https://a.example/x.png", EmailHtml.httpsVersion("HTTP://a.example/x.png")?.lowercase())
        assertNull(EmailHtml.httpsVersion("https://a.example/x.png"))
        assertNull(EmailHtml.httpsVersion("data:image/png;base64,AA"))
    }
}
