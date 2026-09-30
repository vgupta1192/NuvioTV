@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens.jellyfin

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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

    var leftCollapsed by rememberSaveable { mutableStateOf(false) }
    var rightCollapsed by rememberSaveable { mutableStateOf(false) }
    val detailItem = state.selectedDetail
    // The details pane exists only while a title is clicked; clicking any title reopens it.
    LaunchedEffect(detailItem?.id) {
        if (detailItem != null) rightCollapsed = false
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
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvJellyfinIconButton(
                    icon = if (leftCollapsed) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
                    contentDescription = if (leftCollapsed) "Show libraries" else "Hide libraries",
                    onClick = { leftCollapsed = !leftCollapsed },
                )
            }
            if (!leftCollapsed) {
                JellyfinLibrariesColumn(
                    state = state,
                    modifier = Modifier.width(236.dp).fillMaxHeight(),
                )
            }
            JellyfinBrowseColumn(
                state = state,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            if (detailItem != null && !rightCollapsed) {
                JellyfinDetailColumn(
                    state = state,
                    onPlay = onPlayFullscreen,
                    onCollapse = { rightCollapsed = true },
                    modifier = Modifier.width(348.dp).fillMaxHeight(),
                )
            }
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
            .background(
                if (focused) Color.White else NuvioTheme.colors.BackgroundCard,
                RoundedCornerShape(NuvioTheme.radii.md),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (focused) Color.Black else NuvioTheme.colors.TextSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

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
                        contentColor = Color.White,
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
                            color = NuvioTheme.colors.TextPrimary,
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
private fun JellyfinLibrariesColumn(state: JellyfinUiState, modifier: Modifier = Modifier) {
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
            items(visible, key = { it.id }) { library ->
                TvJellyfinLibraryRow(
                    name = library.name,
                    isSelected = state.selectedLibraryId == library.id,
                    isDimmed = false,
                    onToggleVisibility = { JellyfinRepository.toggleLibraryHidden(library.id) },
                    onClick = { JellyfinRepository.selectLibrary(library.id) },
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
                    var focused by remember { mutableStateOf(false) }
                    Text(
                        text = (if (showHidden) "▾ " else "▸ ") + "Hidden (${hidden.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextTertiary,
                        modifier = Modifier
                            .onFocusChanged { focused = it.isFocused }
                            .clip(RoundedCornerShape(NuvioTheme.radii.sm))
                            .clickable { showHidden = !showHidden }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
private fun TvJellyfinLibraryRow(
    name: String,
    isSelected: Boolean,
    isDimmed: Boolean,
    onToggleVisibility: () -> Unit,
    onClick: (() -> Unit)?,
) {
    val rowShape = RoundedCornerShape(NuvioTheme.radii.md)
    var rowFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDimmed) 0.55f else 1f)
            .onFocusChanged { rowFocused = it.isFocused }
            .background(
                when {
                    rowFocused -> NuvioTheme.colors.BackgroundElevated
                    isSelected -> NuvioTheme.colors.Secondary.copy(alpha = 0.18f)
                    else -> NuvioTheme.colors.BackgroundCard
                },
                rowShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .border(
                width = if (rowFocused || isSelected) 2.dp else 1.dp,
                color = when {
                    rowFocused -> NuvioTheme.colors.FocusRing
                    isSelected -> NuvioTheme.colors.Secondary.copy(alpha = 0.7f)
                    else -> NuvioTheme.colors.Border
                },
                shape = rowShape,
            )
            .padding(start = 14.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected || rowFocused) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (isSelected || rowFocused) NuvioTheme.colors.TextPrimary else NuvioTheme.colors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isDimmed) "Show" else "Hide",
            style = MaterialTheme.typography.labelSmall,
            color = NuvioTheme.colors.Secondary,
            modifier = Modifier
                .clip(RoundedCornerShape(NuvioTheme.radii.sm))
                .clickable(onClick = onToggleVisibility)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun JellyfinBrowseColumn(state: JellyfinUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        JellyfinTvTextField(
            value = state.searchQuery,
            onValueChange = JellyfinRepository::setSearchQuery,
            label = "Search your Jellyfin server…",
        )
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
                            contentColor = Color.White,
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
            contentColor = if (isSelected) Color.White else NuvioTheme.colors.TextSecondary,
            focusedContainerColor = if (isSelected) NuvioTheme.colors.Secondary else Color.White.copy(alpha = 0.14f),
            focusedContentColor = Color.White,
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
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.selectedDetail
    val panelShape = RoundedCornerShape(NuvioTheme.radii.lg)
    if (item == null) {
        Box(
            modifier = modifier
                .background(NuvioTheme.colors.BackgroundCard, panelShape)
                .border(1.dp, NuvioTheme.colors.Border, panelShape)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Select a title to see its details",
                style = MaterialTheme.typography.bodyMedium,
                color = NuvioTheme.colors.TextTertiary,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .background(NuvioTheme.colors.BackgroundCard, panelShape)
            .border(1.dp, NuvioTheme.colors.Border, panelShape)
            .padding(16.dp),
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
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    TvJellyfinPlayButton(item = item, state = state, onPlay = onPlay)
                    if (item.resumePositionMs > 0) {
                        Text(
                            text = "Resumes at ${formatPosition(item.resumePositionMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = NuvioTheme.colors.TextSecondary,
                        )
                    }
                }
                TvJellyfinIconButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = "Hide details",
                    onClick = onCollapse,
                )
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
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(NuvioTheme.radii.sm)),
        colors = ButtonDefaults.colors(
            containerColor = NuvioTheme.colors.Secondary,
            contentColor = Color.White,
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
