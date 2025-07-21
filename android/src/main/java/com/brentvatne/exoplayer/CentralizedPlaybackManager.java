package com.brentvatne.exoplayer;

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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Centralized playback manager that provides a single ExoPlayer instance
 * to be shared between React Native Video and any external media services (like Android Auto).
 * This ensures seamless playback experience across different interfaces.
 */
public class CentralizedPlaybackManager extends Service implements ExoPlayer {
    private static final String TAG = "CentralizedPlaybackManager";

    private final long COMMUNICATION_WAIT = 30000;

    private static volatile CentralizedPlaybackManager instance;

    private final Handler mainHandler;
    private ExoPlayer player;


    //===== Initialization =====

    public static synchronized CentralizedPlaybackManager getInstance(){
        if(instance == null){
            instance = new CentralizedPlaybackManager();
        }
        return instance;
    }

    private CentralizedPlaybackManager(){
        this.mainHandler = new Handler(Looper.getMainLooper());
        setupPlayer();
    }

    private void setupPlayer(){
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::setupPlayer);
            return;
        }

        this.player = new ExoPlayer.Builder(this.getApplicationContext()).build();
    }

    //===== Util =====

    private Object convertToMainThreadTask(Supplier<Object> operation){
        try {
            CountDownLatch lock = new CountDownLatch(1);
            AtomicReference<Object> result = new AtomicReference<>();
            mainHandler.post(() -> {
                result.set(operation.get());
                lock.countDown();
            });
            boolean completed = lock.await(COMMUNICATION_WAIT, TimeUnit.MILLISECONDS);
            if(!completed) throw new InterruptedException("Timed out when communicating with internal player");
            return result.get();
        } catch (InterruptedException e) {
            Log.e(TAG,"Interrupted when contacting CentralPlaybackManager internal player: " + e.getMessage() + ", returning null");
            return null;
        }
    }

    //===== Overrides =====

    @Nullable
    @Override
    public ExoPlaybackException getPlayerError() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
           return (ExoPlaybackException) convertToMainThreadTask(() -> player.getPlayerError());
        }else {
            return player.getPlayerError();
        }
    }

    @Override
    public void play() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::play);
            return;
        }
        player.play();
    }

    @Override
    public void pause() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::pause);
            return;
        }
        player.pause();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setPlayWhenReady(playWhenReady));
            return;
        }
        player.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getPlayWhenReady);
        }else {
            return player.getPlayWhenReady();
        }
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setRepeatMode(repeatMode));
            return;
        }
        player.setRepeatMode(repeatMode);
    }

    @Override
    public int getRepeatMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getRepeatMode);
        }else {
            return player.getRepeatMode();
        }
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setShuffleModeEnabled(shuffleModeEnabled));
            return;
        }
        player.setShuffleModeEnabled(shuffleModeEnabled);
    }

    @Override
    public boolean getShuffleModeEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getShuffleModeEnabled);
        }else{
            return player.getShuffleModeEnabled();
        }
    }

    @Override
    public boolean isLoading() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isLoading);
        }else{
            return player.isLoading();
        }
    }

    @Override
    public void seekToDefaultPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToDefaultPosition);
            return;
        }
        player.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekToDefaultPosition(mediaItemIndex));
            return;
        }
        player.seekToDefaultPosition(mediaItemIndex);
    }

    @Override
    public void seekTo(long positionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekTo(positionMs));
            return;
        }
        player.seekTo(positionMs);
    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekTo(mediaItemIndex,positionMs));
            return;
        }
        player.seekTo(mediaItemIndex,positionMs);
    }

    @Override
    public long getSeekBackIncrement() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getSeekBackIncrement);
        }else{
            return player.getSeekBackIncrement();
        }
    }

    @Override
    public void seekBack() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekBack);
            return;
        }
        player.seekBack();
    }

    @Override
    public long getSeekForwardIncrement() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getSeekForwardIncrement);
        }else{
            return player.getSeekForwardIncrement();
        }
    }

    @Override
    public void seekForward() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekForward);
            return;
        }
        player.seekForward();
    }

    @Override
    public boolean hasPrevious() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPrevious);
        }else{
            return player.hasPrevious();
        }
    }

    @Override
    public boolean hasPreviousWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPreviousWindow);
        }else{
            return player.hasPreviousWindow();
        }
    }

    @Override
    public boolean hasPreviousMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPreviousMediaItem);
        }else{
            return player.hasPreviousMediaItem();
        }
    }

    @Override
    public void previous() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::previous);
            return;
        }
        player.previous();
    }

    @Override
    public void seekToPreviousWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPreviousWindow);
            return;
        }
        player.seekToPreviousWindow();
    }

    @Override
    public void seekToPreviousMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPreviousMediaItem);
            return;
        }
        player.seekToPreviousMediaItem();
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getMaxSeekToPreviousPosition);
        }else{
            return player.getMaxSeekToPreviousPosition();
        }
    }

    @Override
    public void seekToPrevious() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPrevious);
            return;
        }
        player.seekToPrevious();
    }

    @Override
    public boolean hasNext() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNext);
        }else{
            return player.hasNext();
        }
    }

    @Override
    public boolean hasNextWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNextWindow);
        }else{
            return player.hasNextWindow();
        }
    }

    @Override
    public boolean hasNextMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNextMediaItem);
        }else{
            return player.hasNextMediaItem();
        }
    }

    @Override
    public void next() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::next);
            return;
        }
        player.next();
    }

    @Override
    public void seekToNextWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNextWindow);
            return;
        }
        player.seekToNextWindow();
    }

    @Override
    public void seekToNextMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNextMediaItem);
            return;
        }
        player.seekToNextMediaItem();
    }

    @Override
    public void seekToNext() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNext);
            return;
        }
        player.seekToNext();
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaybackParameters(playbackParameters));
            return;
        }
        player.setPlaybackParameters(playbackParameters);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaybackSpeed(speed));
            return;
        }
        player.setPlaybackSpeed(speed);
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PlaybackParameters) convertToMainThreadTask(this::getPlaybackParameters);
        }else{
            return player.getPlaybackParameters();
        }
    }

    @Override
    public void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stop);
            return;
        }
        player.stop();
    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AudioComponent) convertToMainThreadTask(this::getAudioComponent);
        }else{
            return player.getAudioComponent();
        }
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (VideoComponent) convertToMainThreadTask(this::getVideoComponent);
        }else{
            return player.getVideoComponent();
        }
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TextComponent) convertToMainThreadTask(this::getTextComponent);
        }else{
            return player.getTextComponent();
        }
    }

    @Nullable
    @Override
    public DeviceComponent getDeviceComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DeviceComponent) convertToMainThreadTask(this::getDeviceComponent);
        }else{
            return player.getDeviceComponent();
        }
    }

    @Override
    public void addAudioOffloadListener(AudioOffloadListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addAudioOffloadListener(listener));
            return;
        }
        player.addAudioOffloadListener(listener);
    }

    @Override
    public void removeAudioOffloadListener(AudioOffloadListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeAudioOffloadListener(listener));
            return;
        }
        player.removeAudioOffloadListener(listener);
    }

    @Override
    public AnalyticsCollector getAnalyticsCollector() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AnalyticsCollector) convertToMainThreadTask(this::getAnalyticsCollector);
        }else{
            return player.getAnalyticsCollector();
        }
    }

    @Override
    public void addAnalyticsListener(AnalyticsListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addAnalyticsListener(listener));
            return;
        }
        player.addAnalyticsListener(listener);
    }

    @Override
    public void removeAnalyticsListener(AnalyticsListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeAnalyticsListener(listener));
            return;
        }
        player.removeAnalyticsListener(listener);
    }

    @Override
    public int getRendererCount() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getRendererCount);
        }else{
            return player.getRendererCount();
        }
    }

    @Override
    public int getRendererType(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(() -> getRendererType(index));
        }else{
            return player.getRendererType(index);
        }
    }

    @Override
    public Renderer getRenderer(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Renderer) convertToMainThreadTask(() -> getRenderer(index));
        }else{
            return player.getRenderer(index);
        }
    }

    @Nullable
    @Override
    public TrackSelector getTrackSelector() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelector) convertToMainThreadTask(this::getTrackSelector);
        }else{
            return player.getTrackSelector();
        }
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackGroupArray) convertToMainThreadTask(this::getCurrentTrackGroups);
        }else{
            return player.getCurrentTrackGroups();
        }
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelectionArray) convertToMainThreadTask(this::getCurrentTrackSelections);
        }else{
            return player.getCurrentTrackSelections();
        }
    }

    @Override
    public Looper getPlaybackLooper() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Looper) convertToMainThreadTask(this::getPlaybackLooper);
        }else{
            return player.getPlaybackLooper();
        }
    }

    @Override
    public Clock getClock() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Clock) convertToMainThreadTask(this::getClock);
        }else{
            return player.getClock();
        }
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prepare(mediaSource));
            return;
        }
        player.prepare(mediaSource);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prepare(mediaSource, resetPosition, resetState));
            return;
        }
        player.prepare(mediaSource, resetPosition, resetState);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources));
            return;
        }
        player.setMediaSources(mediaSources);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources, resetPosition));
            return;
        }
        player.setMediaSources(mediaSources, resetPosition);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources, startMediaItemIndex, startPositionMs));
            return;
        }
        player.setMediaSources(mediaSources, startMediaItemIndex, startPositionMs);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource));
            return;
        }
        player.setMediaSource(mediaSource);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource, startPositionMs));
            return;
        }
        player.setMediaSource(mediaSource, startPositionMs);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource, resetPosition));
            return;
        }
        player.setMediaSource(mediaSource, resetPosition);
    }

    @Override
    public void addMediaSource(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSource(mediaSource));
            return;
        }
        player.addMediaSource(mediaSource);
    }

    @Override
    public void addMediaSource(int index, MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSource(index,mediaSource));
            return;
        }
        player.addMediaSource(index, mediaSource);
    }

    @Override
    public void addMediaSources(List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSources(mediaSources));
            return;
        }
        player.addMediaSources(mediaSources);
    }

    @Override
    public void addMediaSources(int index, List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSources(index,mediaSources));
            return;
        }
        player.addMediaSources(index,mediaSources);
    }

    @Override
    public void setShuffleOrder(ShuffleOrder shuffleOrder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setShuffleOrder(shuffleOrder));
            return;
        }
        player.setShuffleOrder(shuffleOrder);
    }

    @Override
    public void setPreloadConfiguration(PreloadConfiguration preloadConfiguration) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPreloadConfiguration(preloadConfiguration));
            return;
        }
        player.setPreloadConfiguration(preloadConfiguration);
    }

    @Override
    public PreloadConfiguration getPreloadConfiguration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PreloadConfiguration) convertToMainThreadTask(this::getPreloadConfiguration);
        }else{
            return player.getPreloadConfiguration();
        }
    }

    @Override
    public Looper getApplicationLooper() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Looper) convertToMainThreadTask(this::getApplicationLooper);
        }else{
            return player.getApplicationLooper();
        }
    }

    @Override
    public void addListener(Player.Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addListener(listener));
            return;
        }
        player.addListener(listener);
    }

    @Override
    public void removeListener(Player.Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeListener(listener));
            return;
        }
        player.removeListener(listener);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems));
            return;
        }
        player.setMediaItems(mediaItems);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems, resetPosition));
            return;
        }
        player.setMediaItems(mediaItems, resetPosition);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems, startIndex, startPositionMs));
            return;
        }
        player.setMediaItems(mediaItems, startIndex, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem));
            return;
        }
        player.setMediaItem(mediaItem);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem, startPositionMs));
            return;
        }
        player.setMediaItem(mediaItem, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem, resetPosition));
            return;
        }
        player.setMediaItem(mediaItem, resetPosition);
    }

    @Override
    public void addMediaItem(MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItem(mediaItem));
            return;
        }
        player.addMediaItem(mediaItem);
    }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItem(index, mediaItem));
            return;
        }
        player.addMediaItem(index, mediaItem);
    }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItems(mediaItems));
            return;
        }
        player.addMediaItems(mediaItems);
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItems(index,mediaItems));
            return;
        }
        player.addMediaItems(index,mediaItems);
    }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> moveMediaItem(currentIndex,newIndex));
            return;
        }
        player.moveMediaItem(currentIndex,newIndex);
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> moveMediaItems(fromIndex,toIndex,newIndex));
            return;
        }
        player.moveMediaItems(fromIndex,toIndex,newIndex);
    }

    @Override
    public void replaceMediaItem(int index, MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> replaceMediaItem(index,mediaItem));
            return;
        }
        player.replaceMediaItem(index,mediaItem);
    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> replaceMediaItems(fromIndex,toIndex,mediaItems));
            return;
        }
        player.replaceMediaItems(fromIndex,toIndex,mediaItems);
    }

    @Override
    public void removeMediaItem(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeMediaItem(index));
            return;
        }
        player.removeMediaItem(index);
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
