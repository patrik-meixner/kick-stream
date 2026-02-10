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
    fun playerViewUsesSurfaceViewSurface() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = LayoutInflater.from(context).inflate(R.layout.view_player, null, false)
        val playerView = view as androidx.media3.ui.PlayerView
        val surface = playerView.videoSurfaceView
        assertTrue("Expected SurfaceView but got ${surface?.javaClass?.name}", surface is SurfaceView)
    }
}
