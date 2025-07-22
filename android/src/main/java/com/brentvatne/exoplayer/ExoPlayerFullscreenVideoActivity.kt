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
// Note: R will be generated at build time
import kotlin.math.abs

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
    private var stateBeforeStop: Boolean? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val stateCheckHandler = Handler(Looper.getMainLooper())
    private val stateCheckRunnable = object : Runnable {
        override fun run() {
            syncPlaybackState()
            stateCheckHandler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        setContentView(getResources().getIdentifier("exo_player_fullscreen_video", "layout", getPackageName()))
        
        val reactExoplayerViewId = intent.getIntExtra(EXTRA_REACT_EXOPLAYER_VIEW_ID, -1)
        val orientation = intent.getIntExtra(EXTRA_ORIENTATION, ActivityInfo.SCREEN_ORIENTATION_SENSOR)
        controlsConfig = intent.getParcelableExtra(EXTRA_CONTROLS_CONFIG)
        
        if (reactExoplayerViewId == -1) {
            finish()
            return
        }
        
        reactExoplayerView = ReactExoplayerView.getViewInstance(reactExoplayerViewId)
        if (reactExoplayerView == null) {
            finish()
            return
        }
        
        requestedOrientation = orientation
        
        setupPlayerView()
        createNewPlayer()
        startStateSync()
    }
    
    private fun setupPlayerView() {
        playerView = findViewById(getResources().getIdentifier("player_view", "id", getPackageName()))
        playerControlView = findViewById(getResources().getIdentifier("player_controls", "id", getPackageName()))
        
        playerView?.let { pView ->
            player?.let { pView.setPlayer(it) }
        }
        
        setupControlsVisibility()
    }
    
    private fun createNewPlayer() {
        val originalPlayer = reactExoplayerView?.getPlayer() ?: return
        
        val renderersFactory = DefaultRenderersFactory(this)
        player = ExoPlayer.Builder(this, renderersFactory).build()
        
        player?.let { newPlayer ->
            originalPlayer.currentMediaItem?.let { mediaItem ->
                newPlayer.setMediaItem(mediaItem)
                newPlayer.seekTo(originalPlayer.currentPosition)
                newPlayer.volume = 0f // Mute to avoid echo
                newPlayer.prepare()
                
                if (originalPlayer.isPlaying) {
                    newPlayer.play()
                }
            }
        }
        
        playerView?.setPlayer(player)
    }
    
    private fun setupControlsVisibility() {
        controlsConfig?.let { config ->
            playerControlView?.let { controls ->
                controls.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_next)?.let { nextBtn ->
                    nextBtn.visibility = if (config.nextDisabled) View.GONE else View.VISIBLE
                }
                controls.findViewById<ImageButton>(androidx.media3.ui.R.id.exo_prev)?.let { prevBtn ->
                    prevBtn.visibility = if (config.previousDisabled) View.GONE else View.VISIBLE
                }
            }
        }
    }
    
    private fun startStateSync() {
        stateCheckHandler.post(stateCheckRunnable)
    }
    
    private fun stopStateSync() {
        stateCheckHandler.removeCallbacks(stateCheckRunnable)
    }
    
    private fun syncPlaybackState() {
        if (syncingState) return
        
        val originalPlayer = reactExoplayerView?.getPlayer() ?: return
        val fullscreenPlayer = player ?: return
        
        syncingState = true
        
        try {
            val positionDiff = abs(originalPlayer.currentPosition - fullscreenPlayer.currentPosition)
            
            if (positionDiff > 1000) {
                fullscreenPlayer.seekTo(originalPlayer.currentPosition)
            }
            
            if (originalPlayer.isPlaying != fullscreenPlayer.isPlaying) {
                if (originalPlayer.isPlaying) {
                    fullscreenPlayer.play()
                } else {
                    fullscreenPlayer.pause()
                }
            }
        } catch (e: Exception) {
            Log.e("FullscreenActivity", "Error syncing playback state", e)
        } finally {
            syncingState = false
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    
    override fun onStop() {
        super.onStop()
        stateBeforeStop = player?.isPlaying
        player?.pause()
        stopStateSync()
    }
    
    override fun onStart() {
        super.onStart()
        stateBeforeStop?.let { wasPlaying ->
            if (wasPlaying) {
                player?.play()
            }
        }
        startStateSync()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopStateSync()
        
        val originalPlayer = reactExoplayerView?.getPlayer()
        val fullscreenPlayer = player
        
        if (originalPlayer != null && fullscreenPlayer != null) {
            originalPlayer.seekTo(fullscreenPlayer.currentPosition)
            if (fullscreenPlayer.isPlaying) {
                originalPlayer.play()
            } else {
                originalPlayer.pause()
            }
        }
        
        player?.release()
        player = null
        reactExoplayerView?.setFullscreen(false)
    }
    
    companion object {
        const val EXTRA_REACT_EXOPLAYER_VIEW_ID = "react_exoplayer_view_id"
        const val EXTRA_ORIENTATION = "orientation"  
        const val EXTRA_CONTROLS_CONFIG = "controls_config"
    }
}