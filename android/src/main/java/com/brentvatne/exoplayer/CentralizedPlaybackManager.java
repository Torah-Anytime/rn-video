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
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.PlayerMessage;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.SeekParameters;
import androidx.media3.exoplayer.analytics.AnalyticsCollector;
import androidx.media3.exoplayer.analytics.AnalyticsListener;
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider;
import androidx.media3.exoplayer.image.ImageOutput;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.ShuffleOrder;
import androidx.media3.exoplayer.source.TrackGroupArray;
import androidx.media3.exoplayer.trackselection.TrackSelectionArray;
import androidx.media3.exoplayer.trackselection.TrackSelector;
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy;
import androidx.media3.exoplayer.video.VideoFrameMetadataListener;
import androidx.media3.exoplayer.video.spherical.CameraMotionListener;

import android.app.Service;

import com.google.common.util.concurrent.SettableFuture;

import java.util.Arrays;
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
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final long COMMUNICATION_WAIT = 30000;

    private ExoPlayer player;
    private final IBinder binder = new LocalBinder();

    private static volatile CentralizedPlaybackManager instance = null;

    private final static boolean logAllMethodCalls = false;


    //===== Initialization =====

    public CentralizedPlaybackManager(){
        Log.d(TAG,"CPM Instance Created");
    }

    private void setupPlayer(){
        // Ensure we're on the main thread
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::setupPlayer);
            return;
        }

        Log.d(TAG, "Setting up the player on " + this.getApplicationContext());
        this.player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(getCustomMediaSourceFactory())
                .build();
        this.player.setAudioAttributes(AudioAttributes.DEFAULT,true);

        startDebuggingListener();
    }

    private MediaSource.Factory getCustomMediaSourceFactory(){
        DataSource.Factory dsFactory = new DefaultDataSource.Factory(getApplicationContext());
        ProgressiveMediaSource.Factory defaultFactory = new ProgressiveMediaSource.Factory(dsFactory);

        return new MediaSource.Factory() {
            @Override
            public MediaSource.Factory setDrmSessionManagerProvider(DrmSessionManagerProvider drmSessionManagerProvider) {
                return defaultFactory.setDrmSessionManagerProvider(drmSessionManagerProvider);
            }

            @Override
            public MediaSource.Factory setLoadErrorHandlingPolicy(LoadErrorHandlingPolicy loadErrorHandlingPolicy) {
                return defaultFactory.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy);
            }

            @Override
            public int[] getSupportedTypes() {
                return defaultFactory.getSupportedTypes();
            }

            @Override
            public MediaSource createMediaSource(MediaItem mediaItem) {
                return defaultFactory.createMediaSource(mediaItem);
            }
        };
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
        Log.d(TAG, "CentralizedPlaybackManager created");
    }

    @Override
    public void onDestroy() {
        synchronized (CentralizedPlaybackManager.class) {
            super.onDestroy();
            instance = null;
            player.release();
        }
        Log.d(TAG, "CentralizedPlaybackManager destroyed");
    }

    //===== Misc Public API =====

    /**
     * Get the main handler of this object, so other parts of the application can run code on the same
     * @return the instance's mainHandler
     */
    public static Handler getMainHandler() {
        return mainHandler;
    }


    //===== Debugging =====

    private void startDebuggingListener(){
        player.addListener(new Player.Listener() {
            /*@Override
            public void onTimelineChanged(Timeline timeline, int reason) {
                try{
                    throw new IllegalStateException();
                } catch (IllegalStateException e) {
                    Log.w(TAG,"Source changed: current index: " + player.getCurrentMediaItemIndex());
                    Log.e(TAG,"Source changed: reason: " + reason + " Stack trace: ");
                    for(StackTraceElement element : e.getStackTrace()){
                        Log.e(TAG,"\t" + element.toString());
                    }
                }
            }*/
        });
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

    private void logMethodCall(String methodName){
        if(logAllMethodCalls && !methodName.startsWith("get")) Log.d(TAG,"Method Called: " + methodName);
    }

    //===== Overrides =====

    @Nullable
    @Override
    public ExoPlaybackException getPlayerError() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
           return (ExoPlaybackException) convertToMainThreadTask(() -> player.getPlayerError());
        }else {
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlayerError();
        }
    }

    @Override
    public void play() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::play);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.play();
    }

    @Override
    public void pause() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::pause);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.pause();
    }

    @Override
    public void setPlayWhenReady(boolean playWhenReady) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setPlayWhenReady(playWhenReady));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPlayWhenReady(playWhenReady);
    }

    @Override
    public boolean getPlayWhenReady() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getPlayWhenReady);
        }else {
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlayWhenReady();
        }
    }

    @Override
    public void setRepeatMode(int repeatMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setRepeatMode(repeatMode));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setRepeatMode(repeatMode);
    }

    @Override
    public int getRepeatMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getRepeatMode);
        }else {
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getRepeatMode();
        }
    }

    @Override
    public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.setShuffleModeEnabled(shuffleModeEnabled));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setShuffleModeEnabled(shuffleModeEnabled);
    }

    @Override
    public boolean getShuffleModeEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getShuffleModeEnabled);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getShuffleModeEnabled();
        }
    }

    @Override
    public boolean isLoading() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isLoading);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isLoading();
        }
    }

    @Override
    public void seekToDefaultPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToDefaultPosition);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToDefaultPosition();
    }

    @Override
    public void seekToDefaultPosition(int mediaItemIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekToDefaultPosition(mediaItemIndex));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToDefaultPosition(mediaItemIndex);
    }

    @Override
    public void seekTo(long positionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekTo(positionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekTo(positionMs);
    }

    @Override
    public void seekTo(int mediaItemIndex, long positionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> this.seekTo(mediaItemIndex,positionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekTo(mediaItemIndex,positionMs);
    }

    @Override
    public long getSeekBackIncrement() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getSeekBackIncrement);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getSeekBackIncrement();
        }
    }

    @Override
    public void seekBack() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekBack);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekBack();
    }

    @Override
    public long getSeekForwardIncrement() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getSeekForwardIncrement);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getSeekForwardIncrement();
        }
    }

    @Override
    public void seekForward() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekForward);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekForward();
    }

    @Override
    public boolean hasPrevious() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPrevious);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasPrevious();
        }
    }

    @Override
    public boolean hasPreviousWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPreviousWindow);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasPreviousWindow();
        }
    }

    @Override
    public boolean hasPreviousMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasPreviousMediaItem);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasPreviousMediaItem();
        }
    }

    @Override
    public void previous() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::previous);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.previous();
    }

    @Override
    public void seekToPreviousWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPreviousWindow);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToPreviousWindow();
    }

    @Override
    public void seekToPreviousMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPreviousMediaItem);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToPreviousMediaItem();
    }

    @Override
    public long getMaxSeekToPreviousPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getMaxSeekToPreviousPosition);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getMaxSeekToPreviousPosition();
        }
    }

    @Override
    public void seekToPrevious() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToPrevious);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToPrevious();
    }

    @Override
    public boolean hasNext() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNext);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasNext();
        }
    }

    @Override
    public boolean hasNextWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNextWindow);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasNextWindow();
        }
    }

    @Override
    public boolean hasNextMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::hasNextMediaItem);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.hasNextMediaItem();
        }
    }

    @Override
    public void next() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::next);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.next();
    }

    @Override
    public void seekToNextWindow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNextWindow);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToNextWindow();
    }

    @Override
    public void seekToNextMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNextMediaItem);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToNextMediaItem();
    }

    @Override
    public void seekToNext() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::seekToNext);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.seekToNext();
    }

    @Override
    public void setPlaybackParameters(PlaybackParameters playbackParameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaybackParameters(playbackParameters));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPlaybackParameters(playbackParameters);
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaybackSpeed(speed));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPlaybackSpeed(speed);
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PlaybackParameters) convertToMainThreadTask(this::getPlaybackParameters);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlaybackParameters();
        }
    }

    @Override
    public void stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::stop);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.stop();
    }

    @Nullable
    @Override
    public AudioComponent getAudioComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AudioComponent) convertToMainThreadTask(this::getAudioComponent);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAudioComponent();
        }
    }

    @Nullable
    @Override
    public VideoComponent getVideoComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (VideoComponent) convertToMainThreadTask(this::getVideoComponent);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoComponent();
        }
    }

    @Nullable
    @Override
    public TextComponent getTextComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TextComponent) convertToMainThreadTask(this::getTextComponent);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getTextComponent();
        }
    }

    @Nullable
    @Override
    public DeviceComponent getDeviceComponent() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DeviceComponent) convertToMainThreadTask(this::getDeviceComponent);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getDeviceComponent();
        }
    }

    @Override
    public void addAudioOffloadListener(AudioOffloadListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addAudioOffloadListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addAudioOffloadListener(listener);
    }

    @Override
    public void removeAudioOffloadListener(AudioOffloadListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeAudioOffloadListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.removeAudioOffloadListener(listener);
    }

    @Override
    public AnalyticsCollector getAnalyticsCollector() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AnalyticsCollector) convertToMainThreadTask(this::getAnalyticsCollector);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAnalyticsCollector();
        }
    }

    @Override
    public void addAnalyticsListener(AnalyticsListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addAnalyticsListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addAnalyticsListener(listener);
    }

    @Override
    public void removeAnalyticsListener(AnalyticsListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeAnalyticsListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.removeAnalyticsListener(listener);
    }

    @Override
    public int getRendererCount() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getRendererCount);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getRendererCount();
        }
    }

    @Override
    public int getRendererType(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(() -> getRendererType(index));
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getRendererType(index);
        }
    }

    @Override
    public Renderer getRenderer(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Renderer) convertToMainThreadTask(() -> getRenderer(index));
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getRenderer(index);
        }
    }

    @Nullable
    @Override
    public TrackSelector getTrackSelector() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelector) convertToMainThreadTask(this::getTrackSelector);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getTrackSelector();
        }
    }

    @Override
    public TrackGroupArray getCurrentTrackGroups() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackGroupArray) convertToMainThreadTask(this::getCurrentTrackGroups);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentTrackGroups();
        }
    }

    @Override
    public TrackSelectionArray getCurrentTrackSelections() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelectionArray) convertToMainThreadTask(this::getCurrentTrackSelections);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentTrackSelections();
        }
    }

    @Override
    public Looper getPlaybackLooper() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Looper) convertToMainThreadTask(this::getPlaybackLooper);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlaybackLooper();
        }
    }

    @Override
    public Clock getClock() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Clock) convertToMainThreadTask(this::getClock);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getClock();
        }
    }

    @Override
    public void prepare(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prepare(mediaSource));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.prepare(mediaSource);
    }

    @Override
    public void prepare(MediaSource mediaSource, boolean resetPosition, boolean resetState) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> prepare(mediaSource, resetPosition, resetState));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.prepare(mediaSource, resetPosition, resetState);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSources(mediaSources);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources, resetPosition));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSources(mediaSources, resetPosition);
    }

    @Override
    public void setMediaSources(List<MediaSource> mediaSources, int startMediaItemIndex, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSources(mediaSources, startMediaItemIndex, startPositionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSources(mediaSources, startMediaItemIndex, startPositionMs);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSource(mediaSource);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource, startPositionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSource(mediaSource, startPositionMs);
    }

    @Override
    public void setMediaSource(MediaSource mediaSource, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaSource(mediaSource, resetPosition));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaSource(mediaSource, resetPosition);
    }

    @Override
    public void addMediaSource(MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSource(mediaSource));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaSource(mediaSource);
    }

    @Override
    public void addMediaSource(int index, MediaSource mediaSource) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSource(index,mediaSource));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaSource(index, mediaSource);
    }

    @Override
    public void addMediaSources(List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSources(mediaSources));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaSources(mediaSources);
    }

    @Override
    public void addMediaSources(int index, List<MediaSource> mediaSources) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaSources(index,mediaSources));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaSources(index,mediaSources);
    }

    @Override
    public void setShuffleOrder(ShuffleOrder shuffleOrder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setShuffleOrder(shuffleOrder));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setShuffleOrder(shuffleOrder);
    }

    @Override
    public void setPreloadConfiguration(PreloadConfiguration preloadConfiguration) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPreloadConfiguration(preloadConfiguration));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPreloadConfiguration(preloadConfiguration);
    }

    @Override
    public PreloadConfiguration getPreloadConfiguration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PreloadConfiguration) convertToMainThreadTask(this::getPreloadConfiguration);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPreloadConfiguration();
        }
    }

    @Override
    public Looper getApplicationLooper() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Looper) convertToMainThreadTask(this::getApplicationLooper);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getApplicationLooper();
        }
    }

    @Override
    public void addListener(Player.Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addListener(listener);
    }

    @Override
    public void removeListener(Player.Listener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.removeListener(listener);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItems(mediaItems);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems, resetPosition));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItems(mediaItems, resetPosition);
    }

    @Override
    public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItems(mediaItems, startIndex, startPositionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItems(mediaItems, startIndex, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItem(mediaItem);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem, startPositionMs));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItem(mediaItem, startPositionMs);
    }

    @Override
    public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setMediaItem(mediaItem, resetPosition));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setMediaItem(mediaItem, resetPosition);
    }

    @Override
    public void addMediaItem(MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItem(mediaItem));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaItem(mediaItem);
    }

    @Override
    public void addMediaItem(int index, MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItem(index, mediaItem));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaItem(index, mediaItem);
    }

    @Override
    public void addMediaItems(List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItems(mediaItems));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaItems(mediaItems);
    }

    @Override
    public void addMediaItems(int index, List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> addMediaItems(index,mediaItems));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.addMediaItems(index,mediaItems);
    }

    @Override
    public void moveMediaItem(int currentIndex, int newIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> moveMediaItem(currentIndex,newIndex));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.moveMediaItem(currentIndex,newIndex);
    }

    @Override
    public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> moveMediaItems(fromIndex,toIndex,newIndex));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.moveMediaItems(fromIndex,toIndex,newIndex);
    }

    @Override
    public void replaceMediaItem(int index, MediaItem mediaItem) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> replaceMediaItem(index,mediaItem));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.replaceMediaItem(index,mediaItem);
    }

    @Override
    public void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> replaceMediaItems(fromIndex,toIndex,mediaItems));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.replaceMediaItems(fromIndex,toIndex,mediaItems);
    }

    @Override
    public void removeMediaItem(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeMediaItem(index));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.removeMediaItem(index);
    }

    @Override
    public void removeMediaItems(int fromIndex, int toIndex) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> removeMediaItems(fromIndex,toIndex));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.removeMediaItems(fromIndex,toIndex);
    }

    @Override
    public void clearMediaItems() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearMediaItems);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearMediaItems();
    }

    @Override
    public boolean isCommandAvailable(int command) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(() -> isCommandAvailable(command));
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCommandAvailable(command);
        }
    }

    @Override
    public boolean canAdvertiseSession() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::canAdvertiseSession);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.canAdvertiseSession();
        }
    }

    @Override
    public Commands getAvailableCommands() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Commands) convertToMainThreadTask(this::getAvailableCommands);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAvailableCommands();
        }
    }

    @Override
    public void prepare() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::prepare);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.prepare();
    }

    @Override
    public int getPlaybackState() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPlaybackState);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlaybackState();
        }
    }

    @Override
    public int getPlaybackSuppressionReason() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPlaybackSuppressionReason);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlaybackSuppressionReason();
        }
    }

    @Override
    public boolean isPlaying() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isPlaying);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isPlaying();
        }
    }

    @Override
    public void setAudioSessionId(int audioSessionId) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAudioSessionId(audioSessionId));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setAudioSessionId(audioSessionId);
    }

    @Override
    public int getAudioSessionId() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getAudioSessionId);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAudioSessionId();
        }
    }

    @Override
    public void setAuxEffectInfo(AuxEffectInfo auxEffectInfo) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAuxEffectInfo(auxEffectInfo));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setAuxEffectInfo(auxEffectInfo);
    }

    @Override
    public void clearAuxEffectInfo() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearAuxEffectInfo);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearAuxEffectInfo();
    }

    @Override
    public void setPreferredAudioDevice(@Nullable AudioDeviceInfo audioDeviceInfo) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPreferredAudioDevice(audioDeviceInfo));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPreferredAudioDevice(audioDeviceInfo);
    }

    @Override
    public void setSkipSilenceEnabled(boolean skipSilenceEnabled) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSkipSilenceEnabled(skipSilenceEnabled));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setSkipSilenceEnabled(skipSilenceEnabled);
    }

    @Override
    public boolean getSkipSilenceEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getSkipSilenceEnabled);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getSkipSilenceEnabled();
        }
    }

    @Override
    public void setVideoEffects(List<Effect> videoEffects) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoEffects(videoEffects));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoEffects(videoEffects);
    }

    @Override
    public void setVideoScalingMode(int videoScalingMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoScalingMode(videoScalingMode));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoScalingMode(videoScalingMode);
    }

    @Override
    public int getVideoScalingMode() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getVideoScalingMode);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoScalingMode();
        }
    }

    @Override
    public void setVideoChangeFrameRateStrategy(int videoChangeFrameRateStrategy) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoChangeFrameRateStrategy(videoChangeFrameRateStrategy);
    }

    @Override
    public int getVideoChangeFrameRateStrategy() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getVideoChangeFrameRateStrategy);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoChangeFrameRateStrategy();
        }
    }

    @Override
    public void setVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoFrameMetadataListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoFrameMetadataListener(listener);
    }

    @Override
    public void clearVideoFrameMetadataListener(VideoFrameMetadataListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoFrameMetadataListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoFrameMetadataListener(listener);
    }

    @Override
    public void setCameraMotionListener(CameraMotionListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setCameraMotionListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setCameraMotionListener(listener);
    }

    @Override
    public void clearCameraMotionListener(CameraMotionListener listener) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearCameraMotionListener(listener));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearCameraMotionListener(listener);
    }

    @Override
    public PlayerMessage createMessage(PlayerMessage.Target target) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (PlayerMessage) convertToMainThreadTask(() -> createMessage(target));
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.createMessage(target);
        }
    }

    @Override
    public void setSeekParameters(@Nullable SeekParameters seekParameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setSeekParameters(seekParameters));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setSeekParameters(seekParameters);
    }

    @Override
    public SeekParameters getSeekParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (SeekParameters) convertToMainThreadTask(this::getSeekParameters);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getSeekParameters();
        }
    }

    @Override
    public void setForegroundMode(boolean foregroundMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setForegroundMode(foregroundMode));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setForegroundMode(foregroundMode);
    }

    @Override
    public void setPauseAtEndOfMediaItems(boolean pauseAtEndOfMediaItems) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPauseAtEndOfMediaItems(pauseAtEndOfMediaItems);
    }

    @Override
    public boolean getPauseAtEndOfMediaItems() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::getPauseAtEndOfMediaItems);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPauseAtEndOfMediaItems();
        }
    }

    @Nullable
    @Override
    public Format getAudioFormat() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Format) convertToMainThreadTask(this::getAudioFormat);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAudioFormat();
        }
    }

    @Nullable
    @Override
    public Format getVideoFormat() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Format) convertToMainThreadTask(this::getVideoFormat);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoFormat();
        }
    }

    @Nullable
    @Override
    public DecoderCounters getAudioDecoderCounters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DecoderCounters) convertToMainThreadTask(this::getAudioDecoderCounters);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAudioDecoderCounters();
        }
    }

    @Nullable
    @Override
    public DecoderCounters getVideoDecoderCounters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DecoderCounters) convertToMainThreadTask(this::getVideoDecoderCounters);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoDecoderCounters();
        }
    }

    @Override
    public void setHandleAudioBecomingNoisy(boolean handleAudioBecomingNoisy) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setHandleAudioBecomingNoisy(handleAudioBecomingNoisy));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setHandleAudioBecomingNoisy(handleAudioBecomingNoisy);
    }

    @Override
    public void setWakeMode(int wakeMode) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setWakeMode(wakeMode));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setWakeMode(wakeMode);
    }

    @Override
    public void setPriority(int priority) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPriority(priority));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPriority(priority);
    }

    @Override
    public void setPriorityTaskManager(@Nullable PriorityTaskManager priorityTaskManager) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPriorityTaskManager(priorityTaskManager));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPriorityTaskManager(priorityTaskManager);
    }

    @Override
    public boolean isSleepingForOffload() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isSleepingForOffload);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isSleepingForOffload();
        }
    }

    @Override
    public boolean isTunnelingEnabled() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isTunnelingEnabled);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isTunnelingEnabled();
        }
    }

    @Override
    public void release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::release);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.release();
    }

    @Override
    public Tracks getCurrentTracks() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Tracks) convertToMainThreadTask(this::getCurrentTracks);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentTracks();
        }
    }

    @Override
    public TrackSelectionParameters getTrackSelectionParameters() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (TrackSelectionParameters) convertToMainThreadTask(this::getTrackSelectionParameters);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getTrackSelectionParameters();
        }
    }

    @Override
    public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setTrackSelectionParameters(parameters));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setTrackSelectionParameters(parameters);
    }

    @Override
    public MediaMetadata getMediaMetadata() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaMetadata) convertToMainThreadTask(this::getMediaMetadata);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getMediaMetadata();
        }
    }

    @Override
    public MediaMetadata getPlaylistMetadata() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaMetadata) convertToMainThreadTask(this::getPlaylistMetadata);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPlaylistMetadata();
        }
    }

    @Override
    public void setPlaylistMetadata(MediaMetadata mediaMetadata) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setPlaylistMetadata(mediaMetadata));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setPlaylistMetadata(mediaMetadata);
    }

    @Nullable
    @Override
    public Object getCurrentManifest() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return convertToMainThreadTask(this::getCurrentManifest);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentManifest();
        }
    }

    @Override
    public Timeline getCurrentTimeline() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Timeline) convertToMainThreadTask(this::getCurrentTimeline);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentTimeline();
        }
    }

    @Override
    public int getCurrentPeriodIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentPeriodIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentPeriodIndex();
        }
    }

    @Override
    public int getCurrentWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentWindowIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentWindowIndex();
        }
    }

    @Override
    public int getCurrentMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentMediaItemIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentMediaItemIndex();
        }
    }

    @Override
    public int getNextWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getNextWindowIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getNextWindowIndex();
        }
    }

    @Override
    public int getNextMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getNextMediaItemIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getNextMediaItemIndex();
        }
    }

    @Override
    public int getPreviousWindowIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPreviousWindowIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPreviousWindowIndex();
        }
    }

    @Override
    public int getPreviousMediaItemIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getPreviousMediaItemIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getPreviousMediaItemIndex();
        }
    }

    @Nullable
    @Override
    public MediaItem getCurrentMediaItem() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaItem) convertToMainThreadTask(this::getCurrentMediaItem);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentMediaItem();
        }
    }

    @Override
    public int getMediaItemCount() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getMediaItemCount);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getMediaItemCount();
        }
    }

    @Override
    public MediaItem getMediaItemAt(int index) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (MediaItem) convertToMainThreadTask(() -> getMediaItemAt(index));
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getMediaItemAt(index);
        }
    }

    @Override
    public long getDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getDuration);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getDuration();
        }
    }

    @Override
    public long getCurrentPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getCurrentPosition);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentPosition();
        }
    }

    @Override
    public long getBufferedPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getBufferedPosition);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getBufferedPosition();
        }
    }

    @Override
    public int getBufferedPercentage() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getBufferedPercentage);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getBufferedPercentage();
        }
    }

    @Override
    public long getTotalBufferedDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getTotalBufferedDuration);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getTotalBufferedDuration();
        }
    }

    @Override
    public boolean isCurrentWindowDynamic() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowDynamic);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentWindowDynamic();
        }
    }

    @Override
    public boolean isCurrentMediaItemDynamic() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemDynamic);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentMediaItemDynamic();
        }
    }

    @Override
    public boolean isCurrentWindowLive() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowLive);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentWindowLive();
        }
    }

    @Override
    public boolean isCurrentMediaItemLive() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemLive);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentMediaItemLive();
        }
    }

    @Override
    public long getCurrentLiveOffset() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getCurrentLiveOffset);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentLiveOffset();
        }
    }

    @Override
    public boolean isCurrentWindowSeekable() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentWindowSeekable);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentWindowSeekable();
        }
    }

    @Override
    public boolean isCurrentMediaItemSeekable() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isCurrentMediaItemSeekable);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isCurrentMediaItemSeekable();
        }
    }

    @Override
    public boolean isPlayingAd() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isPlayingAd);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isPlayingAd();
        }
    }

    @Override
    public int getCurrentAdGroupIndex() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentAdGroupIndex);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentAdGroupIndex();
        }
    }

    @Override
    public int getCurrentAdIndexInAdGroup() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getCurrentAdIndexInAdGroup);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentAdIndexInAdGroup();
        }
    }

    @Override
    public long getContentDuration() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentDuration);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getContentDuration();
        }
    }

    @Override
    public long getContentPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentPosition);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getContentPosition();
        }
    }

    @Override
    public long getContentBufferedPosition() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (long) convertToMainThreadTask(this::getContentBufferedPosition);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getContentBufferedPosition();
        }
    }

    @Override
    public AudioAttributes getAudioAttributes() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (AudioAttributes) convertToMainThreadTask(this::getAudioAttributes);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getAudioAttributes();
        }
    }

    @Override
    public void setVolume(float volume) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVolume(volume));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVolume(volume);
    }

    @Override
    public float getVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (float) convertToMainThreadTask(this::getVolume);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVolume();
        }
    }

    @Override
    public void clearVideoSurface() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::clearVideoSurface);
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoSurface();
    }

    @Override
    public void clearVideoSurface(@Nullable Surface surface) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurface(surface));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoSurface(surface);
    }

    @Override
    public void setVideoSurface(@Nullable Surface surface) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurface(surface));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoSurface(surface);
    }

    @Override
    public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurfaceHolder(surfaceHolder));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurfaceHolder(surfaceHolder));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoSurfaceHolder(surfaceHolder);
    }

    @Override
    public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoSurfaceView(surfaceView));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoSurfaceView(surfaceView);
    }

    @Override
    public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoSurfaceView(surfaceView));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoSurfaceView(surfaceView);
    }

    @Override
    public void setVideoTextureView(@Nullable TextureView textureView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setVideoTextureView(textureView));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setVideoTextureView(textureView);
    }

    @Override
    public void clearVideoTextureView(@Nullable TextureView textureView) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> clearVideoTextureView(textureView));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.clearVideoTextureView(textureView);
    }

    @Override
    public VideoSize getVideoSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (VideoSize) convertToMainThreadTask(this::getVideoSize);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getVideoSize();
        }
    }

    @Override
    public Size getSurfaceSize() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (Size) convertToMainThreadTask(this::getSurfaceSize);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getSurfaceSize();
        }
    }

    @Override
    public CueGroup getCurrentCues() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (CueGroup) convertToMainThreadTask(this::getCurrentCues);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getCurrentCues();
        }
    }

    @Override
    public DeviceInfo getDeviceInfo() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (DeviceInfo) convertToMainThreadTask(this::getDeviceInfo);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getDeviceInfo();
        }
    }

    @Override
    public int getDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (int) convertToMainThreadTask(this::getDeviceVolume);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.getDeviceVolume();
        }
    }

    @Override
    public boolean isDeviceMuted() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isDeviceMuted);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isDeviceMuted();
        }
    }

    @Override
    public void setDeviceVolume(int volume) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceVolume(volume));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setDeviceVolume(volume);
    }

    @Override
    public void setDeviceVolume(int volume, int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceVolume(volume,flags));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setDeviceVolume(volume,flags);
    }

    @Override
    public void increaseDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> increaseDeviceVolume());
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.increaseDeviceVolume();
    }

    @Override
    public void increaseDeviceVolume(int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> increaseDeviceVolume(flags));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.increaseDeviceVolume(flags);
    }

    @Override
    public void decreaseDeviceVolume() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> decreaseDeviceVolume());
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.decreaseDeviceVolume();
    }

    @Override
    public void decreaseDeviceVolume(int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> decreaseDeviceVolume(flags));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.decreaseDeviceVolume(flags);
    }

    @Override
    public void setDeviceMuted(boolean muted) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceMuted(muted));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setDeviceMuted(muted);
    }

    @Override
    public void setDeviceMuted(boolean muted, int flags) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setDeviceMuted(muted,flags));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setDeviceMuted(muted,flags);
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setAudioAttributes(audioAttributes,handleAudioFocus));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setAudioAttributes(audioAttributes,handleAudioFocus);
    }

    @Override
    public boolean isReleased() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            return (boolean) convertToMainThreadTask(this::isReleased);
        }else{
            logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
            return player.isReleased();
        }
    }

    @Override
    public void setImageOutput(@Nullable ImageOutput imageOutput) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> setImageOutput(imageOutput));
            return;
        }
        logMethodCall(new Throwable().getStackTrace()[0].getMethodName());
        player.setImageOutput(imageOutput);
    }
}