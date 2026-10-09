package cn.kindyear.kwatermarkcam.feature.about

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.kindyear.kwatermarkcam.BuildConfig
import cn.kindyear.kwatermarkcam.R
import cn.kindyear.kwatermarkcam.core.designsystem.PageScaffold
import kotlinx.coroutines.launch

@Composable
fun AboutScreen(back: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val uriHandler = LocalUriHandler.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val linkFailed = stringResource(R.string.about_open_failed)
    var licenses by remember { mutableStateOf(false) }
    fun open(url: String) {
        try { uriHandler.openUri(url) } catch (e: Exception) {
            Log.w("AboutScreen", "Unable to open project link", e)
            scope.launch { snackbar.showSnackbar(linkFailed) }
        }
    }
    PageScaffold(stringResource(R.string.about), back) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.ic_app), stringResource(R.string.app_logo),
                        Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)))
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 18.dp))
                    Text(stringResource(R.string.about_version_detail, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                    Text(stringResource(R.string.about_tagline), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text(stringResource(R.string.about_intro), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutValue(Icons.Default.PhoneAndroid, stringResource(R.string.about_local), Modifier.weight(1f))
                    AboutValue(Icons.Default.WifiOff, stringResource(R.string.about_offline), Modifier.weight(1f))
                    AboutValue(Icons.Default.PersonOutline, stringResource(R.string.about_no_account), Modifier.weight(1f))
                }
                Surface(color = colors.secondaryContainer, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.Shield, null, tint = colors.onSecondaryContainer)
                        Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleMedium, color = colors.onSecondaryContainer)
                        Text(stringResource(R.string.about_privacy_body), style = MaterialTheme.typography.bodyMedium, color = colors.onSecondaryContainer)
                    }
                }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    AboutLink(Icons.Default.Code, stringResource(R.string.about_source), stringResource(R.string.about_source_summary)) { open("https://github.com/kindyear/KWatermarkCam") }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    AboutLink(Icons.Default.SystemUpdate, stringResource(R.string.about_releases), stringResource(R.string.about_releases_summary)) { open("https://github.com/kindyear/KWatermarkCam/releases") }
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    AboutLink(Icons.Default.Description, stringResource(R.string.licenses), stringResource(R.string.about_details)) { licenses = true }
                }
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.about_made_by), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.about_android), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    }
    if (licenses) AppLicensesDialog { licenses = false }

}

@Composable
private fun AboutValue(icon: ImageVector, text: String, modifier: Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun AboutLink(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) {
    Surface(onClick = click) {
        ListItem(headlineContent = { Text(title) }, supportingContent = { Text(subtitle) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
    }
}
