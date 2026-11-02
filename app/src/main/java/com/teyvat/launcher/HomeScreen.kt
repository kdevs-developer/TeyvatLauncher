package com.teyvat.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Everything is laid out on the same 1920x1080 grid as the web design.
// LocalU = how many dp one design pixel is on this screen.
val LocalU = compositionLocalOf { 1.dp }
val Int.d: Dp @Composable get() = LocalU.current * this
val Int.s: TextUnit
    @Composable get() {
        val n = this
        return with(LocalDensity.current) { (LocalU.current * n).toSp() }
    }

private val Ink = Color(0xFFF3EBDD)
private val Muted = Color(0xFFA9B0C8)
private val PillBorder = Color(0xFF3A4570)
private val TileBg = Color(0xC71B2342)
private val TileBorder = Color(0xFF2C365A)
private val FocusBg = Color(0xE6263056)
private val Night = Color(0xFF12172B)

@Composable
fun HomeRoot(refreshKey: Int, enterTick: Int, homeTick: Int) {
    val ctx = LocalContext.current
    val apps by produceState(emptyList<AppEntry>(), refreshKey) {
        value = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    var nationIdx by remember { mutableIntStateOf(Prefs.nationOverride(ctx)) }
    HomeScreen(
        apps = apps,
        enterTick = enterTick,
        homeTick = homeTick,
        nationOverride = nationIdx,
        onLaunch = { Actions.launchApp(ctx, it.packageName) },
        onAction = { Actions.run(ctx, it) },
        onOpenInput = { Actions.openInput(ctx, it) },
        onNationChange = { idx ->
            nationIdx = idx
            Prefs.setNationOverride(ctx, idx)
        },
    )
}

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    enterTick: Int,
    homeTick: Int,
    nationOverride: Int,
    onLaunch: (AppEntry) -> Unit,
    onAction: (Act) -> Unit,
    onOpenInput: (InputEntry) -> Unit,
    onNationChange: (Int) -> Unit,
) {
    val now by produceState(Calendar.getInstance()) {
        while (true) {
            delay(15_000)
            value = Calendar.getInstance()
        }
    }
    val autoNation = remember(now.get(Calendar.YEAR), now.get(Calendar.DAY_OF_YEAR)) { Nations.pick(now) }
    val nation = Nations.all.getOrNull(nationOverride) ?: autoNation
    val phase = Phase.of(now)
    val time = remember(now.timeInMillis / 60_000) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now.time) }
    val date = remember(now.get(Calendar.DAY_OF_YEAR)) { SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now.time) }
    val accent by animateColorAsState(nation.color, tween(700), label = "accent")

    var inputsOpen by remember { mutableStateOf(false) }
    var backTick by remember { mutableIntStateOf(0) }

    // Back (or pressing Home again) always brings you back to the start of the row.
    BackHandler { backTick++ }
    LaunchedEffect(homeTick) { inputsOpen = false }

    // Whole-screen entrance, replayed every time you come back to the launcher.
    val intro = remember { Animatable(0f) }
    LaunchedEffect(enterTick) {
        intro.snapTo(0f)
        intro.animateTo(1f, tween(720, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Night)) {
        CompositionLocalProvider(LocalU provides (maxWidth / 1920f)) {
            NationScene(nation, phase)
            Particles(accent)
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val v = intro.value
                        alpha = 0.55f + 0.45f * v
                        val s = 1.045f - 0.045f * v
                        scaleX = s
                        scaleY = s
                    }
                    .padding(vertical = 56.d),
                verticalArrangement = Arrangement.spacedBy(20.d),
            ) {
                Header(
                    modifier = Modifier.padding(horizontal = 80.d),
                    nation = nation,
                    accent = accent,
                    onNation = { onNationChange((Nations.all.indexOf(nation) + 1) % Nations.all.size) },
                    onInputs = { inputsOpen = true },
                    onAction = onAction,
                )
                EmblemBand(nation, accent, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 80.d))
                AppsRow(apps, accent, backTick + homeTick, onLaunch)
            }
            BigClock(
                time = time, date = date,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 80.d, bottom = 40.d).graphicsLayer {
                    alpha = intro.value
                    translationX = -40f * (1f - intro.value)
                },
            )
            if (inputsOpen) {
                InputsPanel(
                    accent = accent,
                    onPick = { inputsOpen = false; onOpenInput(it) },
                    onMore = { inputsOpen = false; onAction(Act.SETTINGS) },
                    onClose = { inputsOpen = false },
                )
            }
        }
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun Header(
    modifier: Modifier,
    nation: Nation,
    accent: Color,
    onNation: () -> Unit,
    onInputs: () -> Unit,
    onAction: (Act) -> Unit,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.d)) {
            AnimatedStar(44.d, Config.ACCENT)
            Text(
                "TEYVAT", color = Config.ACCENT, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 34.s, letterSpacing = 3.s,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.d)) {
            NationButton(nation, accent, onNation)
            RoundButton(Glyphs.INPUTS, accent, onInputs)
            RoundButton(Glyphs.WIFI, accent) { onAction(Act.NETWORK) }
            RoundButton(Glyphs.SETTINGS, accent) { onAction(Act.SETTINGS) }
        }
    }
}

