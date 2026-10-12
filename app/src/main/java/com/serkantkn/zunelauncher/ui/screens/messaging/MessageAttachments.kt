package com.serkantkn.zunelauncher.ui.screens.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.serkantkn.zunelauncher.R
import com.serkantkn.zunelauncher.data.model.AttachmentKind
import com.serkantkn.zunelauncher.data.model.MmsAttachment
import com.serkantkn.zunelauncher.util.ZuneLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What came with a picture message, drawn inside its bubble.
 *
 * A picture is shown as itself, the width of the bubble; anything else - a clip, a recording, a
 * contact's card - is a line saying what it is. Either is tapped to open it in an app built for
 * it. Before this a picture message said only "attachment", and the picture could not be seen
 * from the launcher at all.
 */
@Composable
internal fun MessageAttachmentView(
    attachment: MmsAttachment,
    ink: Color,
    onOpen: (MmsAttachment) -> Unit
) {
    val context = LocalContext.current
    when (attachment.kind) {
        AttachmentKind.IMAGE -> SubcomposeAsyncImage(
            model = remember(attachment.partId) {
                ImageRequest.Builder(context).data(Uri.parse(attachment.uri)).size(PICTURE_PX).crossfade(true).build()
            },
            contentDescription = stringResource(R.string.msg_attachment_picture),
            contentScale = ContentScale.FillWidth,
            loading = { AttachmentLine(attachment, ink) },
            error = { AttachmentLine(attachment, ink) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .clickable { onOpen(attachment) }
        )
        else -> Box(modifier = Modifier.clickable { onOpen(attachment) }) { AttachmentLine(attachment, ink) }
    }
}

/** A glyph and a word for what came with the message. */
@Composable
private fun AttachmentLine(attachment: MmsAttachment, ink: Color) {
    val (icon, label) = when (attachment.kind) {
        AttachmentKind.IMAGE -> Icons.Default.Image to R.string.msg_attachment_picture
        AttachmentKind.VIDEO -> Icons.Default.PlayArrow to R.string.msg_attachment_video
        AttachmentKind.AUDIO -> Icons.Default.AudioFile to R.string.msg_attachment_audio
        AttachmentKind.CONTACT -> Icons.Default.ContactPage to R.string.msg_attachment_contact
        AttachmentKind.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile to R.string.msg_attachment
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(32.dp).background(ink.copy(alpha = 0.16f))
        ) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelLarge,
                color = ink
            )
            if (attachment.name.isNotBlank()) {
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Hands what came with a message to an app that opens that sort of thing.
 *
 * The message store lets only the messaging app read its parts, so the part is copied into the
 * launcher's cache first and offered from there. Returns false when nothing could take it.
 */
internal suspend fun openAttachment(context: Context, attachment: MmsAttachment): Boolean {
    val copy = withContext(Dispatchers.IO) {
        runCatching {
            val folder = File(context.cacheDir, "mms").apply { mkdirs() }
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mimeType.substringBefore(';').trim())
            val file = File(folder, "part_${attachment.partId}" + (extension?.let { ".$it" } ?: ""))
            if (!file.exists() || file.length() == 0L) {
                context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
            }
            file
        }.onFailure { ZuneLog.w("MessageAttachments", "the attachment could not be read", it) }.getOrNull()
    } ?: return false
    // A film in a message plays in the launcher's own player.
    val mime = attachment.mimeType.substringBefore(';').trim()
    if (com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer.handles(mime)) {
        return runCatching { com.serkantkn.zunelauncher.ui.screens.video.VideoPlayer.open(context, copy, mime); true }
            .onFailure { ZuneLog.w("MessageAttachments", "the video would not play", it) }
            .getOrDefault(false)
    }
    return runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", copy)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType.substringBefore(';').trim())
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(
            Intent.createChooser(view, context.getString(R.string.files_open_with))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    }.onFailure { ZuneLog.w("MessageAttachments", "nothing could open the attachment", it) }.getOrDefault(false)
}

/** Wide enough for the bubble at full width on a tablet, without decoding a camera's full photo. */
private const val PICTURE_PX = 900
