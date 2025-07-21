package com.brentvatne.exoplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized playback manager that provides a single ExoPlayer instance
 * to be shared between React Native Video and any external media services (like Android Auto).
 * This ensures seamless playback experience across different interfaces.
 */
public class CentralizedPlaybackManager {
    private static final String TAG = "CentralizedPlaybackManager";
    private static volatile CentralizedPlaybackManager instance;

    private ExoPlayer player;
    private final Context context;
    private final Handler mainHandler;
    private final List<PlaybackStateListener> listeners;
    private final Object syncLock = new Object();

    // State tracking
    private boolean isInitialized = false;
    private boolean isStandaloneMode = false;
    private boolean isReactNativeActive = false;
    private String currentSource = null;
    private long lastSyncTime = 0;
    private static final long SYNC_THROTTLE_MS = 100; // Prevent rapid sync calls

    // Sync item types enum
    public enum SyncItem {
        PAUSED,
        SEEK,
        SPEED,
        MEDIA_ITEM,
        VOLUME
    }

    public interface PlaybackStateListener {
        void onPlaybackStateChanged(boolean isPlaying, long position, float speed);
        void onMediaItemChanged(@Nullable MediaItem mediaItem);
        void onSeekCompleted(long position);
        void onVolumeChanged(float volume);

        // Add default implementation to avoid breaking existing code
        default void onModeChanged(boolean isStandalone, boolean isReactNativeActive) {
            // Default implementation does nothing
        }
    }

    private CentralizedPlaybackManager(Context context) {
        this.context = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.listeners = new CopyOnWriteArrayList<>();
        initializePlayer();
    }

    public static synchronized CentralizedPlaybackManager getInstance(Context context) {
        if (instance == null) {
            instance = new CentralizedPlaybackManager(context);
        }
        return instance;
    }

