package com.brentvatne.exoplayer
import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.LegacyPlayerControlView
import com.brentvatne.common.api.ControlsConfig
import com.brentvatne.common.api.ResizeMode
import com.brentvatne.common.api.ViewType
import com.brentvatne.react.R

@UnstableApi
class ExoPlayerFullscreenVideoActivity : AppCompatActivity() {

    private var reactExoplayerView: ReactExoplayerView? = null
    private var playerView: ExoPlayerView? = null
    private var playerControlView: LegacyPlayerControlView? = null
    private var controlsConfig: ControlsConfig? = null
    private var isShowing = true
    private var player: ExoPlayer? = null
    private var originalPlayerWasPlaying = false
    private var syncingState = false
    private var audioMuted = true // Always start with audio muted on the fullscreen player

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateCheckHandler = Handler(Looper.getMainLooper())
    private val stateCheckRunnable = object : Runnable {
        override fun run() {
            syncPlaybackState()
            stateCheckHandler.postDelayed(this, 500) // Check every 500ms
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve and validate the ReactExoplayerView ID
        val reactExoplayerViewId = intent.getIntExtra(EXTRA_REACT_EXOPLAYER_VIEW_ID, -1)
        reactExoplayerView = ReactExoplayerView.getViewInstance(reactExoplayerViewId)

        if (reactExoplayerView == null) {
            Log.e(TAG, "No ReactExoplayerView found for ID: $reactExoplayerViewId")
            finishWithError()
            return
        }

        // IMPORTANT: Pause the original player to prevent audio duplication
        val wasPlaying = reactExoplayerView?.player?.isPlaying ?: false
        originalPlayerWasPlaying = wasPlaying
        reactExoplayerView?.player?.playWhenReady = false // Pause original player

        // Keep the screen on during playback
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set screen orientation based on intent extra
        val orientation = intent.getStringExtra(EXTRA_ORIENTATION)
        requestedOrientation = getScreenOrientation(orientation)

        // Retrieve and handle controls configuration
        controlsConfig = intent.getParcelableExtra(EXTRA_CONTROLS_CONFIG) ?: ControlsConfig()

        setContentView(R.layout.exo_player_fullscreen_video)

        // Initialize player view
        playerView = findViewById(R.id.player_view)

        // Create a new player for fullscreen
        createNewPlayer()

        // Setup player controls and listeners
        setupPlayerControls()

        // Apply control configurations
        applyControlsConfig()

        // Hide system UI for immersive mode
        hideSystemUI()

        // Start periodic state checks to keep players in sync
        stateCheckHandler.post(stateCheckRunnable)
    }

    private fun getScreenOrientation(orientation: String?): Int {
        return when (orientation) {
            "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            "sensor" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
    }

    private fun createNewPlayer() {
        try {
            val originalPlayer = reactExoplayerView?.player as? ExoPlayer ?: return

            // Get initial playback state
            val wasPlaying = originalPlayer.isPlaying
            originalPlayerWasPlaying = wasPlaying

            // Create a new player that matches the original
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true)

            player = ExoPlayer.Builder(this, renderersFactory)
                .build()

            // Get the original media item
            val mediaItem = originalPlayer.currentMediaItem ?: return

            // Set the player to the view
            playerView?.setPlayer(player)

            // Set up the media item
            player?.setMediaItem(mediaItem)

            // Copy playback position
            player?.seekTo(originalPlayer.currentPosition)

            // Set same playback parameters and rate
            player?.playbackParameters = originalPlayer.playbackParameters

            // Prepare the player
            player?.prepare()

            // CRUCIAL: Mute the audio on the fullscreen player to avoid echo
            player?.volume = 0f

            // Add listener to sync state changes back to main player
            player?.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!syncingState) {
                        syncingState = true
                        // When fullscreen player changes play state, update originalPlayer too
                        reactExoplayerView?.player?.playWhenReady = playWhenReady
                        syncingState = false
                    }
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    // When fullscreen player seeks, update originalPlayer too
                    if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                        reactExoplayerView?.player?.seekTo(newPosition.positionMs)
                    }
                }
            })

            // Set playback state to match original after a delay to ensure proper setup
            mainHandler.postDelayed({
                player?.playWhenReady = originalPlayerWasPlaying
            }, 300)

            Log.d(TAG, "New player created and prepared")
        } catch (e: Exception) {
            Log.e(TAG, "Error creating player", e)
            finishWithError()
        }
    }

    private fun syncPlaybackState() {
        if (syncingState || player == null || reactExoplayerView?.player == null) return

        syncingState = true
        try {
            val originalPlayer = reactExoplayerView?.player
            val fullscreenPlayer = player

            // Check if pause/play state has changed in original player
            if (originalPlayer?.playWhenReady != fullscreenPlayer?.playWhenReady) {
                fullscreenPlayer?.playWhenReady = originalPlayer?.playWhenReady ?: false
            }

            // Check if position has changed significantly in original player
            val posDifference = Math.abs((originalPlayer?.currentPosition ?: 0) -
                (fullscreenPlayer?.currentPosition ?: 0))
            if (posDifference > 2000) { // More than 2 seconds difference
                fullscreenPlayer?.seekTo(originalPlayer?.currentPosition ?: 0)
            }
        } finally {
            syncingState = false
        }
    }

    private fun setupPlayerControls() {
        playerView?.setOnClickListener { togglePlayerControlVisibility() }

        playerControlView = findViewById(R.id.player_controls)
        player?.let { playerControlView?.setPlayer(it) }

        setupFullscreenButton()
        setupPlayPauseButtons()
    }

    private fun setupFullscreenButton() {
        val fullscreenIcon = playerControlView?.findViewById<ImageButton>(R.id.exo_fullscreen)
        fullscreenIcon?.apply {
            setImageResource(androidx.media3.ui.R.drawable.exo_icon_fullscreen_exit)
            setBackgroundResource(android.R.color.transparent)
            visibility = View.VISIBLE
            setOnClickListener {
                mainHandler.post {
                    reactExoplayerView?.setFullscreen(false)
                    finish()
                }
            }
        }
    }

    private fun setupPlayPauseButtons() {
        playerControlView?.findViewById<View>(R.id.exo_play)?.setOnClickListener {
            if (player?.playbackState == Player.STATE_ENDED) {
                player?.seekTo(0)
            }
            playerControlView?.show()
            player?.playWhenReady = true
            // Save state to sync back to original
            originalPlayerWasPlaying = true
        }

        playerControlView?.findViewById<View>(R.id.exo_pause)?.setOnClickListener {
            player?.playWhenReady = false
            // Save state to sync back to original
            originalPlayerWasPlaying = false
        }
    }

    private fun applyControlsConfig() {
        if (controlsConfig == null || playerControlView == null) return

        updateButtonVisibility(R.id.exo_ffwd, controlsConfig!!.hideForward)
        updateButtonVisibility(R.id.exo_rew, controlsConfig!!.hideRewind)
        updateButtonVisibility(R.id.exo_next, controlsConfig!!.hideNext)
        updateButtonVisibility(R.id.exo_prev, controlsConfig!!.hidePrevious)

        updateViewVisibility(R.id.exo_position, controlsConfig!!.hidePosition)
        updateViewVisibility(R.id.exo_progress, controlsConfig!!.hideSeekBar, View.INVISIBLE)
        updateViewVisibility(R.id.exo_duration, controlsConfig!!.hideDuration)
        updateViewVisibility(R.id.exo_settings, controlsConfig!!.hideSettingButton)
    }

    private fun updateButtonVisibility(buttonId: Int, hide: Boolean) {
        val button = playerControlView?.findViewById<ImageButton>(buttonId)
        button?.apply {
            imageAlpha = if (hide) 0 else 255
            isClickable = !hide
        }
    }

    private fun updateViewVisibility(viewId: Int, hide: Boolean, hiddenVisibility: Int = View.GONE) {
        val view = playerControlView?.findViewById<View>(viewId)
        view?.visibility = if (hide) hiddenVisibility else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        isShowing = true
        player?.playWhenReady = originalPlayerWasPlaying
    }

    override fun onPause() {
        super.onPause()
        // Store current state before pausing
        originalPlayerWasPlaying = player?.playWhenReady ?: false
        player?.playWhenReady = false
    }

    override fun onDestroy() {
        stateCheckHandler.removeCallbacks(stateCheckRunnable)

        // Release our fullscreen player
        player?.release()
        player = null

        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            playerControlView?.postDelayed({ hideSystemUI() }, 200)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            reactExoplayerView?.setFullscreen(false)
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun togglePlayerControlVisibility() {
        if (playerControlView?.isVisible == true) {
            playerControlView?.hide()
        } else {
            playerControlView?.show()
        }
    }

    /**
     * Enables regular immersive mode.
     */
    private fun hideSystemUI() {
        val decorView = window.decorView
        decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
            // Set the content to appear under the system bars so that the
            // content doesn't resize when the system bars hide and show.
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            // Hide the nav bar and status bar
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN)
    }

    private fun finishWithError() {
        runOnUiThread {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun finish() {
        // Save current playback state before finishing
        player?.let { fullscreenPlayer ->
            originalPlayerWasPlaying = fullscreenPlayer.playWhenReady
        }

        // Stop checking state
        stateCheckHandler.removeCallbacks(stateCheckRunnable)

        // Restore the original player's state (delayed to ensure activity transition completes)
        mainHandler.postDelayed({
            reactExoplayerView?.player?.playWhenReady = originalPlayerWasPlaying
        }, 100)

        super.finish()
    }

    // Added method to fix reference error
    fun hideWithoutPlayer() {
        isShowing = false
        // Hide UI elements but keep the player
        playerControlView?.visibility = View.GONE
    }

    // Added method to check if activity is showing
    fun isShowing(): Boolean {
        return isShowing && !isFinishing
    }

    companion object {
        const val EXTRA_REACT_EXOPLAYER_VIEW_ID = "extra_id"
        const val EXTRA_ORIENTATION = "extra_orientation"
        const val EXTRA_CONTROLS_CONFIG = "extra_controls_config"
        private const val TAG = "ExoPlayerFullscreen"
    }
}
