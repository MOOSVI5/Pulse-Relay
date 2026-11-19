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
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ApiRequestLog
import com.example.data.model.GitHubIntegrationRecord
import com.example.data.model.GitHubTriggerMode
import com.example.data.model.PlanTier
import com.example.engine.GitHubReleaseInfo
import com.example.engine.GitHubRepoSummary
import com.example.engine.SimDiagnostics
import com.example.engine.SmsTransportMode
import com.example.engine.TwilioGatewayConfig
import com.example.ui.components.MonoCodeBlock
import com.example.ui.components.SimGatewayStatusBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ApiExecutionResult
import com.example.ui.viewmodel.ConsoleDashboardData

private enum class ApiScreenSubTab(val label: String) {
    LIVE_API("Live REST API"),
    GITHUB_FIREBASE("GitHub & Firebase")
}

private enum class ApiEndpointPreset(
    val label: String,
    val method: String,
    val path: String
) {
    SEND_SMS("POST /v1/messages", "POST", "/v1/messages"),
    SEND_OTP("POST /v1/verify/send", "POST", "/v1/verify/send"),
    CHECK_OTP("POST /v1/verify/check", "POST", "/v1/verify/check"),
    LIST_NUMBERS("GET /v1/numbers", "GET", "/v1/numbers"),
    HEALTH("GET /v1/health", "GET", "/v1/health")
}

