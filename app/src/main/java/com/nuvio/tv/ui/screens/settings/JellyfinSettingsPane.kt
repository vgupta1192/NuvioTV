@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.nuvio.tv.data.repository.JellyfinRepository
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Jellyfin fork feature (TV): the Settings → Jellyfin section, mirroring the Live TV settings
 * pattern. Server connection (address/username/password) plus per-profile library visibility;
 * the sidebar Jellyfin entry opens the browse UI directly.
 */
@Composable
internal fun JellyfinSettingsPane(
    initialFocusRequester: FocusRequester?,
    modifier: Modifier = Modifier
) {
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    val session = state.session
    var serverUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(end = NuvioTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
    ) {
        item {
            SettingsDetailHeader(
                title = "Jellyfin",
                subtitle = "Connect your Jellyfin server, then open Jellyfin from the sidebar to browse and play your libraries."
            )
        }
        item {
            SettingsGroupCard(
                title = "Server",
                subtitle = if (session != null) "Connected to ${session.serverName}" else "Not connected"
            ) {
                if (session == null) {
                    JellyfinSettingsTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = "Server address (https://…)",
                        modifier = (if (initialFocusRequester != null) {
                            Modifier.focusRequester(initialFocusRequester)
                        } else {
                            Modifier
                        }).fillMaxWidth()
                    )
                    JellyfinSettingsTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username",
                        modifier = Modifier.fillMaxWidth()
                    )
                    JellyfinSettingsTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        password = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SettingsActionRow(
                        title = if (state.isLoadingSession) "Connecting…" else "Connect",
                        subtitle = "Signs in and loads your server's libraries.",
                        enabled = !state.isLoadingSession && serverUrl.isNotBlank() && username.isNotBlank(),
                        onClick = { JellyfinRepository.signIn(serverUrl, username, password) }
                    )
                    if (!state.sessionError.isNullOrBlank()) {
                        androidx.compose.material3.Text(
                            text = state.sessionError.orEmpty(),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 18.dp)
                        )
                    }
                } else {
                    SettingsActionRow(
                        title = "Signed in as ${session.userName}",
                        subtitle = session.serverUrl,
                        onClick = {},
                        enabled = false,
                        trailingIcon = Icons.Default.CheckCircle
                    )
                    SettingsActionRow(
                        title = "Refresh libraries",
                        subtitle = "Reload libraries and artwork from the server.",
                        leadingIcon = Icons.Default.Refresh,
                        onClick = { JellyfinRepository.refresh() }
                    )
                    SettingsActionRow(
                        title = "Sign out",
                        subtitle = "Removes the saved session from this device.",
                        leadingIcon = Icons.Default.Logout,
                        onClick = { JellyfinRepository.signOut() }
                    )
                }
            }
        }
        if (session != null) {
            item {
                SettingsGroupCard(
                    title = "Libraries",
                    subtitle = "Visibility applies to the active profile only — hidden libraries leave the Jellyfin screen and search."
                ) {
                    if (state.libraries.isEmpty()) {
                        SettingsActionRow(
                            title = if (state.isLoadingSession) "Loading libraries…" else "No libraries found",
                            subtitle = null,
                            enabled = false,
                            onClick = {}
                        )
                    } else {
                        state.libraries.forEach { library ->
                            val visible = library.id !in state.hiddenLibraryIds
                            SettingsToggleRow(
                                title = library.name,
                                subtitle = if (visible) "Visible in this profile" else "Hidden in this profile",
                                checked = visible,
                                onToggle = { JellyfinRepository.toggleLibraryHidden(library.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyfinSettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.padding(vertical = 2.dp)
    )
}
