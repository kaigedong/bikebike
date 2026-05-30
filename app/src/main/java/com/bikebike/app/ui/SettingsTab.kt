package com.bikebike.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.bikebike.app.R
import com.bikebike.app.data.AppSettings
import com.bikebike.app.data.LocaleHelper
import com.bikebike.app.viewmodel.BikeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

// ==================== Update State ====================
private sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float) : UpdateState()
    data class DownloadReady(val uri: Uri) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

// ==================== Main Settings Tab ====================
@Composable
fun SettingsTab(viewModel: BikeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logEnabled by viewModel.logEnabled.collectAsState()
    val identityLoaded by viewModel.identityLoaded.collectAsState()
    val identityInfo by viewModel.identityInfo.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    val settings = remember { AppSettings(context) }
    val currentLang = settings.language
    val langLabels = mapOf(
        AppSettings.Language.SYSTEM to stringResource(R.string.lang_system),
        AppSettings.Language.ZH to stringResource(R.string.lang_zh),
        AppSettings.Language.EN to stringResource(R.string.lang_en),
    )

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importFile(it).onSuccess { count ->
                Toast.makeText(context,
                    context.getString(R.string.import_success) + " ($count packets)",
                    Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context,
                    context.getString(R.string.import_failed, e.message),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // === Log Settings ===
        SectionHeader(stringResource(R.string.log_settings))
        SwitchItem(
            title = stringResource(R.string.enable_log),
            subtitle = stringResource(R.string.enable_log_desc),
            checked = logEnabled,
            onCheckedChange = { viewModel.setLogEnabled(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // === Device Connection ===
        SectionHeader(stringResource(R.string.device_connection))

        Card(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_identity),
                        style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.import_identity_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Identity status
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (identityLoaded) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(identityInfo, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.identity_not_loaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        IdentityGuide()
        Spacer(modifier = Modifier.height(16.dp))

        // === Appearance ===
        SectionHeader(stringResource(R.string.appearance))

        Card(
            onClick = { showLangDialog = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Language, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.language), style = MaterialTheme.typography.bodyLarge)
                    Text(langLabels[currentLang] ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // === About ===
        SectionHeader(stringResource(R.string.about))
        Text(stringResource(R.string.version, "0.1.0"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp))
        Text("Keep Bike Controller",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

        Spacer(modifier = Modifier.height(12.dp))
        UpdateCheckCard(context)
        Spacer(modifier = Modifier.height(32.dp))
    }

    // Language dialog
    if (showLangDialog) {
        LanguageDialog(
            currentLang = currentLang,
            onSelect = { lang ->
                showLangDialog = false
                val activity = context as? android.app.Activity
                activity?.let { LocaleHelper.switchLanguage(it, lang.code) }
            },
            onDismiss = { showLangDialog = false }
        )
    }
}

// ==================== Language Dialog ====================
@Composable
private fun LanguageDialog(
    currentLang: AppSettings.Language,
    onSelect: (AppSettings.Language) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = AppSettings.Language.entries
    val labels = listOf(
        stringResource(R.string.lang_system),
        stringResource(R.string.lang_zh),
        stringResource(R.string.lang_en)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                languages.forEachIndexed { index, lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = lang == currentLang, onClick = { onSelect(lang) })
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == currentLang, onClick = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(labels[index], style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

// ==================== Update Check ====================
@Composable
private fun UpdateCheckCard(context: Context) {
    var state by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val settings = remember { AppSettings(context) }

    when (state) {
        is UpdateState.Idle -> {
            Card(
                onClick = {
                    state = UpdateState.Checking
                    MainScope().launch(Dispatchers.IO) { doUpdateCheck(context, settings) { state = it } }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SystemUpdate, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.check_update), style = MaterialTheme.typography.bodyLarge)
                        Text(stringResource(R.string.check_update_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is UpdateState.Checking -> {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.checking_update),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { state = UpdateState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel",
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        is UpdateState.UpToDate -> {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.already_latest),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { state = UpdateState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        is UpdateState.Downloading -> {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        progress = { (state as UpdateState.Downloading).progress },
                        modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(stringResource(R.string.downloading_update),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { state = UpdateState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel",
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        is UpdateState.DownloadReady -> {
            Card(
                onClick = {
                    val uri = (state as UpdateState.DownloadReady).uri
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW, uri).apply {
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        setDataAndType(uri, "application/vnd.android.package-archive")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.InstallMobile, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.install_update),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.install_update_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { state = UpdateState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        is UpdateState.Error -> {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, contentDescription = null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.update_error),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error)
                        Text((state as UpdateState.Error).message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { state = UpdateState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss",
                            modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

private fun doUpdateCheck(context: Context, settings: AppSettings, onState: (UpdateState) -> Unit) {
    try {
        val url = URL("https://api.github.com/repos/kaigedong/bikebike/releases/tags/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000

        if (conn.responseCode != 200) {
            onState(UpdateState.Error("HTTP ${conn.responseCode}"))
            return
        }

        val json = conn.inputStream.bufferedReader().readText()
        conn.disconnect()

        val jsonObj = JSONObject(json)

        // --- Version check: compare commit hash ---
        // Release body contains commit hash like "Commit: abc1234 - ..."
        val body = jsonObj.optString("body", "")
        val remoteCommit = body.substringAfter("Commit: ").substringBefore(" - ").substringBefore("\n").trim()
        val localCommit = settings.lastUpdateCommit

        if (remoteCommit.isNotEmpty() && remoteCommit == localCommit) {
            onState(UpdateState.UpToDate)
            return
        }

        // Find APK asset
        val assets = jsonObj.optJSONArray("assets") ?: org.json.JSONArray()
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            if (asset.optString("name") == "bikebike-latest.apk") {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }

        if (apkUrl == null) {
            onState(UpdateState.Error("No APK found"))
            return
        }

        onState(UpdateState.Downloading(0f))

        // Download
        val apkConn = URL(apkUrl).openConnection() as HttpURLConnection
        apkConn.connectTimeout = 30000
        apkConn.readTimeout = 60000
        val totalSize = apkConn.contentLength.toFloat()
        val input = apkConn.inputStream
        val tempFile = java.io.File(context.cacheDir, "bikebike-update.apk")
        val output = FileOutputStream(tempFile)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalRead = 0f

        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalSize > 0) {
                onState(UpdateState.Downloading(totalRead / totalSize))
            }
        }
        output.close()
        input.close()
        apkConn.disconnect()

        // Save commit hash so next check knows we're up to date
        settings.lastUpdateCommit = remoteCommit

        val apkUri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", tempFile)
        onState(UpdateState.DownloadReady(apkUri))

        // Auto trigger install
        val intent = android.content.Intent(
            android.content.Intent.ACTION_VIEW, apkUri).apply {
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            setDataAndType(apkUri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)

    } catch (e: Exception) {
        onState(UpdateState.Error(e.message ?: "Unknown error"))
    }
}

// ==================== Identity Guide ====================
@Composable
private fun IdentityGuide() {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.HelpOutline, contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.how_to_get_identity),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GuideStep(stringResource(R.string.guide_step1), stringResource(R.string.guide_step1_desc))
                GuideStep(stringResource(R.string.guide_step2), stringResource(R.string.guide_step2_desc))
                GuideStep(stringResource(R.string.guide_step3), stringResource(R.string.guide_step3_desc))
                GuideStep(stringResource(R.string.guide_step4), stringResource(R.string.guide_step4_desc), isCode = true)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.guide_tips), style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(stringResource(R.string.guide_tips_desc), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(title: String, detail: String, isCode: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(detail,
                style = if (isCode) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                else MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ==================== Shared ====================
@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
}

@Composable
private fun SwitchItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
