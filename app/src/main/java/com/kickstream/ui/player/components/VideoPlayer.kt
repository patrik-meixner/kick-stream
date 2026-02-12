package com.kickstream.ui.player.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.graphics.Matrix
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kickstream.util.LifecycleStartStopEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
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
    val retryCountRef = remember { Ref(0) }
    val lastEmittedQualitiesRef = remember { Ref<List<VideoQuality>>(emptyList()) }
    val playerViewRef = remember { Ref<PlayerView?>(null) }
    // Tracks whether layout has been validated since last video size change.
    // Prevents redundant requestLayout() calls on every AndroidView.update {}.
    val layoutValidatedRef = remember { Ref(false) }

    val exoPlayer = remember(hlsUrl, selectedQualityHeight) {
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
                // Apply quality preference BEFORE prepare so decoder/surface starts
                // directly in the selected mode instead of hot-reconfiguring in place.
                applyInitialQualityPreference(this, selectedQualityHeight)
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
                        val targetHeight = currentSelectedQuality.value
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
                            // Invalidate so the next update {} re-validates layout
                            layoutValidatedRef.value = false
                            // On some TV emulator stacks, the underlying SurfaceView can
                            // retain source-sized layout params after decoder reconfig.
                            // Force full-bounds layout so rendering stretches back to container.
                            ensurePlayerViewFillsBounds(playerViewRef.value)
                        }
                    }
                })

                Log.d(TAG, "ExoPlayer loading HLS URL: $hlsUrl with quality=${if (selectedQualityHeight == 0) "Auto" else "${selectedQualityHeight}p"}")
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
                setMediaSource(hlsSource)
                prepare()
            }
    }

    LaunchedEffect(hlsUrl) {
        lastEmittedQualitiesRef.value = emptyList()
        currentOnQualitiesAvailable.value(emptyList())
        retryCountRef.value = 0
        layoutValidatedRef.value = false
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            exoPlayer.release()
        }
    }

    // Pause playback when app goes to background to save battery and prevent
    // stale buffered frames. On resume, seek to live edge for fresh content.
    LifecycleStartStopEffect(
        onStop = {
            exoPlayer.playWhenReady = false
        },
        onStart = {
            exoPlayer.playWhenReady = true
            // Seek to live edge so the user sees current content, not stale buffer
            if (exoPlayer.isCurrentMediaItemLive) {
                exoPlayer.seekToDefaultPosition()
            }
        },
    )

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                // Inflate from XML to guarantee surface_type is respected.
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
                    // Keep the workaround disabled for TextureView path.
                    setEnableComposeSurfaceSyncWorkaround(false)
                    useController = false // TV uses D-pad, not on-screen controls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Don't keep the last frame when player resets — avoids stale frame flash
                    setKeepContentOnPlayerReset(false)
                }
            },
            update = { view ->
                if (view.player !== exoPlayer) {
                    view.player = exoPlayer
                }
                playerViewRef.value = view
                // Skip redundant layout validation on recomposition — only re-run
                // after onVideoSizeChanged invalidates the flag.
                if (!layoutValidatedRef.value) {
                    ensurePlayerViewFillsBounds(view)
                    layoutValidatedRef.value = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

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

private fun ensurePlayerViewFillsBounds(playerView: PlayerView?) {
    if (playerView == null) return

    var changed = false

    val pvLp = playerView.layoutParams
    if (pvLp?.width != ViewGroup.LayoutParams.MATCH_PARENT ||
        pvLp.height != ViewGroup.LayoutParams.MATCH_PARENT
    ) {
        playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        changed = true
    }

    val contentFrame = playerView.findViewById<AspectRatioFrameLayout>(androidx.media3.ui.R.id.exo_content_frame)
    val cfLp = contentFrame?.layoutParams as? FrameLayout.LayoutParams
    if (contentFrame != null &&
        (cfLp == null ||
            cfLp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            cfLp.height != ViewGroup.LayoutParams.MATCH_PARENT ||
            cfLp.gravity != Gravity.CENTER)
    ) {
        contentFrame.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        changed = true
    }

    val surfaceView = playerView.videoSurfaceView
    val svLp = surfaceView?.layoutParams as? FrameLayout.LayoutParams
    if (surfaceView is TextureView &&
        (svLp == null ||
            svLp.width != ViewGroup.LayoutParams.MATCH_PARENT ||
            svLp.height != ViewGroup.LayoutParams.MATCH_PARENT ||
            svLp.gravity != Gravity.CENTER)
    ) {
        surfaceView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        changed = true
    }
    if (surfaceView is TextureView) {
        // Defensive reset: stale transform matrices on repeated decoder/surface
        // reconfiguration can shrink output into top-left and leave old content visible.
        surfaceView.setTransform(Matrix())
        surfaceView.scaleX = 1f
        surfaceView.scaleY = 1f
        surfaceView.translationX = 0f
        surfaceView.translationY = 0f
        surfaceView.pivotX = 0f
        surfaceView.pivotY = 0f
        changed = true
    }

    // Only trigger layout passes when something actually changed —
    // avoids expensive measure/layout cycles on every recomposition.
    if (changed) {
        contentFrame?.requestLayout()
        surfaceView?.requestLayout()
        playerView.requestLayout()
    }
}

/**
 * Apply quality selection using TrackSelectionOverride for explicit picks,
 * or clear overrides for Auto mode.
 *
 * TrackSelectionOverride forces ExoPlayer to use a specific track, causing a hard
 * decoder reset (flush + reinit). This prevents ghost frames from the old resolution
 * that setMaxVideoSize (soft constraint) would cause.
 */
private fun applyInitialQualityPreference(player: ExoPlayer, height: Int) {
    // During initial prepare, tracks are not known yet.
    // Use a max-height constraint first; exact overrides are applied once tracks arrive.
    val params = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
    if (height == 0) {
        params.setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        Log.d(TAG, "Quality: Auto (no constraints)")
    } else {
        params.setMaxVideoSize(Int.MAX_VALUE, height)
        Log.d(TAG, "Quality: prefer max height ${height}p (initial)")
    }
    player.trackSelectionParameters = params.build()
}
