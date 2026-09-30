@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.jellyfin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.data.repository.JellyfinRepository
import com.nuvio.tv.domain.model.JellyfinItem
import com.nuvio.tv.domain.model.JellyfinUiState
import com.nuvio.tv.ui.components.LoadingIndicator
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlin.math.roundToInt

/**
 * Jellyfin fork feature (TV): native browse of the user's Jellyfin server, styled with the app's
 * own design system (NuvioTheme + tv-material3 surfaces). Sign in once (or from Settings →
 * Jellyfin), pick a library, browse with the D-pad; nested folders (sagas) and shows list their
 * files/episodes in the right pane and OK plays them in the main player.
 */
@Composable
fun JellyfinScreen(
    onPlayFullscreen: (JellyfinItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    val session = state.session

    if (session == null) {
        JellyfinSignInPane(onBack = onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
            .padding(horizontal = 28.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Jellyfin",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = NuvioTheme.colors.TextPrimary,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${session.serverName}  ·  ${session.userName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TvJellyfinIconButton(
                icon = Icons.Rounded.Refresh,
                contentDescription = "Refresh",
                onClick = { JellyfinRepository.refresh() },
            )
            Spacer(modifier = Modifier.width(10.dp))
            TvJellyfinIconButton(
                icon = Icons.Rounded.Logout,
                contentDescription = "Sign out",
                onClick = { JellyfinRepository.signOut() },
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        var librariesExpanded by rememberSaveable { mutableStateOf(true) }
        var detailsExpanded by rememberSaveable { mutableStateOf(false) }
        val hasSelection = state.selectedItemId != null || state.selectedDetail != null
        val playFocusRequester = remember { FocusRequester() }
        val firstLibraryFocusRequester = remember { FocusRequester() }
        // Clicking a title always reopens the details pane; opening it (or a freshly loaded
        // detail) always moves focus to its Play button.
        LaunchedEffect(state.selectedItemId) {
            if (state.selectedItemId != null) detailsExpanded = true
        }
        LaunchedEffect(detailsExpanded, state.selectedDetail?.id) {
            if (detailsExpanded && state.selectedDetail != null) {
                withFrameNanos { }
                runCatching { playFocusRequester.requestFocus() }
            }
        }
        LaunchedEffect(librariesExpanded, state.libraries.isNotEmpty()) {
            if (librariesExpanded && state.libraries.isNotEmpty()) {
                withFrameNanos { }
                runCatching { firstLibraryFocusRequester.requestFocus() }
            }
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (librariesExpanded) {
                var librariesHadFocus by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .width(232.dp)
                        .fillMaxHeight()
                        .onFocusChanged {
                            if (it.hasFocus) {
                                librariesHadFocus = true
                            } else if (librariesHadFocus) {
                                // Focus moved into the grid — tuck the pane away automatically.
                                librariesHadFocus = false
                                librariesExpanded = false
                            }
                        },
                ) {
                    // End padding keeps rows clear of the collapse handle on the right border.
                    JellyfinLibrariesColumn(
                        state = state,
                        firstRowFocusRequester = firstLibraryFocusRequester,
                        modifier = Modifier.fillMaxHeight().padding(end = 24.dp),
                    )
                    TvJellyfinCollapseHandle(
                        icon = Icons.Rounded.ChevronLeft,
                        contentDescription = "Collapse libraries",
                        onClick = { librariesExpanded = false },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            } else {
                Box(
                    modifier = Modifier.width(40.dp).fillMaxHeight().padding(start = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TvJellyfinCollapseHandle(
                        icon = Icons.Rounded.ChevronRight,
                        contentDescription = "Expand libraries",
                        onClick = { librariesExpanded = true },
                        // Navigating left from the grid onto the handle reopens the pane.
                        onFocusGained = { librariesExpanded = true },
                    )
                }
            }
            JellyfinBrowseColumn(
                state = state,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // Details pane only exists once a title has been clicked; focus leaving it collapses
            // it so the grid takes the full width, and focusing the collapsed handle reopens it.
            if (hasSelection) {
                if (detailsExpanded) {
                    var detailsHadFocus by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .width(344.dp)
                            .fillMaxHeight()
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    detailsHadFocus = true
                                } else if (detailsHadFocus) {
                                    detailsHadFocus = false
                                    detailsExpanded = false
                                }
                            },
                    ) {
                        JellyfinDetailColumn(
                            state = state,
                            onPlay = onPlayFullscreen,
                            playFocusRequester = playFocusRequester,
                            modifier = Modifier.fillMaxHeight(),
                        )
                        TvJellyfinCollapseHandle(
                            icon = Icons.Rounded.ChevronRight,
                            contentDescription = "Collapse details",
                            onClick = { detailsExpanded = false },
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.width(40.dp).fillMaxHeight().padding(end = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TvJellyfinCollapseHandle(
                            icon = Icons.Rounded.ChevronLeft,
                            contentDescription = "Expand details",
                            onClick = { detailsExpanded = true },
                            onFocusGained = { detailsExpanded = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvJellyfinCollapseHandle(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusGained: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocusGained?.invoke() },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(NuvioTheme.radii.sm)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            contentColor = NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, NuvioTheme.colors.Border),
                shape = RoundedCornerShape(NuvioTheme.radii.sm),
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing),
                shape = RoundedCornerShape(NuvioTheme.radii.sm),
            ),
        ),
    ) {
        Box(
            modifier = Modifier.width(22.dp).height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun TvJellyfinIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(42.dp)
            .onFocusChanged { focused = it.isFocused }
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border,
                shape = RoundedCornerShape(NuvioTheme.radii.md),
            )
            // Focus inverts to a white chip with a dark icon — light accent themes would
            // otherwise hide a white icon on a white focus treatment.
            .background(if (focused) Color.White else NuvioTheme.colors.BackgroundCard, RoundedCornerShape(NuvioTheme.radii.md)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else NuvioTheme.colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Readable text/icon color on top of the theme's accent (some palettes use a near-white accent). */
@Composable
private fun onAccentColor(): Color = NuvioTheme.colors.OnSecondary

@Composable
private fun JellyfinSignInPane(onBack: () -> Unit) {
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(
                    NuvioTheme.colors.BackgroundCard,
                    RoundedCornerShape(NuvioTheme.radii.lg),
                )
                .border(1.dp, NuvioTheme.colors.Border, RoundedCornerShape(NuvioTheme.radii.lg))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connect your Jellyfin server",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = NuvioTheme.colors.TextPrimary,
            )
            JellyfinTvTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = "Server address (https://…)")
            JellyfinTvTextField(value = username, onValueChange = { username = it }, label = "Username")
            JellyfinTvTextField(value = password, onValueChange = { password = it }, label = "Password", password = true)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !state.isLoadingSession && serverUrl.isNotBlank() && username.isNotBlank(),
                    onClick = { JellyfinRepository.signIn(serverUrl, username, password) },
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm)),
                    colors = ButtonDefaults.colors(
                        containerColor = NuvioTheme.colors.Secondary,
                        contentColor = onAccentColor(),
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color.Black,
                        disabledContainerColor = NuvioTheme.colors.BackgroundElevated,
                        disabledContentColor = NuvioTheme.colors.TextTertiary,
                    ),
                ) {
                    if (state.isLoadingSession) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = onAccentColor(),
                        )
                    } else {
                        Text(text = "Sign in", fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelLarge,
                    color = NuvioTheme.colors.TextSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(NuvioTheme.radii.sm))
                        .clickable(onClick = onBack)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            if (state.sessionError != null) {
                Text(
                    text = state.sessionError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun JellyfinTvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
        },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = NuvioTheme.colors.TextTertiary,
                modifier = Modifier.size(18.dp),
            )
        },
        shape = RoundedCornerShape(NuvioTheme.radii.md),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = NuvioTheme.colors.BackgroundElevated,
            unfocusedContainerColor = NuvioTheme.colors.BackgroundElevated,
            focusedIndicatorColor = NuvioTheme.colors.FocusRing,
            unfocusedIndicatorColor = NuvioTheme.colors.Border,
            focusedTextColor = NuvioTheme.colors.TextPrimary,
            unfocusedTextColor = NuvioTheme.colors.TextPrimary,
            cursorColor = NuvioTheme.colors.FocusRing,
            focusedLabelColor = NuvioTheme.colors.Secondary,
            unfocusedLabelColor = NuvioTheme.colors.TextTertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun JellyfinLibrariesColumn(
    state: JellyfinUiState,
    modifier: Modifier = Modifier,
    firstRowFocusRequester: FocusRequester? = null,
) {
    var showHidden by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = "Libraries",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = NuvioTheme.colors.TextTertiary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val visible = state.libraries.filter { it.id !in state.hiddenLibraryIds }
            val hidden = state.libraries.filter { it.id in state.hiddenLibraryIds }
            itemsIndexed(visible, key = { _, library -> library.id }) { index, library ->
                TvJellyfinLibraryRow(
                    name = library.name,
                    isSelected = state.selectedLibraryId == library.id,
                    isDimmed = false,
                    onToggleVisibility = { JellyfinRepository.toggleLibraryHidden(library.id) },
                    onClick = { JellyfinRepository.selectLibrary(library.id) },
                    focusRequester = if (index == 0) firstRowFocusRequester else null,
                )
            }
            if (state.libraries.isEmpty() && state.isLoadingItems) {
                item("jellyfin_lib_loading") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        LoadingIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item("jellyfin_hidden_toggle") {
                    TvJellyfinHiddenExpander(
                        expanded = showHidden,
                        hiddenCount = hidden.size,
                        onToggle = { showHidden = !showHidden },
                    )
                }
                if (showHidden) {
                    items(hidden, key = { "hidden_" + it.id }) { library ->
                        TvJellyfinLibraryRow(
                            name = library.name,
                            isSelected = false,
                            isDimmed = true,
                            onToggleVisibility = { JellyfinRepository.toggleLibraryHidden(library.id) },
                            onClick = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvJellyfinHiddenExpander(
    expanded: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(NuvioTheme.radii.sm)
    Surface(
        onClick = onToggle,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = NuvioTheme.colors.BackgroundElevated,
            contentColor = NuvioTheme.colors.TextTertiary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(border = BorderStroke(1.dp, NuvioTheme.colors.Border), shape = shape),
            focusedBorder = Border(border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing), shape = shape),
        ),
    ) {
        Text(
            text = (if (expanded) "▾ " else "▸ ") + "Hidden ($hiddenCount)",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

/**
 * One library = two SEPARATE focusable surfaces (select + visibility toggle), so D-pad focus can
 * reach the Hide/Show control directly instead of the whole row swallowing it.
 */
@Composable
private fun TvJellyfinLibraryRow(
    name: String,
    isSelected: Boolean,
    isDimmed: Boolean,
    onToggleVisibility: () -> Unit,
    onClick: (() -> Unit)?,
    focusRequester: FocusRequester? = null,
) {
    val rowShape = RoundedCornerShape(NuvioTheme.radii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.55f else 1f),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onClick ?: {},
            enabled = onClick != null,
            modifier = Modifier
                .weight(1f)
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
            shape = ClickableSurfaceDefaults.shape(rowShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isSelected) NuvioTheme.colors.Secondary.copy(alpha = 0.18f) else NuvioTheme.colors.BackgroundCard,
                contentColor = if (isSelected) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
                focusedContainerColor = Color.White.copy(alpha = 0.12f),
                focusedContentColor = Color.White,
                disabledContainerColor = NuvioTheme.colors.BackgroundCard,
                disabledContentColor = NuvioTheme.colors.TextTertiary,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) NuvioTheme.colors.Secondary.copy(alpha = 0.7f) else NuvioTheme.colors.Border,
                    ),
                    shape = rowShape,
                ),
                focusedBorder = Border(border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing), shape = rowShape),
            ),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        Surface(
            onClick = onToggleVisibility,
            shape = ClickableSurfaceDefaults.shape(rowShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = NuvioTheme.colors.BackgroundElevated,
                contentColor = NuvioTheme.colors.Secondary,
                focusedContainerColor = Color.White,
                focusedContentColor = Color.Black,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(border = BorderStroke(1.dp, NuvioTheme.colors.Border), shape = rowShape),
                focusedBorder = Border(border = BorderStroke(2.dp, NuvioTheme.colors.FocusRing), shape = rowShape),
            ),
        ) {
            Text(
                text = if (isDimmed) "Show" else "Hide",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun JellyfinBrowseColumn(state: JellyfinUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvSortChip(label = "A–Z", isSelected = !state.sortLatestFirst, onClick = { JellyfinRepository.setSortLatestFirst(false) })
            TvSortChip(label = "Latest", isSelected = state.sortLatestFirst, onClick = { JellyfinRepository.setSortLatestFirst(true) })
            Spacer(modifier = Modifier.weight(1f))
            if (state.totalItemCount > 0) {
                Text(
                    text = "${state.items.size} of ${state.totalItemCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = NuvioTheme.colors.TextTertiary,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoadingItems && state.items.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextTertiary,
                        )
                    }
                }

                state.itemsError != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.itemsError.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { JellyfinRepository.retryItems() },
                        shape = ButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm)),
                        colors = ButtonDefaults.colors(
                            containerColor = NuvioTheme.colors.Secondary,
                            contentColor = onAccentColor(),
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                        ),
                    ) { Text("Retry") }
                }

                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nothing here yet",
                        color = NuvioTheme.colors.TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 132.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.items, key = { it.id }) { item ->
                        TvJellyfinPosterCell(
                            item = item,
                            posterUrl = JellyfinRepository.posterUrlFor(item),
                            isSelected = state.selectedItemId == item.id,
                            onClick = { JellyfinRepository.selectItem(item) },
                        )
                    }
                    if (state.canLoadMore) {
                        item(key = "jellyfin_load_more", span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Button(
                                    enabled = !state.isLoadingItems,
                                    onClick = { JellyfinRepository.loadMore() },
                                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm)),
                                    colors = ButtonDefaults.colors(
                                        containerColor = NuvioTheme.colors.BackgroundCard,
                                        contentColor = NuvioTheme.colors.TextPrimary,
                                        focusedContainerColor = Color.White,
                                        focusedContentColor = Color.Black,
                                        disabledContainerColor = NuvioTheme.colors.BackgroundElevated,
                                        disabledContentColor = NuvioTheme.colors.TextTertiary,
                                    ),
                                ) { Text("Load more") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSortChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.08f),
            contentColor = if (isSelected) onAccentColor() else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected || focused) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TvJellyfinPosterCell(
    item: JellyfinItem,
    posterUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val posterShape = RoundedCornerShape(NuvioTheme.radii.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = if (focused) 1.05f else 1f
                scaleY = if (focused) 1.05f else 1f
            }
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .onFocusChanged { focused = it.isFocused }
                .clip(posterShape)
                .background(NuvioTheme.colors.BackgroundCard)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = when {
                        focused -> NuvioTheme.colors.FocusRing
                        isSelected -> NuvioTheme.colors.Secondary
                        else -> NuvioTheme.colors.Border
                    },
                    shape = posterShape,
                ),
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = item.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = NuvioTheme.colors.TextTertiary,
                    )
                }
            }
            val progress = item.playedPercentage
            if (progress != null && progress > 1.0 && progress < 95.0) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    color = NuvioTheme.colors.Secondary,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.productionYear != null) {
            Text(
                text = item.productionYear.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = NuvioTheme.colors.TextTertiary,
            )
        }
    }
}

