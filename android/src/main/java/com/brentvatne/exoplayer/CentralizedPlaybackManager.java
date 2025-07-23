package com.brentvatne.exoplayer;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioDeviceInfo;
import android.os.Binder;
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

import com.google.common.util.concurrent.SettableFuture;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;


/**
 * Centralized playback manager that provides a single ExoPlayer instance
 * to be shared between React Native Video and any external media services (like Android Auto).
 * This ensures seamless playback experience across different interfaces.
 */
public class CentralizedPlaybackManager extends Service implements ExoPlayer {
    private static final String TAG = "CentralizedPlaybackManager";

    private final long COMMUNICATION_WAIT = 30000;

    private final Handler mainHandler;
    private ExoPlayer player;
    private final IBinder binder = new LocalBinder();

    private static volatile CentralizedPlaybackManager instance = null;
    private static Set<Runnable> onInitializationTasks = new HashSet<>();



    //===== Initialization =====

    public CentralizedPlaybackManager(){
        Log.d(TAG,"CPM Instance Created");
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    private void setupPlayer(){
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::setupPlayer);
            return;
        }

        Log.d(TAG, "Setting up the player on " + this.getApplicationContext());
        this.player = new ExoPlayer.Builder(this).build();
        this.player.setAudioAttributes(AudioAttributes.DEFAULT,true);
    }

    private void executeInitializationTasks() {
        synchronized (onInitializationTasks) {
            for (Runnable r : onInitializationTasks) {
                r.run();
            }onInitializationTasks.clear();
        }
    }

    public static void addInitializationTask(Runnable r){
        synchronized (onInitializationTasks) {
            onInitializationTasks.add(r);
        }
    }

    //===== Binding and Lifecycle =====
    public class LocalBinder extends Binder{
        public CentralizedPlaybackManager getInstance(){
            synchronized (CentralizedPlaybackManager.class) {
                Log.d(TAG,"Instance requested from LocalBinder");
                if (instance == null) {
                    instance = CentralizedPlaybackManager.this;
                }
                return instance;
            }
        }
    }

    public static class LocalBinderConnection implements ServiceConnection{
        private CentralizedPlaybackManager localInstance = null;
        private final Object lock = new Object();

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Log.d(TAG,"Connection from " + componentName.getClassName() + " to CentralizedPlaybackManager");
            synchronized (lock) {
                LocalBinder localBinder = (LocalBinder) iBinder;
                localInstance = localBinder.getInstance();
                lock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG,"Disconnection from " + componentName.getClassName() + " to CentralizedPlaybackManager");
            synchronized (lock) {
                localInstance = null;
                lock.notifyAll();
            }
        }

        public CentralizedPlaybackManager getInstance(){
            Log.d(TAG,"Instance requested from ServiceConnection");
            synchronized (lock) {
                return localInstance;
            }
        }

        public SettableFuture<CentralizedPlaybackManager> getInstanceFuture(){
            SettableFuture<CentralizedPlaybackManager> result = SettableFuture.create();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                synchronized (lock) {
                    while (localInstance == null) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Log.w(TAG, "Thread interrupted while waiting for localInstance to become not null, returning localInstance (probably null)");
                        }
                    }
                    result.set(localInstance);
                }
            });
            return result;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG,"Binding client to CentralizedPlaybackManager");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Unbinding client to CentralizedPlaybackManager");
        return false;
    }

    // This is the CRUCIAL method you need to implement
    @Override
    public void onCreate() {
        super.onCreate();
        setupPlayer();
        executeInitializationTasks();
        //tempPlayerStateLog();
        Log.d(TAG, "CentralizedPlaybackManager created");
    }

    private void tempPlayerStateLog(){
        Executors.newSingleThreadExecutor().execute(() -> {
            while (true){
                try {
                    Thread.sleep(2000);
                    mainHandler.post(() -> {
                        if(player != null && player.getCurrentMediaItem() != null) Log.d(TAG,"Player mediaItem: " + player.getCurrentMediaItem().mediaId);
                    });
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CentralizedPlaybackManager destroyed");
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeMediaItems(fromIndex,toIndex));
            return;
        }
        player.removeMediaItems(fromIndex,toIndex);
    }

    @Override
    public void clearMediaItems() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearMediaItems);
            return;
        }
        player.clearMediaItems();
    }

    @Override
    public boolean isCommandAvailable(int command) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(() -> isCommandAvailable(command));
        }else{
            return player.isCommandAvailable(command);
        }
    }

    @Override
    public boolean canAdvertiseSession() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::canAdvertiseSession);
        }else{
            return player.canAdvertiseSession();
        }
    }

    @Override
    public Commands getAvailableCommands() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Commands) convertToMainThreadTask(this::getAvailableCommands);
        }else{
            return player.getAvailableCommands();
        }
    }

    @Override
    public void prepare() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::prepare);
            return;
        }
        player.prepare();
    }

    @Override
    public int getPlaybackState() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPlaybackState);
        }else{
            return player.getPlaybackState();
        }
    }

    @Override
    public int getPlaybackSuppressionReason() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPlaybackSuppressionReason);
        }else{
            return player.getPlaybackSuppressionReason();
        }
    }

    @Override
    public boolean isPlaying() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isPlaying);
        }else{
            return player.isPlaying();
        }
    }

    @Override
    public void setAudioSessionId(int audioSessionId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAudioSessionId(audioSessionId));
            return;
        }
        player.setAudioSessionId(audioSessionId);
    }

    @Override
    public int getAudioSessionId() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getAudioSessionId);
        }else{
            return player.getAudioSessionId();
        }
    }

    @Override
    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAuxEffectInfo(auxEffectInfo));
            return;
        }
        player.setAuxEffectInfo(auxEffectInfo);
    }

    @Override
    public void clearAuxEffectInfo() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearAuxEffectInfo);
            return;
        }
        player.clearAuxEffectInfo();
    }

    @Override
    public void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPreferredAudioDevice(audioDeviceInfo));
            return;
        }
        player.setPreferredAudioDevice(audioDeviceInfo);
    }

    @Override
    public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSkipSilenceEnabled(skipSilenceEnabled));
            return;
        }
        player.setSkipSilenceEnabled(skipSilenceEnabled);
    }

    @Override
    public boolean getSkipSilenceEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getSkipSilenceEnabled);
        }else{
            return player.getSkipSilenceEnabled();
        }
    }

    @Override
    public void setVideoEffects(List<Effect> videoEffects) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoEffects(videoEffects));
            return;
        }
        player.setVideoEffects(videoEffects);
    }

    @Override
    public void setVideoScalingMode(int videoScalingMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoScalingMode(videoScalingMode));
            return;
        }
        player.setVideoScalingMode(videoScalingMode);
    }

    @Override
    public int getVideoScalingMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getVideoScalingMode);
        }else{
            return player.getVideoScalingMode();
        }
    }

    @Override
    public void setVideoChangeFrameRateStrategy(int videoChangeFrameRateStrategy) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy));
            return;
        }
        player.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy);
    }

    @Override
    public int getVideoChangeFrameRateStrategy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getVideoChangeFrameRateStrategy);
        }else{
            return player.getVideoChangeFrameRateStrategy();
        }
    }

    @Override
    public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoFrameMetadataListener(listener));
            return;
        }
        player.setVideoFrameMetadataListener(listener);
    }

    @Override
    public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoFrameMetadataListener(listener));
            return;
        }
        player.clearVideoFrameMetadataListener(listener);
    }

    @Override
    public void setCameraMotionListener(CameraMotionListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setCameraMotionListener(listener));
            return;
        }
        player.setCameraMotionListener(listener);
    }

    @Override
    public void clearCameraMotionListener(CameraMotionListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearCameraMotionListener(listener));
            return;
        }
        player.clearCameraMotionListener(listener);
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PlayerMessage) convertToMainThreadTask(() -> createMessage(target));
        }else{
            return player.createMessage(target);
        }
    }

    @Override
    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSeekParameters(seekParameters));
            return;
        }
        player.setSeekParameters(seekParameters);
    }

    @Override
    public SeekParameters getSeekParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (SeekParameters) convertToMainThreadTask(this::getSeekParameters);
        }else{
            return player.getSeekParameters();
        }
    }

    @Override
    public void setForegroundMode(boolean foregroundMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setForegroundMode(foregroundMode));
            return;
        }
        player.setForegroundMode(foregroundMode);
    }

    @Override
    public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems));
            return;
        }
        player.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
    }

    @Override
    public boolean getPauseAtEndOfMediaItems() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getPauseAtEndOfMediaItems);
        }else{
            return player.getPauseAtEndOfMediaItems();
        }
    }

    @Nullable
    @Override
    public Format getAudioFormat() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Format) convertToMainThreadTask(this::getAudioFormat);
        }else{
            return player.getAudioFormat();
        }
    }

    @Nullable
    @Override
    public Format getVideoFormat() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Format) convertToMainThreadTask(this::getVideoFormat);
        }else{
            return player.getVideoFormat();
        }
    }

    @Nullable
    @Override
    public DecoderCounters getAudioDecoderCounters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DecoderCounters) convertToMainThreadTask(this::getAudioDecoderCounters);
        }else{
            return player.getAudioDecoderCounters();
        }
    }

    @Nullable
    @Override
    public DecoderCounters getVideoDecoderCounters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DecoderCounters) convertToMainThreadTask(this::getVideoDecoderCounters);
        }else{
            return player.getVideoDecoderCounters();
        }
    }

    @Override
    public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setHandleAudioBecomingNoisy(handleAudioBecomingNoisy));
            return;
        }
        player.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy);
    }

    @Override
    public void setWakeMode(int wakeMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setWakeMode(wakeMode));
            return;
        }
        player.setWakeMode(wakeMode);
    }

    @Override
    public void setPriority(int priority) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPriority(priority));
            return;
        }
        player.setPriority(priority);
    }

    @Override
    public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPriorityTaskManager(priorityTaskManager));
            return;
        }
        player.setPriorityTaskManager(priorityTaskManager);
    }

    @Override
    public boolean isSleepingForOffload() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isSleepingForOffload);
        }else{
            return player.isSleepingForOffload();
        }
    }

    @Override
    public boolean isTunnelingEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isTunnelingEnabled);
        }else{
            return player.isTunnelingEnabled();
        }
    }

    @Override
    public void release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::release);
            return;
        }
        player.release();
    }

    @Override
    public Tracks getCurrentTracks() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Tracks) convertToMainThreadTask(this::getCurrentTracks);
        }else{
            return player.getCurrentTracks();
        }
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelectionParameters) convertToMainThreadTask(this::getTrackSelectionParameters);
        }else{
            return player.getTrackSelectionParameters();
        }
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setTrackSelectionParameters(parameters));
            return;
        }
        player.setTrackSelectionParameters(parameters);
    }

    @Override
    public MediaMetadata getMediaMetadata() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaMetadata) convertToMainThreadTask(this::getMediaMetadata);
        }else{
            return player.getMediaMetadata();
        }
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaMetadata) convertToMainThreadTask(this::getPlaylistMetadata);
        }else{
            return player.getPlaylistMetadata();
        }
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaylistMetadata(mediaMetadata));
            return;
        }
        player.setPlaylistMetadata(mediaMetadata);
    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return convertToMainThreadTask(this::getCurrentManifest);
        }else{
            return player.getCurrentManifest();
        }
    }

    @Override
    public Timeline getCurrentTimeline() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Timeline) convertToMainThreadTask(this::getCurrentTimeline);
        }else{
            return player.getCurrentTimeline();
        }
    }

    @Override
    public int getCurrentPeriodIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentPeriodIndex);
        }else{
            return player.getCurrentPeriodIndex();
        }
    }

    @Override
    public int getCurrentWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentWindowIndex);
        }else{
            return player.getCurrentWindowIndex();
        }
    }

    @Override
    public int getCurrentMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentMediaItemIndex);
        }else{
            return player.getCurrentMediaItemIndex();
        }
    }

    @Override
    public int getNextWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getNextWindowIndex);
        }else{
            return player.getNextWindowIndex();
        }
    }

    @Override
    public int getNextMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getNextMediaItemIndex);
        }else{
            return player.getNextMediaItemIndex();
        }
    }

    @Override
    public int getPreviousWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPreviousWindowIndex);
        }else{
            return player.getPreviousWindowIndex();
        }
    }

    @Override
    public int getPreviousMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPreviousMediaItemIndex);
        }else{
            return player.getPreviousMediaItemIndex();
        }
    }

    @Nullable
    @Override
    public MediaItem getCurrentMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaItem) convertToMainThreadTask(this::getCurrentMediaItem);
        }else{
            return player.getCurrentMediaItem();
        }
    }

    @Override
    public int getMediaItemCount() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getMediaItemCount);
        }else{
            return player.getMediaItemCount();
        }
    }

    @Override
    public MediaItem getMediaItemAt(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaItem) convertToMainThreadTask(() -> getMediaItemAt(index));
        }else{
            return player.getMediaItemAt(index);
        }
    }

    @Override
    public long getDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getDuration);
        }else{
            return player.getDuration();
        }
    }

    @Override
    public long getCurrentPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentPosition);
        }else{
            return player.getCurrentPosition();
        }
    }

    @Override
    public long getBufferedPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getBufferedPosition);
        }else{
            return player.getBufferedPosition();
        }
    }

    @Override
    public int getBufferedPercentage() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getBufferedPercentage);
        }else{
            return player.getBufferedPercentage();
        }
    }

    @Override
    public long getTotalBufferedDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getTotalBufferedDuration);
        }else{
            return player.getTotalBufferedDuration();
        }
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowDynamic);
        }else{
            return player.isCurrentWindowDynamic();
        }
    }

    @Override
    public boolean isCurrentMediaItemDynamic() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemDynamic);
        }else{
            return player.isCurrentMediaItemDynamic();
        }
    }

    @Override
    public boolean isCurrentWindowLive() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowLive);
        }else{
            return player.isCurrentWindowLive();
        }
    }

    @Override
    public boolean isCurrentMediaItemLive() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemLive);
        }else{
            return player.isCurrentMediaItemLive();
        }
    }

    @Override
    public long getCurrentLiveOffset() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getCurrentLiveOffset);
        }else{
            return player.getCurrentLiveOffset();
        }
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowSeekable);
        }else{
            return player.isCurrentWindowSeekable();
        }
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemSeekable);
        }else{
            return player.isCurrentMediaItemSeekable();
        }
    }

    @Override
    public boolean isPlayingAd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isPlayingAd);
        }else{
            return player.isPlayingAd();
        }
    }

    @Override
    public int getCurrentAdGroupIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentAdGroupIndex);
        }else{
            return player.getCurrentAdGroupIndex();
        }
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentAdIndexInAdGroup);
        }else{
            return player.getCurrentAdIndexInAdGroup();
        }
    }

    @Override
    public long getContentDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentDuration);
        }else{
            return player.getContentDuration();
        }
    }

    @Override
    public long getContentPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentPosition);
        }else{
            return player.getContentPosition();
        }
    }

    @Override
    public long getContentBufferedPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentBufferedPosition);
        }else{
            return player.getContentBufferedPosition();
        }
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AudioAttributes) convertToMainThreadTask(this::getAudioAttributes);
        }else{
            return player.getAudioAttributes();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVolume(volume));
            return;
        }
        player.setVolume(volume);
    }

    @Override
    public float getVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (float) convertToMainThreadTask(this::getVolume);
        }else{
            return player.getVolume();
        }
    }

    @Override
    public void clearVideoSurface() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearVideoSurface);
            return;
        }
        player.clearVideoSurface();
    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurface(surface));
            return;
        }
        player.clearVideoSurface(surface);
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurface(surface));
            return;
        }
        player.setVideoSurface(surface);
    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurfaceHolder(surfaceHolder));
            return;
        }
        player.setVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurfaceHolder(surfaceHolder));
            return;
        }
        player.clearVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurfaceView(surfaceView));
            return;
        }
        player.setVideoSurfaceView(surfaceView);
    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurfaceView(surfaceView));
            return;
        }
        player.clearVideoSurfaceView(surfaceView);
    }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoTextureView(textureView));
            return;
        }
        player.setVideoTextureView(textureView);
    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoTextureView(textureView));
            return;
        }
        player.clearVideoTextureView(textureView);
    }

    @Override
    public VideoSize getVideoSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (VideoSize) convertToMainThreadTask(this::getVideoSize);
        }else{
            return player.getVideoSize();
        }
    }

    @Override
    public Size getSurfaceSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Size) convertToMainThreadTask(this::getSurfaceSize);
        }else{
            return player.getSurfaceSize();
        }
    }

    @Override
    public CueGroup getCurrentCues() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (CueGroup) convertToMainThreadTask(this::getCurrentCues);
        }else{
            return player.getCurrentCues();
        }
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DeviceInfo) convertToMainThreadTask(this::getDeviceInfo);
        }else{
            return player.getDeviceInfo();
        }
    }

    @Override
    public int getDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getDeviceVolume);
        }else{
            return player.getDeviceVolume();
        }
    }

    @Override
    public boolean isDeviceMuted() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isDeviceMuted);
        }else{
            return player.isDeviceMuted();
        }
    }

    @Override
    public void setDeviceVolume(int volume) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceVolume(volume));
            return;
        }
        player.setDeviceVolume(volume);
    }

    @Override
    public void setDeviceVolume(int volume, int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceVolume(volume,flags));
            return;
        }
        player.setDeviceVolume(volume,flags);
    }

    @Override
    public void increaseDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> increaseDeviceVolume());
            return;
        }
        player.increaseDeviceVolume();
    }

    @Override
    public void increaseDeviceVolume(int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> increaseDeviceVolume(flags));
            return;
        }
        player.increaseDeviceVolume(flags);
    }

    @Override
    public void decreaseDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> decreaseDeviceVolume());
            return;
        }
        player.decreaseDeviceVolume();
    }

    @Override
    public void decreaseDeviceVolume(int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> decreaseDeviceVolume(flags));
            return;
        }
        player.decreaseDeviceVolume(flags);
    }

    @Override
    public void setDeviceMuted(boolean muted) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceMuted(muted));
            return;
        }
        player.setDeviceMuted(muted);
    }

    @Override
    public void setDeviceMuted(boolean muted, int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceMuted(muted,flags));
            return;
        }
        player.setDeviceMuted(muted,flags);
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAudioAttributes(audioAttributes,handleAudioFocus));
            return;
        }
        player.setAudioAttributes(audioAttributes,handleAudioFocus);
    }

    @Override
    public boolean isReleased() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isReleased);
        }else{
            return player.isReleased();
        }
    }

    @Override
    public void setImageOutput(@Nullable ImageOutput imageOutput) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setImageOutput(imageOutput));
            return;
        }
        player.setImageOutput(imageOutput);
    }
}
