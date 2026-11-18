package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApiKeyRecord
import com.example.data.model.CountryCarrierInfo
import com.example.data.model.GlobalCountryCatalog
import com.example.data.model.KeyEnvironment
import com.example.data.model.NumberStatus
import com.example.data.model.NumberType
import com.example.data.model.PhoneNumberRecord
import com.example.data.model.PlanTier
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ConsoleDashboardData

private enum class NumbersKeysSubTab(val label: String) {
    NUMBERS("Global Numbers"),
    API_KEYS("API Keys")
}

@Composable
fun NumbersKeysScreen(
    data: ConsoleDashboardData,
    isBusy: Boolean,
    onProvisionNumber: (CountryCarrierInfo, NumberType, String, PlanTier) -> Unit,
    onUpdateNumber: (PhoneNumberRecord, String, NumberStatus, Boolean) -> Unit,
    onReleaseNumber: (String) -> Unit,
    onCreateApiKey: (String, KeyEnvironment, String) -> Unit,
    onToggleApiKey: (String, Boolean) -> Unit,
    onDeleteApiKey: (String) -> Unit,
    onCopied: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(NumbersKeysSubTab.NUMBERS) }
    var showProvisionDialog by remember { mutableStateOf(false) }
    var showCreateKeyDialog by remember { mutableStateOf(false) }
    var editingNumber by remember { mutableStateOf<PhoneNumberRecord?>(null) }

    val planTier = PlanTier.fromRaw(data.settings?.planTier)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 880.dp)
                .padding(horizontal = 16.dp)
        ) {
            NumbersKeysSubTab.entries.forEachIndexed { idx, tab ->
                SegmentedButton(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(idx, NumbersKeysSubTab.entries.size),
                    modifier = Modifier.testTag("nk_tab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            NumbersKeysSubTab.NUMBERS -> NumbersListSection(
                numbers = data.phoneNumbers,
                isBusy = isBusy,
                onOpenProvision = { showProvisionDialog = true },
                onEditNumber = { editingNumber = it },
                onSetDefault = { num ->
                    onUpdateNumber(num, num.webhookUrl, NumberStatus.fromRaw(num.status), true)
                },
                onReleaseNumber = onReleaseNumber,
                onCopied = onCopied
            )

            NumbersKeysSubTab.API_KEYS -> ApiKeysListSection(
                keys = data.apiKeys,
                isBusy = isBusy,
                onOpenCreateKey = { showCreateKeyDialog = true },
                onToggleKey = onToggleApiKey,
                onDeleteKey = onDeleteApiKey,
                onCopied = onCopied
            )
        }
    }

    if (showProvisionDialog) {
        ProvisionNumberDialog(
            planTier = planTier,
            onDismiss = { showProvisionDialog = false },
            onConfirm = { country, type, alphaId ->
                showProvisionDialog = false
                onProvisionNumber(country, type, alphaId, planTier)
            }
        )
    }

    if (showCreateKeyDialog) {
        CreateApiKeyDialog(
            onDismiss = { showCreateKeyDialog = false },
            onConfirm = { label, env, scopes ->
                showCreateKeyDialog = false
                onCreateApiKey(label, env, scopes)
            }
        )
    }

    editingNumber?.let { num ->
        EditNumberWebhookDialog(
            number = num,
            onDismiss = { editingNumber = null },
            onSave = { webhook, status, isDefault ->
                editingNumber = null
                onUpdateNumber(num, webhook, status, isDefault)
            }
        )
    }
}

