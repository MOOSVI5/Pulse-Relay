package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Api
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.firebase.signOutUser
import com.example.data.model.TeamRole
import com.example.ui.components.PulseBrandMark
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.viewmodel.PulseConsoleViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.coroutines.launch

enum class ConsoleTab(val label: String, val icon: ImageVector, val testTag: String) {
    OVERVIEW("Overview", Icons.Rounded.Dashboard, "nav_overview"),
    MESSAGING("SMS & OTP", Icons.AutoMirrored.Rounded.Send, "nav_messaging"),
    NUMBERS_KEYS("Numbers & Keys", Icons.Rounded.Key, "nav_numbers_keys"),
    API("API", Icons.Rounded.Api, "nav_api")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleScaffold(
    viewModel: PulseConsoleViewModel,
    userEmail: String?,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onShowPublicSite: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
    val serverPort by viewModel.serverPort.collectAsStateWithLifecycle()
    val apiRequestLogs by viewModel.apiRequestLogs.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()
    val lastOtpCheck by viewModel.lastOtpCheck.collectAsStateWithLifecycle()
    val lastApiResult by viewModel.lastApiResult.collectAsStateWithLifecycle()
    val firebaseLatencyMs by viewModel.firebaseLatencyMs.collectAsStateWithLifecycle()
    val fetchedRepos by viewModel.fetchedGitHubRepos.collectAsStateWithLifecycle()
    val latestRelease by viewModel.latestRelease.collectAsStateWithLifecycle()
    val activeProfileId by viewModel.activeProfileId.collectAsStateWithLifecycle()
    val otpServiceEnabled by viewModel.otpServiceEnabled.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(ConsoleTab.OVERVIEW) }
    var messagingSubTab by remember { mutableStateOf(MessagingSubTab.SEND_SMS) }
    var showProfileSwitcher by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val loadedData = (dashboardState as? UiState.Success)?.data
    val activeProfile = loadedData?.teamProfiles?.firstOrNull { it.id == activeProfileId }
        ?: loadedData?.teamProfiles?.firstOrNull { it.ownerAdmin }
        ?: loadedData?.teamProfiles?.firstOrNull()
    val activeRole = TeamRole.fromRaw(activeProfile?.role)