/** Time and date, middle-left, a little bigger than before. */
@Composable
private fun BigClock(time: String, date: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.d)) {
        AnimatedContent(
            targetState = time,
            transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
            label = "clock",
        ) { t ->
            Text(t, color = Ink, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 84.s)
        }
        Text(date, color = Ink.copy(alpha = 0.85f), fontSize = 28.s, letterSpacing = 1.s)
    }
}

/** Slowly turning, breathing logo star. */
@Composable
private fun AnimatedStar(size: Dp, color: Color) {
    val inf = rememberInfiniteTransition(label = "logo")
    val rot by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)), label = "rot",
    )
    val pulse by inf.animateFloat(
        initialValue = 0.88f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse",
    )
    Box(Modifier.graphicsLayer { rotationZ = rot; scaleX = pulse; scaleY = pulse }) { StarMark(size, color) }
}

/** One click = next nation. The element symbol spins and the whole scene swaps with a circular reveal. */
@Composable
private fun NationButton(nation: Nation, accent: Color, onClick: () -> Unit) {
    var spins by remember { mutableIntStateOf(0) }
    val angle by animateFloatAsState(
        targetValue = spins * 360f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "spin",
    )
    TvTile(
        modifier = Modifier.height(64.d),
        corner = 32.d,
        accent = accent,
        idleBorder = accent.copy(alpha = 0.55f),
        idleBg = Color.Transparent,
        onClick = {
            spins++
            onClick()
        },
    ) {
        Row(Modifier.padding(horizontal = 24.d), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.d)) {
            Box(Modifier.graphicsLayer { rotationZ = angle }) { SvgIcon(nation.icon, 30.d, accent) }
            AnimatedContent(
                targetState = nation.name,
                transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                label = "nationName",
            ) { n ->
                Text(n, color = Ink, fontFamily = FontFamily.Serif, fontSize = 26.s, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoundButton(icon: List<String>, accent: Color, onClick: () -> Unit) {
    TvTile(
        modifier = Modifier.size(64.d),
        corner = 32.d,
        accent = accent,
        idleBorder = PillBorder,
        idleBg = Color.Transparent,
        onClick = onClick,
    ) { SvgIcon(icon, 30.d, Ink) }
}

// ------------------------------------------------------------------ emblem

/** Big star logo on the right: rings turn, sparks orbit, it breathes, and it pops when the nation changes. */
@Composable
private fun EmblemBand(nation: Nation, col: Color, modifier: Modifier) {
    val inf = rememberInfiniteTransition(label = "emblem")
    val spin by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)), label = "spin",
    )
    val breathe by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe",
    )
    val pop = remember { Animatable(1f) }
    val flash = remember { Animatable(0f) }
    LaunchedEffect(nation.name) {
        pop.snapTo(0.55f)
        flash.snapTo(1f)
        launch { flash.animateTo(0f, tween(1000)) }
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }

    Canvas(modifier) {
        scale(size.width / 1760f, Offset.Zero) {
            // large logo: 460x460 at right:-60 top:-30, floating gently
            translate(1360f, -30f) {
                scale(4.6f, Offset.Zero) {
                    val mid = Offset(50f, 50f)
                    // soft pulsing glow behind everything (brighter right after a nation change)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(col.copy(alpha = (0.26f + 0.12f * breathe + 0.30f * flash.value).coerceIn(0f, 1f)), Color.Transparent),
                            center = mid, radius = 64f,
                        ),
                        radius = 64f, center = mid,
                    )
                    // shock-wave ring when the nation changes
                    if (flash.value > 0.01f) {
                        drawCircle(col, 18f + 62f * (1f - flash.value), mid, alpha = 0.85f * flash.value, style = Stroke(1.0f))
                    }
                    drawCircle(col, 48f, mid, alpha = 0.30f + 0.12f * breathe, style = Stroke(0.4f))
                    rotate(spin, mid) {
                        drawCircle(col, 38f, mid, alpha = 0.5f, style = Stroke(0.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 2f))))
                        drawCircle(col, 1.4f, Offset(mid.x + 38f, mid.y), alpha = 0.95f)
                        drawCircle(Ink, 0.9f, Offset(mid.x - 38f, mid.y), alpha = 0.85f)
                    }
                    rotate(-spin * 1.7f, mid) {
                        drawCircle(col, 28f, mid, alpha = 0.35f, style = Stroke(0.4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.5f, 3f))))
                        drawCircle(col, 1.1f, Offset(mid.x, mid.y - 28f), alpha = 0.9f)
                        drawCircle(Ink, 0.7f, Offset(mid.x, mid.y + 28f), alpha = 0.7f)
                    }
                    scale(pop.value * (1f + 0.025f * breathe), mid) {
                        drawPath(P[Glyphs.STAR_BIG], col, alpha = 0.9f)
                        drawCircle(Night, 15f, mid)
                        drawCircle(col, 15f, mid, alpha = 0.6f, style = Stroke(0.5f))
                        // elemental symbol, centred inside the logo
                        translate(50f - 11.4f, 50f - 11.4f) {
                            scale(22.8f / 24f, Offset.Zero) {
                                nation.icon.forEach {
                                    drawPath(P[it], col, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                }
                            }
                        }
                    }
                }
            }
            // two little sparkles that twinkle
            translate(1172f, 70f) {
                scale(0.28f * (0.75f + 0.5f * breathe), Offset.Zero) {
                    drawPath(P[Glyphs.STAR], Config.ACCENT, alpha = 0.35f + 0.45f * breathe)
                }
            }
            translate(1620f, 336f) {
                scale(0.20f * (1.25f - 0.5f * breathe), Offset.Zero) {
                    drawPath(P[Glyphs.STAR], Ink, alpha = 0.3f + 0.5f * (1f - breathe))
                }
            }
        }
    }
}