@Composable
private fun NumbersListSection(
    numbers: List<PhoneNumberRecord>,
    isBusy: Boolean,
    onOpenProvision: () -> Unit,
    onEditNumber: (PhoneNumberRecord) -> Unit,
    onSetDefault: (PhoneNumberRecord) -> Unit,
    onReleaseNumber: (String) -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = onOpenProvision,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
                    .testTag("provision_number_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("Provision Global Phone Number", color = Color.White)
            }
        }

        items(numbers, key = { it.id.ifBlank { it.e164Number } }) { num ->
            val status = NumberStatus.fromRaw(num.status)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (num.defaultSender) TelemetryCyan.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            StatusPill(text = num.countryCode, color = TelemetryCyan)
                            Text(
                                text = num.e164Number,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = JetBrainsMonoFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(num.e164Number))
                                onCopied("Copied ${num.e164Number}")
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy number",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${num.regionLabel} · ${NumberType.fromRaw(num.numberType).label} · ${num.capabilities}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        StatusPill(
                            text = if (status == NumberStatus.ACTIVE) "ACTIVE" else "PAUSED",
                            color = if (status == NumberStatus.ACTIVE) SignalEmerald else WarningAmber
                        )
                    }

                    if (num.webhookUrl.isNotBlank()) {
                        Text(
                            text = "Webhook: ${num.webhookUrl}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (num.defaultSender) {
                            StatusPill(text = "DEFAULT SENDER", color = SignalEmerald)
                        } else {
                            OutlinedButton(
                                onClick = { onSetDefault(num) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Set Default", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        OutlinedButton(
                            onClick = { onEditNumber(num) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.Webhook, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Configure", style = MaterialTheme.typography.labelMedium)
                        }

                        Spacer(Modifier.weight(1f))

                        if (numbers.size > 1) {
                            IconButton(
                                onClick = { onReleaseNumber(num.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Release number",
                                    tint = PulseCrimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ApiKeysListSection(
    keys: List<ApiKeyRecord>,
    isBusy: Boolean,
    onOpenCreateKey: () -> Unit,
    onToggleKey: (String, Boolean) -> Unit,
    onDeleteKey: (String) -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var revealedIds by remember { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = onOpenCreateKey,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
                    .testTag("create_api_key_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = Color(0xFF070B14))
                Spacer(Modifier.width(6.dp))
                Text("Create New API Key", color = Color(0xFF070B14))
            }
        }

        items(keys, key = { it.id.ifBlank { it.secretKey } }) { apiKey ->
            val revealed = apiKey.id in revealedIds
            val masked = if (apiKey.secretKey.length > 12) {
                "${apiKey.secretKey.take(10)}••••••••${apiKey.secretKey.takeLast(4)}"
            } else {
                "••••••••••••"
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = apiKey.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${apiKey.scopes} · ${apiKey.requestsCount} API calls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = apiKey.environment,
                            color = if (apiKey.environment == KeyEnvironment.LIVE.name) PulseCrimson else TelemetryCyan
                        )
                    }

                    Surface(
                        color = Color(0xFF070B14),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (revealed) apiKey.secretKey else masked,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                color = Color(0xFFE2E8F0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    revealedIds = if (revealed) revealedIds - apiKey.id else revealedIds + apiKey.id
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(apiKey.secretKey))
                                    onCopied("API key copied to clipboard")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy API key",
                                    tint = TelemetryCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = apiKey.active,
                                onCheckedChange = { onToggleKey(apiKey.id, it) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (apiKey.active) "Active" else "Revoked",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (apiKey.active) SignalEmerald else PulseCrimson
                            )
                        }

                        if (keys.size > 1) {
                            IconButton(
                                onClick = { onDeleteKey(apiKey.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete API key",
                                    tint = PulseCrimson
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvisionNumberDialog(
    planTier: PlanTier,
    onDismiss: () -> Unit,
    onConfirm: (CountryCarrierInfo, NumberType, String) -> Unit
) {
    var selectedCountry by remember { mutableStateOf(GlobalCountryCatalog.countries.first()) }
    var selectedType by remember { mutableStateOf(NumberType.LOCAL) }
    var customAlpha by remember { mutableStateOf("PulseApp") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Public, contentDescription = null, tint = TelemetryCyan) },
        title = { Text("Provision Global Number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select Country Route", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GlobalCountryCatalog.countries.forEach { country ->
                        FilterChip(
                            selected = selectedCountry.countryCode == country.countryCode,
                            onClick = { selectedCountry = country },
                            label = { Text("${country.countryCode} ${country.dialPrefix}") }
                        )
                    }
                }
                Text(
                    text = "${selectedCountry.countryName} · ${selectedCountry.carrierName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TelemetryCyan
                )

                Text("Number Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NumberType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                if (selectedType == NumberType.ALPHANUMERIC) {
                    OutlinedTextField(
                        value = customAlpha,
                        onValueChange = { customAlpha = it.filter { ch -> ch.isLetterOrDigit() }.take(11) },
                        label = { Text("Alphanumeric Sender ID (max 11 chars)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text(
                    text = if (planTier == PlanTier.UNLIMITED_DIRECT) {
                        "Included at $0.00/mo on Unlimited Direct"
                    } else {
                        "Standard metered monthly number fee applies"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = SignalEmerald
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCountry, selectedType, customAlpha) },
                colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
            ) {
                Text("Provision Now", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateApiKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, KeyEnvironment, String) -> Unit
) {
    var label by remember { mutableStateOf("Mobile Production Key") }
    var env by remember { mutableStateOf(KeyEnvironment.LIVE) }
    var scopes by remember { mutableStateOf("SMS,VERIFY,NUMBERS") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Key, contentDescription = null, tint = TelemetryCyan) },
        title = { Text("Create API Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(80) },
                    label = { Text("Key Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyEnvironment.entries.forEach { item ->
                        FilterChip(
                            selected = env == item,
                            onClick = { env = item },
                            label = { Text(item.name) }
                        )
                    }
                }
                OutlinedTextField(
                    value = scopes,
                    onValueChange = { scopes = it.take(120) },
                    label = { Text("Scopes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (label.isNotBlank()) onConfirm(label, env, scopes) },
                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
            ) {
                Text("Generate Key", color = Color(0xFF070B14))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditNumberWebhookDialog(
    number: PhoneNumberRecord,
    onDismiss: () -> Unit,
    onSave: (String, NumberStatus, Boolean) -> Unit
) {
    var webhook by remember { mutableStateOf(number.webhookUrl) }
    var status by remember { mutableStateOf(NumberStatus.fromRaw(number.status)) }
    var isDefault by remember { mutableStateOf(number.defaultSender) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${number.e164Number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = webhook,
                    onValueChange = { webhook = it.take(500) },
                    label = { Text("Delivery Webhook URL (https://...)") },
                    placeholder = { Text("https://api.yourapp.com/sms-webhook") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Number Active")
                    Switch(
                        checked = status == NumberStatus.ACTIVE,
                        onCheckedChange = { status = if (it) NumberStatus.ACTIVE else NumberStatus.PAUSED }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Default Outbound Sender")
                    Switch(checked = isDefault, onCheckedChange = { isDefault = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(webhook, status, isDefault) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
