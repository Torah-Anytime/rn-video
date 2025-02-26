package com.brentvatne.exoplayer

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.media3.common.AdViewProvider
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Assertions
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.SubtitleView
import com.brentvatne.common.api.ResizeMode
import com.brentvatne.common.api.SubtitleStyle
import com.brentvatne.common.api.ViewType
import com.brentvatne.common.toolbox.DebugLog

@UnstableApi
class ExoPlayerView : FrameLayout, AdViewProvider {
    var surfaceView: View? = null
        private set
    private lateinit var shutterView: View
    private lateinit var subtitleLayout: SubtitleView
    private lateinit var layout: AspectRatioFrameLayout
    private lateinit var componentListener: ComponentListener
    private var player: ExoPlayer? = null
    private val layoutParams: ViewGroup.LayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    private var adOverlayFrameLayout: FrameLayout? = null
    val isPlaying: Boolean
        get() = player != null && player?.isPlaying == true

    @ViewType.ViewType
    private var viewType = ViewType.VIEW_TYPE_SURFACE
    private var hideShutterView = false

    private var localStyle = SubtitleStyle()

    // Add required constructors for XML inflation
    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context)
    }

    // Initialize common components
    private fun init(context: Context) {
        componentListener = ComponentListener()

        val aspectRatioParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        )
        aspectRatioParams.gravity = Gravity.CENTER
        layout = AspectRatioFrameLayout(context)
        layout.layoutParams = aspectRatioParams

        shutterView = View(context)
        shutterView.layoutParams = layoutParams
        shutterView.setBackgroundColor(ContextCompat.getColor(context, android.R.color.black))

        subtitleLayout = SubtitleView(context)
        subtitleLayout.layoutParams = layoutParams
        subtitleLayout.setUserDefaultStyle()
        subtitleLayout.setUserDefaultTextSize()

        updateSurfaceView(viewType)

        layout.addView(shutterView, 1, layoutParams)
        if (localStyle.subtitlesFollowVideo) {
            layout.addView(subtitleLayout, layoutParams)
        }

        addViewInLayout(layout, 0, aspectRatioParams)
        if (!localStyle.subtitlesFollowVideo) {
            addViewInLayout(subtitleLayout, 1, layoutParams)
        }
    }

    fun clearVideoView() {
        Log.d("ExoPlayerView", "Clearing video view: $surfaceView")
        try {
            when (val view = surfaceView) {
                is TextureView -> {
                    if (player != null) {
                        player?.clearVideoTextureView(view)
                        Log.d("ExoPlayerView", "Cleared TextureView from player")
                    }
                }
                is SurfaceView -> {
                    if (player != null) {
                        player?.clearVideoSurfaceView(view)
                        Log.d("ExoPlayerView", "Cleared SurfaceView from player")
                    }
                }
                else -> {
                    Log.w(
                        "ExoPlayerView",
                        "Unexpected surfaceView type: ${surfaceView?.javaClass?.name}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ExoPlayerView", "Error clearing video view", e)
        }
    }

    fun setVideoView() {
        Log.d("ExoPlayerView", "Setting video view: $surfaceView")
        try {
            when (val view = surfaceView) {
                is TextureView -> {
                    if (player != null) {
                        player?.setVideoTextureView(view)
                        Log.d("ExoPlayerView", "Set TextureView to player")
                    }
                }
                is SurfaceView -> {
                    if (player != null) {
                        player?.setVideoSurfaceView(view)
                        Log.d("ExoPlayerView", "Set SurfaceView to player")
                    }
                }
                else -> {
                    Log.w(
                        "ExoPlayerView",
                        "Unexpected surfaceView type: ${surfaceView?.javaClass?.name}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ExoPlayerView", "Error setting video view", e)
        }
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        // ensure we reset subtitle style before reapplying it
        subtitleLayout.setUserDefaultStyle()
        subtitleLayout.setUserDefaultTextSize()

        if (style.fontSize > 0) {
            subtitleLayout.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.toFloat())
        }
        subtitleLayout.setPadding(
            style.paddingLeft,
            style.paddingTop,
            style.paddingTop,
            style.paddingBottom
        )
        if (style.opacity != 0.0f) {
            subtitleLayout.alpha = style.opacity
            subtitleLayout.visibility = View.VISIBLE
        } else {
            subtitleLayout.visibility = View.GONE
        }
        if (localStyle.subtitlesFollowVideo != style.subtitlesFollowVideo) {
            // No need to manipulate layout if value didn't change
            if (style.subtitlesFollowVideo) {
                removeViewInLayout(subtitleLayout)
                layout.addView(subtitleLayout, layoutParams)
            } else {
                layout.removeViewInLayout(subtitleLayout)
                addViewInLayout(subtitleLayout, 1, layoutParams, false)
            }
            requestLayout()
        }
        localStyle = style
    }

    fun setShutterColor(color: Int) {
        shutterView.setBackgroundColor(color)
    }

    fun updateSurfaceView(@ViewType.ViewType viewType: Int) {
        this.viewType = viewType
        var viewNeedRefresh = false
        when (viewType) {
            ViewType.VIEW_TYPE_SURFACE, ViewType.VIEW_TYPE_SURFACE_SECURE -> {
                if (surfaceView !is SurfaceView) {
                    surfaceView = SurfaceView(context)
                    viewNeedRefresh = true
                }
                (surfaceView as SurfaceView).setSecure(viewType == ViewType.VIEW_TYPE_SURFACE_SECURE)
            }

            ViewType.VIEW_TYPE_TEXTURE -> {
                if (surfaceView !is TextureView) {
                    surfaceView = TextureView(context)
                    viewNeedRefresh = true
                }
                // Support opacity properly:
                (surfaceView as TextureView).isOpaque = false
            }

            else -> {
                DebugLog.wtf(TAG, "Unexpected texture view type: $viewType")
            }
        }

        if (viewNeedRefresh) {
            surfaceView?.layoutParams = layoutParams

            if (layout.getChildAt(0) != null) {
                layout.removeViewAt(0)
            }
            layout.addView(surfaceView, 0, layoutParams)

            if (this.player != null) {
                setVideoView()
            }
        }
    }

    private var adsShown = false
    fun showAds() {
        if (!adsShown) {
            adOverlayFrameLayout = FrameLayout(context)
            layout.addView(adOverlayFrameLayout, layoutParams)
            adsShown = true
        }
    }

    fun hideAds() {
        if (adsShown) {
            layout.removeView(adOverlayFrameLayout)
            adOverlayFrameLayout = null
            adsShown = false
        }
    }

    fun updateShutterViewVisibility() {
        shutterView.visibility = if (this.hideShutterView) {
            View.INVISIBLE
        } else {
            View.VISIBLE
        }
    }

    override fun requestLayout() {
        super.requestLayout()
        post(measureAndLayout)
    }

    // AdsLoader.AdViewProvider implementation.
    override fun getAdViewGroup(): ViewGroup =
        Assertions.checkNotNull(
            adOverlayFrameLayout,
            "exo_ad_overlay must be present for ad playback"
        )

    /**
     * Set the {@link ExoPlayer} to use. The {@link ExoPlayer#addListener} method of the
     * player will be called and previous
     * assignments are overridden.
     *
     * @param newPlayer The {@link ExoPlayer} to use.
     */
    fun setPlayer(newPlayer: ExoPlayer?) {
        Log.d("ExoPlayerView", "Setting player: $newPlayer, current player: $player")

        // If player is already set to the same player, do nothing
        if (this.player == newPlayer) {
            Log.d("ExoPlayerView", "Player is already set to this view")
            return
        }

        // If there's an existing player, remove its listener and clear video view
        if (this.player != null) {
            this.player!!.removeListener(componentListener)
            clearVideoView()
        }

        // Set the new player
        this.player = newPlayer

        // Update shutter view visibility
        updateShutterViewVisibility()

        // If the new player is not null, attach it and add listener
        if (newPlayer != null) {
            setVideoView()
            newPlayer.addListener(componentListener)
        }

        Log.d("ExoPlayerView", "Player set: ${this.player}")
    }

    /**
     * Sets the resize mode which can be of value {@link ResizeMode.Mode}
     *
     * @param resizeMode The resize mode.
     */
    fun setResizeMode(@ResizeMode.Mode resizeMode: Int) {
        if (layout.resizeMode != resizeMode) {
            layout.resizeMode = resizeMode
            post(measureAndLayout)
        }
    }

    fun setHideShutterView(hideShutterView: Boolean) {
        this.hideShutterView = hideShutterView
        updateShutterViewVisibility()
    }

    private val measureAndLayout: Runnable = Runnable {
        measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        )
        layout(left, top, right, bottom)
    }

    private fun updateForCurrentTrackSelections(tracks: Tracks?) {
        if (tracks == null) {
            return
        }
        val groups = tracks.groups

        for (group in groups) {
            if (group.type == C.TRACK_TYPE_VIDEO && group.length > 0) {
                // get the first track of the group to identify aspect ratio
                val format = group.getTrackFormat(0)
                if (format.width > 0 || format.height > 0) {
                    layout.updateAspectRatio(format)
                }
                return
            }
        }
        // no video tracks, in that case refresh shutterView visibility
        updateShutterViewVisibility()
    }

    fun invalidateAspectRatio() {
        // Resetting aspect ratio will force layout refresh on next video size changed
        layout.invalidateAspectRatio()
    }

    private inner class ComponentListener : Player.Listener {
        override fun onCues(cues: List<Cue>) {
            subtitleLayout.setCues(cues)
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.height == 0 || videoSize.width == 0) {
                // When changing video track we receive an ghost state with height / width = 0
                // No need to resize the view in that case
                return
            }
            // Here we use updateForCurrentTrackSelections to have a consistent behavior.
            // according to: https://github.com/androidx/media/issues/1207
            // sometimes media3 send bad Video size information
            player?.let {
                updateForCurrentTrackSelections(it.currentTracks)
            }
        }

        override fun onRenderedFirstFrame() {
            shutterView.visibility = INVISIBLE
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateForCurrentTrackSelections(tracks)
        }
    }

    companion object {
        private const val TAG = "ExoPlayerView"
    }
}
