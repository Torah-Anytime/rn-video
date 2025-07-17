package com.brentvatne.exoplayer;

import androidx.media3.common.MediaItem;
import java.util.List;
import java.util.Map;

/**
 * Generic interface for external media services (like Android Auto, Android TV, etc.)
 * to interact with the centralized playback manager.
 * This interface provides a future-proof way for any external media service
 * to integrate with react-native-video without modifying the core library.
 */
public interface ExternalMediaServiceInterface {

    /**
     * Called when the external media service connects to the centralized manager
     */
    void onConnect();

    /**
     * Called when the external media service disconnects from the centralized manager
     */
    void onDisconnect();

    /**
     * Called when playback state changes from the centralized manager
     * @param isPlaying Whether the player is currently playing
     * @param position Current playback position in milliseconds
     * @param speed Current playback speed
     */
    void onPlaybackStateChanged(boolean isPlaying, long position, float speed);

    /**
     * Called when the media item changes in the centralized manager
     * @param mediaItem The new media item, or null if no media is loaded
     */
    void onMediaItemChanged(MediaItem mediaItem);

    /**
     * Called when a seek operation completes in the centralized manager
     * @param position The new position in milliseconds
     */
    void onSeekCompleted(long position);

    /**
     * Called when the volume changes in the centralized manager
     * @param volume The new volume level (0.0 to 1.0)
     */
    void onVolumeChanged(float volume);

    /**
     * Called when the mode changes in the centralized manager
     * @param isStandalone Whether the manager is in standalone mode
     * @param isReactNativeActive Whether React Native is currently active
     */
    default void onModeChanged(boolean isStandalone, boolean isReactNativeActive) {
        // Default implementation does nothing
    }

    /**
     * Request to update playback state from the external service
     * @param paused Whether playback should be paused
     * @return true if the request was handled successfully
     */
    boolean requestPlaybackStateChange(boolean paused);

    /**
     * Request to seek to a specific position from the external service
     * @param position Position in milliseconds
     * @return true if the request was handled successfully
     */
    boolean requestSeek(long position);

    /**
     * Request to change playback speed from the external service
     * @param speed New playback speed
     * @return true if the request was handled successfully
     */
    boolean requestSpeedChange(float speed);

    /**
     * Request to change volume from the external service
     * @param volume New volume level (0.0 to 1.0)
     * @return true if the request was handled successfully
     */
    boolean requestVolumeChange(float volume);

    /**
     * Request to change media from the external service
     * @param mediaItems List of media items to play
     * @return true if the request was handled successfully
     */
    boolean requestMediaChange(List<MediaItem> mediaItems);

    /**
     * Get the service identifier for logging and debugging
     * @return Unique identifier for this external service
     */
    String getServiceId();

    /**
     * Check if this service is currently connected
     * @return true if connected and active
     */
    boolean isConnected();

    /**
     * Get capabilities of this external service
     * @return Map of capability names to boolean values
     */
    Map<String, Boolean> getCapabilities();
}
