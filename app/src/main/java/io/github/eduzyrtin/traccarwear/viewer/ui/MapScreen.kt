package io.github.eduzyrtin.traccarwear.viewer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Device
import io.github.eduzyrtin.traccarwear.viewer.api.Position
import io.github.eduzyrtin.traccarwear.viewer.map.MapStyle
import io.github.eduzyrtin.traccarwear.viewer.map.TileLoader
import io.github.eduzyrtin.traccarwear.viewer.map.WebMercator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val REFRESH_INTERVAL_MILLIS = 30_000L
private const val SELECTED_ZOOM = 15
private const val MAX_FIT_ZOOM = 16
private const val ROTARY_STEP_PX = 80f
private const val CONTROLS_HIDE_MILLIS = 4_000L

private data class Marker(val device: Device, val position: Position)

/** Pantalla principal: mapa con todos los dispositivos, como la app de Traccar. */
@Composable
fun MapScreen(viewModel: ViewerViewModel, onOpenList: () -> Unit, onOpenDevice: (Long) -> Unit) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedDeviceId.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val mapChoice by viewModel.mapChoice.collectAsStateWithLifecycle()
    val autoHideControls by viewModel.autoHideMapControls.collectAsStateWithLifecycle()

    // Posiciones al día mientras el mapa está a la vista
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    val current = snapshot
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val style = remember(current, mapChoice) { current.mapStyle(mapChoice) }
    val markers = remember(current) {
        current.devices.mapNotNull { device -> current.positionOf(device.id)?.let { Marker(device, it) } }
    }

    val density = LocalDensity.current
    val tilePx = 256.0 * density.density
    val textMeasurer = rememberTextMeasurer()
    val focusRequester = remember { FocusRequester() }

    // -1: todavía sin encuadrar
    var zoom by rememberSaveable { mutableIntStateOf(-1) }
    var centerLatitude by rememberSaveable { mutableDoubleStateOf(40.0) }
    var centerLongitude by rememberSaveable { mutableDoubleStateOf(-3.7) }
    // Se incrementa al llegar un mosaico, para repintar
    var loadedTiles by remember { mutableIntStateOf(0) }
    // Giro acumulado de la corona o el bisel
    val rotary = remember { floatArrayOf(0f) }
    // true: el mapa sigue al dispositivo seleccionado
    var following by remember { mutableStateOf(true) }
    // Cada toque o giro de la corona lo incrementa, para reiniciar la cuenta atrás de los botones
    var touchCount by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    // true si el toque que se está haciendo es el que ha vuelto a mostrar los botones
    val revealing = remember { booleanArrayOf(false) }

    fun zoomBy(delta: Int) {
        zoom = (zoom.coerceAtLeast(MapStyle.MIN_ZOOM) + delta).coerceIn(MapStyle.MIN_ZOOM, style.maxZoom)
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            // Ve todos los toques sin consumirlos: reinicia la cuenta atrás de los botones
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) {
                            revealing[0] = !controlsVisible
                            touchCount++
                        }
                    }
                }
            },
    ) {
        val width = constraints.maxWidth.toDouble()
        val height = constraints.maxHeight.toDouble()

        // Al abrir, encuadra todos los dispositivos
        LaunchedEffect(markers.isNotEmpty()) {
            if (zoom < 0 && markers.isNotEmpty()) {
                val fit = fitMarkers(markers, tilePx, width, height, style.maxZoom)
                centerLatitude = fit.first
                centerLongitude = fit.second
                zoom = fit.third
            }
        }
        // Al seleccionar un dispositivo (en el mapa, en la lista o desde el tile), centrarlo y seguirlo
        val selectedMarker = markers.find { it.device.id == selectedId }
        LaunchedEffect(selectedId) {
            following = true
            if (selectedMarker != null && zoom < SELECTED_ZOOM) zoom = SELECTED_ZOOM
        }
        // Mientras se siga, el mapa se mueve con el dispositivo en cada actualización de su posición.
        // Arrastrar el mapa deja de seguirlo; tocar el dispositivo, o elegirlo de nuevo, lo sigue otra vez.
        LaunchedEffect(selectedId, selectedMarker?.position?.latitude, selectedMarker?.position?.longitude, following) {
            val marker = selectedMarker ?: return@LaunchedEffect
            if (following) {
                centerLatitude = marker.position.latitude
                centerLongitude = marker.position.longitude
            }
        }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Botones y ≡: se ocultan tras unos segundos sin tocar la pantalla (si el usuario lo quiere así)
        LaunchedEffect(touchCount, autoHideControls) {
            controlsVisible = true
            if (autoHideControls) {
                delay(CONTROLS_HIDE_MILLIS)
                controlsVisible = false
            }
        }
        val showControls = controlsVisible || !autoHideControls

        val effectiveZoom = zoom.coerceAtLeast(MapStyle.MIN_ZOOM)
        val world = WebMercator.worldSize(effectiveZoom, tilePx)
        val centerX = WebMercator.x(centerLongitude, world)
        val centerY = WebMercator.y(centerLatitude, world)
        val tiles = WebMercator.visibleTiles(centerX, centerY, effectiveZoom, tilePx, width, height)
        val tileUrls = tiles.map { style.tileUrl(effectiveZoom, it.x, it.y) }

        LaunchedEffect(tileUrls) {
            tileUrls.filter { TileLoader.cached(it) == null }.forEach { url ->
                launch { if (TileLoader.load(url) != null) loadedTiles++ }
            }
        }

        fun screenOf(position: Position) = Offset(
            (WebMercator.x(position.longitude, world) - centerX + width / 2).toFloat(),
            (WebMercator.y(position.latitude, world) - centerY + height / 2).toFloat(),
        )

        Canvas(
            Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    rotary[0] += event.verticalScrollPixels
                    touchCount++
                    if (abs(rotary[0]) >= ROTARY_STEP_PX) {
                        zoomBy(if (rotary[0] > 0) 1 else -1)
                        rotary[0] = 0f
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .pointerInput(Unit) {
                    var pinch = 1f
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        if (gestureZoom == 1f && (pan.x != 0f || pan.y != 0f)) following = false
                        val currentWorld = WebMercator.worldSize(zoom.coerceAtLeast(MapStyle.MIN_ZOOM), tilePx)
                        val x = WebMercator.x(centerLongitude, currentWorld) - pan.x
                        val y = (WebMercator.y(centerLatitude, currentWorld) - pan.y).coerceIn(0.0, currentWorld)
                        centerLongitude = (WebMercator.longitude(x, currentWorld) + 540.0) % 360.0 - 180.0
                        centerLatitude = WebMercator.latitude(y, currentWorld)
                        pinch *= gestureZoom
                        if (pinch > 1.6f) {
                            zoomBy(1)
                            pinch = 1f
                        } else if (pinch < 0.62f) {
                            zoomBy(-1)
                            pinch = 1f
                        }
                    }
                }
                .pointerInput(markers) {
                    detectTapGestures(
                        onDoubleTap = { zoomBy(1) },
                        onTap = { tap ->
                            val hitRadius = 28.dp.toPx()
                            val hit = markers
                                .map { it to screenOf(it.position) }
                                .filter { (_, point) -> hypot(point.x - tap.x, point.y - tap.y) <= hitRadius }
                                .minByOrNull { (_, point) -> hypot(point.x - tap.x, point.y - tap.y) }
                            when {
                                hit != null -> {
                                    viewModel.select(hit.first.device.id)
                                    following = true
                                }
                                // El primer toque con los botones ocultos solo los muestra
                                !revealing[0] -> viewModel.select(null)
                            }
                        },
                    )
                },
        ) {
            loadedTiles // lectura para repintar cuando llega un mosaico
            drawRect(Color(0xFFE8E6E1))
            val size = tilePx.roundToInt()
            tiles.zip(tileUrls).forEach { (tile, url) ->
                TileLoader.cached(url)?.let { bitmap ->
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(tile.left.roundToInt(), tile.top.roundToInt()),
                        dstSize = IntSize(size, size),
                    )
                }
            }
            // El seleccionado, el último para que quede encima
            markers.sortedBy { it.device.id == selectedId }.forEach { marker ->
                drawMarker(marker, screenOf(marker.position), marker.device.id == selectedId, textMeasurer)
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            FilledTonalIconButton(
                onClick = { zoomBy(-1) },
                modifier = Modifier.padding(start = 4.dp).size(40.dp),
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        }
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            FilledTonalIconButton(
                onClick = { zoomBy(1) },
                modifier = Modifier.padding(end = 4.dp).size(40.dp),
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val selected = markers.find { it.device.id == selectedId }
            when {
                selected != null -> Text(
                    text = "${selected.device.name} · ${stringResource(statusLabel(selected.device.status))}\n" +
                        "${timeAgo(parseTime(selected.device.lastUpdate))} · " +
                        stringResource(R.string.speed, knotsToKmh(selected.position.speed)),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(max = 170.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .clickable { onOpenDevice(selected.device.id) }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                markers.isEmpty() -> Text(
                    stringResource(R.string.no_positions),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1)
            }
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut()) {
                FilledTonalIconButton(onClick = onOpenList, modifier = Modifier.padding(top = 2.dp).size(36.dp)) {
                    Text("≡", style = MaterialTheme.typography.titleMedium)
                }
            }
            // La atribución de los datos del mapa es obligatoria (licencia ODbL de OpenStreetMap y términos
            // de cada proveedor): siempre visible, pequeña y sin tapar nada
            if (style.attribution.isNotEmpty()) {
                Text(
                    style.attribution,
                    fontSize = 7.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }
}

private fun DrawScope.drawMarker(marker: Marker, point: Offset, selected: Boolean, textMeasurer: TextMeasurer) {
    val radius = (if (selected) 9 else 7).dp.toPx()
    val border = 2.5.dp.toPx()
    drawCircle(Color(0x55000000), radius + border, point + Offset(0f, 1.dp.toPx()))
    drawCircle(Color.White, radius + border, point)
    drawCircle(Color(statusColor(marker.device.status)), radius, point)

    val label = textMeasurer.measure(
        marker.device.name,
        TextStyle(color = Color.White, fontSize = if (selected) 12.sp else 10.sp),
        maxLines = 1,
    )
    val padding = 4.dp.toPx()
    val topLeft = Offset(point.x - label.size.width / 2f, point.y - radius - border - label.size.height - padding * 1.5f)
    drawRoundRect(
        color = Color(if (selected) 0xE6000000 else 0xB3000000),
        topLeft = topLeft - Offset(padding, padding / 2),
        size = Size(label.size.width + padding * 2, label.size.height + padding),
        cornerRadius = CornerRadius(padding * 2),
    )
    drawText(label, topLeft = topLeft)
}

/** Centro y zoom que encuadran todos los dispositivos con margen. */
private fun fitMarkers(markers: List<Marker>, tilePx: Double, width: Double, height: Double, maxZoom: Int): Triple<Double, Double, Int> {
    if (markers.size == 1) {
        return Triple(markers[0].position.latitude, markers[0].position.longitude, SELECTED_ZOOM)
    }
    val minLat = markers.minOf { it.position.latitude }
    val maxLat = markers.maxOf { it.position.latitude }
    val minLon = markers.minOf { it.position.longitude }
    val maxLon = markers.maxOf { it.position.longitude }
    var zoom = minOf(MAX_FIT_ZOOM, maxZoom)
    while (zoom > MapStyle.MIN_ZOOM) {
        val world = WebMercator.worldSize(zoom, tilePx)
        val spanX = WebMercator.x(maxLon, world) - WebMercator.x(minLon, world)
        val spanY = WebMercator.y(minLat, world) - WebMercator.y(maxLat, world)
        // La pantalla es redonda: los marcadores, dentro del 60 % central
        if (spanX <= width * 0.6 && spanY <= height * 0.6) break
        zoom--
    }
    val world = WebMercator.worldSize(zoom, tilePx)
    val centerY = (WebMercator.y(minLat, world) + WebMercator.y(maxLat, world)) / 2
    return Triple(WebMercator.latitude(centerY, world), (minLon + maxLon) / 2, zoom)
}
