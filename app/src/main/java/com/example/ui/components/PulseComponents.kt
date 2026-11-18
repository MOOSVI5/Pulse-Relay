package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PulseBrandMark(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(PulseCrimson, Color(0xFFFF5F73))
            ),
            radius = w * 0.46f,
            center = Offset(w / 2f, h / 2f)
        )

        val pulsePath = Path().apply {
            moveTo(w * 0.22f, h * 0.54f)
            lineTo(w * 0.36f, h * 0.54f)
            lineTo(w * 0.44f, h * 0.30f)
            lineTo(w * 0.56f, h * 0.70f)
            lineTo(w * 0.64f, h * 0.46f)
            lineTo(w * 0.78f, h * 0.46f)
        }
        drawPath(
            path = pulsePath,
            color = Color.White,
            style = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        )

        drawCircle(
            color = TelemetryCyan,
            radius = w * 0.08f,
            center = Offset(w * 0.78f, h * 0.26f)
        )
    }
}

private data class RelayHubNode(
    val code: String,
    val xFrac: Float,
    val yFrac: Float,
    val color: Color
)

/**
 * Hand-crafted animated Canvas hero visualization showing global SMS & OTP packet routing
 * across Tier-1 carrier hubs without external chart/map libraries.
 */
@Composable
fun GlobalMeshHeroCanvas(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "mesh_hero")
    val packetPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "packet_phase"
    )
    val pulsePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_phase"
    )

    val hubs = listOf(
        RelayHubNode("SFO", 0.14f, 0.42f, TelemetryCyan),
        RelayHubNode("NYC", 0.30f, 0.34f, PulseCrimson),
        RelayHubNode("SAO", 0.34f, 0.74f, SignalEmerald),
        RelayHubNode("LON", 0.50f, 0.26f, TelemetryCyan),
        RelayHubNode("FRA", 0.58f, 0.40f, PulseCrimson),
        RelayHubNode("DXB", 0.67f, 0.56f, SignalEmerald),
        RelayHubNode("SIN", 0.79f, 0.66f, TelemetryCyan),
        RelayHubNode("TYO", 0.88f, 0.34f, PulseCrimson)
    )

    val routes = listOf(
        0 to 1, 1 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 1 to 2, 3 to 5, 0 to 3
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0A1020), Color(0xFF10192E), Color(0xFF1A1022))
                )
            )
            .border(1.dp, Color(0xFF283552), RoundedCornerShape(22.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Perspective telemetry grid
            val cols = 12
            val rows = 6
            for (i in 1 until cols) {
                val x = w * i / cols
                drawLine(
                    color = Color(0x1694A3B8),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }
            for (j in 1 until rows) {
                val y = h * j / rows
                drawLine(
                    color = Color(0x1694A3B8),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1f
                )
            }

            // Soft radial glow behind primary hub
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x33F22F46), Color.Transparent),
                    center = Offset(w * 0.50f, h * 0.40f),
                    radius = w * 0.42f
                ),
                center = Offset(w * 0.50f, h * 0.40f),
                radius = w * 0.42f
            )

            // Draw quadratic Bezier routes & animated packets
            routes.forEachIndexed { idx, (aIdx, bIdx) ->
                val a = Offset(w * hubs[aIdx].xFrac, h * hubs[aIdx].yFrac)
                val b = Offset(w * hubs[bIdx].xFrac, h * hubs[bIdx].yFrac)
                val midX = (a.x + b.x) / 2f
                val arcLift = if (idx % 2 == 0) -h * 0.16f else h * 0.12f
                val ctrl = Offset(midX, ((a.y + b.y) / 2f + arcLift).coerceIn(16f, h - 16f))

                val path = Path().apply {
                    moveTo(a.x, a.y)
                    quadraticTo(ctrl.x, ctrl.y, b.x, b.y)
                }

                drawPath(
                    path = path,
                    color = Color(0x4000D1FF),
                    style = Stroke(
                        width = 2.2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                )

                // Compute animated packet along quadratic Bezier
                val t = (packetPhase + idx * 0.14f) % 1f
                val inv = 1f - t
                val px = inv * inv * a.x + 2 * inv * t * ctrl.x + t * t * b.x
                val py = inv * inv * a.y + 2 * inv * t * ctrl.y + t * t * b.y
                val packetColor = if (idx % 2 == 0) TelemetryCyan else PulseCrimson

                drawCircle(
                    color = packetColor.copy(alpha = 0.30f),
                    radius = 10f,
                    center = Offset(px, py)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(px, py)
                )
            }

            // Draw global carrier hubs with pulsing wave ring
            hubs.forEachIndexed { idx, hub ->
                val pos = Offset(w * hub.xFrac, h * hub.yFrac)
                val ringProgress = (pulsePhase + idx * 0.12f) % 1f
                drawCircle(
                    color = hub.color.copy(alpha = (1f - ringProgress) * 0.45f),
                    radius = 8f + ringProgress * 22f,
                    center = pos,
                    style = Stroke(width = 2f)
                )
                drawCircle(
                    color = Color(0xFF070B14),
                    radius = 8f,
                    center = pos
                )
                drawCircle(
                    color = hub.color,
                    radius = 5.5f,
                    center = pos
                )
            }
        }

        // Floating HUD Telemetry Chips overlaid on Canvas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopStart),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xCC070B14),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFF283552))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(SignalEmerald)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "GLOBAL MESH · 195 COUNTRIES",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF8FAFC)
                    )
                }
            }

            Surface(
                color = Color(0xCC070B14),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, PulseCrimson.copy(alpha = 0.5f))
            ) {
                Text(
                    text = "UNLIMITED DIRECT",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = PulseCrimson
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.BottomStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HeroStatChip(label = "DELIVERY", value = "99.98%", accent = SignalEmerald)
            HeroStatChip(label = "MEDIAN OTP", value = "112ms", accent = TelemetryCyan)
            HeroStatChip(label = "TEXT CAP", value = "UNLIMITED", accent = PulseCrimson)
        }
    }
}

