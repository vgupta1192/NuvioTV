package com.nuvio.tv.domain.model

/**
 * Single source of truth for which addon catalogs are "Live TV" catalogs.
 *
 * Live TV catalogs are shown only on the Live TV screen; Home, Search and Library hide them.
 * Classification is by catalog type only, so the two sides always partition the same set.
 */
object LiveTvCatalogs {
    private val TYPES = setOf(
        "tv", "channel", "channels", "iptv", "live", "livetv", "live_tv",
        "stream", "streams", "broadcast", "broadcasts", "radio", "sports",
        "sport", "events", "event", "news", "cctv", "feed", "feeds"
    )

    fun isLiveTvType(type: String?): Boolean =
        type?.trim()?.lowercase() in TYPES
}

fun CatalogDescriptor.isLiveTvCatalog(): Boolean =
    LiveTvCatalogs.isLiveTvType(rawType) || LiveTvCatalogs.isLiveTvType(apiType)

fun Addon.hasLiveTvCatalogs(): Boolean =
    catalogs.any { it.isLiveTvCatalog() }

/** Keeps only this addon's Live TV catalogs. */
fun Addon.onlyLiveTvCatalogs(): Addon =
    copy(catalogs = catalogs.filter { it.isLiveTvCatalog() })

/** Drops this addon's Live TV catalogs; returns the same instance when there are none. */
fun Addon.withoutLiveTvCatalogs(): Addon =
    if (hasLiveTvCatalogs()) copy(catalogs = catalogs.filterNot { it.isLiveTvCatalog() }) else this

fun List<Addon>.withoutLiveTvCatalogs(): List<Addon> =
    map { it.withoutLiveTvCatalogs() }
