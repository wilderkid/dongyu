package app.dongyu.ui.components

import android.os.Bundle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dongyu.ui.charts.drawTrack
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** OpenStreetMap raster tiles; no API key. Coordinates are WGS-84 so no GCJ-02 shift is needed. */
private fun osmStyle(dark: Boolean): String = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [
    {"id": "bg", "type": "background", "paint": {"background-color": "${if (dark) "#1b1f26" else "#eef1f4"}"}},
    {"id": "osm", "type": "raster", "source": "osm",
     "paint": {${if (dark) "\"raster-brightness-max\": 0.55, \"raster-saturation\": -0.5, \"raster-contrast\": 0.1" else "\"raster-saturation\": -0.15"}}}
  ]
}
""".trimIndent()

/**
 * Track on an OSM map via MapLibre. In previews / screenshot tests (no native GL) a canvas
 * rendering of the same path is used instead.
 */
@Composable
fun TrackMap(
    path: List<Pair<Double, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    interactive: Boolean = true,
) {
    if (LocalInspectionMode.current || path.size < 2) {
        TrackCanvasFallback(path, color, modifier, dark)
        return
    }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }
    DisposableEffect(lifecycle, mapView) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
        }
    }
    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { mv ->
            mv.getMapAsync { map ->
                map.uiSettings.isAttributionEnabled = true
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isRotateGesturesEnabled = false
                map.uiSettings.setAllGesturesEnabled(interactive)
                map.setStyle(Style.Builder().fromJson(osmStyle(dark))) { style ->
                    val pts = path.map { Point.fromLngLat(it.second, it.first) }
                    style.addSource(GeoJsonSource("track", LineString.fromLngLats(pts)))
                    style.addLayer(
                        LineLayer("track-casing", "track").withProperties(
                            PropertyFactory.lineColor(android.graphics.Color.WHITE),
                            PropertyFactory.lineWidth(8f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addLayer(
                        LineLayer("track-line", "track").withProperties(
                            PropertyFactory.lineColor(color.toArgb()),
                            PropertyFactory.lineWidth(5f),
                            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                        )
                    )
                    style.addSource(GeoJsonSource("start", pts.first()))
                    style.addSource(GeoJsonSource("end", pts.last()))
                    style.addLayer(CircleLayer("start-dot", "start").withProperties(
                        PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(0xFF19B36B.toInt()),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(3f),
                    ))
                    style.addLayer(CircleLayer("end-dot", "end").withProperties(
                        PropertyFactory.circleRadius(7f), PropertyFactory.circleColor(0xFFE5484D.toInt()),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(3f),
                    ))
                    val bounds = LatLngBounds.Builder().includes(path.map { LatLng(it.first, it.second) }).build()
                    map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                }
            }
        }
        Text(
            "© OpenStreetMap",
            fontSize = 9.sp,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun TrackCanvasFallback(path: List<Pair<Double, Double>>, color: Color, modifier: Modifier = Modifier, dark: Boolean = false) {
    val bg = if (dark) listOf(Color(0xFF1C222B), Color(0xFF151A21)) else listOf(Color(0xFFE8F1EE), Color(0xFFDCE8F2))
    val gridC = if (dark) Color(0x16FFFFFF) else Color(0x14000000)
    Canvas(modifier.background(Brush.linearGradient(bg))) {
        val step = 28.dp.toPx()
        var x = 0f
        while (x < size.width) { drawLine(gridC, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y < size.height) { drawLine(gridC, Offset(0f, y), Offset(size.width, y), 1f); y += step }
        // a couple of soft "parks/water" blobs for a map-like feel
        drawCircle(Color(0x2219B36B), size.minDimension * 0.28f, Offset(size.width * 0.18f, size.height * 0.3f))
        drawCircle(Color(0x221E9BF0), size.minDimension * 0.35f, Offset(size.width * 0.85f, size.height * 0.8f))
        drawTrack(path, color, 5.dp.toPx(), 28.dp.toPx())
    }
}

@Composable
fun MapPlaceholderText(text: String) = Text(text, style = MaterialTheme.typography.labelSmall)
