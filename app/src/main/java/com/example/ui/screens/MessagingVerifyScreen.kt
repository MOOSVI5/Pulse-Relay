package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GlobalCountryCatalog
import com.example.data.model.MessageDirection
import com.example.data.model.MessageStatus
import com.example.data.model.OtpChannel
import com.example.data.model.OtpStatus
import com.example.data.model.OtpVerificationRecord
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import com.example.engine.OtpCheckResult
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ConsoleDashboardData
import kotlinx.coroutines.delay

enum class MessagingSubTab(val label: String) {
    SEND_SMS("Send SMS"),
    OTP_VERIFY("OTP Verify"),
    LOGS("Logs")
}

private val globalQuickRecipients = listOf(
    "PK +92" to "+923001234567",
    "US +1" to "+14155550199",
    "UK +44" to "+447700900412",
    "AE +971" to "+971501234567",
    "IN +91" to "+919820112233",
    "JP +81" to "+819044128890",
    "DE +49" to "+4915123456789",
    "SG +65" to "+6581234567"
)

@Composable
fun MessagingVerifyScreen(
    data: ConsoleDashboardData,
    initialSubTab: MessagingSubTab,
    isBusy: Boolean,
    lastOtpCheck: OtpCheckResult?,
    calculateSegments: (String) -> Int,
    estimatePrice: (Int, PlanTier, String) -> Double,
    onSendSms: (to: String, from: String, body: String) -> Unit,
    onSendOtp: (to: String, from: String, service: String, length: Int, channel: OtpChannel) -> Unit,
    onCheckOtp: (to: String, code: String) -> Unit,
    onRevokeOtp: (OtpVerificationRecord) -> Unit,
    onClearOtpCheck: () -> Unit
) {
    var activeTab by remember(initialSubTab) { mutableStateOf(initialSubTab) }

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
            MessagingSubTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(index, MessagingSubTab.entries.size),
                    modifier = Modifier.testTag("messaging_tab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        when (activeTab) {
            MessagingSubTab.SEND_SMS -> SendSmsSection(
                data = data,
                isBusy = isBusy,
                calculateSegments = calculateSegments,
                estimatePrice = estimatePrice,
                onSendSms = onSendSms
            )
            MessagingSubTab.OTP_VERIFY -> OtpVerifySection(
                data = data,
                isBusy = isBusy,
                lastOtpCheck = lastOtpCheck,
                onSendOtp = onSendOtp,
                onCheckOtp = onCheckOtp,
                onRevokeOtp = onRevokeOtp,
                onClearOtpCheck = onClearOtpCheck
            )
            MessagingSubTab.LOGS -> MessageLogsSection(messages = data.smsMessages)
        }
    }
}

