package com.serkantkn.zeta.ui.tv

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serkantkn.zeta.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

/**
 * Windows Vista's Media Center, the second of a TV's two designs.
 *
 * Where Windows 7's Media Center laid its strips across the screen, Vista's stood them one above
 * another round a fixed point: the word "start" faint at the left, the chosen strip's name in
 * white, a pane of glass for the thing the remote is on, and everything else sliding past it.
 * Its libraries had their name huge and faint in the top corner, their ways in (folders, tags,
 * date taken) as a row of words with the chosen one between chevrons, and its settings were
 * menus of plain lines with the same glass behind the chosen one. It spoke Segoe UI - Selawik
 * here, Microsoft's open twin of it - and it ticked and chimed as the remote moved.
 */
object Vista {
    /** The ground's darkest corner and its lit middle. */
    val Top = Color(0xFF041637)
    val Middle = Color(0xFF052C62)
    val Bottom = Color(0xFF031B4F)
    val Light = Color(0xFF1A5BA8)
    val Glow = Color(0xFF3E8FD6)

    /** A strip not chosen, a line under a title. */
    val Category = Color(0xFF8DB4DF)

    /** Words that can be chosen but are not: a library's other ways in, a link. */
    val Link = Color(0xFF5FB3EC)

    /** The huge faint names: "start", a library's title, the settings' path. */
    val Faint = Color(0xFF2B66A6)
    val StartWord = Color(0xFF173E72)

    /** A dialog's glass. */
    val PanelTop = Color(0xFF4A82BE)
    val PanelBottom = Color(0xFF2C609E)
}

/** Which of the two Media Centers a TV wears, and whether Vista's sounds are on. */
object TvDesign {
    private var prefs: android.content.SharedPreferences? = null
    private val _vista = MutableStateFlow(false)
    val vista: StateFlow<Boolean> = _vista.asStateFlow()
    private val _sounds = MutableStateFlow(true)
    val sounds: StateFlow<Boolean> = _sounds.asStateFlow()

    /** The Xbox's ground ([XboxTheme] by name), and the picture for its "picture" theme. */
    private val _theme = MutableStateFlow(XboxTheme.GREY.name)
    val theme: StateFlow<String> = _theme.asStateFlow()
    private val _themePhoto = MutableStateFlow<String?>(null)
    val themePhoto: StateFlow<String?> = _themePhoto.asStateFlow()

    /** What the screen does when the remote is left alone ([TvSaver] by name), and after how long. */
    private val _saver = MutableStateFlow(TvSaver.DIM.name)
    val saver: StateFlow<String> = _saver.asStateFlow()
    private val _saverMinutes = MutableStateFlow(DEFAULT_SAVER_MINUTES)
    val saverMinutes: StateFlow<Int> = _saverMinutes.asStateFlow()

    /** The music's own screen, come up by itself while something plays and the remote rests. */
    private val _visualizer = MutableStateFlow(true)
    val visualizer: StateFlow<Boolean> = _visualizer.asStateFlow()

    /** True once attached on a television; everything here is a TV's alone. */
    @Volatile
    var onTv = false
        private set

    /** Shown once each time the launcher starts, as Media Center's logo was. */
    @Volatile
    var splashShown = false

    /**
     * Read at once, not through the settings' store: the design decides what the very first
     * frame is - Vista's logo or Windows 7's Start - and a store read a moment later would show
     * the wrong one first.
     */
    fun attach(context: Context) {
        if (prefs != null) return
        // A phone keeps none of this: its keyboard's arrows must not tick.
        if (!com.serkantkn.zeta.ui.theme.isTelevision(context)) return
        onTv = true
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        _vista.value = p.getBoolean(KEY_VISTA, false)
        _sounds.value = p.getBoolean(KEY_SOUNDS, true)
        _theme.value = p.getString(KEY_THEME, null) ?: XboxTheme.GREY.name
        _themePhoto.value = p.getString(KEY_THEME_PHOTO, null)
        _saver.value = p.getString(KEY_SAVER, null) ?: TvSaver.DIM.name
        _saverMinutes.value = p.getInt(KEY_SAVER_MINUTES, DEFAULT_SAVER_MINUTES)
        _visualizer.value = p.getBoolean(KEY_VISUALIZER, true)
    }

