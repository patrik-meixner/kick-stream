package com.kickstream.ui.player.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kickstream.R
import com.kickstream.data.api.NetworkModule

private const val TAG = "KickStream"
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 2000L
private const val SHUTTER_TIMEOUT_MS = 1500L
/** Minimum video height exposed in the quality menu (below this, scaling breaks on TV) */
private const val MIN_QUALITY_HEIGHT = 480

/**
 * Mutable ref that does NOT trigger recomposition.
 */
private class Ref<T>(var value: T)

/** Represents a selectable video quality option */
data class VideoQuality(
    val label: String,
    val height: Int,       // 0 for "Auto"
    val isSelected: Boolean = false,
)

@Composable
fun VideoPlayer(
    hlsUrl: String,
    modifier: Modifier = Modifier,
    onBufferingChanged: (Boolean) -> Unit = {},
    onQualitiesAvailable: (List<VideoQuality>) -> Unit = {},
    selectedQualityHeight: Int = 0,
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Shared data source factory — routes HLS through the SSL-permissive OkHttpClient
    // so TV emulators with outdated CA stores can validate Kick CDN certificates.
    val dataSourceFactory = remember { OkHttpDataSource.Factory(NetworkModule.permissiveSslClient) }

    val currentOnBufferingChanged = rememberUpdatedState(onBufferingChanged)
    val currentOnQualitiesAvailable = rememberUpdatedState(onQualitiesAvailable)
    val currentSelectedQuality = rememberUpdatedState(selectedQualityHeight)
    val lastTracksRef = remember { Ref<Tracks?>(null) }
    val retryCountRef = remember { Ref(0) }
    val lastAppliedHeightRef = remember { Ref<Int?>(null) }
    val lastVideoTrackSignatureRef = remember { Ref<String?>(null) }
    val lastEmittedQualitiesRef = remember { Ref<List<VideoQuality>>(emptyList()) }
    val playerViewEpoch = remember { mutableStateOf(0) }
    // Black shutter overlay: shown during quality switch, dismissed on onVideoSizeChanged
    val shutterVisible = remember { mutableStateOf(false) }

    val exoPlayer = remember {
        // Tuned buffer for live HLS on TV:
        // - Generous buffers to prevent micro-stutter and audio pops
        // - Small back-buffer (5s) to prevent unbounded memory growth
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 4_000,
                /* bufferForPlaybackAfterRebufferMs */ 7_000,
            )
            .setBackBuffer(
                /* backBufferDurationMs */ 5_000,
                /* retainBackBufferFromKeyframe */ false,
            )
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus */ true,
            )
            .build()
            .apply {
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_OFF
                // For live streams, stay near the live edge
                setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer error: ${error.errorCodeName} -- ${error.message}", error)
                        // Auto-retry on error (network hiccups, transient failures)
                        val retryCount = retryCountRef.value
                        if (retryCount < MAX_RETRIES) {
                            retryCountRef.value = retryCount + 1
                            Log.d(TAG, "ExoPlayer auto-retry ${retryCountRef.value}/$MAX_RETRIES in ${RETRY_DELAY_MS}ms")
                            mainHandler.postDelayed({ prepare() }, RETRY_DELAY_MS)
                        } else {
                            Log.w(TAG, "ExoPlayer max retries reached, giving up")
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val state = when (playbackState) {
                            Player.STATE_IDLE -> "IDLE"
                            Player.STATE_BUFFERING -> "BUFFERING"
                            Player.STATE_READY -> "READY"
                            Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN($playbackState)"
                        }
                        Log.d(TAG, "ExoPlayer state: $state")
                        // Notify about buffering state for UI overlay
                        currentOnBufferingChanged.value(playbackState == Player.STATE_BUFFERING)
                        // Reset retry counter on successful playback
                        if (playbackState == Player.STATE_READY) {
                            retryCountRef.value = 0
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        lastTracksRef.value = tracks
                        val targetHeight = currentSelectedQuality.value
                        val trackSignature = buildVideoTrackSignature(tracks)
                        if (
                            shouldReapplyQualityConstraint(
                                lastAppliedHeight = lastAppliedHeightRef.value,
                                targetHeight = targetHeight,
                                previousTrackSignature = lastVideoTrackSignatureRef.value,
                                currentTrackSignature = trackSignature,
                            )
                        ) {
                            applyQualityConstraint(this@apply, targetHeight)
                            lastAppliedHeightRef.value = targetHeight
                        }
                        lastVideoTrackSignatureRef.value = trackSignature
                        val qualities = extractQualities(tracks, targetHeight)
                        if (qualities != lastEmittedQualitiesRef.value) {
                            lastEmittedQualitiesRef.value = qualities
                            currentOnQualitiesAvailable.value(qualities)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        Log.d(TAG, "Video size changed: ${videoSize.width}x${videoSize.height}")
                        // Ignore transient 0x0 callbacks around decoder resets.
                        // Dismissing only on real dimensions avoids exposing half-transition states.
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            shutterVisible.value = false
                        }
                    }
                })
            }
    }

    LaunchedEffect(selectedQualityHeight) {
        val tracks = lastTracksRef.value
        val targetHeight = selectedQualityHeight
        val previousHeight = lastAppliedHeightRef.value
        // Show black shutter when quality actually changes (not on initial load)
        if (previousHeight != null && previousHeight != targetHeight) {
            shutterVisible.value = true
            if (shouldRecreatePlayerViewOnQualitySwitch(previousHeight, targetHeight)) {
                playerViewEpoch.value += 1
            }

            // Safety timeout: dismiss shutter if onVideoSizeChanged doesn't fire
            // (e.g., same-resolution tracks with different bitrates)
            mainHandler.postDelayed({ shutterVisible.value = false }, SHUTTER_TIMEOUT_MS)

            // Apply the track override (triggers codec reset internally).
            // Do NOT manually rebind the SurfaceView here. It can desync PlayerView's
            // internal sizing pipeline and collapse video into top-left after switch.
            applyQualityConstraint(exoPlayer, targetHeight)

            if (shouldForceLiveEdgeSeekOnQualitySwitch(previousHeight, targetHeight)) {
                // Keep this behind a policy hook: forced seek on every switch can cause
                // visible loop/jump artifacts on live streams during rapid transitions.
                mainHandler.postDelayed({
                    if (exoPlayer.isCurrentMediaItemLive) {
                        exoPlayer.seekToDefaultPosition()
                    }
                }, 100)
            }
        } else {
            applyQualityConstraint(exoPlayer, targetHeight)
        }
        lastAppliedHeightRef.value = targetHeight
        if (tracks != null) {
            val qualities = extractQualities(tracks, targetHeight)
            if (qualities != lastEmittedQualitiesRef.value) {
                lastEmittedQualitiesRef.value = qualities
                currentOnQualitiesAvailable.value(qualities)
            }
        }
    }

    LaunchedEffect(hlsUrl) {
        lastTracksRef.value = null
        lastEmittedQualitiesRef.value = emptyList()
        lastVideoTrackSignatureRef.value = null
        currentOnQualitiesAvailable.value(emptyList())
        retryCountRef.value = 0
        Log.d(TAG, "ExoPlayer loading HLS URL: $hlsUrl")
        // Configure live stream offset targets — balance latency vs. smooth playback.
        // Too aggressive (< 4s) causes buffer underruns → choppy video + audio pops.
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(6_000)
            .setMinOffsetMs(4_000)
            .setMaxOffsetMs(15_000)
            .build()
        val mediaItem = MediaItem.Builder()
            .setUri(hlsUrl)
            .setLiveConfiguration(liveConfig)
            .build()
        val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(true)
            .createMediaSource(mediaItem)
        exoPlayer.setMediaSource(hlsSource)
        exoPlayer.prepare()
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            exoPlayer.release()
        }
    }

    Box(modifier = modifier) {
        key(playerViewEpoch.value) {
            AndroidView(
                factory = { ctx ->
                    // Inflate from XML to guarantee surface_type="texture_view" is respected.
                    // TextureView keeps rendering inside the normal view composition tree.
                    // This avoids SurfaceView multi-plane artifacts seen during repeated
                    // quality transitions on newer emulator/device graphics stacks.
                    val view = LayoutInflater.from(ctx)
                        .inflate(R.layout.view_player, null) as PlayerView
                    view.apply {
                        // CRITICAL: inflate(…, null) discards XML layout params.
                        // Without explicit MATCH_PARENT the PlayerView collapses to
                        // the video's native resolution after a codec transition
                        // (quality switch) because AspectRatioFrameLayout has no
                        // parent-size reference to stretch against.
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player = exoPlayer
                        useController = false // TV uses D-pad, not on-screen controls
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        // Don't keep the last frame when player resets — avoids stale frame flash
                        setKeepContentOnPlayerReset(false)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Black shutter: covers player during quality transition to hide artifacts.
        // Dismissed by onVideoSizeChanged when new resolution renders, or by safety timeout.
        if (shutterVisible.value) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    }
}

/**
 * Policy hook kept explicit so regressions are caught in tests:
 * manual surface rebinding during quality switch is forbidden.
 */
@Suppress("UNUSED_PARAMETER")
internal fun shouldRebindVideoSurfaceOnQualitySwitch(
    previousHeight: Int?,
    resolvedHeight: Int,
): Boolean = false

@Suppress("UNUSED_PARAMETER")
internal fun shouldForceLiveEdgeSeekOnQualitySwitch(
    previousHeight: Int?,
    resolvedHeight: Int,
): Boolean = false

internal fun shouldRecreatePlayerViewOnQualitySwitch(
    previousHeight: Int?,
    resolvedHeight: Int,
): Boolean = previousHeight != null && previousHeight != resolvedHeight

/** Extract available video qualities from ExoPlayer track groups */
private fun extractQualities(tracks: Tracks, selectedQualityHeight: Int): List<VideoQuality> {
    val heights = mutableSetOf<Int>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            if (format.height > 0) {
                heights.add(format.height)
            }
        }
    }
    if (heights.isEmpty()) return emptyList()

    // Drop resolutions below MIN_QUALITY_HEIGHT — they don't scale properly on TV
    val sorted = heights.filter { it >= MIN_QUALITY_HEIGHT }.sortedDescending()
    if (sorted.isEmpty()) return emptyList()

    val selectedHeight = when {
        selectedQualityHeight == 0 -> 0
        sorted.contains(selectedQualityHeight) -> selectedQualityHeight
        else -> 0
    }
    val qualities = mutableListOf(
        VideoQuality(label = "Auto", height = 0, isSelected = selectedHeight == 0),
    )
    sorted.forEach { h ->
        qualities.add(
            VideoQuality(
                label = "${h}p",
                height = h,
                isSelected = selectedHeight == h,
            ),
        )
    }
    return qualities
}

internal fun shouldReapplyQualityConstraint(
    lastAppliedHeight: Int?,
    targetHeight: Int,
    previousTrackSignature: String?,
    currentTrackSignature: String,
): Boolean {
    if (lastAppliedHeight != targetHeight) return true
    return previousTrackSignature != currentTrackSignature
}

private fun buildVideoTrackSignature(tracks: Tracks): String {
    val groups = mutableListOf<String>()
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        val formats = mutableListOf<String>()
        for (i in 0 until group.length) {
            val format = group.getTrackFormat(i)
            formats += "${format.id ?: "no-id"}:${format.width}x${format.height}@${format.bitrate}"
        }
        groups += formats.joinToString(separator = ",")
    }
    return groups.joinToString(separator = "|")
}