@Composable
private fun SendSmsSection(
    data: ConsoleDashboardData,
    isBusy: Boolean,
    calculateSegments: (String) -> Int,
    estimatePrice: (Int, PlanTier, String) -> Double,
    onSendSms: (to: String, from: String, body: String) -> Unit
) {
    val activeNumbers = data.phoneNumbers.filter { it.status == "ACTIVE" }
    val defaultFrom = activeNumbers.firstOrNull { it.defaultSender }?.e164Number
        ?: activeNumbers.firstOrNull()?.e164Number
        ?: "+14158904321"

    var selectedFrom by remember(defaultFrom) { mutableStateOf(defaultFrom) }
    var toNumber by remember { mutableStateOf("+923001234567") }
    var body by remember { mutableStateOf("Hello from PulseRelay! Your direct global route is live.") }

    val planTier = PlanTier.fromRaw(data.settings?.planTier)
    val carrier = remember(toNumber) { GlobalCountryCatalog.resolveDestination(toNumber) }
    val segments = remember(body) { calculateSegments(body) }
    val price = remember(segments, planTier, toNumber) { estimatePrice(segments, planTier, toNumber) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Text(
                        text = "Sender ID / Virtual Number",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val choices = activeNumbers.map { it.e164Number }.ifEmpty { listOf("+14158904321") }
                        choices.forEach { num ->
                            FilterChip(
                                selected = selectedFrom == num,
                                onClick = { selectedFrom = num },
                                label = {
                                    Text(
                                        num,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = TelemetryCyan
                                )
                            )
                        }
                    }

                    Text(
                        text = "Quick Global Test Destinations",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        globalQuickRecipients.forEach { (label, e164) ->
                            FilterChip(
                                selected = toNumber == e164,
                                onClick = { toNumber = e164 },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = toNumber,
                        onValueChange = { toNumber = it },
                        label = { Text("Recipient E.164 Number") },
                        placeholder = { Text("+14155550199") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_to_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            text = "${carrier.countryCode} · ${carrier.carrierName}",
                            color = TelemetryCyan
                        )
                        Text(
                            text = "~${carrier.avgLatencyMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald
                        )
                    }

                    OutlinedTextField(
                        value = body,
                        onValueChange = { if (it.length <= 1600) body = it },
                        label = { Text("Message Body") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sms_body_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${body.length} chars · $segments segment${if (segments > 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (planTier == PlanTier.UNLIMITED_DIRECT) {
                                "UNLIMITED PLAN · $0.00"
                            } else {
                                "EST. COST · $${String.format("%.4f", price)}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (planTier == PlanTier.UNLIMITED_DIRECT) SignalEmerald else WarningAmber
                        )
                    }

                    Button(
                        onClick = { onSendSms(toNumber, selectedFrom, body) },
                        enabled = !isBusy && body.isNotBlank() && toNumber.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_sms_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isBusy) "Dispatching..." else "Send Direct Global SMS",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Live Handset Preview Bubble
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = Color(0xFF070B14),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HANDSET PREVIEW · $selectedFrom",
                            style = MaterialTheme.typography.labelSmall,
                            color = TelemetryCyan
                        )
                        Text(
                            text = "TO $toNumber",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    Surface(
                        color = Color(0xFF1A253D),
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 4.dp
                        )
                    ) {
                        Text(
                            text = body.ifBlank { "Your SMS preview appears here..." },
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFF8FAFC)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OtpVerifySection(
    data: ConsoleDashboardData,
    isBusy: Boolean,
    lastOtpCheck: OtpCheckResult?,
    onSendOtp: (to: String, from: String, service: String, length: Int, channel: OtpChannel) -> Unit,
    onCheckOtp: (to: String, code: String) -> Unit,
    onRevokeOtp: (OtpVerificationRecord) -> Unit,
    onClearOtpCheck: () -> Unit
) {
    val activeNumbers = data.phoneNumbers.filter { it.status == "ACTIVE" }
    val defaultFrom = activeNumbers.firstOrNull { it.countryCode == "PK" }?.e164Number
        ?: activeNumbers.firstOrNull { it.defaultSender }?.e164Number
        ?: activeNumbers.firstOrNull()?.e164Number
        ?: "+14158904321"

    var serviceName by remember { mutableStateOf("PulseAuth Global") }
    var otpRecipient by remember { mutableStateOf("+923001234567") }
    var codeLength by remember { mutableIntStateOf(6) }
    var channel by remember { mutableStateOf(OtpChannel.SMS) }
    var verifyCodeInput by remember { mutableStateOf("") }

    // Ticker for countdown display
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMillis = System.currentTimeMillis()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Dispatch OTP Challenge Card
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = SignalEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Generate & Send Global OTP",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = serviceName,
                        onValueChange = { serviceName = it },
                        label = { Text("App / Service Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_service_input")
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        globalQuickRecipients.forEach { (label, e164) ->
                            FilterChip(
                                selected = otpRecipient == e164,
                                onClick = { otpRecipient = e164 },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = otpRecipient,
                        onValueChange = { otpRecipient = it },
                        label = { Text("Recipient E.164 Number (+92, +1, +44...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("otp_to_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(4, 6, 8).forEach { len ->
                                FilterChip(
                                    selected = codeLength == len,
                                    onClick = { codeLength = len },
                                    label = { Text("$len-digit") }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OtpChannel.entries.forEach { ch ->
                                FilterChip(
                                    selected = channel == ch,
                                    onClick = { channel = ch },
                                    label = { Text(if (ch == OtpChannel.SMS) "SMS" else "Flash") }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onClearOtpCheck()
                            onSendOtp(otpRecipient, defaultFrom, serviceName, codeLength, channel)
                        },
                        enabled = !isBusy && otpRecipient.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("send_otp_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald)
                    ) {
                        Text(
                            text = "Dispatch Secure OTP Code",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF061510)
                        )
                    }
                }
            }
        }

        // 2. Verify / Check OTP Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.45f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.VerifiedUser,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Verify Submitted OTP Code",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = verifyCodeInput,
                            onValueChange = { verifyCodeInput = it.filter { c -> c.isDigit() }.take(8) },
                            label = { Text("Enter OTP Code") },
                            placeholder = { Text("492817") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("verify_code_input")
                        )
                        Button(
                            onClick = { onCheckOtp(otpRecipient, verifyCodeInput) },
                            enabled = !isBusy && verifyCodeInput.length >= 4,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("check_otp_button")
                        ) {
                            Text(
                                "Verify",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF070B14)
                            )
                        }
                    }

                    lastOtpCheck?.let { chk ->
                        val color = if (chk.valid) SignalEmerald else PulseCrimson
                        Surface(
                            color = color.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (chk.valid) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "${chk.status.name} ${if (chk.otpSid.isNotBlank()) "· ${chk.otpSid}" else ""}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                    Text(
                                        text = chk.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. Active & Past OTP Verifications List
        item {
            Text(
                text = "OTP VERIFICATION CHALLENGES (${data.otpVerifications.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
            )
        }

        items(data.otpVerifications, key = { it.id.ifBlank { it.sid } }) { otp ->
            OtpChallengeCard(
                otp = otp,
                nowMillis = nowMillis,
                onPrefillVerify = {
                    otpRecipient = otp.toNumber
                    verifyCodeInput = otp.code
                },
                onRevokeOtp = { onRevokeOtp(otp) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
            )
        }
    }
}

@Composable
private fun OtpChallengeCard(
    otp: OtpVerificationRecord,
    nowMillis: Long,
    onPrefillVerify: () -> Unit,
    onRevokeOtp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = OtpStatus.fromRaw(otp.status)
    val statusColor = when (status) {
        OtpStatus.APPROVED -> SignalEmerald
        OtpStatus.PENDING -> TelemetryCyan
        OtpStatus.EXPIRED -> WarningAmber
        OtpStatus.MAX_ATTEMPTS -> PulseCrimson
    }
    val remainingSec = ((otp.expiresAtMillis - nowMillis) / 1000L).coerceAtLeast(0L)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
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
                        text = otp.toNumber,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFont),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${otp.serviceName} · ${otp.sid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(text = status.name, color = statusColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = Color(0xFF070B14),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "CODE: ${otp.code}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = JetBrainsMonoFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = TelemetryCyan
                        )
                    }
                    Text(
                        text = if (status == OtpStatus.PENDING) "${remainingSec}s left" else "Attempts: ${otp.attempts}/${otp.maxAttempts}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (status == OtpStatus.PENDING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onPrefillVerify,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Fill & Test", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = onRevokeOtp,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Expire", color = PulseCrimson, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageLogsSection(messages: List<SmsMessageRecord>) {
    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf("ALL") }
    var expandedId by remember { mutableStateOf<String?>(null) }

    val filtered = remember(messages, searchQuery, filterMode) {
        messages.filter { msg ->
            val matchesFilter = when (filterMode) {
                "DELIVERED" -> msg.status == MessageStatus.DELIVERED.name
                "API" -> msg.direction == MessageDirection.OUTBOUND_API.name
                "CONSOLE" -> msg.direction == MessageDirection.OUTBOUND_CONSOLE.name
                else -> true
            }
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                msg.toNumber.lowercase().contains(q) ||
                msg.fromNumber.lowercase().contains(q) ||
                msg.body.lowercase().contains(q) ||
                msg.sid.lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Search number, SID, or text...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ALL", "DELIVERED", "API", "CONSOLE").forEach { mode ->
                        FilterChip(
                            selected = filterMode == mode,
                            onClick = { filterMode = mode },
                            label = { Text(mode) }
                        )
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.Sms, contentDescription = null, tint = TelemetryCyan)
                        Spacer(Modifier.height(6.dp))
                        Text("No matching message logs", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id.ifBlank { it.sid } }) { msg ->
                val isExpanded = expandedId == msg.id
                val status = MessageStatus.fromRaw(msg.status)
                val statusColor = when (status) {
                    MessageStatus.DELIVERED -> SignalEmerald
                    MessageStatus.SENT, MessageStatus.SENDING -> TelemetryCyan
                    MessageStatus.QUEUED -> WarningAmber
                    MessageStatus.FAILED -> PulseCrimson
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp)
                        .clickable { expandedId = if (isExpanded) null else msg.id },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${msg.fromNumber} → ${msg.toNumber}",
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFont),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            StatusPill(text = status.name, color = statusColor)
                        }

                        Text(
                            text = msg.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isExpanded) 10 else 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF070B14), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("SID: ${msg.sid}", style = MaterialTheme.typography.labelSmall, color = TelemetryCyan)
                                    Text("Carrier: ${msg.carrierName} (${msg.countryCode})", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                    Text("Direction: ${msg.direction} · Key: ${msg.apiKeyLabel}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCBD5E1))
                                    Text("Latency: ${msg.latencyMs}ms · Segments: ${msg.segments} · Cost: $${String.format("%.4f", msg.priceUsd)}", style = MaterialTheme.typography.labelSmall, color = SignalEmerald)
                                    Text("Webhook: ${msg.webhookStatus}", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
