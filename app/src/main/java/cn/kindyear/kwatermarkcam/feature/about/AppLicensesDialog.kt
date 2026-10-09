package cn.kindyear.kwatermarkcam.feature.about

import android.util.Log
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cn.kindyear.kwatermarkcam.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** Local license text remains readable without a browser or network. */
@Composable
fun AppLicensesDialog(dismiss: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val scroll = rememberScrollState()
    val body by produceState("", full, resources) {
        value = if (full) withContext(Dispatchers.IO) {
            try {
                val gpl = resources.openRawResource(R.raw.gpl_3).bufferedReader().use { it.readText() }
                val exception = resources.openRawResource(R.raw.license_exception).bufferedReader().use { it.readText() }
                "$gpl\n\n$exception"
            } catch (e: IOException) {
                Log.e("AppLicensesDialog", "Unable to read packaged license", e)
                resources.getString(R.string.license_read_failed)
            }
        } else resources.getString(R.string.licenses_body)
    }
    LaunchedEffect(full) { scroll.scrollTo(0) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(R.string.licenses)) },
        text = { Text(body, Modifier.heightIn(max = 400.dp).verticalScroll(scroll)) },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = { full = !full }) { Text(stringResource(if (full) R.string.license_summary else R.string.license_full)) } })
}