/**
 * Apply quality selection using TrackSelectionOverride for explicit picks,
 * or clear overrides for Auto mode.
 *
 * TrackSelectionOverride forces ExoPlayer to use a specific track, causing a hard
 * decoder reset (flush + reinit). This prevents ghost frames from the old resolution
 * that setMaxVideoSize (soft constraint) would cause.
 */
private fun applyQualityConstraint(player: ExoPlayer, height: Int) {
    val params = player.trackSelectionParameters.buildUpon()
        // Always clear previous constraints — we use overrides instead
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)

    if (height == 0) {
        Log.d(TAG, "Quality: Auto (no constraints)")
    } else {
        // Find the video track group and the specific track index matching the height
        val tracks = player.currentTracks
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_VIDEO) continue
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.height == height) {
                    val override = TrackSelectionOverride(group.mediaTrackGroup, i)
                    params.addOverride(override)
                    Log.d(TAG, "Quality: override to ${format.width}x${format.height} (track $i)")
                    player.trackSelectionParameters = params.build()
                    return
                }
            }
        }
        // Fallback: if exact track not found, use maxVideoSize as hint
        params.setMaxVideoSize(Int.MAX_VALUE, height)
        Log.d(TAG, "Quality: max height ${height}p (fallback, no exact track match)")
    }

    player.trackSelectionParameters = params.build()
}
