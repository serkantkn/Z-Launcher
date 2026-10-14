package com.serkantkn.zunelauncher.debug

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zunelauncher.data.model.AccentColor
import com.serkantkn.zunelauncher.data.model.Alarm
import com.serkantkn.zunelauncher.data.model.CalendarEvent
import com.serkantkn.zunelauncher.data.model.ContactModel
import com.serkantkn.zunelauncher.data.model.DailyPoint
import com.serkantkn.zunelauncher.data.model.HourlyPoint
import com.serkantkn.zunelauncher.data.model.HubType
import com.serkantkn.zunelauncher.data.model.NewsItem
import com.serkantkn.zunelauncher.data.model.Note
import com.serkantkn.zunelauncher.data.model.TemperatureUnit
import com.serkantkn.zunelauncher.data.model.ThemeMode
import com.serkantkn.zunelauncher.data.model.TileCornerStyle
import com.serkantkn.zunelauncher.data.model.WeatherCondition
import com.serkantkn.zunelauncher.data.model.WeatherNow
import com.serkantkn.zunelauncher.data.model.WeatherPlace
import com.serkantkn.zunelauncher.data.model.WeatherSnapshot
import com.serkantkn.zunelauncher.ui.components.LocalTileFaceOverride
import com.serkantkn.zunelauncher.ui.components.LocalTileStyle
import com.serkantkn.zunelauncher.ui.components.TileLine
import com.serkantkn.zunelauncher.ui.components.TileStyle
import com.serkantkn.zunelauncher.ui.components.W10MAlbumTile
import com.serkantkn.zunelauncher.ui.components.W10MAppTile
import com.serkantkn.zunelauncher.ui.components.W10MCalendarTile
import com.serkantkn.zunelauncher.ui.components.W10MClockTile
import com.serkantkn.zunelauncher.ui.components.W10MHubTile
import com.serkantkn.zunelauncher.ui.components.W10MMusicTile
import com.serkantkn.zunelauncher.ui.components.W10MNewsTile
import com.serkantkn.zunelauncher.ui.components.W10MNoteTile
import com.serkantkn.zunelauncher.ui.components.W10MPeopleTile
import com.serkantkn.zunelauncher.ui.components.W10MPersonTile
import com.serkantkn.zunelauncher.ui.components.W10MThreadTile
import com.serkantkn.zunelauncher.ui.components.W10MWeatherTile
import com.serkantkn.zunelauncher.ui.components.W10MWebTile
import com.serkantkn.zunelauncher.ui.theme.ZuneLauncherTheme
import com.serkantkn.zunelauncher.util.TileIconFace
import java.io.File

/**
 * Every Start tile at every size, filled with made-up data, for looking at tile designs on the
 * emulator. Debug builds only; nothing in the launcher opens it.
 *
 * adb shell am start -n com.serkantkn.zunelauncher/.debug.TileGalleryActivity \
 *     --es tile weather --es face back --ei cols 4 --ez light false
 *
 * `tile` is a hub's name in lower case (phone, messaging, email, notes, calculator, camera, files,
 * internet, settings, pictures, clock, calendar, people, weather, news, music) or one of app,
 * person, thread, web, note, album; `face` is front or back.
 */
class TileGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tile = intent.getStringExtra("tile") ?: "weather"
        val back = intent.getStringExtra("face") == "back"
        val columns = intent.getIntExtra("cols", 4)
        val light = intent.getBooleanExtra("light", false)
        val opacity = intent.getFloatExtra("opacity", 0.8f)
        val samples = Samples(this)
        setContent {
            ZuneLauncherTheme(themeMode = if (light) ThemeMode.LIGHT else ThemeMode.DARK, accentColor = AccentColor.COBALT) {
                CompositionLocalProvider(
                    LocalTileStyle provides TileStyle(opacity = opacity),
                    LocalTileFaceOverride provides back
                ) {
                    Gallery(tile, columns, samples)
                }
            }
        }
    }
}

