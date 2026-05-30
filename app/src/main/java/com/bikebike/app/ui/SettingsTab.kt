package com.bikebike.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

@Composable
fun SettingsTab(viewModel: BikeViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logEnabled by viewModel.logEnabled.collectAsState()
    val identityLoaded by viewModel.identityLoaded.collectAsState()
    val identityInfo by viewModel.identityInfo.collectAsState()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.importIdentity(it).onSuccess { count ->
                Toast.makeText(
                    context,
                    context.getString(R.string.import_success) + " ($count packets)",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Toast.makeText(
                    context,
                    context.getString(R.string.import_failed, e.message),
                    Toast.LENGTH_LONG
                ).show()
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

        // Import button
        Card(
            onClick = { filePicker.launch(arrayOf("application/json", "*/*")) },
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
                    Text(
                        stringResource(R.string.import_identity),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        stringResource(R.string.import_identity_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.ChevronRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Identity status
        if (identityLoaded) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(identityInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.identity_not_loaded),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // === Step-by-step guide ===
        IdentityGuide()

        Spacer(modifier = Modifier.height(16.dp))

        // === Appearance ===
        SectionHeader(stringResource(R.string.appearance))
        LanguageSelector(context)

        Spacer(modifier = Modifier.height(32.dp))

        // === About ===
        SectionHeader(stringResource(R.string.about))
        Text(
            stringResource(R.string.version, "0.1.0"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "Keep Bike Controller",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun IdentityGuide() {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        // Clickable header
        Card(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.how_to_get_identity),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expandable steps
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GuideStep(
                    title = stringResource(R.string.guide_step1),
                    detail = stringResource(R.string.guide_step1_desc)
                )
                GuideStep(
                    title = stringResource(R.string.guide_step2),
                    detail = stringResource(R.string.guide_step2_desc)
                )
                GuideStep(
                    title = stringResource(R.string.guide_step3),
                    detail = stringResource(R.string.guide_step3_desc)
                )
                GuideStep(
                    title = stringResource(R.string.guide_step4),
                    detail = stringResource(R.string.guide_step4_desc),
                    isCode = true
                )
                GuideStep(
                    title = stringResource(R.string.guide_step5),
                    detail = stringResource(R.string.guide_step5_desc),
                    isCode = true
                )

                // Tips
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.guide_tips),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.guide_tips_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideStep(
    title: String,
    detail: String,
    isCode: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                detail,
                style = if (isCode)
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                else
                    MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LanguageSelector(context: android.content.Context) {
    var langExpanded by remember { mutableStateOf(false) }
    val languages = AppSettings.Language.entries
    val langLabels = listOf(
        stringResource(R.string.lang_system),
        stringResource(R.string.lang_zh),
        stringResource(R.string.lang_en)
    )

    Card(
        onClick = { langExpanded = true },
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
                Text(
                    stringResource(R.string.language),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            DropdownMenu(
                expanded = langExpanded,
                onDismissRequest = { langExpanded = false }
            ) {
                languages.forEachIndexed { index, lang ->
                    DropdownMenuItem(
                        text = { Text(langLabels[index]) },
                        onClick = {
                            langExpanded = false
                            val activity = context as? android.app.Activity
                            activity?.let { act ->
                                LocaleHelper.switchLanguage(act, lang.code)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SwitchItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
