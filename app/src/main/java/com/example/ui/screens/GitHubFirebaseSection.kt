package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Webhook
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.model.GitHubIntegrationRecord
import com.example.data.model.GitHubTriggerMode
import com.example.engine.GitHubReleaseInfo
import com.example.engine.GitHubRepoSummary
import com.example.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber
import com.example.ui.viewmodel.ConsoleDashboardData

@Composable
fun GitHubFirebaseSection(
    data: ConsoleDashboardData,
    userUid: String,
    userEmail: String?,
    isBusy: Boolean,
    firebaseLatencyMs: Long?,
    fetchedRepos: List<GitHubRepoSummary>,
    latestRelease: GitHubReleaseInfo?,
    onVerifyFirebase: () -> Unit,
    onFetchRepos: (username: String, pat: String) -> Unit,
    onLinkRepo: (username: String, repoFullName: String, toNumber: String, mode: GitHubTriggerMode, pat: String) -> Unit,
    onTriggerCommitAlert: (GitHubIntegrationRecord, String) -> Unit,
    onInstallWebhook: (GitHubIntegrationRecord, String, String) -> Unit,
    onToggleIntegration: (GitHubIntegrationRecord, Boolean) -> Unit,
    onDeleteIntegration: (String) -> Unit,
    onCheckAppUpdate: (repoFullName: String, pat: String) -> Unit,
    onCopied: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val databaseId = remember(context) { context.getString(R.string.firestore_database_id) }

    var githubUsername by remember { mutableStateOf("MOOSVI5") }
    var githubPat by remember { mutableStateOf("") }
    var selectedRepo by remember(fetchedRepos) {
        mutableStateOf(fetchedRepos.firstOrNull()?.fullName ?: "MOOSVI5/Pulse-Relay")
    }
    var updateRepoSlug by remember { mutableStateOf("MOOSVI5/Pulse-Relay") }
    var notifyToNumber by remember { mutableStateOf("+14155550199") }
    var triggerMode by remember { mutableStateOf(GitHubTriggerMode.COMMIT_SMS) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Live Firebase Cloud Status Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.45f))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = SignalEmerald)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Firebase Firestore & Auth Connected",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = userEmail ?: userUid,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            text = if (firebaseLatencyMs != null) "${firebaseLatencyMs}ms LIVE" else "SYNCED",
                            color = SignalEmerald
                        )
                    }

                    Text(
                        text = "DB: $databaseId",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                        color = TelemetryCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Auth UID: $userUid · Collections: ${data.apiKeys.size} keys, ${data.phoneNumbers.size} numbers, ${data.smsMessages.size} SMS, ${data.otpVerifications.size} OTPs, ${data.githubIntegrations.size} GitHub hooks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedButton(
                        onClick = onVerifyFirebase,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("verify_firebase_sync_button")
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Verify Live Firestore Ping")
                    }
                }
            }
        }

        // 1b. App Updates & Permanent APK Link (GitHub Releases)
        item {
            val permanentReleaseUrl = "https://github.com/${updateRepoSlug.trim()}/releases/latest"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = WarningAmber)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "App Updates (GitHub Releases)",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Installed: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            text = latestRelease?.releaseName ?: "AUTO-RELEASE",
                            color = if (latestRelease != null) SignalEmerald else WarningAmber
                        )
                    }

                    OutlinedTextField(
                        value = updateRepoSlug,
                        onValueChange = { updateRepoSlug = it },
                        label = { Text("App Release Repo (owner/repo)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_repo_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCheckAppUpdate(updateRepoSlug, githubPat) },
                            enabled = !isBusy && updateRepoSlug.contains("/"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("check_app_update_button")
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color(0xFF1A1200))
                            Spacer(Modifier.width(6.dp))
                            Text("Check Update", color = Color(0xFF1A1200))
                        }

                        OutlinedButton(
                            onClick = {
                                val targetUrl = latestRelease?.apkDownloadUrl
                                    ?: latestRelease?.htmlUrl?.takeIf { it.isNotBlank() }
                                    ?: permanentReleaseUrl
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)))
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("open_apk_release_button")
                        ) {
                            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (latestRelease?.apkDownloadUrl != null) "Download APK" else "Open Releases")
                        }

                        IconButton(
                            onClick = {
                                val shareUrl = latestRelease?.apkDownloadUrl ?: permanentReleaseUrl
                                clipboard.setText(AnnotatedString(shareUrl))
                                onCopied("Permanent update link copied")
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy permanent APK link",
                                tint = TelemetryCyan
                            )
                        }
                    }

                    if (latestRelease != null) {
                        Text(
                            text = "Latest on GitHub: ${latestRelease.releaseName} (${latestRelease.tagName})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFont),
                            color = SignalEmerald
                        )
                    }
                }
            }
        }

        // 2. Connect GitHub Account & Fetch Repositories
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Code, contentDescription = null, tint = TelemetryCyan)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "GitHub Repository Connect",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        StatusPill(text = "REST v3", color = TelemetryCyan)
                    }

                    Text(
                        text = "Load public repos by username or enter a Personal Access Token for private repos & webhook installation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = githubUsername,
                        onValueChange = { githubUsername = it },
                        label = { Text("GitHub Username") },
                        placeholder = { Text("octocat") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_username_input")
                    )

                    OutlinedTextField(
                        value = githubPat,
                        onValueChange = { githubPat = it },
                        label = { Text("Personal Access Token (optional)") },
                        placeholder = { Text("ghp_... for private repos & webhooks") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("github_pat_input")
                    )

                    Button(
                        onClick = { onFetchRepos(githubUsername, githubPat) },
                        enabled = !isBusy && (githubUsername.isNotBlank() || githubPat.isNotBlank()),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("fetch_github_repos_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = Color(0xFF070B14))
                        Spacer(Modifier.width(6.dp))
                        Text("Load GitHub Repositories", color = Color(0xFF070B14))
                    }

                    if (fetchedRepos.isNotEmpty()) {
                        Text(
                            text = "Select Repository (${fetchedRepos.size} loaded)",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            fetchedRepos.forEach { repo ->
                                FilterChip(
                                    selected = selectedRepo == repo.fullName,
                                    onClick = { selectedRepo = repo.fullName },
                                    label = {
                                        Text(
                                            "${repo.name} (${repo.language})",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = selectedRepo,
                        onValueChange = { selectedRepo = it },
                        label = { Text("Repository (owner/repo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notifyToNumber,
                        onValueChange = { notifyToNumber = it },
                        label = { Text("Alert / OTP Recipient (E.164)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GitHubTriggerMode.entries.forEach { mode ->
                            FilterChip(
                                selected = triggerMode == mode,
                                onClick = { triggerMode = mode },
                                label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onLinkRepo(githubUsername, selectedRepo, notifyToNumber, triggerMode, githubPat)
                        },
                        enabled = !isBusy && selectedRepo.contains("/") && notifyToNumber.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("link_github_repo_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson)
                    ) {
                        Icon(Icons.Rounded.Link, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Save Repo Alert to Firebase", color = Color.White)
                    }
                }
            }
        }

        // 3. Saved GitHub Integrations in Firestore
        item {
            Text(
                text = "LINKED GITHUB REPOSITORIES (${data.githubIntegrations.size})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 880.dp)
            )
        }

        if (data.githubIntegrations.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 880.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "No GitHub repositories linked yet. Fetch a repository above and bind it to SMS alerts or Deploy OTP gates.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(data.githubIntegrations, key = { it.id.ifBlank { it.repoFullName } }) { item ->
                val mode = GitHubTriggerMode.fromRaw(item.triggerMode)
                val webhookUrl = data.settings?.defaultWebhookUrl.orEmpty()

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
                                    text = item.repoFullName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = JetBrainsMonoFont,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${mode.label} → ${item.notifyToNumber}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TelemetryCyan
                                )
                            }
                            Switch(
                                checked = item.active,
                                onCheckedChange = { onToggleIntegration(item, it) }
                            )
                        }

                        if (item.lastCommitMessage.isNotBlank()) {
                            Text(
                                text = "Last commit (${item.lastCommitSha.take(7)}): ${item.lastCommitMessage}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onTriggerCommitAlert(item, githubPat) },
                                enabled = !isBusy && item.active,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.clickable { }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = Color(0xFF061510),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (mode == GitHubTriggerMode.DEPLOY_OTP) "Send Deploy OTP" else "Poll Commit & SMS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF061510)
                                )
                            }

                            if (githubPat.isNotBlank() && webhookUrl.startsWith("http")) {
                                OutlinedButton(
                                    onClick = { onInstallWebhook(item, githubPat, webhookUrl) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Rounded.Webhook, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Install Hook", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            IconButton(
                                onClick = { onDeleteIntegration(item.id) },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete GitHub integration",
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