    fun setTheme(theme: XboxTheme, photo: String? = _themePhoto.value) {
        _theme.value = theme.name
        _themePhoto.value = photo
        prefs?.edit()?.putString(KEY_THEME, theme.name)?.putString(KEY_THEME_PHOTO, photo)?.apply()
    }

    fun setSaver(saver: TvSaver) {
        _saver.value = saver.name
        prefs?.edit()?.putString(KEY_SAVER, saver.name)?.apply()
    }

    fun setSaverMinutes(minutes: Int) {
        _saverMinutes.value = minutes
        prefs?.edit()?.putInt(KEY_SAVER_MINUTES, minutes)?.apply()
    }

    fun setVisualizer(on: Boolean) {
        _visualizer.value = on
        prefs?.edit()?.putBoolean(KEY_VISUALIZER, on)?.apply()
    }

    fun setVista(on: Boolean) {
        _vista.value = on
        prefs?.edit()?.putBoolean(KEY_VISTA, on)?.apply()
    }

    fun setSounds(on: Boolean) {
        _sounds.value = on
        prefs?.edit()?.putBoolean(KEY_SOUNDS, on)?.apply()
    }

    private const val PREFS = "tv_design"
    private const val KEY_VISTA = "vista"
    private const val KEY_SOUNDS = "sounds"
    private const val KEY_THEME = "xbox_theme"
    private const val KEY_THEME_PHOTO = "xbox_theme_photo"
    private const val KEY_SAVER = "saver"
    private const val KEY_SAVER_MINUTES = "saver_minutes"
    private const val KEY_VISUALIZER = "visualizer"
    private const val DEFAULT_SAVER_MINUTES = 10
}

/** True on a TV wearing Vista's Media Center. */
val LocalVista = androidx.compose.runtime.compositionLocalOf { false }

/** Segoe UI as Vista set it, through Selawik. */
val SelawikFamily = FontFamily(
    Font(R.font.selawik_light, FontWeight.Light),
    Font(R.font.selawik_light, FontWeight.ExtraLight),
    Font(R.font.selawik_light, FontWeight.Thin),
    Font(R.font.selawik_semilight, FontWeight.Normal),
    Font(R.font.selawik_regular, FontWeight.Medium),
    Font(R.font.selawik_semibold, FontWeight.SemiBold),
    Font(R.font.selawik_bold, FontWeight.Bold),
    Font(R.font.selawik_bold, FontWeight.ExtraBold),
    Font(R.font.selawik_bold, FontWeight.Black)
)

/** The launcher's type in Selawik, its letters not pulled together as Metro's were. */
fun vistaTypography(base: androidx.compose.material3.Typography): androidx.compose.material3.Typography {
    fun TextStyle.v() = copy(fontFamily = SelawikFamily, letterSpacing = 0.sp)
    return base.copy(
        displayLarge = base.displayLarge.v(), displayMedium = base.displayMedium.v(), displaySmall = base.displaySmall.v(),
        headlineLarge = base.headlineLarge.v(), headlineMedium = base.headlineMedium.v(), headlineSmall = base.headlineSmall.v(),
        titleLarge = base.titleLarge.v(), titleMedium = base.titleMedium.v(), titleSmall = base.titleSmall.v(),
        bodyLarge = base.bodyLarge.v(), bodyMedium = base.bodyMedium.v(), bodySmall = base.bodySmall.v(),
        labelLarge = base.labelLarge.v(), labelMedium = base.labelMedium.v(), labelSmall = base.labelSmall.v()
    )
}

