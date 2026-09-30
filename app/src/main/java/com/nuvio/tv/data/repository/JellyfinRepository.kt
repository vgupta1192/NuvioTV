package com.nuvio.tv.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.domain.model.JellyfinItem
import com.nuvio.tv.domain.model.JellyfinItemPage
import com.nuvio.tv.domain.model.JellyfinLibrary
import com.nuvio.tv.domain.model.JellyfinSession
import com.nuvio.tv.domain.model.JellyfinUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Native Jellyfin integration (fork feature). Talks directly to the user's Jellyfin server with
 * `Authorization: MediaBrowser …` (Jellyfin 12 killed the X-Emby-* headers); media/image URLs
 * carry the token as `api_key=`. Persistence in SharedPreferences; hidden libraries are stored
 * per Nuvio profile (profile id supplied by MainActivity via [profileIdProvider]).
 */
object JellyfinRepository {
    private const val PREFS = "nuvio_jellyfin"
    private const val KEY_SERVER = "server_url"
    private const val KEY_SERVER_NAME = "server_name"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_TOKEN = "access_token"
    private const val KEY_DEVICE_ID = "device_id"
    private const val HIDDEN_KEY_PREFIX = "hidden_libraries_p"

    private const val PAGE_SIZE = 60
    private const val MAX_MERGED_SEARCH_ITEMS = 200
    private const val SEARCH_DEBOUNCE_MS = 350L
    private val VIDEO_LIBRARY_TYPES = setOf("movies", "tvshows", "mixed")

    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Set from MainActivity so hidden libraries can be stored per profile. */
    var profileIdProvider: () -> Int = { 1 }

    private var preferences: SharedPreferences? = null
    private var initialized = false
    private var lastAppliedProfileId: Int? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _uiState = MutableStateFlow(JellyfinUiState())
    val uiState: StateFlow<JellyfinUiState> = _uiState.asStateFlow()

    private var librariesJob: Job? = null
    private var itemsJob: Job? = null
    private var detailJob: Job? = null

    val hasSession: Boolean
        get() = _uiState.value.session != null

    fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        loadPersistedSession()
    }

    private fun prefs(): SharedPreferences? = preferences

    private fun loadPersistedSession() {
        val p = prefs() ?: return
        val server = p.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: return
        val userId = p.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return
        val token = p.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return
        val session = JellyfinSession(
            serverUrl = server,
            serverName = p.getString(KEY_SERVER_NAME, null)?.takeIf { it.isNotBlank() } ?: "Jellyfin",
            userId = userId,
            userName = p.getString(KEY_USER_NAME, null).orEmpty().ifBlank { "user" },
            accessToken = token,
        )
        _uiState.update {
            it.copy(session = session, hiddenLibraryIds = loadHiddenLibraryIds(currentProfileId()))
        }
        refresh()
    }

    private fun deviceId(): String {
        val p = prefs()
        val existing = p?.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = "nuviotv-" + buildString {
            repeat(8) { append(kotlin.random.Random.nextInt(16).toString(16)) }
        }
        p?.edit()?.putString(KEY_DEVICE_ID, generated)?.apply()
        return generated
    }

    private fun authHeaders(token: String?, contentType: String? = null): Map<String, String> {
        val authorization = buildString {
            append("MediaBrowser Client=\"Nuvio TV Jellyfin\", Device=\"Nuvio TV\", DeviceId=\"")
                .append(deviceId()).append("\", Version=\"0.1\"")
            if (!token.isNullOrBlank()) append(", Token=\"").append(token).append("\"")
        }
        return buildMap {
            put("Authorization", authorization)
            if (contentType != null) put("Content-Type", contentType)
        }
    }

    fun normalizeServerUrl(raw: String): String? {
        var url = raw.trim()
        if (url.isBlank()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url.trimEnd('/').takeIf { it.removePrefix("https://").removePrefix("http://").isNotBlank() }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        val base = normalizeServerUrl(serverUrl) ?: run {
            _uiState.update { it.copy(sessionError = "Enter a valid Jellyfin server address") }
            return
        }
        _uiState.update { it.copy(isLoadingSession = true, sessionError = null) }
        scope.launch {
            val result = try {
                Result.success(authenticate(base, username, password))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure<JellyfinSession>(error)
            }
            result.fold(
                onSuccess = { session ->
                    prefs()?.edit()?.apply {
                        putString(KEY_SERVER, session.serverUrl)
                        putString(KEY_SERVER_NAME, session.serverName)
                        putString(KEY_USER_ID, session.userId)
                        putString(KEY_USER_NAME, session.userName)
                        putString(KEY_TOKEN, session.accessToken)
                    }
                    _uiState.update {
                        JellyfinUiState(
                            session = session,
                            hiddenLibraryIds = loadHiddenLibraryIds(currentProfileId()),
                        )
                    }
                    refresh()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoadingSession = false, sessionError = error.message ?: "Sign-in failed")
                    }
                },
            )
        }
    }

    fun signOut() {
        librariesJob?.cancel()
        itemsJob?.cancel()
        detailJob?.cancel()
        prefs()?.edit()?.apply {
            remove(KEY_SERVER)
            remove(KEY_SERVER_NAME)
            remove(KEY_USER_ID)
            remove(KEY_USER_NAME)
            remove(KEY_TOKEN)
        }
        _uiState.value = JellyfinUiState()
    }

    private suspend fun authenticate(base: String, username: String, password: String): JellyfinSession {
        if (username.isBlank()) error("Enter your Jellyfin username")
        val response = httpCall(
            method = "POST",
            url = "$base/Users/AuthenticateByName",
            headers = authHeaders(token = null, contentType = "application/json"),
            body = buildJsonObject {
                put("Username", username)
                put("Pw", password)
            }.toString(),
        ) ?: error("Could not reach the Jellyfin server")
        if (response.first == 401) error("Wrong username or password")
        if (response.first !in 200..299) error("Jellyfin server error ${response.first}")
        val root = runCatching { json.parseToJsonElement(response.second).jsonObject }.getOrNull()
            ?: error("Unexpected response from the Jellyfin server")
        val token = root.string("AccessToken") ?: error("Jellyfin did not return an access token")
        val user = root["User"] as? JsonObject
        val userId = user?.string("Id") ?: error("Jellyfin did not return a user id")
        val serverName = runCatching {
            val info = httpCall("GET", "$base/System/Info/Public", emptyMap(), null)
            info?.takeIf { it.first in 200..299 }?.let { json.parseToJsonElement(it.second).jsonObject.string("ServerName") }
        }.getOrNull() ?: "Jellyfin"
        return JellyfinSession(base, serverName, userId, user.string("Name") ?: username, token)
    }

    /** Lists the playable files inside a nested folder (saga folders behave like shows). */
    private suspend fun call(session: JellyfinSession, path: String, query: Map<String, String?> = emptyMap()): JsonObject {
        val url = buildString {
            append(session.serverUrl).append(path)
            val params = query.filterValues { !it.isNullOrBlank() }
            if (params.isNotEmpty()) {
                append('?')
                params.entries.forEachIndexed { index, (name, value) ->
                    if (index > 0) append('&')
                    append(name).append('=').append(encodeQueryValue(value.orEmpty()))
                }
            }
        }
        val response = httpCall("GET", url, authHeaders(session.accessToken), null)
            ?: error("Could not reach the Jellyfin server")
        if (response.first == 401) error("Jellyfin session expired — sign in again")
        if (response.first !in 200..299) error("Jellyfin server error ${response.first}")
        return runCatching { json.parseToJsonElement(response.second).jsonObject }.getOrNull()
            ?: error("Unexpected response from the Jellyfin server")
    }

    private fun httpCall(method: String, url: String, headers: Map<String, String>, body: String?): Pair<Int, String>? =
        runCatching {
            val builder = Request.Builder().url(url)
            headers.forEach { (name, value) -> builder.header(name, value) }
            when (method.uppercase()) {
                "POST" -> builder.post((body.orEmpty()).toRequestBody("application/json".toMediaType()))
                "GET" -> builder.get()
                else -> builder.method(method.uppercase(), null)
            }
            client.newCall(builder.build()).execute().use { response ->
                response.code to (response.body?.string().orEmpty())
            }
        }.getOrNull()

    /** RFC 3986 query-value encoding. */
    private fun encodeQueryValue(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            when {
                code in 48..57 || code in 65..90 || code in 97..122 -> append(code.toChar())
                code == '-'.code || code == '_'.code || code == '.'.code || code == '~'.code -> append(code.toChar())
                else -> {
                    append('%')
                    append(code.toString(16).padStart(2, '0'))
                }
            }
        }
    }

    fun refresh() {
        val session = _uiState.value.session ?: return
        librariesJob?.cancel()
        librariesJob = scope.launch {
            val libraries = try {
                getLibraries(session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyList()
            }
            var selectionChanged = false
            _uiState.update { state ->
                val visible = libraries.filter { it.id !in state.hiddenLibraryIds }
                val selected = visible.firstOrNull { it.id == state.selectedLibraryId }
                    ?: visible.firstOrNull { lib -> lib.collectionType != null && lib.collectionType in VIDEO_LIBRARY_TYPES }
                    ?: visible.firstOrNull()
                selectionChanged = selected?.id != state.selectedLibraryId
                state.copy(libraries = libraries, selectedLibraryId = selected?.id)
            }
            if (selectionChanged || _uiState.value.items.isEmpty()) loadItems(reset = true)
        }
    }

    private suspend fun getLibraries(session: JellyfinSession): List<JellyfinLibrary> {
        val root = call(session, "/Users/${session.userId}/Views")
        return root.array("Items").orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val id = obj.string("Id") ?: return@mapNotNull null
            val name = obj.string("Name") ?: return@mapNotNull null
            JellyfinLibrary(id = id, name = name, collectionType = obj.string("CollectionType"))
        }
    }

    private suspend fun getItems(
        session: JellyfinSession,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = PAGE_SIZE,
        sortBy: String = "SortName",
        sortAscending: Boolean = true,
        searchTerm: String? = null,
        includeItemTypes: String? = null,
        recursive: Boolean = false,
    ): JellyfinItemPage {
        val root = call(
            session,
            "/Users/${session.userId}/Items",
            buildMap {
                put("SortBy", sortBy)
                put("SortOrder", if (sortAscending) "Ascending" else "Descending")
                put("StartIndex", startIndex.toString())
                put("Limit", limit.toString())
                put("Fields", "Overview,Genres,ProductionYear,CommunityRating,OfficialRating,RunTimeTicks,Container,UserData,SeriesId,SeriesName")
                put("Recursive", recursive.toString())
                put("ImageTypeLimit", "1")
                put("EnableImageTypes", "Primary,Backdrop,Thumb")
                if (parentId != null) put("ParentId", parentId)
                if (searchTerm != null) put("SearchTerm", searchTerm)
                if (includeItemTypes != null) put("IncludeItemTypes", includeItemTypes)
            },
        )
        val items = root.array("Items").orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let(::parseItem)
        }
        return JellyfinItemPage(items = items, totalRecordCount = root.int("TotalRecordCount") ?: items.size)
    }

    private suspend fun getItem(session: JellyfinSession, itemId: String): JellyfinItem? {
        val root = call(session, "/Users/${session.userId}/Items/$itemId")
        return parseItem(root)
    }

    private fun parseItem(obj: JsonObject): JellyfinItem? {
        val id = obj.string("Id") ?: return null
        val name = obj.string("Name") ?: return null
        val userData = obj["UserData"] as? JsonObject
        return JellyfinItem(
            id = id,
            name = name,
            type = obj.string("Type") ?: "",
            overview = obj.string("Overview")?.takeIf { it.isNotBlank() },
            productionYear = obj.int("ProductionYear"),
            communityRating = obj.double("CommunityRating"),
            officialRating = obj.string("OfficialRating"),
            runTimeTicks = obj.long("RunTimeTicks"),
            indexNumber = obj.int("IndexNumber"),
            parentIndexNumber = obj.int("ParentIndexNumber"),
            seriesId = obj.string("SeriesId"),
            seriesName = obj.string("SeriesName"),
            imageTag = (obj["ImageTags"] as? JsonObject)?.string("Primary"),
            backdropTag = (obj["BackdropImageTags"] as? JsonArray)
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.contentOrNull,
            container = obj.string("Container"),
            playbackPositionTicks = userData?.long("PlaybackPositionTicks"),
            playedPercentage = userData?.double("PlayedPercentage"),
        )
    }

    fun selectLibrary(libraryId: String) {
        val current = _uiState.value
        if (current.selectedLibraryId == libraryId) return
        _uiState.update {
            it.copy(selectedLibraryId = libraryId, searchQuery = "", items = emptyList(), totalItemCount = 0, itemsError = null)
        }
        loadItems(reset = true)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadItems(reset = true, debounce = true)
    }

    fun setSortLatestFirst(latest: Boolean) {
        val current = _uiState.value
        if (current.sortLatestFirst == latest) return
        _uiState.update { it.copy(sortLatestFirst = latest) }
        loadItems(reset = true)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingItems || !state.canLoadMore) return
        loadItems(reset = false)
    }

    fun retryItems() {
        loadItems(reset = true)
    }

    /**
     * Video libraries browse recursively by item type (movies/adult → Movie,Folder; shows →
     * Series,Folder) so saga folders appear as one browsable title whose files list like episodes.
     */
    private fun browseItemTypes(collectionType: String?): String? = when (collectionType?.lowercase()) {
        "movies" -> "Movie,Folder"
        "tvshows" -> "Series,Folder"
        "mixed" -> "Movie,Series,Folder"
        else -> null
    }

    private fun loadItems(reset: Boolean, debounce: Boolean = false) {
        val session = _uiState.value.session ?: return
        itemsJob?.cancel()
        itemsJob = scope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val state = _uiState.value
            if (state.session?.serverUrl != session.serverUrl) return@launch
            val searchTerm = state.searchQuery.trim().takeIf { it.isNotBlank() }
            _uiState.update {
                it.copy(
                    isLoadingItems = true,
                    itemsError = null,
                    items = if (reset) emptyList() else it.items,
                    totalItemCount = if (reset) 0 else it.totalItemCount,
                )
            }
            val result = try {
                val page = if (searchTerm == null) {
                    val library = state.libraries.firstOrNull { it.id == state.selectedLibraryId }
                    val browseTypes = browseItemTypes(library?.collectionType)
                    getItems(
                        session = session,
                        parentId = state.selectedLibraryId,
                        startIndex = if (reset) 0 else state.items.size,
                        sortBy = if (state.sortLatestFirst) "DateCreated" else "SortName",
                        sortAscending = !state.sortLatestFirst,
                        includeItemTypes = browseTypes,
                        recursive = browseTypes != null,
                    )
                } else {
                    searchVisibleLibraries(session, state, searchTerm)
                }
                Result.success(page)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure<JellyfinItemPage>(error)
            }
            result.fold(
                onSuccess = { page ->
                    _uiState.update { current ->
                        val merged = if (reset) page.items else current.items + page.items
                        val deduped = merged.distinctBy { it.id }
                        current.copy(
                            items = deduped,
                            totalItemCount = maxOf(page.totalRecordCount, deduped.size),
                            isLoadingItems = false,
                            itemsError = null,
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { current ->
                        current.copy(isLoadingItems = false, itemsError = error.message ?: "Could not load items")
                    }
                },
            )
        }
    }

    /** Server-side search across every VISIBLE library (parallel), merged and de-duplicated. */
    private suspend fun searchVisibleLibraries(
        session: JellyfinSession,
        state: JellyfinUiState,
        searchTerm: String,
    ): JellyfinItemPage {
        val visibleIds = state.libraries
            .filter { it.id !in state.hiddenLibraryIds }
            .map { it.id }
        if (visibleIds.isEmpty()) return JellyfinItemPage(emptyList(), 0)
        val pages = coroutineScope {
            visibleIds.map { libraryId ->
                async {
                    runCatching {
                        getItems(
                            session = session,
                            parentId = libraryId,
                            startIndex = 0,
                            limit = PAGE_SIZE,
                            sortBy = "SortName",
                            searchTerm = searchTerm,
                            includeItemTypes = "Movie,Series,Episode",
                            recursive = true,
                        )
                    }.getOrNull()
                }
            }.awaitAll()
        }
        val merged = pages
            .filterNotNull()
            .flatMap { it.items }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        return JellyfinItemPage(merged.take(MAX_MERGED_SEARCH_ITEMS), merged.size.coerceAtMost(MAX_MERGED_SEARCH_ITEMS))
    }

    /** Phone/back handling + selection for detail. */
    fun clearSelection() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                selectedItemId = null,
                selectedDetail = null,
                seasons = emptyList(),
                episodes = emptyList(),
                selectedSeasonId = null,
                isLoadingDetail = false,
                detailError = null,
            )
        }
    }

    fun selectItem(item: JellyfinItem) {
        detailJob?.cancel()
        val loadsChildren = item.isSeries || item.isFolder
        _uiState.update {
            it.copy(
                selectedItemId = item.id,
                selectedDetail = item,
                seasons = if (item.isSeries) it.seasons else emptyList(),
                episodes = if (loadsChildren) it.episodes else emptyList(),
                selectedSeasonId = null,
                isLoadingDetail = loadsChildren,
                detailError = null,
            )
        }
        when {
            item.isSeries -> loadSeriesDetail(item.id)
            item.isFolder -> loadFolderChildren(item.id)
        }
    }

    private fun loadSeriesDetail(seriesId: String) {
        val session = _uiState.value.session ?: return
        detailJob = scope.launch {
            val seasonsResult = try {
                Result.success(
                    call(session, "/Shows/$seriesId/Seasons", mapOf("userId" to session.userId))
                        .array("Items").orEmpty()
                        .mapNotNull { (it as? JsonObject)?.let(::parseItem) },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure<List<JellyfinItem>>(error)
            }
            val seasons = seasonsResult.getOrNull().orEmpty()
            _uiState.update { state ->
                if (state.selectedItemId != seriesId) return@update state
                if (seasonsResult.isFailure && seasons.isEmpty()) {
                    state.copy(isLoadingDetail = false, detailError = seasonsResult.exceptionOrNull()?.message ?: "Could not load seasons")
                } else {
                    state.copy(seasons = seasons, selectedSeasonId = seasons.firstOrNull()?.id, isLoadingDetail = false, detailError = null)
                }
            }
            _uiState.value.seasons.firstOrNull()?.id?.let { loadEpisodes(seriesId, it) }
        }
    }

    fun selectSeason(seasonId: String) {
        val state = _uiState.value
        val seriesId = state.selectedDetail?.id ?: return
        if (state.selectedSeasonId == seasonId) return
        _uiState.update { it.copy(selectedSeasonId = seasonId, episodes = emptyList()) }
        loadEpisodes(seriesId, seasonId)
    }

    private fun loadEpisodes(seriesId: String, seasonId: String) {
        val session = _uiState.value.session ?: return
        detailJob = scope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            val result = try {
                Result.success(
                    call(
                        session,
                        "/Shows/$seriesId/Episodes",
                        mapOf(
                            "userId" to session.userId,
                            "seasonId" to seasonId,
                            "Fields" to "Overview,CommunityRating,RunTimeTicks,Container,UserData,SeriesId,SeriesName",
                            "ImageTypeLimit" to "1",
                            "EnableImageTypes" to "Primary,Thumb",
                        ),
                    ).array("Items").orEmpty().mapNotNull { (it as? JsonObject)?.let(::parseItem) },
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure<List<JellyfinItem>>(error)
            }
            val episodes = result.getOrNull().orEmpty()
            _uiState.update { state ->
                if (state.selectedDetail?.id != seriesId || state.selectedSeasonId != seasonId) return@update state
                state.copy(
                    episodes = episodes,
                    isLoadingDetail = false,
                    detailError = if (result.isFailure && episodes.isEmpty()) {
                        result.exceptionOrNull()?.message ?: "Could not load episodes"
                    } else {
                        null
                    },
                )
            }
        }
    }

    private fun loadFolderChildren(folderId: String) {
        val session = _uiState.value.session ?: return
        detailJob = scope.launch {
            _uiState.update { it.copy(isLoadingDetail = true) }
            val result = try {
                Result.success(
                    getItems(
                        session = session,
                        parentId = folderId,
                        startIndex = 0,
                        limit = 500,
                        sortBy = "SortName",
                        sortAscending = true,
                        includeItemTypes = "Movie,Video,Episode",
                        recursive = true,
                    ).items,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure<List<JellyfinItem>>(error)
            }
            val children = result.getOrNull().orEmpty()
            _uiState.update { state ->
                if (state.selectedDetail?.id != folderId) return@update state
                state.copy(
                    episodes = children,
                    isLoadingDetail = false,
                    detailError = if (result.isFailure && children.isEmpty()) {
                        result.exceptionOrNull()?.message ?: "Could not load the folder contents"
                    } else {
                        null
                    },
                )
            }
        }
    }

    // ---- library visibility (per Nuvio profile) ----

    fun toggleLibraryHidden(libraryId: String) {
        val current = _uiState.value
        val updated = if (libraryId in current.hiddenLibraryIds) {
            current.hiddenLibraryIds - libraryId
        } else {
            current.hiddenLibraryIds + libraryId
        }
        prefs()?.edit()?.putString(hiddenKey(currentProfileId()), updated.joinToString("\n"))?.apply()
        _uiState.update { it.copy(hiddenLibraryIds = updated) }
        if (current.selectedLibraryId == libraryId && libraryId in updated) {
            val nextLibrary = current.libraries.firstOrNull { it.id !in updated }
            _uiState.update {
                it.copy(selectedLibraryId = nextLibrary?.id, searchQuery = "", items = emptyList(), totalItemCount = 0, itemsError = null)
            }
            loadItems(reset = true)
        }
    }

    /** Hidden libraries are per Nuvio profile; switching profiles swaps the visible set. */
    fun applyProfile(profileId: Int) {
        if (lastAppliedProfileId == null) {
            lastAppliedProfileId = profileId
            _uiState.update { it.copy(hiddenLibraryIds = loadHiddenLibraryIds(profileId)) }
            return
        }
        if (lastAppliedProfileId == profileId) return
        lastAppliedProfileId = profileId
        _uiState.update { it.copy(hiddenLibraryIds = loadHiddenLibraryIds(profileId)) }
        refresh()
        if (_uiState.value.session != null) loadItems(reset = true)
    }

    private fun loadHiddenLibraryIds(profileId: Int): Set<String> =
        prefs()?.getString(hiddenKey(profileId), null)
            ?.split('\n')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    private fun hiddenKey(profileId: Int): String = "$HIDDEN_KEY_PREFIX$profileId"

    private fun currentProfileId(): Int = runCatching { profileIdProvider() }.getOrDefault(1)

    // ---- playback / image URL helpers ----

    fun streamUrlFor(item: JellyfinItem): String? =
        _uiState.value.session?.let { session ->
            "${session.serverUrl}/Videos/${item.id}/stream?Static=true&api_key=${encodeQueryValue(session.accessToken)}"
        }

    fun posterUrlFor(item: JellyfinItem, maxWidth: Int = 480): String? {
        if (item.imageTag.isNullOrBlank()) return null
        return _uiState.value.session?.let { session ->
            "${session.serverUrl}/Items/${item.id}/Images/Primary?maxWidth=$maxWidth&quality=88&api_key=${encodeQueryValue(session.accessToken)}"
        }
    }

    fun backdropUrlFor(item: JellyfinItem): String? {
        if (item.backdropTag.isNullOrBlank()) return null
        return _uiState.value.session?.let { session ->
            "${session.serverUrl}/Items/${item.id}/Images/Backdrop/0?maxWidth=1280&quality=80&api_key=${encodeQueryValue(session.accessToken)}"
        }
    }

    // ---- small JSON helpers ----

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(key: String): Long? = this[key]?.jsonPrimitive?.longOrNull
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
}
