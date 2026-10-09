package com.nuvio.tv.domain.model

/** Prefix that marks a search-result id as a Jellyfin item. */
const val JELLYFIN_META_ID_PREFIX = "jf:"

data class JellyfinSession(
    val serverUrl: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
)

data class JellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String?,
)

data class JellyfinItem(
    val id: String,
    val name: String,
    val type: String,
    val overview: String? = null,
    val productionYear: Int? = null,
    val communityRating: Double? = null,
    val officialRating: String? = null,
    val runTimeTicks: Long? = null,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
    val seriesId: String? = null,
    val seriesName: String? = null,
    val imageTag: String? = null,
    val backdropTag: String? = null,
    val container: String? = null,
    val playbackPositionTicks: Long? = null,
    val playedPercentage: Double? = null,
) {
    val isSeries: Boolean get() = type.equals("Series", ignoreCase = true)
    val isEpisode: Boolean get() = type.equals("Episode", ignoreCase = true)
    val isFolder: Boolean
        get() = type.equals("Folder", ignoreCase = true) ||
            type.equals("AggregateFolder", ignoreCase = true) ||
            type.equals("PlaylistFolder", ignoreCase = true)

    /** True when this item type has a playable video stream (folders / box sets do not). */
    val isPlayable: Boolean
        get() = type.equals("Movie", ignoreCase = true) ||
            type.equals("Episode", ignoreCase = true) ||
            type.equals("Video", ignoreCase = true) ||
            type.equals("MusicVideo", ignoreCase = true)

    val runTimeMinutes: Int?
        get() = runTimeTicks?.let { ticks -> (ticks / 600_000_000L).toInt() }

    val resumePositionMs: Long
        get() = playbackPositionTicks?.div(10_000L)?.takeIf { it > 0L } ?: 0L
}

data class JellyfinItemPage(
    val items: List<JellyfinItem>,
    val totalRecordCount: Int,
)

data class JellyfinUiState(
    val session: JellyfinSession? = null,
    val isLoadingSession: Boolean = false,
    val sessionError: String? = null,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val hiddenLibraryIds: Set<String> = emptySet(),
    val selectedLibraryId: String? = null,
    val items: List<JellyfinItem> = emptyList(),
    val totalItemCount: Int = 0,
    val isLoadingItems: Boolean = false,
    val itemsError: String? = null,
    val searchQuery: String = "",
    val sortLatestFirst: Boolean = false,
    val selectedItemId: String? = null,
    val selectedDetail: JellyfinItem? = null,
    val seasons: List<JellyfinItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<JellyfinItem> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailError: String? = null,
) {
    val canLoadMore: Boolean get() = items.size < totalItemCount
}