    LaunchedEffect(toastMessage) {
        val msg = toastMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val notifyCopied: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.navigationBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PulseBrandMark(size = 28.dp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PulseRelay",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${activeProfile?.displayName ?: "Admin"} · ${activeRole.label.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = if (activeRole == TeamRole.ADMIN) SignalEmerald else TelemetryCyan,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (loadedData != null && loadedData.teamProfiles.isNotEmpty()) {
                            Surface(
                                onClick = { showProfileSwitcher = true },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                color = TelemetryCyan.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .padding(end = 2.dp)
                                    .testTag("topbar_profile_switcher")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SwapHoriz,
                                        contentDescription = "Switch profile",
                                        tint = TelemetryCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        text = activeProfile?.displayName ?: "Admin",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TelemetryCyan,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        TextButton(
                            onClick = onShowPublicSite,
                            modifier = Modifier.testTag("console_public_site_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Public,
                                contentDescription = "View public site",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Site")
                        }
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Toggle theme"
                            )
                        }
                        IconButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                    onBeforeSignOut = { viewModel.stopServerForSignOut() }
                                )
                            },
                            modifier = Modifier.testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                                tint = PulseCrimson
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        ConsoleTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        ConsoleTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = { activeTab = tab },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = PulseCrimson)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Syncing PulseRelay Console...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Sync Interrupted",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = PulseCrimson
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Success -> {
                            val data = state.data
                            when (activeTab) {
                                ConsoleTab.OVERVIEW -> ConsoleOverviewScreen(
                                    data = data,
                                    activeProfile = activeProfile,
                                    otpServiceEnabled = otpServiceEnabled,
                                    isServerRunning = isServerRunning,
                                    serverPort = serverPort,
                                    isBusy = isBusy,
                                    lastOtpCheck = lastOtpCheck,
                                    onToggleServer = { viewModel.toggleApiServer(it) },
                                    onToggleOtpService = { viewModel.toggleOtpService(it) },
                                    onSendQuickOtp = { to, from, svc, len, ch, ttl ->
                                        viewModel.sendOtpFromConsole(to, from, svc, len, ch, data, ttl)
                                    },
                                    onVerifyQuickOtp = { to, code ->
                                        viewModel.verifyOtpFromConsole(to, code, data)
                                    },
                                    onRevokeOtp = { otp -> viewModel.revokeOtpChallenge(otp) },
                                    onClearOtpCheck = { viewModel.clearOtpCheck() },
                                    onOpenProfileSwitcher = { showProfileSwitcher = true },
                                    onSwitchToAdmin = {
                                        data.teamProfiles.firstOrNull { it.ownerAdmin }?.let {
                                            viewModel.switchActiveProfile(it)
                                        }
                                    },
                                    onCreateTeamMember = { name, contact, role, limit ->
                                        viewModel.createTeamMember(name, contact, role, limit)
                                    },
                                    onUpdateTeamMember = { profile, name, contact, role, active, limit ->
                                        viewModel.updateTeamMember(profile, name, contact, role, active, limit)
                                    },
                                    onDeleteTeamMember = { profile ->
                                        viewModel.deleteTeamMember(profile)
                                    },
                                    onNavigateToSendSms = {
                                        messagingSubTab = MessagingSubTab.SEND_SMS
                                        activeTab = ConsoleTab.MESSAGING
                                    },
                                    onNavigateToOtp = {
                                        messagingSubTab = MessagingSubTab.OTP_VERIFY
                                        activeTab = ConsoleTab.MESSAGING
                                    },
                                    onNavigateToNumbers = { activeTab = ConsoleTab.NUMBERS_KEYS },
                                    onNavigateToApi = { activeTab = ConsoleTab.API },
                                    onCopied = notifyCopied
                                )

                                ConsoleTab.MESSAGING -> MessagingVerifyScreen(
                                    data = data,
                                    initialSubTab = messagingSubTab,
                                    isBusy = isBusy,
                                    lastOtpCheck = lastOtpCheck,
                                    calculateSegments = { viewModel.calculateSegments(it) },
                                    estimatePrice = { segs, tier, to -> viewModel.estimatePrice(segs, tier, to) },
                                    onSendSms = { to, from, body ->
                                        viewModel.sendSmsFromConsole(to, from, body, data)
                                    },
                                    onSendOtp = { to, from, svc, len, ch ->
                                        viewModel.sendOtpFromConsole(to, from, svc, len, ch, data)
                                    },
                                    onCheckOtp = { to, code ->
                                        viewModel.verifyOtpFromConsole(to, code, data)
                                    },
                                    onRevokeOtp = { otp ->
                                        viewModel.revokeOtpChallenge(otp)
                                    },
                                    onClearOtpCheck = { viewModel.clearOtpCheck() }
                                )

                                ConsoleTab.NUMBERS_KEYS -> NumbersKeysScreen(
                                    data = data,
                                    isBusy = isBusy,
                                    onProvisionNumber = { country, type, alpha, tier ->
                                        viewModel.provisionGlobalNumber(country, type, alpha, tier)
                                    },
                                    onUpdateNumber = { num, webhook, status, setDefault ->
                                        viewModel.updateNumberConfig(num, webhook, status, setDefault, data.phoneNumbers)
                                    },
                                    onReleaseNumber = { viewModel.releaseNumber(it) },
                                    onCreateApiKey = { label, env, scopes ->
                                        viewModel.createApiKey(label, env, scopes)
                                    },
                                    onToggleApiKey = { id, active ->
                                        viewModel.toggleApiKey(id, active)
                                    },
                                    onDeleteApiKey = { viewModel.deleteApiKey(it) },
                                    onCopied = notifyCopied
                                )

                                ConsoleTab.API -> ApiPlaygroundScreen(
                                    data = data,
                                    userUid = viewModel.currentUserId,
                                    userEmail = userEmail,
                                    isServerRunning = isServerRunning,
                                    serverPort = serverPort,
                                    isBusy = isBusy,
                                    lastApiResult = lastApiResult,
                                    requestLogs = apiRequestLogs,
                                    firebaseLatencyMs = firebaseLatencyMs,
                                    fetchedRepos = fetchedRepos,
                                    latestRelease = latestRelease,
                                    onToggleServer = { viewModel.toggleApiServer(it) },
                                    onSavePlanSettings = { tier, direct, webhook ->
                                        viewModel.updateAccountSettings(tier, direct, webhook)
                                    },
                                    onExecuteHttpRequest = { method, path, secret, body ->
                                        viewModel.executeLiveHttpApiCall(method, path, secret, body)
                                    },
                                    onVerifyFirebase = { viewModel.verifyFirebaseSync() },
                                    onFetchRepos = { user, pat -> viewModel.fetchGitHubRepos(user, pat) },
                                    onLinkRepo = { user, repo, to, mode, pat ->
                                        viewModel.linkGitHubRepo(user, repo, to, mode, pat)
                                    },
                                    onTriggerCommitAlert = { item, pat ->
                                        viewModel.triggerGitHubAlert(item, pat, data)
                                    },
                                    onInstallWebhook = { item, pat, url ->
                                        viewModel.installGitHubWebhook(item, pat, url)
                                    },
                                    onToggleGitHubIntegration = { item, active ->
                                        viewModel.toggleGitHubIntegration(item, active)
                                    },
                                    onDeleteGitHubIntegration = { id ->
                                        viewModel.deleteGitHubIntegration(id)
                                    },
                                    onCheckAppUpdate = { repo, pat ->
                                        viewModel.checkAppUpdate(repo, pat)
                                    },
                                    onCopied = notifyCopied
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showProfileSwitcher && loadedData != null) {
            ProfileSwitcherDialog(
                profiles = loadedData.teamProfiles,
                activeProfileId = activeProfile?.id ?: activeProfileId,
                onSelectProfile = { profile -> viewModel.switchActiveProfile(profile) },
                onDismiss = { showProfileSwitcher = false }
            )
        }
    }
}
