package com.nuvio.tv.updater

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.updater.model.AppUpdate
import javax.inject.Inject
import javax.inject.Singleton

internal class NoEligibleUpdateException(channel: UpdateChannel) :
    IllegalStateException("No compatible APK release found for ${channel.storedValue} channel")

@Singleton
class UpdateRepository @Inject constructor(
    private val gitHubReleaseApi: GitHubReleaseApi
) {

    suspend fun getLatestUpdate(channel: UpdateChannel): Result<AppUpdate> {
        return runCatching {
            val owner = BuildConfig.GITHUB_OWNER
            val repo = BuildConfig.GITHUB_REPO

            // Self-host fork patch: the fork publishes every CI build (as prereleases) with the
            // CI run number in the asset names; newest build number wins, channel does not matter.
            val forkResponse = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
            if (!forkResponse.isSuccessful) {
                error("GitHub API error: ${forkResponse.code()}")
            }
            val (forkRelease, forkAsset, forkBuild) = ForkBuild
                .newest(forkResponse.body() ?: error("Empty GitHub release response"))
                ?: throw NoEligibleUpdateException(channel)
            return@runCatching AppUpdate(
                tag = ForkBuild.tag(forkBuild),
                title = "${forkRelease.name?.takeIf { it.isNotBlank() } ?: forkRelease.tagName.orEmpty()} (build $forkBuild)".trim(),
                notes = forkRelease.body.orEmpty(),
                releaseUrl = forkRelease.htmlUrl,
                assetName = forkAsset.name,
                assetUrl = forkAsset.browserDownloadUrl,
                assetSizeBytes = forkAsset.size
            )

            @Suppress("UNREACHABLE_CODE")
            val releases = when (channel) {
                UpdateChannel.STABLE -> {
                    val response = gitHubReleaseApi.getLatestRelease(owner = owner, repo = repo)
                    if (!response.isSuccessful) {
                        error("GitHub API error: ${response.code()}")
                    }
                    listOf(response.body() ?: error("Empty GitHub release response"))
                }
                UpdateChannel.BETA -> {
                    val response = gitHubReleaseApi.getReleases(owner = owner, repo = repo)
                    if (!response.isSuccessful) {
                        error("GitHub API error: ${response.code()}")
                    }
                    response.body() ?: error("Empty GitHub release response")
                }
            }
            val releaseWithAsset = ReleaseSelector
                .eligibleReleases(releases, channel)
                .firstNotNullOfOrNull { release ->
                    AbiSelector.chooseBestApkAsset(release.assets)?.let { asset ->
                        release to asset
                    }
                }
                ?: throw NoEligibleUpdateException(channel)
            val (dto, asset) = releaseWithAsset

            val tag = dto.tagName?.takeIf { it.isNotBlank() }
                ?: dto.name?.takeIf { it.isNotBlank() }
                ?: error("Release has no tag/name")

            AppUpdate(
                tag = tag,
                title = dto.name?.takeIf { it.isNotBlank() } ?: tag,
                notes = dto.body.orEmpty(),
                releaseUrl = dto.htmlUrl,
                assetName = asset.name,
                assetUrl = asset.browserDownloadUrl,
                assetSizeBytes = asset.size
            )
        }
    }
}