// ── The ground ─────────────────────────────────────────────────────────────────────────────

/**
 * Vista Media Center's ground: navy darkest in the top left, lit in the middle, the light pooling
 * low on the right, and soft rays sweeping up from the bottom left across it. The rays are drawn
 * once into a picture - a slow television cannot repaint blurred light every frame.
 */
@Composable
fun VistaBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    // Still, not drifting: a TV repainting the whole screen every frame for a slow sway of light
    // had nothing left for the remote - Start ran at a frame every 109 ms on the Mi TV.
    Box(modifier = modifier.fillMaxSize().background(Vista.Top)) {
        // The ground and its rays are one picture, drawn once: four full-screen gradients drawn
        // again each frame cost the Mi TV 77 ms of every one while the remote moved.
        val ground = vistaRays()
        if (ground == null) Canvas(modifier = Modifier.fillMaxSize()) { drawVistaGround() }
        val rays = ground
        if (rays != null) {
            Image(
                bitmap = rays,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        content()
    }
}

private fun DrawScope.drawVistaGround() {
    drawRect(Brush.verticalGradient(listOf(Vista.Top, Vista.Middle, Vista.Bottom)))
    // The lit middle, a little right of centre.
    drawRect(
        Brush.radialGradient(
            listOf(Vista.Light.copy(alpha = 0.55f), Vista.Light.copy(alpha = 0.18f), Color.Transparent),
            center = Offset(size.width * 0.62f, size.height * 0.42f),
            radius = size.width * 0.55f
        )
    )
    // Shadow in the top-left corner.
    drawRect(
        Brush.radialGradient(
            listOf(Color(0xFF020B22).copy(alpha = 0.7f), Color.Transparent),
            center = Offset(0f, 0f),
            radius = size.width * 0.45f
        )
    )
    // The pool of light low on the right.
    drawRect(
        Brush.radialGradient(
            listOf(Vista.Glow.copy(alpha = 0.38f), Color.Transparent),
            center = Offset(size.width * 0.87f, size.height * 0.80f),
            radius = size.width * 0.22f
        )
    )
}

/** The ground and its rays, drawn once at half size, the rays blurred, kept for the life of the process. */
@Composable
private fun vistaRays(): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = VistaRaysCache.bitmap) {
        if (value == null) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { VistaRaysCache.draw() }
        }
    }
    return bitmap
}

private object VistaRaysCache {
    @Volatile
    var bitmap: ImageBitmap? = null

