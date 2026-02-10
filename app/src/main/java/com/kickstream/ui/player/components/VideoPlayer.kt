package com.kickstream.ui.player.components

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

private const val TAG = "KickStream"
private const val MAX_RETRIES = 3
private const val RETRY_DELAY_MS = 2000L

@Composable
fun VideoPlayer(
    hlsUrl: String,
    modifier: Modifier = Modifier,
    onBufferingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    // Shared data source factory — reused across HLS URL changes
    val dataSourceFactory = remember { DefaultHttpDataSource.Factory() }

    val exoPlayer = remember {
        // Tuned buffer for live HLS on TV:
        // - Low minBuffer (5s) for fast start on live streams
        // - Small back-buffer (5s) to prevent unbounded memory growth
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 5_000,
                /* maxBufferMs */ 30_000,
                /* bufferForPlaybackMs */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000,
            )
            .setBackBuffer(
                /* backBufferDurationMs */ 5_000,
                /* retainBackBufferFromKeyframe */ false,
            )
            .build()

        var retryCount = 0

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
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
                        if (retryCount < MAX_RETRIES) {
                            retryCount++
                            Log.d(TAG, "ExoPlayer auto-retry $retryCount/$MAX_RETRIES in ${RETRY_DELAY_MS}ms")
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
                        onBufferingChanged(playbackState == Player.STATE_BUFFERING)
                        // Reset retry counter on successful playback
                        if (playbackState == Player.STATE_READY) {
                            retryCount = 0
                        }
                    }
                })
            }
    }

    LaunchedEffect(hlsUrl) {
        Log.d(TAG, "ExoPlayer loading HLS URL: $hlsUrl")
        // Configure live stream offset targets for lower latency
        val liveConfig = MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(3_000)
            .setMinOffsetMs(2_000)
            .setMaxOffsetMs(8_000)
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

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = false // TV uses D-pad, not on-screen controls
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        modifier = modifier,
    )
}
