package cn.kindyear.kwatermarkcam.feature.gallery

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import cn.kindyear.kwatermarkcam.feature.camera.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GalleryScreen(vm: CameraViewModel, back: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val capture by vm.capture.collectAsStateWithLifecycle()
    val photos = (listOfNotNull(capture.lastPhoto) + ui.photos).distinctBy { it.id }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var openingFailed by remember { mutableStateOf(false) }
    val photo = photos.firstOrNull { it.id == selected } ?: photos.firstOrNull()
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo?.uri, retry) {
        value = null; loading = true
        value = photo?.let { vm.photoRepository.preview(it.uri) }
        loading = false
    }
    PageScaffold(stringResource(R.string.gallery), back, actions = {
        IconButton(onClick = {
            photo?.let {
                try { context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(it.uri), "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                catch (e: ActivityNotFoundException) { Log.w("Gallery", "No image viewer", e); openingFailed = true }
                catch (e: SecurityException) { Log.w("Gallery", "Photo no longer accessible", e); openingFailed = true }
            }
        }, enabled = photo != null) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_gallery)) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
                when {
                    photo == null -> Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.gallery_empty), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
                        Text(stringResource(R.string.gallery_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    loading -> CircularProgressIndicator()
                    bitmap != null -> Image(requireNotNull(bitmap).asImageBitmap(), stringResource(R.string.gallery), Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.photo_missing))
                        TextButton(onClick = { retry++ }) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            photo?.let { p ->
                val date = Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                Text(stringResource(R.string.photo_info, p.width, p.height, date), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            }
            LazyRow(contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(photos, key = { it.id }) { p -> PhotoThumbnail(p.uri, vm.photoRepository, Modifier.size(72.dp).clip(MaterialTheme.shapes.medium)
                    .border(if (photo?.id == p.id) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium).clickable { selected = p.id }) }
            }
        }
    }
    if (openingFailed) AlertDialog(onDismissRequest = { openingFailed = false }, text = { Text(stringResource(R.string.photo_unavailable_retry)) }, confirmButton = { TextButton(onClick = { openingFailed = false }) { Text(stringResource(R.string.confirm)) } })
}