// ------------------------------------------------------------------ apps row

@Composable
private fun AppsRow(apps: List<AppEntry>, accent: Color, homeKey: Int, onLaunch: (AppEntry) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // First load: focus the first app (same as before).
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) {
            delay(300)
            runCatching { firstFocus.requestFocus() }
        }
    }
    // Back / Home pressed: jump straight to the first app.
    LaunchedEffect(homeKey) {
        if (homeKey > 0 && apps.isNotEmpty()) {
            listState.scrollToItem(0)
            delay(100)
            runCatching { firstFocus.requestFocus() }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(22.d)) {
        Text(
            "YOUR APPS", color = Muted, fontFamily = FontFamily.Serif, fontSize = 26.s, letterSpacing = 3.s,
            modifier = Modifier.padding(horizontal = 80.d),
        )
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.d),
            contentPadding = PaddingValues(horizontal = 86.d, vertical = 12.d),
        ) {
            itemsIndexed(apps, key = { _, a -> a.packageName }) { i, app ->
                TvTile(
                    modifier = Modifier.size(288.d, 162.d).then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    corner = 22.d,
                    accent = accent,
                    onClick = { onLaunch(app) },
                ) {
                    // Just the banner, filling the whole tile - no name on it.
                    Image(
                        bitmap = app.icon,
                        contentDescription = app.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ inputs panel

/** Pick HDMI 1 / HDMI 2 / AV ... A dialog window, so the remote's focus stays inside it and Back closes it. */
@Composable
private fun InputsPanel(accent: Color, onPick: (InputEntry) -> Unit, onMore: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val inputs by produceState<List<InputEntry>?>(null) {
        value = withContext(Dispatchers.Default) { loadInputs(ctx) }
    }
    val firstFocus = remember { FocusRequester() }
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(inputs) {
        if (inputs != null) {
            delay(200)
            runCatching { firstFocus.requestFocus() }
        }
    }
    val shape = RoundedCornerShape(36.d)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC0B0F1E))
                .pointerInput(Unit) { detectTapGestures { onClose() } },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        val a = appear.value
                        alpha = a.coerceIn(0f, 1f)
                        scaleX = 0.88f + 0.12f * a
                        scaleY = 0.88f + 0.12f * a
                        translationY = (1f - a) * 60f
                    }
                    .clip(shape)
                    .background(Color(0xF2182040))
                    .border(2.d, accent.copy(alpha = 0.6f), shape)
                    .pointerInput(Unit) { detectTapGestures { } } // taps on the card itself don't close it
                    .padding(horizontal = 56.d, vertical = 44.d),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(30.d),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.d)) {
                    SvgIcon(Glyphs.INPUTS, 40.d, accent)
                    Text("INPUTS", color = Ink, fontFamily = FontFamily.Serif, fontSize = 34.s, letterSpacing = 4.s)
                }
                val list = inputs
                when {
                    list == null -> Text("Looking for inputs...", color = Muted, fontSize = 24.s)
                    list.isEmpty() -> {
                        Text("No inputs were found on this device.", color = Muted, fontSize = 24.s)
                        PanelPill("TV settings", accent, Modifier.focusRequester(firstFocus)) { onMore() }
                    }
                    else -> Row(horizontalArrangement = Arrangement.spacedBy(22.d)) {
                        list.forEachIndexed { i, input ->
                            PanelPill(
                                input.label, accent,
                                if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                            ) { onPick(input) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelPill(label: String, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    TvTile(
        modifier = modifier.heightIn(min = 84.d).widthIn(min = 220.d),
        corner = 42.d,
        accent = accent,
        idleBorder = PillBorder,
        onClick = onClick,
    ) {
        Text(label, color = Ink, fontSize = 28.s, maxLines = 1, modifier = Modifier.padding(horizontal = 34.d, vertical = 22.d))
    }
}

// ------------------------------------------------------------------ shared tile

/** Focusable, D-pad friendly surface: grows a little and lights up in the nation colour when selected. */
@Composable
private fun TvTile(
    modifier: Modifier,
    corner: Dp,
    accent: Color,
    onClick: () -> Unit,
    idleBorder: Color = TileBorder,
    idleBg: Color = TileBg,
    content: @Composable BoxScope.() -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.06f else 1f, label = "grow")
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .graphicsLayer { scaleX = grow; scaleY = grow }
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) FocusBg else idleBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
        // border drawn on top so banners never cover it
        Box(
            Modifier
                .matchParentSize()
                .border(if (focused) 4.d else 2.d, if (focused) accent else idleBorder, shape),
        )
    }
}