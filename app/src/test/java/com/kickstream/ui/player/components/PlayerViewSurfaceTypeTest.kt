package com.kickstream.ui.player.components

import android.view.LayoutInflater
import android.view.SurfaceView
import androidx.test.core.app.ApplicationProvider
import com.kickstream.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerViewSurfaceTypeTest {
    @Test
    fun playerViewDefaultsSurfaceView() {
        // Inflate view_player.xml and verify it uses SurfaceView (not TextureView).
        // This matches how VideoPlayer.kt inflates the layout at runtime.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val playerView = LayoutInflater.from(context)
            .inflate(R.layout.view_player, null) as androidx.media3.ui.PlayerView
        val surface = playerView.videoSurfaceView
        assertTrue("Expected SurfaceView but got ${surface?.javaClass?.name}", surface is SurfaceView)
    }
}
