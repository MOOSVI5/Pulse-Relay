package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.example.data.model.TeamProfileRecord
import com.example.data.model.TeamRole
import com.example.ui.components.StatusPill
import com.example.ui.theme.PulseCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.WarningAmber

@Composable
fun TeamWorkspaceControlCard(
    profiles: List<TeamProfileRecord>,
    activeProfile: TeamProfileRecord?,
    isBusy: Boolean,
    onOpenSwitcher: () -> Unit,
    onSwitchToAdmin: () -> Unit,
    onCreateMember: (displayName: String, contactHandle: String, role: TeamRole, dailyLimit: Long) -> Unit,
    onUpdateMember: (profile: TeamProfileRecord, displayName: String, contactHandle: String, role: TeamRole, active: Boolean, dailyLimit: Long) -> Unit,
    onDeleteMember: (TeamProfileRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf<TeamProfileRecord?>(null) }

    val currentRole = TeamRole.fromRaw(activeProfile?.role)
    val canManageTeam = (activeProfile?.active != false) && currentRole.canManageTeam
    val adminProfile = profiles.firstOrNull { it.ownerAdmin }

    Surface(
        modifier = modifier,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        tint = if (currentRole == TeamRole.ADMIN) SignalEmerald else TelemetryCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Profile & Team Roles",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Active: ${activeProfile?.displayName ?: "Admin"} · ${currentRole.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeProfile?.active == false) PulseCrimson else TelemetryCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenSwitcher,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("switch_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Switch profile",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Switch", maxLines = 1)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentRole.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (activeProfile?.ownerAdmin != true && adminProfile != null) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = onSwitchToAdmin,
                            modifier = Modifier.testTag("return_to_admin_button")
                        ) {
                            Text("Admin Mode", color = SignalEmerald)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "TEAM MEMBERS (${profiles.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    enabled = !isBusy && canManageTeam,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TelemetryCyan),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("add_team_member_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Add team member",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Add Member",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.surface,
                        maxLines = 1
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                profiles.forEach { member ->
                    val role = TeamRole.fromRaw(member.role)
                    val isCurrent = member.id == activeProfile?.id
                    val roleColor = when (role) {
                        TeamRole.ADMIN -> SignalEmerald
                        TeamRole.OTP_OPERATOR -> TelemetryCyan
                        TeamRole.DEVELOPER -> WarningAmber
                        TeamRole.VIEWER -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        color = if (isCurrent) {
                            TelemetryCyan.copy(alpha = 0.08f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isCurrent) TelemetryCyan.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenSwitcher)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = member.displayName,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    StatusPill(
                                        text = if (member.ownerAdmin) "OWNER ADMIN" else role.label.uppercase(),
                                        color = roleColor
                                    )
                                    if (!member.active) {
                                        StatusPill(text = "PAUSED", color = PulseCrimson)
                                    }
                                }
                                Text(
                                    text = buildString {
                                        if (member.contactHandle.isNotBlank()) {
                                            append(member.contactHandle)
                                            append(" · ")
                                        }
                                        append("OTP Limit: ${member.dailyOtpLimit}/day")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (canManageTeam && !member.ownerAdmin) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { editingProfile = member },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("edit_member_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit ${member.displayName} role",
                                            tint = TelemetryCyan,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteMember(member) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("delete_member_${member.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove ${member.displayName}",
                                            tint = PulseCrimson,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Active profile",
                                    tint = SignalEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        MemberRoleEditorDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, contact, role, _, limit ->
                showAddDialog = false
                onCreateMember(name, contact, role, limit)
            }
        )
    }

    editingProfile?.let { member ->
        MemberRoleEditorDialog(
            existing = member,
            onDismiss = { editingProfile = null },
            onSave = { name, contact, role, active, limit ->
                editingProfile = null
                onUpdateMember(member, name, contact, role, active, limit)
            }
        )
    }
}

@Composable
fun ProfileSwitcherDialog(
    profiles: List<TeamProfileRecord>,
    activeProfileId: String,
    onSelectProfile: (TeamProfileRecord) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.SwapHoriz,
                contentDescription = null,
                tint = TelemetryCyan
            )
        },
        title = { Text("Switch Active Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Switch between your Owner Admin profile and team member roles to operate or preview permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profiles.forEach { profile ->
                    val role = TeamRole.fromRaw(profile.role)
                    val selected = profile.id == activeProfileId
                    Surface(
                        color = if (selected) TelemetryCyan.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) TelemetryCyan else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectProfile(profile)
                                onDismiss()
                            }
                            .testTag("profile_option_${profile.id}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = profile.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    StatusPill(
                                        text = if (profile.ownerAdmin) "ADMIN" else role.label.uppercase(),
                                        color = if (role == TeamRole.ADMIN) SignalEmerald else TelemetryCyan
                                    )
                                }
                                Text(
                                    text = role.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = SignalEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun MemberRoleEditorDialog(
    existing: TeamProfileRecord?,
    onDismiss: () -> Unit,
    onSave: (displayName: String, contactHandle: String, role: TeamRole, active: Boolean, dailyLimit: Long) -> Unit
) {
    var displayName by remember { mutableStateOf(existing?.displayName ?: "") }
    var contactHandle by remember { mutableStateOf(existing?.contactHandle ?: "+92") }
    var selectedRole by remember {
        mutableStateOf(
            existing?.let { TeamRole.fromRaw(it.role) } ?: TeamRole.OTP_OPERATOR
        )
    }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    var dailyLimit by remember { mutableLongStateOf(existing?.dailyOtpLimit ?: 500L) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = TelemetryCyan) },
        title = { Text(if (existing == null) "Add Team Member" else "Edit Member Role") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(80) },
                    label = { Text("Member / Profile Name") },
                    placeholder = { Text("e.g. Ali OTP Desk") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_name_input")
                )
                OutlinedTextField(
                    value = contactHandle,
                    onValueChange = { contactHandle = it.take(120) },
                    label = { Text("Phone or Email") },
                    placeholder = { Text("+923001234567 or ali@app.pk") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("member_contact_input")
                )

                Text("Assign Role", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TeamRole.entries.forEach { role ->
                        FilterChip(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role },
                            label = { Text(role.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
                                selectedLabelColor = TelemetryCyan
                            ),
                            modifier = Modifier.testTag("role_chip_${role.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = selectedRole.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Daily OTP Quota", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(100L, 500L, 2500L, 10000L).forEach { quota ->
                        FilterChip(
                            selected = dailyLimit == quota,
                            onClick = { dailyLimit = quota },
                            label = { Text("$quota/day") }
                        )
                    }
                }

                if (existing != null && !existing.ownerAdmin) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Profile Active")
                        Switch(checked = active, onCheckedChange = { active = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.isNotBlank()) {
                        onSave(displayName, contactHandle, selectedRole, active, dailyLimit)
                    }
                },
                enabled = displayName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PulseCrimson),
                modifier = Modifier.testTag("save_member_button")
            ) {
                Text(if (existing == null) "Add Member" else "Save Role", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