    fun draw(): ImageBitmap {
        bitmap?.let { return it }
        val w = 960
        val h = 540
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        // The ground first, as drawVistaGround draws it.
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            androidx.compose.ui.unit.Density(1f),
            androidx.compose.ui.unit.LayoutDirection.Ltr,
            androidx.compose.ui.graphics.Canvas(canvas),
            Size(w.toFloat(), h.toFloat())
        ) { drawVistaGround() }
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        // Each ray: a long curved band from the bottom left up across to the right.
        class Ray(val x0: Float, val y0: Float, val cx: Float, val cy: Float, val x1: Float, val y1: Float, val width: Float, val alpha: Int, val blur: Float)
        val rays = listOf(
            Ray(-0.05f, 1.05f, 0.45f, 0.55f, 1.10f, 0.62f, 0.10f, 52, 26f),
            Ray(0.05f, 1.10f, 0.55f, 0.70f, 1.10f, 0.78f, 0.06f, 46, 18f),
            Ray(0.20f, 1.10f, 0.60f, 0.62f, 1.05f, 0.48f, 0.035f, 60, 10f),
            Ray(0.30f, 1.08f, 0.70f, 0.80f, 1.08f, 0.90f, 0.05f, 40, 22f),
            Ray(-0.10f, 0.95f, 0.35f, 0.35f, 0.95f, 0.10f, 0.12f, 18, 40f),
            Ray(0.42f, 1.05f, 0.80f, 0.70f, 1.10f, 0.70f, 0.02f, 70, 6f)
        )
        for (ray in rays) {
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = ray.width * h
            paint.strokeCap = android.graphics.Paint.Cap.ROUND
            paint.maskFilter = android.graphics.BlurMaskFilter(ray.blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
            paint.shader = android.graphics.LinearGradient(
                ray.x0 * w, ray.y0 * h, ray.x1 * w, ray.y1 * h,
                intArrayOf(
                    android.graphics.Color.argb(0, 120, 190, 255),
                    android.graphics.Color.argb(ray.alpha, 120, 190, 255),
                    android.graphics.Color.argb(ray.alpha / 3, 160, 210, 255),
                    android.graphics.Color.argb(0, 160, 210, 255)
                ),
                floatArrayOf(0f, 0.45f, 0.8f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            val path = android.graphics.Path().apply {
                moveTo(ray.x0 * w, ray.y0 * h)
                quadTo(ray.cx * w, ray.cy * h, ray.x1 * w, ray.y1 * h)
            }
            canvas.drawPath(path, paint)
        }
        return out.asImageBitmap().also { bitmap = it }
    }
}

// ── Glass ──────────────────────────────────────────────────────────────────────────────────

/**
 * Vista's pane of glass: brighter at the top and in its top-left corner, nearly clear at the
 * foot, a thin light edge and a darker line outside it. What the remote is on sits behind one.
 */
fun DrawScope.drawVistaGlass(topLeft: Offset, size: Size, corner: Dp = 3.dp, strength: Float = 1f, fill: Float = 1f) {
    val radius = CornerRadius(corner.toPx())
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.30f * strength),
        topLeft = topLeft - Offset(1.5f, 1.5f),
        size = Size(size.width + 3f, size.height + 3f),
        cornerRadius = radius,
        style = Stroke(width = 1.5f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.34f * strength * fill),
            0.45f to Color.White.copy(alpha = 0.13f * strength * fill),
            1f to Color.White.copy(alpha = 0.05f * strength * fill),
            startY = topLeft.y,
            endY = topLeft.y + size.height
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius
    )
    // The sheen in the top-left corner.
    drawRoundRect(
        brush = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.26f * strength * fill),
            1f to Color.Transparent,
            start = topLeft,
            end = topLeft + Offset(size.width * 0.55f, size.height * 0.9f)
        ),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.72f * strength),
        topLeft = topLeft,
        size = size,
        cornerRadius = radius,
        style = Stroke(width = 1.2.dp.toPx())
    )
}

/** A rectangle of [drawVistaGlass], for whatever is drawn on it. */
fun Modifier.vistaGlass(strength: Float = 1f, corner: Dp = 3.dp): Modifier =
    this.drawBehind { drawVistaGlass(Offset.Zero, size, corner, strength) }

// ── The orb, the clock ────────────────────────────────────────────────────────────────────

/** Zeta's mark: the Z of shards the app's icon carries, on nothing. */
@Composable
fun ZetaMark(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(R.drawable.zeta_mark),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/** Media Center's green orb: a ball of glass, lit from the top left. */
@Composable
fun VistaOrb(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(Color(0xFF0E3A0A), radius = r, center = c)
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFC6F57A), Color(0xFF6CC22E), Color(0xFF2E7D16), Color(0xFF184F0C)),
                center = c + Offset(-r * 0.3f, -r * 0.35f),
                radius = r * 1.5f
            ),
            radius = r * 0.92f,
            center = c
        )
        // The window's light in the top half of the glass.
        drawOval(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.05f)),
                startY = c.y - r * 0.85f,
                endY = c.y + r * 0.05f
            ),
            topLeft = Offset(c.x - r * 0.62f, c.y - r * 0.85f),
            size = Size(r * 1.24f, r * 0.85f)
        )
        drawCircle(Color.White.copy(alpha = 0.35f), radius = r * 0.92f, center = c, style = Stroke(width = r * 0.08f))
    }
}