    public static synchronized CentralizedPlaybackManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CentralizedPlaybackManager not initialized. Call getInstance(Context) first.");
        }
        return instance;
    }

    /**
     * Initialize in standalone mode (called by Android Auto service)
     */
    public synchronized void initializeStandalone() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized, switching to standalone mode");
            isStandaloneMode = true;
            notifyModeChanged();
            return;
        }

        Log.d(TAG, "Initializing in standalone mode");
        isStandaloneMode = true;
        isReactNativeActive = false;
        initializePlayer();
    }

    private void initializePlayer() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::initializePlayer);
            return;
        }

        try {
            if (player == null) {
                player = new ExoPlayer.Builder(context).build();
                player.setAudioAttributes(AudioAttributes.DEFAULT, true);

                // Set up player listener
                player.addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        notifyPlaybackStateChanged();
                    }

                    @Override
                    public void onPositionDiscontinuity(
                            @NonNull Player.PositionInfo oldPosition,
                            @NonNull Player.PositionInfo newPosition,
                            @Player.DiscontinuityReason int reason) {
                        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                            notifySeekCompleted(newPosition.positionMs);
                        }
                    }

                    @Override
                    public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
                        notifyPlaybackStateChanged();
                    }

                    @Override
                    public void onMediaItemTransition(@Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
                        notifyMediaItemChanged(mediaItem);
                    }

                    @Override
                    public void onVolumeChanged(float volume) {
                        notifyVolumeChanged(volume);
                    }
                });
            }

            isInitialized = true;
            String mode = isStandaloneMode ? "standalone" : "React Native";
            Log.d(TAG, "CentralizedPlaybackManager initialized in " + mode + " mode");
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize player", e);
        }
    }

    public ExoPlayer getPlayer() {
        if (!isInitialized) {
            // Auto-initialize in standalone mode if accessed by external service
            if (!isReactNativeActive) {
                initializeStandalone();
            }
        }
        return player;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public boolean isReactNativeActive() {
        return isReactNativeActive;
    }

    public void addListener(PlaybackStateListener listener) {
        listeners.add(listener);
    }

    public void removeListener(PlaybackStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Set media source - typically called from React Native side
     */
    public void setMediaSource(String uri) {
        if (!isInitialized) {
            Log.w(TAG, "Player not initialized, cannot set media source");
            return;
        }

        mainHandler.post(() -> {
            try {
                MediaItem mediaItem = MediaItem.fromUri(uri);
                player.setMediaItem(mediaItem);
                player.prepare();
                currentSource = uri;
                Log.d(TAG, "Media source set: " + uri);
            } catch (Exception e) {
                Log.e(TAG, "Failed to set media source", e);
            }
        });
    }

    /**
     * Set media items - typically called from external media services
     */
    public void setMediaItems(List<MediaItem> mediaItems) {
        if (!isInitialized) {
            Log.w(TAG, "Player not initialized, cannot set media items");
            return;
        }

        mainHandler.post(() -> {
            try {
                player.setMediaItems(mediaItems);
                player.prepare();
                Log.d(TAG, "Media items set, count: " + mediaItems.size());
            } catch (Exception e) {
                Log.e(TAG, "Failed to set media items", e);
            }
        });
    }

    /**
     * Synchronize state from external source
     */
    public void syncState(Map<SyncItem, Object> stateUpdates, String source) {
        synchronized (syncLock) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastSyncTime < SYNC_THROTTLE_MS) {
                Log.d(TAG, "Sync throttled, ignoring rapid sync call from " + source);
                return;
            }
            lastSyncTime = currentTime;
        }

        mainHandler.post(() -> {
            try {
                Log.d(TAG, "Syncing state from " + source + ": " + stateUpdates.keySet());

                for (Map.Entry<SyncItem, Object> entry : stateUpdates.entrySet()) {
                    switch (entry.getKey()) {
                        case PAUSED:
                            if (entry.getValue() instanceof Boolean) {
                                boolean shouldPlay = !(Boolean) entry.getValue();
                                if (player.getPlayWhenReady() != shouldPlay) {
                                    player.setPlayWhenReady(shouldPlay);
                                }
                            }
                            break;

                        case SEEK:
                            if (entry.getValue() instanceof Long) {
                                long position = (Long) entry.getValue();
                                player.seekTo(position);
                            } else if (entry.getValue() instanceof Double) {
                                long position = (long) ((Double) entry.getValue() * 1000);
                                player.seekTo(position);
                            }
                            break;

                        case SPEED:
                            if (entry.getValue() instanceof Float) {
                                float speed = (Float) entry.getValue();
                                PlaybackParameters params = new PlaybackParameters(speed, 1.0f);
                                player.setPlaybackParameters(params);
                            }
                            break;

                        case VOLUME:
                            if (entry.getValue() instanceof Float) {
                                float volume = (Float) entry.getValue();
                                player.setVolume(volume);
                            }
                            break;

                        case MEDIA_ITEM:
                            if (entry.getValue() instanceof MediaItem mediaItem) {
                                player.setMediaItem(mediaItem);
                                player.prepare();
                            } else if (entry.getValue() instanceof String uri) {
                                MediaItem mediaItem = MediaItem.fromUri(uri);
                                player.setMediaItem(mediaItem);
                                player.prepare();
                            }
                            break;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error syncing state", e);
            }
        });
    }

    /**
     * Get current playback state
     */
    public Map<SyncItem, Object> getCurrentState() {
        Map<SyncItem, Object> state = new HashMap<>();

        if (player != null) {
            state.put(SyncItem.PAUSED, !player.getPlayWhenReady());
            state.put(SyncItem.SEEK, player.getCurrentPosition());
            state.put(SyncItem.SPEED, player.getPlaybackParameters().speed);
            state.put(SyncItem.VOLUME, player.getVolume());

            MediaItem currentItem = player.getCurrentMediaItem();
            if (currentItem != null) {
                state.put(SyncItem.MEDIA_ITEM, currentItem);
            }
        }

        return state;
    }

    /**
     * Get current media URI
     */
    public String getCurrentSource() {
        return currentSource;
    }

    private void notifyPlaybackStateChanged() {
        if (player == null) return;

        boolean isPlaying = player.getPlayWhenReady() && player.getPlaybackState() == Player.STATE_READY;
        long position = player.getCurrentPosition();
        float speed = player.getPlaybackParameters().speed;

        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onPlaybackStateChanged(isPlaying, position, speed);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifySeekCompleted(long position) {
        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onSeekCompleted(position);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyMediaItemChanged(@Nullable MediaItem mediaItem) {
        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onMediaItemChanged(mediaItem);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyVolumeChanged(float volume) {
        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onVolumeChanged(volume);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    private void notifyModeChanged() {
        for (PlaybackStateListener listener : listeners) {
            try {
                listener.onModeChanged(isStandaloneMode, isReactNativeActive);
            } catch (Exception e) {
                Log.e(TAG, "Error notifying listener", e);
            }
        }
    }

    /**
     * Release resources - should only be called when the app is completely shutting down
     */
    public void release() {
        mainHandler.post(() -> {
            if (player != null) {
                player.release();
                player = null;
            }
            listeners.clear();
            isInitialized = false;
            isStandaloneMode = false;
            isReactNativeActive = false;
            currentSource = null;
            Log.d(TAG, "CentralizedPlaybackManager released");
        });

        synchronized (CentralizedPlaybackManager.class) {
            instance = null;
        }
    }
}
