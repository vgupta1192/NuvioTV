package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.dto.GitHubAssetDto
import com.nuvio.tv.data.remote.dto.GitHubReleaseDto

/**
 * Self-host fork patch: updates come from github.com/vgupta1192/NuvioTV releases. CI names assets
 * NuvioTV-<version>-b<run number>-<abi>-<debug|release>.apk and builds this app with
 * BuildConfig.FORK_BUILD = that run number, so "newer" = higher build number (the upstream
 * version name stays the same across fork rebuilds). Only assets of the installed variant
 * (debug = com.nuviodebug.com, release = com.nuvio.tv) are offered, so the update installs over it.
 */
internal object ForkBuild {
    private val buildPattern = Regex("-b(\\d+)-")
    private const val TAG_PREFIX = "fork-build-"

    fun buildNumber(assetName: String): Int? =
        buildPattern.find(assetName)?.groupValues?.get(1)?.toIntOrNull()

    fun tag(build: Int): String = "$TAG_PREFIX$build"

    fun isNewer(tag: String): Boolean {
        val remote = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return false
        return remote > BuildConfig.FORK_BUILD
    }

    fun newest(releases: List<GitHubReleaseDto>): Triple<GitHubReleaseDto, GitHubAssetDto, Int>? {
        val variantSuffix = if (BuildConfig.IS_DEBUG_BUILD) "-debug.apk" else "-release.apk"
        return releases
            .asSequence()
            .filterNot(GitHubReleaseDto::draft)
            .mapNotNull { release ->
                val candidates = release.assets.filter {
                    it.name.endsWith(variantSuffix, ignoreCase = true) && buildNumber(it.name) != null
                }
                val newestBuild = candidates.mapNotNull { buildNumber(it.name) }.maxOrNull()
                    ?: return@mapNotNull null
                val asset = AbiSelector.chooseBestApkAsset(
                    candidates.filter { buildNumber(it.name) == newestBuild }
                ) ?: return@mapNotNull null
                Triple(release, asset, newestBuild)
            }
            .maxByOrNull { it.third }
    }
}
