package com.kickstream.ui.player.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerQualitySwitchPolicyTest {
    @Test
    fun qualityChangeDoesNotUseManualSurfaceRebind() {
        assertFalse(shouldRebindVideoSurfaceOnQualitySwitch(previousHeight = 1080, resolvedHeight = 720))
        assertFalse(shouldRebindVideoSurfaceOnQualitySwitch(previousHeight = 720, resolvedHeight = 1080))
    }

    @Test
    fun qualityChangeDoesNotForceLiveEdgeSeek() {
        assertFalse(shouldForceLiveEdgeSeekOnQualitySwitch(previousHeight = 720, resolvedHeight = 480))
        assertFalse(shouldForceLiveEdgeSeekOnQualitySwitch(previousHeight = 480, resolvedHeight = 720))
    }

    @Test
    fun qualityChangeDoesNotRecreatePlayerView() {
        assertFalse(shouldRecreatePlayerViewOnQualitySwitch(previousHeight = 1080, resolvedHeight = 720))
        assertFalse(shouldRecreatePlayerViewOnQualitySwitch(previousHeight = 720, resolvedHeight = 480))
    }

    @Test
    fun initialSelectionAlsoDoesNotRecreatePlayerView() {
        assertFalse(shouldRecreatePlayerViewOnQualitySwitch(previousHeight = null, resolvedHeight = 720))
    }

    @Test
    fun reapplyWhenTrackSignatureChangesEvenIfHeightStaysSame() {
        assertTrue(
            shouldReapplyQualityConstraint(
                lastAppliedHeight = 480,
                targetHeight = 480,
                previousTrackSignature = "v:h=1080,720,480",
                currentTrackSignature = "v:h=1080,720,480,360",
            ),
        )
    }

    @Test
    fun noReapplyWhenHeightAndTrackSignatureAreUnchanged() {
        assertFalse(
            shouldReapplyQualityConstraint(
                lastAppliedHeight = 480,
                targetHeight = 480,
                previousTrackSignature = "v:h=1080,720,480",
                currentTrackSignature = "v:h=1080,720,480",
            ),
        )
    }
}