@Composable
private fun HeroStatChip(label: String, value: String, accent: Color) {
    Surface(
        color = Color(0xD90B1222),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFF263248))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = Color(0xFF94A3B8)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold
                ),
                color = accent
            )
        }
    }
}

@Composable
fun ThroughputSparklineCanvas(
    values: List<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val points = if (values.size >= 2) values else listOf(12f, 24f, 18f, 34f, 29f, 44f, 52f)
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val maxVal = (points.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val minVal = 0f
        val stepX = w / (points.size - 1).coerceAtLeast(1)

        val linePath = Path()
        val fillPath = Path()

        points.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = ((v - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
            val y = h - (norm * h * 0.78f) - (h * 0.1f)
            if (idx == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                val prevX = (idx - 1) * stepX
                val prevNorm = ((points[idx - 1] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val prevY = h - (prevNorm * h * 0.78f) - (h * 0.1f)
                val cx = (prevX + x) / 2f
                linePath.cubicTo(cx, prevY, cx, y, x, y)
                fillPath.cubicTo(cx, prevY, cx, y, x, y)
            }
            if (idx == points.lastIndex) {
                fillPath.lineTo(x, h)
                fillPath.close()
            }
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = linePath,
            color = accentColor,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
        )

        // Highlight last point
        val lastNorm = ((points.last() - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
        val lastY = h - (lastNorm * h * 0.78f) - (h * 0.1f)
        drawCircle(color = accentColor.copy(alpha = 0.3f), radius = 12f, center = Offset(w, lastY))
        drawCircle(color = Color.White, radius = 5f, center = Offset(w, lastY))
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MonoCodeBlock(
    title: String,
    code: String,
    onCopied: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF060A13),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelemetryCyan
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        onCopied?.invoke()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy $title",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = JetBrainsMonoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp
                    ),
                    color = Color(0xFFE2E8F0)
                )
            }
        }
    }
}

@Composable
fun KpiMetricTile(
    label: String,
    value: String,
    sublabel: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = JetBrainsMonoFont,
                    fontWeight = FontWeight.Bold
                ),
                color = accent,
                maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun waveOffset(index: Int, phase: Float): Float {
    return sin((phase * 2f * PI + index * 0.6f).toDouble()).toFloat()
}
