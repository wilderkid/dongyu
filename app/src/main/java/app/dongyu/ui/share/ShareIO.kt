package app.dongyu.ui.share

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import app.dongyu.R
import app.dongyu.share.MapBackground
import app.dongyu.share.TrackProjection
import app.dongyu.ui.components.osmStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.File
import kotlin.coroutines.resume

/** Gallery save, share sheet and map snapshots for share cards. */
object ShareIO {
    const val ALBUM = "动屿"

    fun authority(ctx: Context) = "${ctx.packageName}.files"

    /** Saves a PNG into Pictures/动屿. Needs WRITE_EXTERNAL_STORAGE on API ≤ 28 (requested by the caller). */
    suspend fun saveToGallery(ctx: Context, img: ImageBitmap, name: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = img.asAndroidBitmap()
            val file = "$name.png"
            if (Build.VERSION.SDK_INT >= 29) {
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val r = ctx.contentResolver
                val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return@runCatching false
                try {
                    r.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0)
                    r.update(uri, cv, null, null)
                } catch (e: Exception) {
                    r.delete(uri, null, null); throw e
                }
                true
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
                val f = File(dir, file)
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, file)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    @Suppress("DEPRECATION") put(MediaStore.Images.Media.DATA, f.absolutePath)
                }
                ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                true
            }
        }.getOrDefault(false)
    }

    /** Writes the card to cache/share and returns a content:// URI from our FileProvider. */
    suspend fun cacheForShare(ctx: Context, img: ImageBitmap, name: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "$name.png")
        f.outputStream().use { img.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(ctx, authority(ctx), f)
    }

    fun shareIntent(ctx: Context, uri: Uri, mime: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, ctx.getString(R.string.share_chooser))
    }

    /**
     * Renders an OSM map behind the track. The region is the projection's full-canvas bounds so the
     * drawn track lines up with the tiles. Returns null on failure or after [timeoutMs] (no network,
     * no GL) and the caller falls back to the plain template.
     */
    suspend fun mapSnapshot(ctx: Context, proj: TrackProjection, w: Int, h: Int, timeoutMs: Long = 15_000): MapBackground? =
        withContext(Dispatchers.Main) {
            runCatching {
                MapLibre.getInstance(ctx)
                val (s, west, n, e) = proj.canvasBounds().toList()
                val bounds = LatLngBounds.Builder().include(LatLng(n, e)).include(LatLng(s, west)).build()
                val opts = MapSnapshotter.Options(w / 2, h / 2)
                    .withRegion(bounds)
                    .withPixelRatio(2f)
                    .withLogo(false)
                    .withStyleBuilder(Style.Builder().fromJson(osmStyle(false)))
                val snap = MapSnapshotter(ctx, opts)
                try {
                    withTimeoutOrNull(timeoutMs) {
                        suspendCancellableCoroutine { cont ->
                            snap.start({ shot -> if (cont.isActive) cont.resume(MapBackground(shot.bitmap.asImageBitmap())) }) { _ ->
                                if (cont.isActive) cont.resume(null)
                            }
                            cont.invokeOnCancellation { snap.cancel() }
                        }
                    }
                } finally {
                    snap.cancel()
                }
            }.getOrNull()
        }
}