@Composable
private fun JellyfinDetailColumn(
    state: JellyfinUiState,
    onPlay: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
    playFocusRequester: FocusRequester? = null,
) {
    val item = state.selectedDetail
    val panelShape = RoundedCornerShape(NuvioTheme.radii.lg)
    if (item == null) {
        // The pane only composes once a title is clicked; until its detail arrives, show the
        // app's loading indicator instead of the old always-visible placeholder.
        Box(
            modifier = modifier
                .background(NuvioTheme.colors.BackgroundCard, panelShape)
                .border(1.dp, NuvioTheme.colors.Border, panelShape),
            contentAlignment = Alignment.Center,
        ) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .background(NuvioTheme.colors.BackgroundCard, panelShape)
            .border(1.dp, NuvioTheme.colors.Border, panelShape)
            // Start padding keeps content clear of the collapse handle on the left border.
            .padding(start = 34.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "jellyfin_detail_header") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(NuvioTheme.radii.md))
                        .background(NuvioTheme.colors.BackgroundElevated),
                ) {
                    val posterUrl = JellyfinRepository.posterUrlFor(item, maxWidth = 400)
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NuvioTheme.colors.TextPrimary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isEpisode && !item.seriesName.isNullOrBlank()) {
                        Text(
                            text = item.seriesName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = NuvioTheme.colors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val metaLine = buildList {
                        item.productionYear?.let { add(it.toString()) }
                        item.communityRating?.let { rating -> add("★ ${(rating * 10).roundToInt() / 10.0}") }
                        item.runTimeMinutes?.let { minutes -> add("${minutes}m") }
                        item.officialRating?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }.joinToString("  ·  ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            text = metaLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TvJellyfinPlayButton(item = item, state = state, onPlay = onPlay, focusRequester = playFocusRequester)
                    if (item.resumePositionMs > 0) {
                        Text(
                            text = "Resumes at ${formatPosition(item.resumePositionMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                    }
                }
            }
        }
        if (!item.overview.isNullOrBlank()) {
            item(key = "jellyfin_detail_overview") {
                Text(
                    text = item.overview.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = NuvioTheme.colors.TextSecondary,
                )
            }
        }
        if (item.isSeries) {
            item(key = "jellyfin_detail_seasons") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = NuvioTheme.colors.TextTertiary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        state.seasons.forEach { season ->
                            TvSortChip(
                                label = season.name ?: "Season ${season.indexNumber ?: ""}",
                                isSelected = state.selectedSeasonId == season.id,
                                onClick = { JellyfinRepository.selectSeason(season.id) },
                            )
                        }
                    }
                }
            }
        }
        if (item.isSeries || item.isFolder) {
            item(key = "jellyfin_detail_episodes_label") {
                Text(
                    text = if (item.isSeries) "Episodes" else "Files in this folder",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = NuvioTheme.colors.TextTertiary,
                )
            }
            if (state.isLoadingDetail && state.episodes.isEmpty()) {
                item(key = "jellyfin_detail_episodes_loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        LoadingIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            }
            items(state.episodes, key = { it.id }) { episode ->
                TvJellyfinEpisodeRow(episode = episode, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun TvJellyfinPlayButton(
    item: JellyfinItem,
    state: JellyfinUiState,
    onPlay: (JellyfinItem) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val playable: JellyfinItem? = when {
        item.isSeries || item.isFolder ->
            state.episodes.firstOrNull { it.resumePositionMs > 0 } ?: state.episodes.firstOrNull()
        item.isPlayable -> item
        else -> null
    }
    Button(
        enabled = playable != null,
        onClick = { playable?.let(onPlay) },
        modifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm)),
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.Secondary,
            contentColor = onAccentColor(),
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
            disabledContainerColor = NuvioTheme.colors.BackgroundElevated,
            disabledContentColor = NuvioTheme.colors.TextTertiary,
        ),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = when {
                playable == null && item.isSeries -> "No episodes"
                playable == null && item.isFolder -> "No files"
                playable == null -> "Not playable"
                playable.resumePositionMs > 0 -> "Resume"
                else -> "Play"
            },
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TvJellyfinEpisodeRow(episode: JellyfinItem, onPlay: (JellyfinItem) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val rowShape = RoundedCornerShape(NuvioTheme.radii.md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(rowShape)
            .background(if (focused) Color.White.copy(alpha = 0.10f) else NuvioTheme.colors.BackgroundElevated)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) NuvioTheme.colors.FocusRing else NuvioTheme.colors.Border,
                shape = rowShape,
            )
            .clickable { onPlay(episode) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when {
                episode.isEpisode -> {
                    val season = episode.parentIndexNumber
                    val number = episode.indexNumber
                    if (season != null && number != null) "S$season·E$number" else "E${episode.indexNumber ?: ""}"
                }
                episode.productionYear != null -> episode.productionYear.toString()
                else -> "•"
            },
            style = MaterialTheme.typography.labelMedium,
            color = NuvioTheme.colors.Secondary,
            modifier = Modifier.width(58.dp),
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.bodySmall,
                color = NuvioTheme.colors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episode.runTimeMinutes?.let {
                    Text(
                        "${it}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = NuvioTheme.colors.TextTertiary,
                    )
                }
                if (episode.resumePositionMs > 0) {
                    Text(
                        "resume at ${formatPosition(episode.resumePositionMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NuvioTheme.colors.Secondary,
                    )
                }
            }
        }
    }
}

internal fun formatPosition(positionMs: Long): String {
    val totalSeconds = positionMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes.toString().padStart(2, '0')}m" else "${minutes}m"
}