/** The time in the top right with the orb beside it, as Media Center kept them. */
@Composable
fun VistaClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time by produceState(initialValue = vistaTime(context)) {
        while (true) {
            value = vistaTime(context)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = time,
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = SelawikFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
            color = Color.White
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        VistaOrb(size = 24.dp)
    }
}

private fun vistaTime(context: Context): String = android.text.format.DateFormat.getTimeFormat(context).format(Date())

// ── Sounds ─────────────────────────────────────────────────────────────────────────────────

/**
 * The TV's ticks and chimes, made here (Microsoft's own are theirs): a move, a choice, back, a page
 * and the logo - Media Center's glassy set, or the Xbox's shorter, woodier one.
 */
object VistaSounds {
    enum class Kind { MOVE, SELECT, BACK, PAGE, START }

    private var pool: SoundPool? = null
    private val ids = mutableMapOf<Kind, Int>()
    private val xboxIds = mutableMapOf<Kind, Int>()

    private fun ready(context: Context): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        val app = context.applicationContext
        ids[Kind.MOVE] = p.load(app, R.raw.vista_move, 1)
        ids[Kind.SELECT] = p.load(app, R.raw.vista_select, 1)
        ids[Kind.BACK] = p.load(app, R.raw.vista_back, 1)
        ids[Kind.PAGE] = p.load(app, R.raw.vista_page, 1)
        ids[Kind.START] = p.load(app, R.raw.vista_start, 1)
        xboxIds[Kind.MOVE] = p.load(app, R.raw.xbox_move, 1)
        xboxIds[Kind.SELECT] = p.load(app, R.raw.xbox_select, 1)
        xboxIds[Kind.BACK] = p.load(app, R.raw.xbox_back, 1)
        xboxIds[Kind.PAGE] = p.load(app, R.raw.xbox_page, 1)
        xboxIds[Kind.START] = p.load(app, R.raw.xbox_start, 1)
        pool = p
        return p
    }

    /** Loads them early, so the first press is heard. */
    fun prepare(context: Context) {
        ready(context)
    }

    fun play(context: Context, kind: Kind) {
        if (!TvDesign.onTv || !TvDesign.sounds.value) return
        val set = if (TvDesign.vista.value) ids else xboxIds
        val id = set[kind] ?: run { ready(context); set[kind] } ?: return
        val volume = if (kind == Kind.START) 0.8f else 0.6f
        ready(context).play(id, volume, volume, 1, 0, 1f)
    }
}


// ── Settings' controls ─────────────────────────────────────────────────────────────────────

/** Vista's radio button: a dark ring, a white dot in it when chosen. */
@Composable
fun VistaRadio(selected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(20.dp)) {
        val r = size.minDimension / 2
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFF0A2A55), Color(0xFF15457F))),
            radius = r
        )
        drawCircle(Color.White.copy(alpha = 0.55f), radius = r - 0.6.dp.toPx(), style = Stroke(width = 1.2.dp.toPx()))
        if (selected) drawCircle(Color.White, radius = r * 0.38f)
    }
}

/** Vista's check box: a dark square with a light edge, a white tick in it when on. */
@Composable
fun VistaCheck(checked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(26.dp)) {
        drawRoundRect(
            Brush.verticalGradient(listOf(Color(0xFF0A2A55), Color(0xFF15457F))),
            cornerRadius = CornerRadius(2.dp.toPx())
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.6f),
            topLeft = Offset(0.6.dp.toPx(), 0.6.dp.toPx()),
            size = Size(size.width - 1.2.dp.toPx(), size.height - 1.2.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = 1.2.dp.toPx())
        )
        if (checked) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.25f, size.height * 0.52f)
                lineTo(size.width * 0.43f, size.height * 0.70f)
                lineTo(size.width * 0.76f, size.height * 0.32f)
            }
            drawPath(path, Color.White, style = Stroke(width = 2.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

