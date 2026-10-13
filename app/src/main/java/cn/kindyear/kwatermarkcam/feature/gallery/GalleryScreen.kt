package cn.kindyear.kwatermarkcam.feature.gallery

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.core.storage.*
import cn.kindyear.kwatermarkcam.domain.model.PhotoRecord
import cn.kindyear.kwatermarkcam.feature.camera.*
import kotlinx.coroutines.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.min

@Composable
fun GalleryScreen(vm: CameraViewModel, back: () -> Unit, openLatest: Boolean = false) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    val photos = (listOfNotNull(capture.lastPhoto) + ui.photos).distinctBy { it.id }.sortedByDescending { it.createdAt }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var opened by rememberSaveable { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    fun leave() {
        if (capture.busy) return
        if (editing != null) { if (dirty) confirmExit = true else editing = null }
        else if (selected != null) selected = null else back()
    }
    BackHandler(editing != null || selected != null || capture.busy) { leave() }
    LaunchedEffect(photos, openLatest) {
        if (openLatest && !opened && photos.isNotEmpty()) { opened = true; selected = photos.first().id }
    }
    LaunchedEffect(capture.lastPhoto?.id) {
        if (editing != null && capture.lastPhoto != null && capture.lastPhoto?.id != editing) {
            selected = capture.lastPhoto?.id; editing = null; dirty = false
        }
    }
    val edited = photos.firstOrNull { it.id == editing }
    when {
        edited != null -> PhotoEditor(edited, vm.photoRepository, capture.busy, capture.pending != null, ::leave,
            { dirty = it }, { vm.savePhotoEdit(edited, it) }, vm::retrySave)
        selected != null && photos.isNotEmpty() -> key(selected) {
            PhotoViewer(photos, selected, vm.photoRepository, ::leave, { editing = it.id; dirty = false }, capture.busy || capture.pending != null)
        }
        else -> PhotoLibrary(photos, vm.photoRepository, back) { selected = it.id }
    }
    if (confirmExit) AlertDialog(onDismissRequest = { confirmExit = false }, title = { Text(stringResource(R.string.edit_leave_title)) },
        text = { Text(stringResource(R.string.edit_leave_body)) }, confirmButton = { TextButton(onClick = { confirmExit = false; editing = null; dirty = false }) { Text(stringResource(R.string.discard)) } },
        dismissButton = { TextButton(onClick = { confirmExit = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun photoDescription(photo: PhotoRecord): String = stringResource(R.string.gallery_photo_description,
    Instant.ofEpochMilli(photo.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), photo.width, photo.height)

@Composable
private fun PhotoLibrary(photos: List<PhotoRecord>, repository: PhotoRepository, back: () -> Unit, select: (PhotoRecord) -> Unit) {
    PageScaffold(stringResource(R.string.gallery_library), back) { padding ->
        if (photos.isEmpty()) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.gallery_empty), Modifier.padding(vertical = 16.dp), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.gallery_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyVerticalStaggeredGrid(StaggeredGridCells.Adaptive(150.dp), Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalItemSpacing = 8.dp) {
            item(span = StaggeredGridItemSpan.FullLine) {
                Column(Modifier.padding(bottom = 16.dp)) {
                    Text(stringResource(R.string.gallery_count, photos.size), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.gallery_library_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val groups = photos.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            groups.forEach { (day, group) ->
                item(key = "day:$day", span = StaggeredGridItemSpan.FullLine) { Text(day.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), Modifier.padding(top = 12.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium) }
                items(group, key = { it.id }) { photo ->
                    val ratio = (photo.width.toFloat() / photo.height.coerceAtLeast(1)).coerceIn(.55f, 1.8f)
                    Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(MaterialTheme.shapes.medium).clickable { select(photo) }) {
                        PhotoThumbnail(photo.uri, repository, Modifier.fillMaxSize(), photoDescription(photo))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoViewer(photos: List<PhotoRecord>, initial: String?, repository: PhotoRepository, back: () -> Unit,
    edit: (PhotoRecord) -> Unit, saving: Boolean) {
    val context = LocalContext.current
    val galleryLabel = stringResource(R.string.gallery)
    val shareLabel = stringResource(R.string.share_photo)
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = photos.indexOfFirst { it.id == initial }.coerceAtLeast(0), pageCount = { photos.size })
    val photo = photos[pager.currentPage.coerceIn(photos.indices)]
    var zoomed by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { zoomed = false }
    fun open(share: Boolean) {
        scope.launch {
            if (!repository.isReadable(photo.uri)) { error = true; return@launch }
            try {
                val uri = Uri.parse(photo.uri)
                val intent = if (share) Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri)
                    .apply { clipData = ClipData.newUri(context.contentResolver, galleryLabel, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(if (share) Intent.createChooser(intent, shareLabel) else intent)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (exception: Exception) { Log.w("Gallery", "System photo action unavailable", exception); error = true }
        }
    }
    PageScaffold(stringResource(R.string.gallery_position, pager.currentPage + 1, photos.size), back, actions = {
        IconButton(onClick = { open(true) }, enabled = !saving) { Icon(Icons.Default.Share, stringResource(R.string.share_photo)) }
        IconButton(onClick = { open(false) }, enabled = !saving) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_gallery)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(pager, Modifier.fillMaxWidth().weight(1f), userScrollEnabled = !zoomed, key = { photos[it].id }) { page ->
                ZoomablePhoto(photos[page], repository, Modifier.fillMaxSize()) { if (page == pager.currentPage) zoomed = it > 1.01f }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Instant.ofEpochMilli(photo.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.photo_dimensions, photo.width, photo.height), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                FilledTonalButton(onClick = { edit(photo) }, enabled = !saving) { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit_photo)) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { item ->
                    PhotoThumbnail(item.uri, repository, Modifier.size(54.dp).clip(MaterialTheme.shapes.small)
                        .border(if (item.id == photo.id) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
                        .clickable { scope.launch { pager.scrollToPage(photos.indexOf(item)) } }, photoDescription(item))
                }
            }
        }
    }
    if (error) AlertDialog(onDismissRequest = { error = false }, text = { Text(stringResource(R.string.photo_unavailable_retry)) },
        confirmButton = { TextButton(onClick = { error = false }) { Text(stringResource(R.string.confirm)) } })
}

@Composable
private fun ZoomablePhoto(photo: PhotoRecord, repository: PhotoRepository, modifier: Modifier, scaleChanged: (Float) -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.uri, retry) {
        loading = true; value = null; value = repository.preview(photo.uri, 2400); loading = false
    }
    var scale by rememberSaveable(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    SideEffect { scaleChanged(scale) }
    fun transform(zoom: Float, pan: Offset, centroid: Offset) {
        if (!zoom.isFinite() || !pan.x.isFinite() || !pan.y.isFinite() || !centroid.x.isFinite() || !centroid.y.isFinite()) return
        val next = (scale * zoom).coerceIn(1f, 6f)
        val anchor = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        val moved = (offset - anchor) * (next / scale) + anchor + pan
        val image = bitmap
        val fit = if (image == null) 1f else min(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
        val maxX = ((image?.width ?: viewport.width) * fit * next - viewport.width).coerceAtLeast(0f) / 2f
        val maxY = ((image?.height ?: viewport.height) * fit * next - viewport.height).coerceAtLeast(0f) / 2f
        offset = if (next == 1f) Offset.Zero else Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
        scale = next; scaleChanged(next)
    }
    Box(modifier.clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerLow).onSizeChanged { viewport = it }, contentAlignment = Alignment.Center) {
        when {
            loading -> CircularProgressIndicator()
            bitmap == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.photo_missing)); TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
            }
            else -> Image(bitmap!!.asImageBitmap(), stringResource(R.string.photo_zoom_hint), Modifier.fillMaxSize()
                .pointerInput(photo.id, viewport) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.pressed } && (event.changes.count { it.pressed } > 1 || scale > 1.01f)) {
                                transform(event.calculateZoom(), event.calculatePan(), event.calculateCentroid())
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }.pointerInput(photo.id) { detectTapGestures(onDoubleTap = { transform(if (scale > 1.01f) 1f / scale else 2.5f, Offset.Zero, it) }) }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
        }
        if (bitmap != null) Surface(Modifier.align(Alignment.BottomStart).padding(16.dp), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) { Text(stringResource(R.string.photo_zoom_level, scale), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium) }
        if (bitmap != null) Row(Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
            FilledTonalIconButton(onClick = { transform(1f / scale, Offset.Zero, Offset(viewport.width / 2f, viewport.height / 2f)) }) { Icon(Icons.Default.FitScreen, stringResource(R.string.zoom_reset)) }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = { transform(1.5f, Offset.Zero, Offset(viewport.width / 2f, viewport.height / 2f)) }) { Icon(Icons.Default.ZoomIn, stringResource(R.string.zoom_in)) }
        }
    }
}

@Composable
private fun PhotoEditor(photo: PhotoRecord, repository: PhotoRepository, busy: Boolean, pending: Boolean, back: () -> Unit,
    dirty: (Boolean) -> Unit, save: (PhotoEdits) -> Unit, retry: () -> Unit) {
    var turns by rememberSaveable(photo.id) { mutableIntStateOf(0) }
    var mirror by rememberSaveable(photo.id) { mutableStateOf(false) }
    var crop by rememberSaveable(photo.id) { mutableStateOf<String?>(null) }
    var brightness by rememberSaveable(photo.id) { mutableFloatStateOf(0f) }
    var contrast by rememberSaveable(photo.id) { mutableFloatStateOf(1f) }
    var mono by rememberSaveable(photo.id) { mutableStateOf(false) }
    val canEdit = !busy && !pending
    val brightnessLabel = stringResource(R.string.edit_brightness)
    val contrastLabel = stringResource(R.string.edit_contrast)
    val edits = PhotoEdits(turns, mirror, when (crop) { "square" -> 1f; "portrait" -> .75f; "wide" -> 16f/9f; else -> null }, brightness, contrast, mono)
    SideEffect { dirty(edits != PhotoEdits()) }
    var loading by remember { mutableStateOf(true) }
    var previewError by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val source by produceState<android.graphics.Bitmap?>(null, photo.uri, refresh) {
        loading = true
        value = repository.preview(photo.uri, 1000)
        loading = false
    }
    val preview by produceState<android.graphics.Bitmap?>(null, source, edits, refresh) {
        previewError = false
        val image = source ?: return@produceState
        delay(80)
        try { value = withContext(Dispatchers.Default) { PhotoEditEngine.render(image, edits) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Log.w("Gallery", "Edit preview failed", error); previewError = true }
        catch (error: OutOfMemoryError) { Log.w("Gallery", "Edit preview memory exhausted", error); previewError = true }
    }
    PageScaffold(stringResource(R.string.edit_photo), back, actions = {
        TextButton(onClick = { save(edits) }, enabled = !busy && !pending && source != null && !previewError && edits != PhotoEdits()) { Text(stringResource(R.string.save_copy)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
                if (!loading && (source == null || previewError)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (previewError) R.string.edit_failed else R.string.photo_missing))
                    TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.retry)) }
                } else if (preview != null) Image(preview!!.asImageBitmap(), stringResource(R.string.edit_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator()
                if (busy) CircularProgressIndicator()
            }
            Column(Modifier.fillMaxWidth().heightIn(max = 310.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text(stringResource(R.string.edit_copy_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { turns = (turns + 1) % 4 }, enabled = canEdit) { Icon(Icons.Default.RotateRight, stringResource(R.string.rotate_photo)) }
                    IconButton(onClick = { mirror = !mirror }, enabled = canEdit) { Icon(Icons.Default.Flip, stringResource(R.string.flip_photo)) }
                    IconToggleButton(checked = mono, onCheckedChange = { mono = it }, enabled = canEdit) { Icon(Icons.Default.FilterBAndW, stringResource(R.string.monochrome_photo)) }
                    TextButton(onClick = { turns = 0; mirror = false; crop = null; brightness = 0f; contrast = 1f; mono = false }, enabled = canEdit) { Text(stringResource(R.string.edit_reset)) }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to R.string.crop_original, "square" to R.string.crop_square, "portrait" to R.string.crop_portrait, "wide" to R.string.crop_wide).forEach { (value, label) ->
                        item { FilterChip(crop == value, { crop = value }, label = { Text(stringResource(label)) }, enabled = canEdit) }
                    }
                }
                Text(stringResource(R.string.edit_brightness), style = MaterialTheme.typography.labelLarge)
                Slider(brightness, { brightness = it }, modifier = Modifier.semantics { contentDescription = brightnessLabel }, valueRange = -.5f.. .5f, enabled = canEdit)
                Text(stringResource(R.string.edit_contrast), style = MaterialTheme.typography.labelLarge)
                Slider(contrast, { contrast = it }, modifier = Modifier.semantics { contentDescription = contrastLabel }, valueRange = .5f..1.5f, enabled = canEdit)
                if (pending) Button(onClick = retry, enabled = !busy) { Text(stringResource(R.string.retry_save)) }
            }
        }
    }
}
