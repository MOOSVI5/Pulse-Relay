package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.data.firebase.onGoogleSignInClicked
import com.example.data.model.GlobalCountryCatalog
import com.example.ui.components.GlobalMeshHeroCanvas
import com.example.ui.components.MonoCodeBlock
import com.example.ui.components.PulseBrandMark
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import kotlinx.coroutines.launch

private enum class LandingSnippetLang(val label: String) {
    CURL("cURL"),
    KOTLIN("Kotlin"),
    NODE("Node.js"),
    PYTHON("Python")
}

@Composable
fun LandingScreen(
    isAuthenticated: Boolean,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onEnterConsole: () -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSigningIn by remember { mutableStateOf(false) }
    var selectedLang by remember { mutableStateOf(LandingSnippetLang.CURL) }

    val triggerSignInOrEnter = {
        if (isAuthenticated) {
            onEnterConsole()
        } else {
            isSigningIn = true
            onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                scope = scope,
                onAuthSuccess = {
                    isSigningIn = false
                    onAuthSuccess()
                },
                onAuthError = { msg ->
                    isSigningIn = false
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
                onAuthCancelled = {
                    isSigningIn = false
                }
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 900.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Top Public Marketing Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseBrandMark(size = 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PulseRelay",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "SMS & OTP CLOUD",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                                    color = TelemetryCyan
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onToggleTheme,
                                modifier = Modifier.testTag("landing_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = triggerSignInOrEnter,
                                enabled = !isSigningIn,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag(
                                    if (isAuthenticated) "return_to_console_button" else "header_console_cta"
                                )
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (isAuthenticated) "Console" else "Sign In",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Hero Copy + Custom Canvas Global Mesh
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth >= 680.dp
                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                HeroIntroBlock(
                                    isAuthenticated = isAuthenticated,
                                    isSigningIn = isSigningIn,
                                    onPrimaryClick = triggerSignInOrEnter,
                                    onSecondaryClick = {
                                        scope.launch { scrollState.animateScrollTo(850) }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                GlobalMeshHeroCanvas(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(280.dp)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                HeroIntroBlock(
                                    isAuthenticated = isAuthenticated,
                                    isSigningIn = isSigningIn,
                                    onPrimaryClick = triggerSignInOrEnter,
                                    onSecondaryClick = {
                                        scope.launch { scrollState.animateScrollTo(780) }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                GlobalMeshHeroCanvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(235.dp)
                                )
                            }
                        }
                    }

                    // Global Carrier Reach Strip
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DIRECT GLOBAL CARRIER ROUTES",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            GlobalCountryCatalog.countries.take(8).forEach { country ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = country.countryCode,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFamily = JetBrainsMonoFont,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = TelemetryCyan
                                        )
                                        Text(
                                            text = "${country.dialPrefix} · ${country.carrierName}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${country.avgLatencyMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SignalEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Feature Pillars Grid
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Simple. Unique. Fast. Direct.",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Everything you expect from Twilio — re-engineered with flat-rate Unlimited Texts, one-tap virtual numbers, and an instant REST API for any app or website.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FeaturePillarCard(
                                    icon = Icons.Rounded.AllInclusive,
                                    accent = PulseCrimson,
                                    title = "Unlimited Texts",
                                    body = "Send transactional alerts & OTPs globally with $0.00 overage surprises.",
                                    modifier = Modifier.weight(1f)
                                )
                                FeaturePillarCard(
                                    icon = Icons.Rounded.Lock,
                                    accent = SignalEmerald,
                                    title = "Global OTP Verify",
                                    body = "4–8 digit SecureRandom codes, TTL expiry & anti-brute-force lockouts.",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FeaturePillarCard(
                                    icon = Icons.Rounded.Public,
                                    accent = TelemetryCyan,
                                    title = "Instant Numbers",
                                    body = "Provision Local, Toll-Free, Short Code & Alpha IDs across 195 countries.",
                                    modifier = Modifier.weight(1f)
                                )
                                FeaturePillarCard(
                                    icon = Icons.Rounded.Bolt,
                                    accent = Color(0xFFF59E0B),
                                    title = "Real HTTP API",
                                    body = "Call POST /v1/messages & /v1/verify/send from any backend or frontend.",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Interactive 3-Line Code Showcase
                    Surface(
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        tint = TelemetryCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Integrate in 30 Seconds",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                StatusPill(text = "REST v1", color = TelemetryCyan)
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LandingSnippetLang.entries.forEach { lang ->
                                    FilterChip(
                                        selected = selectedLang == lang,
                                        onClick = { selectedLang = lang },
                                        label = {
                                            Text(
                                                lang.label,
                                                style = MaterialTheme.typography.labelMedium
                                            )
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PulseCrimson.copy(alpha = 0.2f),
                                            selectedLabelColor = PulseCrimson
                                        )
                                    )
                                }
                            }

                            MonoCodeBlock(
                                title = "SEND GLOBAL OTP · ${selectedLang.label.uppercase()}",
                                code = landingSnippetFor(selectedLang),
                                onCopied = {
                                    scope.launch { snackbarHostState.showSnackbar("Snippet copied to clipboard") }
                                }
                            )
                        }
                    }

                    // Transparent Pricing Tiers
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Simple Global Pricing",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        PricingCard(
                            badge = "MOST POPULAR",
                            title = "Unlimited Direct",
                            price = "$29",
                            cadence = "/ month flat",
                            accent = PulseCrimson,
                            perks = listOf(
                                "Unlimited global SMS & OTPs across 195+ countries",
                                "Zero per-segment fees & included virtual numbers",
                                "Direct Tier-1 carrier routing + Webhook callbacks",
                                "Full REST API keys, live logs & Verify engine"
                            ),
                            ctaLabel = if (isAuthenticated) "Open Unlimited Console" else "Start Unlimited with Google",
                            isBusy = isSigningIn,
                            onAction = triggerSignInOrEnter,
                            testTag = "pricing_unlimited_cta"
                        )

                        PricingCard(
                            badge = "METERED",
                            title = "Pay-As-You-Go",
                            price = "$0.0075",
                            cadence = "/ SMS segment",
                            accent = TelemetryCyan,
                            perks = listOf(
                                "Zero monthly commitment — pay only per delivered segment",
                                "Same global carrier mesh & sub-150ms OTP latency",
                                "Full API compatibility with Twilio-style payloads"
                            ),
                            ctaLabel = if (isAuthenticated) "Switch in Console" else "Launch Pay-As-You-Go",
                            isBusy = isSigningIn,
                            onAction = triggerSignInOrEnter,
                            testTag = "pricing_payg_cta"
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun HeroIntroBlock(
    isAuthenticated: Boolean,
    isSigningIn: Boolean,
    onPrimaryClick: () -> Unit,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusPill(
            text = "TWILIO-COMPATIBLE API · UNLIMITED OPTION",
            color = PulseCrimson
        )
        Text(
            text = "Global SMS & OTP.\nSimple. Fast. Direct.",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Send verification codes and unlimited text messages to 195+ countries from any app or website. Manage API keys, numbers, logs, and live HTTP endpoints in one console.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onPrimaryClick,
                enabled = !isSigningIn,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("sign_in_google_button")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Connecting...", color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isAuthenticated) "Open Console" else "Sign in with Google",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            OutlinedButton(
                onClick = onSecondaryClick,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                modifier = Modifier.testTag("view_api_docs_button")
            ) {
                Text(
                    text = "API Docs",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun FeaturePillarCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PricingCard(
    badge: String,
    title: String,
    price: String,
    cadence: String,
    accent: Color,
    perks: List<String>,
    ctaLabel: String,
    isBusy: Boolean,
    onAction: () -> Unit,
    testTag: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusPill(text = badge, color = accent)
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontFamily = JetBrainsMonoFont
                    ),
                    color = accent
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = cadence,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 5.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                perks.forEach { perk ->
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = SignalEmerald,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = perk,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Button(
                onClick = onAction,
                enabled = !isBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text(
                    text = ctaLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (accent == TelemetryCyan) Color(0xFF070B14) else Color.White
                )
            }
        }
    }
}

private fun landingSnippetFor(lang: LandingSnippetLang): String = when (lang) {
    LandingSnippetLang.CURL -> """
curl -X POST http://127.0.0.1:8765/v1/verify/send \
  -H "Authorization: Bearer pr_live_your_key" \
  -H "Content-Type: application/json" \
  -d '{"to":"+447700900412","length":6,"service":"MyApp"}'
    """.trimIndent()

    LandingSnippetLang.KOTLIN -> """
val req = Request.Builder()
  .url("http://127.0.0.1:8765/v1/messages")
  .header("Authorization", "Bearer pr_live_your_key")
  .post("{\"to\":\"+14155550199\",\"body\":\"Hi from PulseRelay!\"}".toRequestBody())
  .build()
client.newCall(req).execute()
    """.trimIndent()

    LandingSnippetLang.NODE -> """
await fetch("http://127.0.0.1:8765/v1/verify/send", {
  method: "POST",
  headers: {
    "Authorization": "Bearer pr_live_your_key",
    "Content-Type": "application/json"
  },
  body: JSON.stringify({ to: "+819044128890", length: 6 })
});
    """.trimIndent()

    LandingSnippetLang.PYTHON -> """
import requests
requests.post(
  "http://127.0.0.1:8765/v1/messages",
  headers={"Authorization": "Bearer pr_live_your_key"},
  json={"to": "+4915123456789", "body": "Unlimited direct SMS!"}
)
    """.trimIndent()
}
