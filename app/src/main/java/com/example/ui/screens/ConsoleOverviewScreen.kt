package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MessageStatus
import com.example.data.model.OtpStatus
import com.example.data.model.PlanTier
import com.example.data.model.SmsMessageRecord
import com.example.ui.components.KpiMetricTile
import com.example.ui.components.StatusPill
import com.example.ui.components.ThroughputSparklineCanvas
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ConsoleDashboardData

@Composable
fun ConsoleOverviewScreen(
    data: ConsoleDashboardData,
    isServerRunning: Boolean,
    serverPort: Int,
    onToggleServer: (Boolean) -> Unit,
    onNavigateToSendSms: () -> Unit,
    onNavigateToOtp: () -> Unit,
    onNavigateToNumbers: () -> Unit,
    onNavigateToApi: () -> Unit,
    onCopied: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val settings = data.settings
    val planTier = PlanTier.fromRaw(settings?.planTier)
    val deliveredCount = data.smsMessages.count { MessageStatus.fromRaw(it.status) == MessageStatus.DELIVERED }
    val deliveryRate = if (data.smsMessages.isEmpty()) 100 else ((deliveredCount * 100f) / data.smsMessages.size).toInt()
    val avgLatency = data.smsMessages.mapNotNull { it.latencyMs.takeIf { l -> l > 0 } }
        .average()
        .takeIf { !it.isNaN() }
        ?.toInt() ?: 112
    val verifiedOtps = data.otpVerifications.count { OtpStatus.fromRaw(it.status) == OtpStatus.APPROVED }

    val sparklineSeries = if (data.smsMessages.size >= 4) {
        data.smsMessages.take(10).reversed().map { it.latencyMs.coerceAtLeast(40L).toFloat() }
    } else {
        listOf(128f, 118f, 122f, 105f, 114f, 98f, avgLatency.toFloat())
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live API Engine & Account Header Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isServerRunning) SignalEmerald else WarningAmber)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (isServerRunning) "HTTP REST ENGINE LIVE" else "HTTP REST ENGINE PAUSED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isServerRunning) SignalEmerald else WarningAmber
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "http://127.0.0.1:$serverPort/v1",
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFont),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = onToggleServer,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = SignalEmerald
                            ),
                            modifier = Modifier.testTag("overview_server_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "SID: ${settings?.accountSid?.take(14) ?: "AC..."}...",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            IconButton(
                                onClick = {
                                    val sid = settings?.accountSid.orEmpty()
                                    if (sid.isNotBlank()) {
                                        clipboard.setText(AnnotatedString(sid))
                                        onCopied("Account SID copied")
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = "Copy Account SID",
                                    tint = TelemetryCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }

                        StatusPill(
                            text = planTier.displayName.uppercase(),
                            color = if (planTier == PlanTier.UNLIMITED_DIRECT) PulseCrimson else TelemetryCyan
                        )
                    }
                }
            }
        }

        // 2x2 KPI Grid
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricTile(
                        label = "Messages Sent",
                        value = "${(settings?.totalMessagesSent ?: data.smsMessages.size.toLong())}",
                        sublabel = if (planTier == PlanTier.UNLIMITED_DIRECT) "Unlimited · $0.00" else "Metered route",
                        accent = PulseCrimson,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricTile(
                        label = "Delivery Rate",
                        value = "$deliveryRate%",
                        sublabel = "Avg ${avgLatency}ms global",
                        accent = SignalEmerald,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiMetricTile(
                        label = "OTP Verified",
                        value = "${(settings?.totalOtpVerified ?: verifiedOtps.toLong())}",
                        sublabel = "${data.otpVerifications.count { it.status == "PENDING" }} pending now",
                        accent = TelemetryCyan,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMetricTile(
                        label = "Active Numbers",
                        value = "${data.phoneNumbers.size}",
                        sublabel = "${data.apiKeys.count { it.active }} live API keys",
                        accent = WarningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Carrier Latency & Routing Sparkline Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Global Carrier Latency",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Direct Tier-1 SMS & OTP handoff speed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${avgLatency}ms",
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFont),
                            color = TelemetryCyan
                        )
                    }

                    ThroughputSparklineCanvas(
                        values = sparklineSeries,
                        accentColor = TelemetryCyan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(82.dp)
                    )
                }
            }
        }

        // Quick Actions Row
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "QUICK ACTIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionTile(
                        icon = Icons.AutoMirrored.Rounded.Send,
                        label = "Send SMS",
                        accent = PulseCrimson,
                        onClick = onNavigateToSendSms,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Lock,
                        label = "Send OTP",
                        accent = SignalEmerald,
                        onClick = onNavigateToOtp,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Public,
                        label = "Numbers",
                        accent = TelemetryCyan,
                        onClick = onNavigateToNumbers,
                        modifier = Modifier.weight(1f)
                    )
                    QuickActionTile(
                        icon = Icons.Rounded.Api,
                        label = "Call API",
                        accent = WarningAmber,
                        onClick = onNavigateToApi,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Recent Live Traffic
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT DISPATCH STREAM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${data.smsMessages.size} total",
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
            }
        }

        if (data.smsMessages.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Dialpad,
                            contentDescription = null,
                            tint = TelemetryCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No messages dispatched yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send your first global SMS or OTP challenge to watch real-time carrier telemetry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onNavigateToSendSms,
                            colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
                        ) {
                            Text("Send First SMS", color = Color.White)
                        }
                    }
                }
            }
        } else {
            items(data.smsMessages.take(6), key = { it.id.ifBlank { it.sid } }) { msg ->
                CompactMessageRow(
                    msg = msg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp)
                        .clickable { onNavigateToSendSms() }
                )
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompactMessageRow(
    msg: SmsMessageRecord,
    modifier: Modifier = Modifier
) {
    val status = MessageStatus.fromRaw(msg.status)
    val statusColor = when (status) {
        MessageStatus.DELIVERED -> SignalEmerald
        MessageStatus.SENT, MessageStatus.SENDING -> TelemetryCyan
        MessageStatus.QUEUED -> WarningAmber
        MessageStatus.FAILED -> PulseCrimson
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    StatusPill(text = msg.countryCode, color = TelemetryCyan)
                    Text(
                        text = msg.toNumber,
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFont),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(text = status.name, color = statusColor)
            }
            Text(
                text = msg.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${msg.carrierName} · ${msg.segments} seg",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (msg.latencyMs > 0) "${msg.latencyMs}ms · $${String.format("%.4f", msg.priceUsd)}" else "Routing...",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = SignalEmerald
                )
            }
        }
    }
}
