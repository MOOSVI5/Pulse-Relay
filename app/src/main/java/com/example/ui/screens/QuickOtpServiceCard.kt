package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GlobalCountryCatalog
import com.example.data.model.OtpChannel
import com.example.data.model.OtpStatus
import com.example.data.model.OtpVerificationRecord
import com.example.engine.OtpCheckResult
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ConsoleDashboardData

private val otpCountryPresets = listOf(
    "PK +92" to "+923001234567",
    "US +1" to "+14155550199",
    "UK +44" to "+447700900412",
    "AE +971" to "+971501234567",
    "IN +91" to "+919820112233"
)

@Composable
fun QuickOtpServiceCard(
    data: ConsoleDashboardData,
    otpServiceEnabled: Boolean,
    canControlService: Boolean,
    canSendOtp: Boolean,
    serverPort: Int,
    isBusy: Boolean,
    lastOtpCheck: OtpCheckResult?,
    onToggleOtpService: (Boolean) -> Unit,
    onSendOtp: (to: String, from: String, service: String, length: Int, channel: OtpChannel, ttlSeconds: Int) -> Unit,
    onVerifyOtp: (to: String, code: String) -> Unit,
    onRevokeOtp: (OtpVerificationRecord) -> Unit,
    onClearOtpCheck: () -> Unit,
    onCopied: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val activeNumbers = data.phoneNumbers.filter { it.status == "ACTIVE" }
    val defaultSender = activeNumbers.firstOrNull { it.countryCode == "PK" }?.e164Number
        ?: activeNumbers.firstOrNull { it.defaultSender }?.e164Number
        ?: activeNumbers.firstOrNull()?.e164Number
        ?: "+923008904321"

    var selectedSender by remember(defaultSender) { mutableStateOf(defaultSender) }
    var recipient by remember { mutableStateOf("+923001234567") }
    var serviceName by remember { mutableStateOf("PulseAuth") }
    var codeLength by remember { mutableIntStateOf(6) }
    var ttlSeconds by remember { mutableIntStateOf(300) }
    var verifyCodeInput by remember { mutableStateOf("") }

    val carrier = remember(recipient) { GlobalCountryCatalog.resolveDestination(recipient) }
    val latestPendingOtp = remember(data.otpVerifications) {
        data.otpVerifications.firstOrNull { OtpStatus.fromRaw(it.status) == OtpStatus.PENDING }
    }
    val activeKey = data.apiKeys.firstOrNull { it.active }?.secretKey ?: "pr_live_your_key"

    Surface(
        modifier = modifier.testTag("quick_otp_card"),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.dp,
            if (otpServiceEnabled) SignalEmerald.copy(alpha = 0.55f) else WarningAmber.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = if (otpServiceEnabled) SignalEmerald else WarningAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "OTP SMS Service",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusPill(
                            text = if (otpServiceEnabled) "LIVE · FULL CONTROL" else "PAUSED BY ADMIN",
                            color = if (otpServiceEnabled) SignalEmerald else WarningAmber
                        )
                    }
                }

                Switch(
                    checked = otpServiceEnabled,
                    onCheckedChange = onToggleOtpService,
                    enabled = canControlService,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SignalEmerald
                    ),
                    modifier = Modifier.testTag("otp_service_master_switch")
                )
            }

            // Quick Country Presets including Pakistan +92
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                otpCountryPresets.forEach { (label, sample) ->
                    FilterChip(
                        selected = recipient.startsWith(sample.take(3)),
                        onClick = { recipient = sample },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalEmerald.copy(alpha = 0.18f),
                            selectedLabelColor = SignalEmerald
                        ),
                        modifier = Modifier.testTag("quick_otp_country_${label.substringBefore(" ").lowercase()}")
                    )
                }
            }

            OutlinedTextField(
                value = recipient,
                onValueChange = { recipient = it.take(24) },
                label = { Text("Recipient Cell Phone (+92, +1, +44...)") },
                placeholder = { Text("+923001234567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                supportingText = {
                    Text(
                        text = "${carrier.countryCode} · ${carrier.carrierName} (~${carrier.avgLatencyMs}ms)",
                        color = TelemetryCyan
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("quick_otp_to_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it.take(40) },
                    label = { Text("Service / Brand") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_otp_service_input")
                )
            }

            if (activeNumbers.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "From:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    activeNumbers.forEach { num ->
                        FilterChip(
                            selected = selectedSender == num.e164Number,
                            onClick = { selectedSender = num.e164Number },
                            label = {
                                Text(
                                    text = "${num.countryCode} ${num.e164Number}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont)
                                )
                            }
                        )
                    }
                }
            }

            // Code length & Expiry controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(4, 6, 8).forEach { len ->
                    FilterChip(
                        selected = codeLength == len,
                        onClick = { codeLength = len },
                        label = { Text("$len-digit") }
                    )
                }
                Spacer(Modifier.width(4.dp))
                listOf(120 to "2m", 300 to "5m", 600 to "10m").forEach { (sec, label) ->
                    FilterChip(
                        selected = ttlSeconds == sec,
                        onClick = { ttlSeconds = sec },
                        label = { Text("TTL $label") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onClearOtpCheck()
                        onSendOtp(recipient, selectedSender, serviceName, codeLength, OtpChannel.SMS, ttlSeconds)
                    },
                    enabled = !isBusy && otpServiceEnabled && canSendOtp && recipient.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("quick_send_otp_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sms,
                        contentDescription = null,
                        tint = Color(0xFF061510),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (!otpServiceEnabled) "Service Paused" else "Send OTP SMS Now",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF061510),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = {
                        val curl = """
curl -X POST http://127.0.0.1:$serverPort/v1/verify/send \
  -H "Authorization: Bearer $activeKey" \
  -H "Content-Type: application/json" \
  -d '{"to":"$recipient","from":"$selectedSender","service":"$serviceName","length":$codeLength,"ttl_seconds":$ttlSeconds}'
                        """.trimIndent()
                        clipboard.setText(AnnotatedString(curl))
                        onCopied("OTP API cURL copied")
                    },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("quick_copy_otp_curl")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy OTP API cURL",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("API", maxLines = 1)
                }
            }

            if (latestPendingOtp != null) {
                Surface(
                    color = Color(0xFF070B14),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f))
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
                                Text(
                                    text = "ACTIVE OTP -> ${latestPendingOtp.toNumber}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TelemetryCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "CODE: ${latestPendingOtp.code}",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFont,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = SignalEmerald
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(latestPendingOtp.code))
                                        verifyCodeInput = latestPendingOtp.code
                                        onCopied("OTP code ${latestPendingOtp.code} copied")
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy OTP code",
                                        tint = TelemetryCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onRevokeOtp(latestPendingOtp) },
                                    enabled = canSendOtp,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("quick_expire_otp_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = "Expire OTP",
                                        tint = PulseCrimson,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Expire", color = PulseCrimson, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = verifyCodeInput,
                                onValueChange = { verifyCodeInput = it.filter(Char::isDigit).take(8) },
                                label = { Text("Test / Verify Code") },
                                placeholder = { Text(latestPendingOtp.code) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_verify_code_input")
                            )
                            Button(
                                onClick = {
                                    onVerifyOtp(
                                        latestPendingOtp.toNumber,
                                        verifyCodeInput.ifBlank { latestPendingOtp.code }
                                    )
                                },
                                enabled = !isBusy,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("quick_verify_otp_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VerifiedUser,
                                    contentDescription = "Verify OTP",
                                    tint = Color(0xFF070B14),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Verify", color = Color(0xFF070B14))
                            }
                        }

                        lastOtpCheck?.let { chk ->
                            val statusColor = if (chk.valid) SignalEmerald else PulseCrimson
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${chk.status.name}: ${chk.message}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = statusColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
