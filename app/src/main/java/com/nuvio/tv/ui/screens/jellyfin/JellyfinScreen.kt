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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.nuvio.tv.data.repository.JellyfinRepository
import com.nuvio.tv.domain.model.JellyfinItem
import com.nuvio.tv.domain.model.JellyfinUiState
import kotlin.math.roundToInt

/**
 * Jellyfin fork feature (TV): native browse of the user's Jellyfin server. Sign in once, pick a
 * library, browse with the D-pad; nested folders (sagas) and shows list their files/episodes in
 * the right pane and OK plays them in the main player.
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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Jellyfin · ${session.serverName}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Back",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            IconButton(onClick = { JellyfinRepository.refresh() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = { JellyfinRepository.signOut() }) {
                Icon(Icons.Rounded.Logout, contentDescription = "Sign out", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            JellyfinLibrariesColumn(
                state = state,
                modifier = Modifier.width(230.dp).fillMaxHeight(),
            )
            JellyfinBrowseColumn(
                state = state,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            JellyfinDetailColumn(
                state = state,
                onPlay = onPlayFullscreen,
                modifier = Modifier.width(360.dp).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun JellyfinSignInPane(onBack: () -> Unit) {
    val state by JellyfinRepository.uiState.collectAsStateWithLifecycle()
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Connect your Jellyfin server",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedTextField(value = serverUrl, onValueChange = { serverUrl = it }, label = { Text("Server address (https://…)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !state.isLoadingSession && serverUrl.isNotBlank() && username.isNotBlank(),
                    onClick = { JellyfinRepository.signIn(serverUrl, username, password) },
                ) {
                    if (state.isLoadingSession) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Sign in")
                    }
                }
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
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
private fun JellyfinLibrariesColumn(state: JellyfinUiState, modifier: Modifier = Modifier) {
    var showHidden by remember { mutableStateOf(false) }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                text = "Signed in as ${state.session?.userName ?: ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Text(
                text = "Libraries",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
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
            item { Text("Loading libraries…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (hidden.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = (if (showHidden) "▾ " else "▸ ") + "Hidden (${hidden.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showHidden = !showHidden }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
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

@Composable
private fun TvJellyfinLibraryRow(
    name: String,
    isSelected: Boolean,
    isDimmed: Boolean,
    onToggleVisibility: () -> Unit,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, top = 4.dp, end = 2.dp, bottom = 4.dp)
            .alpha(if (isDimmed) 0.55f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isDimmed) "Show" else "Hide",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onToggleVisibility)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun JellyfinBrowseColumn(state: JellyfinUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = JellyfinRepository::setSearchQuery,
            label = { Text("Search your Jellyfin server…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TvSortChip(label = "A–Z", isSelected = !state.sortLatestFirst, onClick = { JellyfinRepository.setSortLatestFirst(false) })
            TvSortChip(label = "Latest", isSelected = state.sortLatestFirst, onClick = { JellyfinRepository.setSortLatestFirst(true) })
            Spacer(modifier = Modifier.weight(1f))
            if (state.totalItemCount > 0) {
                Text(
                    text = "${state.items.size} of ${state.totalItemCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoadingItems && state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.itemsError != null && state.items.isEmpty() -> Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(state.itemsError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = { JellyfinRepository.retryItems() }) { Text("Retry") }
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing here yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
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
                                Button(enabled = !state.isLoadingItems, onClick = { JellyfinRepository.loadMore() }) { Text("Load more") }
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
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
private fun TvJellyfinPosterCell(
    item: JellyfinItem,
    posterUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val progress = item.playedPercentage
            if (progress != null && progress > 1.0 && progress < 95.0) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.productionYear != null) {
            Text(
                text = item.productionYear.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JellyfinDetailColumn(
    state: JellyfinUiState,
    onPlay: (JellyfinItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = state.selectedDetail
    if (item == null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Select a title to see its details",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "jellyfin_detail_header") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isEpisode && !item.seriesName.isNullOrBlank()) {
                        Text(
                            text = item.seriesName.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(text = metaLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    TvJellyfinPlayButton(item = item, state = state, onPlay = onPlay)
                    if (item.resumePositionMs > 0) {
                        Text(
                            text = "Resumes at ${formatPosition(item.resumePositionMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (!item.overview.isNullOrBlank()) {
            item(key = "jellyfin_detail_overview") {
                Text(text = item.overview.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (item.isSeries) {
            item(key = "jellyfin_detail_seasons") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Seasons",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isLoadingDetail && state.episodes.isEmpty()) {
                item(key = "jellyfin_detail_episodes_loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
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
    Button(enabled = playable != null, onClick = { playable?.let(onPlay) }) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = when {
                playable == null && item.isSeries -> "No episodes"
                playable == null && item.isFolder -> "No files"
                playable == null -> "Not playable"
                playable.resumePositionMs > 0 -> "Resume"
                else -> "Play"
            },
        )
    }
}

@Composable
private fun TvJellyfinEpisodeRow(episode: JellyfinItem, onPlay: (JellyfinItem) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onPlay(episode) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = episode.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                episode.runTimeMinutes?.let { Text("${it}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (episode.resumePositionMs > 0) {
                    Text(
                        "resume at ${formatPosition(episode.resumePositionMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
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