@Composable
fun ApiPlaygroundScreen(
    data: ConsoleDashboardData,
    userUid: String,
    userEmail: String?,
    simDiagnostics: SimDiagnostics,
    transportMode: SmsTransportMode,
    twilioConfig: TwilioGatewayConfig,
    isServerRunning: Boolean,
    serverPort: Int,
    lanBindEnabled: Boolean,
    lanIpAddresses: List<String>,
    isBusy: Boolean,
    lastApiResult: ApiExecutionResult?,
    requestLogs: List<ApiRequestLog>,
    firebaseLatencyMs: Long?,
    fetchedRepos: List<GitHubRepoSummary>,
    latestRelease: GitHubReleaseInfo?,
    onRequestSmsPermission: () -> Unit,
    onSelectTransportMode: (SmsTransportMode) -> Unit,
    onSaveTwilioConfig: (sid: String, token: String, fromNumber: String) -> Unit,
    onToggleLanBind: (Boolean) -> Unit,
    onRefreshLanIps: () -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onSavePlanSettings: (PlanTier, Boolean, String) -> Unit,
    onExecuteHttpRequest: (method: String, path: String, bearerSecret: String, jsonBody: String) -> Unit,
    onVerifyFirebase: () -> Unit,
    onFetchRepos: (username: String, pat: String) -> Unit,
    onLinkRepo: (username: String, repoFullName: String, toNumber: String, mode: GitHubTriggerMode, pat: String) -> Unit,
    onTriggerCommitAlert: (GitHubIntegrationRecord, String) -> Unit,
    onInstallWebhook: (GitHubIntegrationRecord, String, String) -> Unit,
    onToggleGitHubIntegration: (GitHubIntegrationRecord, Boolean) -> Unit,
    onDeleteGitHubIntegration: (String) -> Unit,
    onCheckAppUpdate: (repoFullName: String, pat: String) -> Unit,
    onCopied: (String) -> Unit
) {
    var activeSubTab by remember { mutableStateOf(ApiScreenSubTab.LIVE_API) }

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
            ApiScreenSubTab.entries.forEachIndexed { idx, tab ->
                SegmentedButton(
                    selected = activeSubTab == tab,
                    onClick = { activeSubTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(idx, ApiScreenSubTab.entries.size),
                    modifier = Modifier.testTag("api_subtab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        when (activeSubTab) {
            ApiScreenSubTab.LIVE_API -> LiveApiRunnerContent(
                data = data,
                simDiagnostics = simDiagnostics,
                transportMode = transportMode,
                twilioConfig = twilioConfig,
                isServerRunning = isServerRunning,
                serverPort = serverPort,
                lanBindEnabled = lanBindEnabled,
                lanIpAddresses = lanIpAddresses,
                isBusy = isBusy,
                lastApiResult = lastApiResult,
                requestLogs = requestLogs,
                onRequestSmsPermission = onRequestSmsPermission,
                onSelectTransportMode = onSelectTransportMode,
                onSaveTwilioConfig = onSaveTwilioConfig,
                onToggleLanBind = onToggleLanBind,
                onRefreshLanIps = onRefreshLanIps,
                onToggleServer = onToggleServer,
                onSavePlanSettings = onSavePlanSettings,
                onExecuteHttpRequest = onExecuteHttpRequest,
                onCopied = onCopied
            )

            ApiScreenSubTab.GITHUB_FIREBASE -> GitHubFirebaseSection(
                data = data,
                userUid = userUid,
                userEmail = userEmail,
                isBusy = isBusy,
                firebaseLatencyMs = firebaseLatencyMs,
                fetchedRepos = fetchedRepos,
                latestRelease = latestRelease,
                onVerifyFirebase = onVerifyFirebase,
                onFetchRepos = onFetchRepos,
                onLinkRepo = onLinkRepo,
                onTriggerCommitAlert = onTriggerCommitAlert,
                onInstallWebhook = onInstallWebhook,
                onToggleIntegration = onToggleGitHubIntegration,
                onDeleteIntegration = onDeleteGitHubIntegration,
                onCheckAppUpdate = onCheckAppUpdate,
                onCopied = onCopied
            )
        }
    }
}

@Composable
private fun LiveApiRunnerContent(
    data: ConsoleDashboardData,
    simDiagnostics: SimDiagnostics,
    transportMode: SmsTransportMode,
    twilioConfig: TwilioGatewayConfig,
    isServerRunning: Boolean,
    serverPort: Int,
    lanBindEnabled: Boolean,
    lanIpAddresses: List<String>,
    isBusy: Boolean,
    lastApiResult: ApiExecutionResult?,
    requestLogs: List<ApiRequestLog>,
    onRequestSmsPermission: () -> Unit,
    onSelectTransportMode: (SmsTransportMode) -> Unit,
    onSaveTwilioConfig: (sid: String, token: String, fromNumber: String) -> Unit,
    onToggleLanBind: (Boolean) -> Unit,
    onRefreshLanIps: () -> Unit,
    onToggleServer: (Boolean) -> Unit,
    onSavePlanSettings: (PlanTier, Boolean, String) -> Unit,
    onExecuteHttpRequest: (method: String, path: String, bearerSecret: String, jsonBody: String) -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val activeKeys = data.apiKeys.filter { it.active }
    val defaultKey = activeKeys.firstOrNull()?.secretKey
        ?: data.apiKeys.firstOrNull()?.secretKey
        ?: "pr_live_demo_key"
    val defaultFrom = data.phoneNumbers.firstOrNull { it.countryCode == "PK" }?.e164Number
        ?: data.phoneNumbers.firstOrNull { it.defaultSender }?.e164Number
        ?: data.phoneNumbers.firstOrNull()?.e164Number
        ?: "+923008904321"

    var selectedPreset by remember { mutableStateOf(ApiEndpointPreset.SEND_SMS) }
    var selectedBearer by remember(defaultKey) { mutableStateOf(defaultKey) }
    var requestJson by remember(selectedPreset, defaultFrom) {
        mutableStateOf(defaultJsonFor(selectedPreset, defaultFrom))
    }

    var twilioSid by remember(twilioConfig.accountSid) { mutableStateOf(twilioConfig.accountSid) }
    var twilioToken by remember(twilioConfig.authToken) { mutableStateOf(twilioConfig.authToken) }
    var twilioFrom by remember(twilioConfig.fromNumber) { mutableStateOf(twilioConfig.fromNumber) }

    val currentPlan = PlanTier.fromRaw(data.settings?.planTier)
    var planTier by remember(currentPlan) { mutableStateOf(currentPlan) }
    var directRouting by remember(data.settings?.directRouting) {
        mutableStateOf(data.settings?.directRouting ?: true)
    }
    var globalWebhook by remember(data.settings?.defaultWebhookUrl) {
        mutableStateOf(data.settings?.defaultWebhookUrl.orEmpty())
    }

    val primaryLanIp = if (lanBindEnabled) lanIpAddresses.firstOrNull() else null
    val externalBaseUrl = primaryLanIp?.let { "http://$it:$serverPort" } ?: "http://127.0.0.1:$serverPort"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Endpoints, LAN Wi-Fi Access & API Key Copy Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Api, contentDescription = null, tint = TelemetryCyan)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "REST API Gateway Endpoints",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                StatusPill(
                                    text = if (isServerRunning) "SERVER LIVE ON :$serverPort" else "SERVER PAUSED",
                                    color = if (isServerRunning) SignalEmerald else WarningAmber
                                )
                            }
                        }
                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = onToggleServer,
                            modifier = Modifier.testTag("api_server_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Wifi, contentDescription = null, tint = SignalEmerald, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Allow Wi-Fi / LAN Devices", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = if (lanBindEnabled) "Listening on 0.0.0.0:$serverPort (external apps & PCs)" else "Localhost 127.0.0.1:$serverPort only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onRefreshLanIps) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh IP addresses", tint = TelemetryCyan)
                            }
                            Switch(
                                checked = lanBindEnabled,
                                onCheckedChange = onToggleLanBind,
                                modifier = Modifier.testTag("api_lan_bind_switch")
                            )
                        }
                    }

                    Surface(
                        color = Color(0xFF060A13),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("BASE URL (WI-FI / LAN)", style = MaterialTheme.typography.labelSmall, color = SignalEmerald)
                                    Text(
                                        text = primaryLanIp?.let { "http://$it:$serverPort/v1" } ?: "No Wi-Fi IP detected (use 127.0.0.1)",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFont),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val url = "$externalBaseUrl/v1"
                                        clipboard.setText(AnnotatedString(url))
                                        onCopied("Copied API URL: $url")
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy API Base URL", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("BEARER API KEY", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                                    Text(
                                        text = selectedBearer,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                        color = Color(0xFFE2E8F0),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(selectedBearer))
                                        onCopied("Bearer API key copied")
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy API Key", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Copy Key", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Real SMS Transport Mode Card (Phone GSM SIM vs Twilio Cloud)
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SimCard, contentDescription = null, tint = SignalEmerald)
                        Spacer(Modifier.width(8.dp))
                        Text("SMS Carrier Transport", style = MaterialTheme.typography.titleMedium)
                    }

                    SimGatewayStatusBanner(
                        simDiagnostics = simDiagnostics,
                        transportMode = transportMode,
                        twilioConfigured = twilioConfig.isConfigured(),
                        onRequestSmsPermission = onRequestSmsPermission
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmsTransportMode.entries.forEach { mode ->
                            FilterChip(
                                selected = transportMode == mode,
                                onClick = { onSelectTransportMode(mode) },
                                label = { Text(mode.label, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SignalEmerald.copy(alpha = 0.18f),
                                    selectedLabelColor = SignalEmerald
                                ),
                                modifier = Modifier.testTag("transport_${mode.name.lowercase()}")
                            )
                        }
                    }

                    if (transportMode == SmsTransportMode.TWILIO_CLOUD) {
                        OutlinedTextField(
                            value = twilioSid,
                            onValueChange = { twilioSid = it },
                            label = { Text("Twilio Account SID (AC...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = twilioToken,
                            onValueChange = { twilioToken = it },
                            label = { Text("Twilio Auth Token") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = twilioFrom,
                            onValueChange = { twilioFrom = it },
                            label = { Text("Twilio From Number (+1...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onSaveTwilioConfig(twilioSid, twilioToken, twilioFrom) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Apply Twilio Gateway")
                        }
                    }
                }
            }
        }

        // 3. Interactive HTTP Endpoint Runner
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Test HTTP Endpoint Live", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ApiEndpointPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    requestJson = defaultJsonFor(preset, defaultFrom)
                                },
                                label = { Text(preset.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Text("Authenticate With API Key", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        data.apiKeys.forEach { key ->
                            FilterChip(
                                selected = selectedBearer == key.secretKey,
                                onClick = { selectedBearer = key.secretKey },
                                label = {
                                    Text(
                                        "${key.label} (${if (key.active) key.environment else "REVOKED"})",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }

                    if (selectedPreset.method == "POST") {
                        OutlinedTextField(
                            value = requestJson,
                            onValueChange = { requestJson = it },
                            label = { Text("JSON Payload") },
                            minLines = 4,
                            maxLines = 8,
                            textStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_playground_body_input")
                        )
                    }

                    Button(
                        onClick = {
                            onExecuteHttpRequest(
                                selectedPreset.method,
                                selectedPreset.path,
                                selectedBearer,
                                requestJson
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("execute_api_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isBusy) "Dispatching HTTP + ${transportMode.shortTag}..." else "Execute Real HTTP ${selectedPreset.method}",
                            color = Color.White
                        )
                    }

                    lastApiResult?.let { res ->
                        val color = if (res.isSuccess) SignalEmerald else PulseCrimson
                        Surface(
                            color = Color(0xFF060A13),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatusPill(text = "HTTP ${res.statusCode}", color = color)
                                    Text(
                                        text = "${res.latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryCyan
                                    )
                                }
                                Text(
                                    text = res.responseBody,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Ready-to-run cURL (Uses LAN IP when Wi-Fi LAN is available)
        item {
            val curl = if (selectedPreset.method == "POST") {
                """
curl -X POST $externalBaseUrl${selectedPreset.path} \
  -H "Authorization: Bearer $selectedBearer" \
  -H "Content-Type: application/json" \
  -d '${requestJson.replace("\n", "").replace("  ", "")}'
                """.trimIndent()
            } else {
                """
curl -X GET $externalBaseUrl${selectedPreset.path} \
  -H "Authorization: Bearer $selectedBearer"
                """.trimIndent()
            }
            MonoCodeBlock(
                title = "READY-TO-RUN CURL (${if (primaryLanIp != null) "WI-FI LAN" else "LOCALHOST"})",
                code = curl,
                onCopied = { onCopied("cURL command copied") },
                modifier = Modifier.widthIn(max = 880.dp)
            )
        }

        // 5. Plan & Global Routing
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Plan & Webhook Routing", style = MaterialTheme.typography.titleMedium)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlanTier.entries.forEach { tier ->
                            FilterChip(
                                selected = planTier == tier,
                                onClick = { planTier = tier },
                                label = { Text(tier.displayName) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Direct Carrier Routing", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Send immediately through active gateway",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = directRouting, onCheckedChange = { directRouting = it })
                    }

                    OutlinedTextField(
                        value = globalWebhook,
                        onValueChange = { globalWebhook = it.take(500) },
                        label = { Text("Default Webhook Callback URL") },
                        placeholder = { Text("https://yourapp.com/webhooks/pulserelay") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { onSavePlanSettings(planTier, directRouting, globalWebhook) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Routing Settings")
                    }
                }
            }
        }

        item {
            Text(
                text = "SERVER REQUEST INSPECTOR (${requestLogs.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
            )
        }

        if (requestLogs.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "No HTTP requests received yet. Tap Execute above or call $externalBaseUrl/v1/health.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(requestLogs.take(15), key = { it.id }) { log ->
                val ok = log.statusCode in 200..299
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${log.method} ${log.path}",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFamily = JetBrainsMonoFont,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Key: ${log.apiKeyUsed} · ${log.latencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(
                            text = "${log.statusCode}",
                            color = if (ok) SignalEmerald else WarningAmber
                        )
                    }
                }
            }
        }
    }
}

private fun defaultJsonFor(preset: ApiEndpointPreset, defaultFrom: String): String = when (preset) {
    ApiEndpointPreset.SEND_SMS -> """
{
  "to": "+923001234567",
  "from": "$defaultFrom",
  "body": "Live GSM SIM SMS test from PulseRelay!"
}
    """.trimIndent()

    ApiEndpointPreset.SEND_OTP -> """
{
  "to": "+923001234567",
  "from": "$defaultFrom",
  "service": "PulseAuth",
  "length": 6,
  "ttl_seconds": 300
}
    """.trimIndent()

    ApiEndpointPreset.CHECK_OTP -> """
{
  "to": "+923001234567",
  "code": "492817"
}
    """.trimIndent()

    ApiEndpointPreset.LIST_NUMBERS, ApiEndpointPreset.HEALTH -> ""
}