@Composable
private fun Gallery(tile: String, columns: Int, samples: Samples) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF2B3A4A), Color(0xFF14181D), Color(0xFF3B2A35))))
            .padding(start = 12.dp, end = 12.dp, top = 36.dp)
    ) {
        BoxWithConstraints {
            val spacing = 8.dp
            val cell = (maxWidth - spacing * (columns - 1)) / columns
            fun widthOf(span: Int): Dp {
                val cells = when (span) { 1 -> 1; 2 -> 2; else -> 4 }
                return cell * cells + spacing * (cells - 1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
                Text(tile, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    Box(Modifier.width(widthOf(1))) { SampleTile(tile, 1, columns, spacing, samples) }
                    Box(Modifier.width(widthOf(2))) { SampleTile(tile, 2, columns, spacing, samples) }
                }
                Box(Modifier.width(widthOf(4))) { SampleTile(tile, 4, columns, spacing, samples) }
                Box(Modifier.width(widthOf(8))) { SampleTile(tile, 8, columns, spacing, samples) }
            }
        }
    }
}

@Composable
private fun SampleTile(tile: String, span: Int, columns: Int, spacing: Dp, s: Samples) {
    val corner = TileCornerStyle.ROUNDED
    val none = {}
    val hub = HubType.entries.firstOrNull { it.name.equals(tile, ignoreCase = true) }
    when (tile) {
        "clock" -> W10MClockTile(
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner, timeFormat = "HH:mm", alarms = s.alarms,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "calendar" -> W10MCalendarTile(
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner, events = s.events,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "people" -> W10MPeopleTile(
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner, contacts = s.contacts,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "weather" -> W10MWeatherTile(
            snapshot = s.weather, temperatureUnit = TemperatureUnit.CELSIUS,
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "news" -> W10MNewsTile(
            stories = s.news,
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "music" -> W10MMusicTile(
            span = span, gridColumns = columns, spacing = spacing, isEditing = false, isDragging = false,
            cornerStyle = corner, albumArt = s.cover, trackTitle = "Balkonda Çay", trackArtist = "Elif Kaya",
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none
        )
        "app" -> W10MAppTile(
            label = "WhatsApp", face = TileIconFace.Glyph(s.glyph.asImageBitmap()), span = span,
            isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            notificationCount = 3, notificationTitle = "Zeynep Çelik",
            notificationText = "Akşam yemeğine geliyor musun? Saat yedide masa ayırttım.",
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        "person" -> W10MPersonTile(
            label = "Zeynep Çelik", photoUri = s.faces.first().toString(), span = span,
            isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        "thread" -> W10MThreadTile(
            label = "Annem", span = span, isEditing = false, isDragging = false,
            unreadCount = 2, lastMessage = "Eve gelirken ekmek al, bir de süt lazım",
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        "web" -> W10MWebTile(
            label = "Wikipedia", span = span, isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        "note" -> W10MNoteTile(
            note = s.note, span = span, isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        "album" -> W10MAlbumTile(
            label = "Tatil 2026", photoUris = s.photos, span = span, isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            cornerStyle = corner, gridColumns = columns, spacing = spacing
        )
        else -> if (hub != null) W10MHubTile(
            hubType = hub, span = span, isEditing = false, isDragging = false,
            onClick = none, onLongClick = none, onRemoveClick = none, onResizeClick = none,
            badgeCount = s.badgeOf(hub), liveTitle = s.titleOf(hub), liveSubtitle = s.subtitleOf(hub), liveItems = s.itemsOf(hub),
            cornerStyle = corner, gridColumns = columns, spacing = spacing,
            photoUris = if (hub == HubType.PICTURES) s.photos else emptyList()
        )
    }
}

/** Made-up people, mail, weather and pictures. The pictures are drawn on the spot into the cache. */
private class Samples(activity: ComponentActivity) {
    private val dir = File(activity.cacheDir, "tile-gallery").apply { mkdirs() }
    private val now = System.currentTimeMillis()

    val photos: List<Uri> = listOf(0xFF3E7CB1, 0xFFB15B3E, 0xFF4E9A5B, 0xFF8E5BB1).mapIndexed { i, c ->
        picture("photo$i", c.toInt(), (c.toInt() xor 0x00FFFFFF) or 0xFF000000.toInt())
    }
    val faces: List<Uri> = listOf(0xFFC98B6B, 0xFF7B5E4A, 0xFFD9A982).mapIndexed { i, c ->
        picture("face$i", c.toInt(), 0xFF2F2A28.toInt())
    }
    val cover: Bitmap = gradient(0xFFE0A030.toInt(), 0xFF7A2E1C.toInt())
    val glyph: Bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
        Canvas(it).drawCircle(48f, 48f, 40f, paint)
    }

    val contacts: List<ContactModel> = listOf("Zeynep Çelik", "Ahmet Yılmaz", "Ayşe Demir", "Burak Arslan", "Can Öztürk",
        "Deniz Mavi", "Elif Kaya", "Fatma Şahin", "Gökhan Er", "Hande Ay", "İpek Su", "Kemal Tan", "Leyla Ün",
        "Murat Bal", "Nur Ak", "Okan Ece", "Pınar Gül", "Rıza Öz").mapIndexed { i, name ->
        ContactModel(id = "c$i", name = name, photoUri = faces.getOrNull(i % 6), isFavorite = i < 4,
            lastTimeContacted = now - i * 3_600_000L, hasPhoneNumber = true)
    }

    val alarms = listOf(
        Alarm(hour = 7, minute = 30, label = "İş"),
        Alarm(hour = 9, minute = 0, label = "Spor", daysOfWeek = setOf(1, 7))
    )

    val events = listOf(
        CalendarEvent(title = "Diş hekimi", location = "Kadıköy", startMillis = now + 2 * 3_600_000L),
        CalendarEvent(title = "Proje toplantısı", startMillis = now + 26 * 3_600_000L),
        CalendarEvent(title = "Annemin doğum günü", startMillis = now + 50 * 3_600_000L, isAllDay = true),
        CalendarEvent(title = "Sinema", location = "Cevahir", startMillis = now + 74 * 3_600_000L),
        CalendarEvent(title = "Kira ödemesi", startMillis = now + 98 * 3_600_000L)
    )

    val weather: WeatherSnapshot = run {
        val nowSeconds = now / 1000
        val hour = 3600L
        val conditions = listOf(WeatherCondition.PARTLY_CLOUDY, WeatherCondition.RAIN, WeatherCondition.CLEAR,
            WeatherCondition.OVERCAST, WeatherCondition.DRIZZLE, WeatherCondition.CLEAR, WeatherCondition.PARTLY_CLOUDY)
        WeatherSnapshot(
            place = WeatherPlace(id = "ist", name = "Esenyurt", region = "İstanbul", latitude = 41.0, longitude = 28.6),
            now = WeatherNow(temperature = 17.4, apparent = 16.0, condition = WeatherCondition.RAIN, isDay = true,
                humidity = 82, windSpeed = 18.0, windDirection = 220, pressure = 1012.0, cloudCover = 90,
                precipitation = 1.2, dewPoint = 14.0, visibilityMeters = 8000.0, uvIndex = 1.0),
            hourly = (0 until 24).map { i ->
                HourlyPoint(nowSeconds - nowSeconds % hour + i * hour, 17.0 - i % 6, conditions[i % conditions.size], 40, i < 8)
            },
            daily = (0 until 7).map { i ->
                DailyPoint(nowSeconds - nowSeconds % (24 * hour) + i * 24 * hour, 11.0 + i % 3, 19.0 - i % 4,
                    conditions[i], 30, 1.0, 20.0, 3.0, null, null)
            },
            airQuality = null,
            utcOffsetSeconds = 3 * 3600,
            fetchedAt = now
        )
    }

    val news = listOf(
        NewsItem("İstanbul'u sağanak ve fırtına vurdu, yollar göle döndü", "https://a", "NTV Haber", now - 3_600_000L,
            "Kentte sabah saatlerinde başlayan yağış birçok ilçede su baskınlarına yol açtı.", photos[0].toString()),
        NewsItem("UEFA Konferans Ligi'nde ilk hafta maçları yarın oynanacak", "https://b", "Hürriyet", now - 7_200_000L,
            "", photos[1].toString()),
        NewsItem("Pamuk fiyatları 48 ayın en hızlı düşüşünü kaydetti", "https://c", "Bloomberg HT", now - 9_000_000L,
            "", photos[2].toString())
    )

    val note = Note(title = "Alışveriş", content = "Süt\nYumurta\nEkmek\nZeytinyağı\nDomates\nPeynir", colorHex = "#2E7D32")

    fun itemsOf(hub: HubType): List<TileLine> = when (hub) {
        HubType.MESSAGING -> listOf(
            TileLine("Zeynep Çelik", "Akşam yemeğine geliyor musun? Saat yedide masa ayırttım.", "18:42"),
            TileLine("Annem", "Eve gelirken ekmek al", "17:15"),
            TileLine("Burak Arslan", "Maç kaçta başlıyor?", "dün")
        )
        HubType.EMAIL -> listOf(
            TileLine("Ahmet Yılmaz", "Sunum dosyası ekte, yarın sabaha kadar bakabilir misin?", "09:12"),
            TileLine("GitHub", "[Z-Launcher] Yeni bir yıldız", "08:40"),
            TileLine("Banka", "Ekim ayı hesap özetiniz hazır", "dün"),
            TileLine("Kargo", "Paketiniz yola çıktı", "dün"),
            TileLine("Belediye", "Su kesintisi duyurusu", "pzt")
        )
        HubType.PHONE -> listOf(TileLine("Ayşe Demir", null, "18:05"), TileLine("+90 545 454 54 54", null, "16:30"))
        HubType.NOTES -> listOf(
            TileLine("Alışveriş", "Süt, yumurta, ekmek, zeytinyağı"),
            TileLine("Tatil planı", "Kaş, Kalkan, Fethiye; otel rezervasyonu"),
            TileLine("Kitaplar", "Tutunamayanlar, Kürk Mantolu Madonna")
        )
        HubType.FILES -> listOf(TileLine("dahili depolama", "128 GB içinde 41,6 GB boş"))
        HubType.CALCULATOR -> listOf(TileLine("12 × 3 = 36"), TileLine("1450 ÷ 5 = 290"), TileLine("18% × 2400 = 432"))
        else -> emptyList()
    }

    fun badgeOf(hub: HubType) = when (hub) {
        HubType.PHONE -> 2; HubType.MESSAGING -> 3; HubType.EMAIL -> 5; HubType.NOTES -> 1; else -> 0
    }

    fun titleOf(hub: HubType) = when (hub) {
        HubType.PHONE -> "Ayşe Demir"; HubType.MESSAGING -> "Zeynep Çelik"; HubType.EMAIL -> "Ahmet Yılmaz"; else -> null
    }

    fun subtitleOf(hub: HubType) = when (hub) {
        HubType.PHONE -> "2 cevapsız arama"
        HubType.MESSAGING -> "Akşam yemeğine geliyor musun?"
        HubType.EMAIL -> "Sunum dosyası ekte"
        HubType.NOTES -> "Alışveriş"
        HubType.CALCULATOR -> "12 × 3 = 36"
        else -> null
    }

    private fun gradient(from: Int, to: Int): Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).also {
        val paint = Paint().apply { shader = LinearGradient(0f, 0f, 400f, 400f, from, to, Shader.TileMode.CLAMP) }
        Canvas(it).drawRect(0f, 0f, 400f, 400f, paint)
    }

    private fun picture(name: String, from: Int, accent: Int): Uri {
        val file = File(dir, "$name.png")
        if (!file.exists()) {
            val bitmap = gradient(from, accent)
            Canvas(bitmap).drawCircle(200f, 170f, 90f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; alpha = 160 })
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        return Uri.fromFile(file)
    }
}
