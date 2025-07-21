package com.brentvatne.exoplayer;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.AuxEffectInfo;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.PriorityTaskManager;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Size;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.PlayerMessage;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.TrackSelectionArray;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionListener;

import android.app.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Centralized playback manager that provides a single ExoPlayer instance
 * to be shared between React Native Video and any external media services (like Android Auto).
 * This ensures seamless playback experience across different interfaces.
 */
public class CentralizedPlaybackManager extends Service implements ExoPlayer {
    private static final String TAG = "CentralizedPlaybackManager";
    private static volatile CentralizedPlaybackManager instance;

    private ExoPlayer player;

    //Initialization
    private CentralizedPlaybackManager(){
        this.player = new ExoPlayer.Builder(this.getApplicationContext()).build();
    }

    public static synchronized CentralizedPlaybackManager getInstance(){
        if(instance == null){
            instance = new CentralizedPlaybackManager();
        }
        return instance;
    }

    //===== Overrides =====

    @Nullable
    @Override
    public ExoPlaybackException getPlayerError() {
        return null;
    }

    @Override
    public void play() {

    }

    @Override
    public void pause() {

    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {

    }

    @Override
    public boolean getPlayWhenReady() {
        return false;
    }

    @Override
    public void setRepeatMode(int repeatMode) {

    }

    @Override
    public int getRepeatMode() {
        return 0;
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {

    }

    @Override
    public boolean getShuffleModeEnabled() {
        return false;
    }

    @Override
    public boolean isLoading() {
        return false;
    }

    @Override
    public void seekToDefaultPosition() {

    }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) {

    }

    @Override
    public void seekTo(long positionMs) {

    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) {

    }

    @Override
    public long getSeekBackIncrement() {
        return 0;
    }

    @Override
    public void seekBack() {

    }

    @Override
    public long getSeekForwardIncrement() {
        return 0;
    }

    @Override
    public void seekForward() {

    }

    @Override
    public boolean hasPrevious() {
        return false;
    }

    @Override
    public boolean hasPreviousWindow() {
        return false;
    }

    @Override
    public boolean hasPreviousMediaItem() {
        return false;
    }

    @Override
    public void previous() {

    }

    @Override
    public void seekToPreviousWindow() {

    }

    @Override
    public void seekToPreviousMediaItem() {

    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        return 0;
    }

    @Override
    public void seekToPrevious() {

    }

    @Override
    public boolean hasNext() {
        return false;
    }

    @Override
    public boolean hasNextWindow() {
        return false;
    }

    @Override
    public boolean hasNextMediaItem() {
        return false;
    }

    @Override
    public void next() {

    }

    @Override
    public void seekToNextWindow() {

    }

    @Override
    public void seekToNextMediaItem() {

    }

    @Override
    public void seekToNext() {

    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {

    }

    @Override
    public void setPlaybackSpeed(float speed) {

    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return null;
    }

    @Override
    public void stop() {

    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        return null;
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        return null;
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        return null;
    }

    @Nullable
    @Override
    public DeviceComponent getDeviceComponent() {
        return null;
    }

    @Override
    public void addAudioOffloadListener(AudioOffloadListener listener) {

    }

    @Override
    public void removeAudioOffloadListener(AudioOffloadListener listener) {

    }

    @Override
    public AnalyticsCollector getAnalyticsCollector() {
        return null;
    }

    @Override
    public void addAnalyticsListener(AnalyticsListener listener) {

    }

    @Override
    public void removeAnalyticsListener(AnalyticsListener listener) {

    }

    @Override
    public int getRendererCount() {
        return 0;
    }

    @Override
    public int getRendererType(int index) {
        return 0;
    }

    @Override
    public Renderer getRenderer(int index) {
        return null;
    }

    @Nullable
    @Override
    public TrackSelector getTrackSelector() {
        return null;
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        return null;
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        return null;
    }

    @Override
    public Looper getPlaybackLooper() {
        return null;
    }

    @Override
    public Clock getClock() {
        return null;
    }

    @Override
    public void prepare(MediaSource mediaSource) {

    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {

    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources) {

    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {

    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {

    }

    @Override
    public void setMediaSource(MediaSource mediaSource) {

    }

    @Override
    public void setMediaSource(MediaSource mediaSource, long startPositionMs) {

    }

    @Override
    public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {

    }

    @Override
    public void addMediaSource(MediaSource mediaSource) {

    }

    @Override
    public void addMediaSource(int index, MediaSource mediaSource) {

    }

    @Override
    public void addMediaSources(List<MediaSource> mediaSources) {

    }

    @Override
    public void addMediaSources(int index, List<MediaSource> mediaSources) {

    }

    @Override
    public void setShuffleOrder(ShuffleOrder shuffleOrder) {

    }

    @Override
    public void setPreloadConfiguration(PreloadConfiguration preloadConfiguration) {

    }

    @Override
    public PreloadConfiguration getPreloadConfiguration() {
        return null;
    }

    @Override
    public Looper getApplicationLooper() {
        return null;
    }

    @Override
    public void addListener(Player.Listener listener) {

    }

    @Override
    public void removeListener(Player.Listener listener) {

    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) {

    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {

    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {

    }

    @Override
    public void setMediaItem(MediaItem mediaItem) {

    }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) {

    }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {

    }

    @Override
    public void addMediaItem(MediaItem mediaItem) {

    }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) {

    }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) {

    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {

    }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) {

    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {

    }

    @Override
    public void replaceMediaItem(int index, MediaItem mediaItem) {

    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {

    }

    @Override
    public void removeMediaItem(int index) {

    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {

    }

    @Override
    public void clearMediaItems() {

    }

    @Override
    public boolean isCommandAvailable(int command) {
        return false;
    }

    @Override
    public boolean canAdvertiseSession() {
        return false;
    }

    @Override
    public Commands getAvailableCommands() {
        return null;
    }

    @Override
    public void prepare() {

    }

    @Override
    public int getPlaybackState() {
        return 0;
    }

    @Override
    public int getPlaybackSuppressionReason() {
        return 0;
    }

    @Override
    public boolean isPlaying() {
        return false;
    }

    @Override
    public void setAudioSessionId(int audioSessionId) {

    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    @Override
    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {

    }

    @Override
    public void clearAuxEffectInfo() {

    }

    @Override
    public void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {

    }

    @Override
    public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {

    }

    @Override
    public boolean getSkipSilenceEnabled() {
        return false;
    }

    @Override
    public void setVideoEffects(List<Effect> videoEffects) {

    }

    @Override
    public void setVideoScalingMode(int videoScalingMode) {

    }

    @Override
    public int getVideoScalingMode() {
        return 0;
    }

    @Override
    public void setVideoChangeFrameRateStrategy(int videoChangeFrameRateStrategy) {

    }

    @Override
    public int getVideoChangeFrameRateStrategy() {
        return 0;
    }

    @Override
    public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {

    }

    @Override
    public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {

    }

    @Override
    public void setCameraMotionListener(CameraMotionListener listener) {

    }

    @Override
    public void clearCameraMotionListener(CameraMotionListener listener) {

    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        return null;
    }

    @Override
    public void setSeekParameters(@Nullable SeekParameters seekParameters) {

    }

    @Override
    public SeekParameters getSeekParameters() {
        return null;
    }

    @Override
    public void setForegroundMode(boolean foregroundMode) {

    }

    @Override
    public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {

    }

    @Override
    public boolean getPauseAtEndOfMediaItems() {
        return false;
    }

    @Nullable
    @Override
    public Format getAudioFormat() {
        return null;
    }

    @Nullable
    @Override
    public Format getVideoFormat() {
        return null;
    }

    @Nullable
    @Override
    public DecoderCounters getAudioDecoderCounters() {
        return null;
    }

    @Nullable
    @Override
    public DecoderCounters getVideoDecoderCounters() {
        return null;
    }

    @Override
    public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {

    }

    @Override
    public void setWakeMode(int wakeMode) {

    }

    @Override
    public void setPriority(int priority) {

    }

    @Override
    public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {

    }

    @Override
    public boolean isSleepingForOffload() {
        return false;
    }

    @Override
    public boolean isTunnelingEnabled() {
        return false;
    }

    @Override
    public void release() {

    }

    @Override
    public Tracks getCurrentTracks() {
        return null;
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        return null;
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {

    }

    @Override
    public MediaMetadata getMediaMetadata() {
        return null;
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        return null;
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {

    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
        return null;
    }

    @Override
    public Timeline getCurrentTimeline() {
        return null;
    }

    @Override
    public int getCurrentPeriodIndex() {
        return 0;
    }

    @Override
    public int getCurrentWindowIndex() {
        return 0;
    }

    @Override
    public int getCurrentMediaItemIndex() {
        return 0;
    }

    @Override
    public int getNextWindowIndex() {
        return 0;
    }

    @Override
    public int getNextMediaItemIndex() {
        return 0;
    }

    @Override
    public int getPreviousWindowIndex() {
        return 0;
    }

    @Override
    public int getPreviousMediaItemIndex() {
        return 0;
    }

    @Nullable
    @Override
    public MediaItem getCurrentMediaItem() {
        return null;
    }

    @Override
    public int getMediaItemCount() {
        return 0;
    }

    @Override
    public MediaItem getMediaItemAt(int index) {
        return null;
    }

    @Override
    public long getDuration() {
        return 0;
    }

    @Override
    public long getCurrentPosition() {
        return 0;
    }

    @Override
    public long getBufferedPosition() {
        return 0;
    }

    @Override
    public int getBufferedPercentage() {
        return 0;
    }

    @Override
    public long getTotalBufferedDuration() {
        return 0;
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        return false;
    }

    @Override
    public boolean isCurrentMediaItemDynamic() {
        return false;
    }

    @Override
    public boolean isCurrentWindowLive() {
        return false;
    }

    @Override
    public boolean isCurrentMediaItemLive() {
        return false;
    }

    @Override
    public long getCurrentLiveOffset() {
        return 0;
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        return false;
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        return false;
    }

    @Override
    public boolean isPlayingAd() {
        return false;
    }

    @Override
    public int getCurrentAdGroupIndex() {
        return 0;
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        return 0;
    }

    @Override
    public long getContentDuration() {
        return 0;
    }

    @Override
    public long getContentPosition() {
        return 0;
    }

    @Override
    public long getContentBufferedPosition() {
        return 0;
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        return null;
    }

    @Override
    public void setVolume(float volume) {

    }

    @Override
    public float getVolume() {
        return 0;
    }

    @Override
    public void clearVideoSurface() {

    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {

    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {

    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {

    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {

    }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {

    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {

    }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) {

    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) {

    }

    @Override
    public VideoSize getVideoSize() {
        return null;
    }

    @Override
    public Size getSurfaceSize() {
        return null;
    }

    @Override
    public CueGroup getCurrentCues() {
        return null;
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        return null;
    }

    @Override
    public int getDeviceVolume() {
        return 0;
    }

    @Override
    public boolean isDeviceMuted() {
        return false;
    }

    @Override
    public void setDeviceVolume(int volume) {

    }

    @Override
    public void setDeviceVolume(int volume, int flags) {

    }

    @Override
    public void increaseDeviceVolume() {

    }

    @Override
    public void increaseDeviceVolume(int flags) {

    }

    @Override
    public void decreaseDeviceVolume() {

    }

    @Override
    public void decreaseDeviceVolume(int flags) {

    }

    @Override
    public void setDeviceMuted(boolean muted) {

    }

    @Override
    public void setDeviceMuted(boolean muted, int flags) {

    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {

    }

    @Override
    public boolean isReleased() {
        return false;
    }

    @Override
    public void setImageOutput(@Nullable ImageOutput imageOutput) {

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
