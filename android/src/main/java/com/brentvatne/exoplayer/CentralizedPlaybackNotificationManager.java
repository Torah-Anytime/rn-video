package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.MediaStyleNotificationHelper;
import androidx.media3.session.SessionCommand;

import com.brentvatne.react.R;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Centralized notification manager for media playback that coordinates with external media services.
 *
 * <p>This service manages media playback notifications and automatically defers notification
 * handling to external media clients when they are active. This prevents duplicate notifications
 * and ensures a seamless user experience across different interfaces.</p>
 *
 * <h3>External Media Service Coordination</h3>
 * <p>The service detects when external media controllers (such as Android Auto, Android TV,
 * or other car systems) are connected and automatically suppresses its own notifications to
 * avoid conflicts. External media services typically provide their own notification management
 * and UI controls.</p>
 *
 * <h3>Notification Management Logic</h3>
 * <ul>
 *   <li><strong>Show notifications when:</strong> No external controllers are connected and
 *       the device is not in car mode</li>
 *   <li><strong>Hide notifications when:</strong> External controllers are detected (Android Auto,
 *       car systems) or the device is in car mode (UI_MODE_TYPE_CAR)</li>
 * </ul>
 *
 * <p>This ensures that:</p>
 * <ul>
 *   <li>Users see appropriate media controls in their notification shade when using the app directly</li>
 *   <li>External systems (like car infotainment) can manage their own media UI without interference</li>
 *   <li>No duplicate or conflicting notifications appear across different interfaces</li>
 * </ul>
 *
 * @see MediaSessionService
 */
public class CentralizedPlaybackNotificationManager extends MediaSessionService {

    public static final String TAG = "CentralizedPlaybackNotificationManager";
    private final Binder binder = new CPNMBinder(this);
    private final SessionCommand commandSeekForward = new SessionCommand(Command.SEEK_FORWARD.stringValue, Bundle.EMPTY);
    private final SessionCommand commandSeekBackward = new SessionCommand(Command.SEEK_BACKWARD.stringValue, Bundle.EMPTY);
    private final String NOTIFICATION_CHANEL_ID = "CPNM_SESSION_NOTIFICATION";
    @SuppressLint("PrivateResource")
    private final CommandButton seekForwardBtn = new CommandButton.Builder().setDisplayName("forward").setSessionCommand(commandSeekForward).setIconResId(androidx.media3.ui.R.drawable.exo_notification_fastforward).build();
    @SuppressLint("PrivateResource")
    private final CommandButton seekBackwardBtn = new CommandButton.Builder().setDisplayName("backward").setSessionCommand(commandSeekBackward).setIconResId(androidx.media3.ui.R.drawable.exo_notification_rewind).build();
    private Player player = null;
    private MediaSession mediaSession = null;
    private boolean isForegroundServiceActive = false;

    /**
     * Sets up notification management for the specified player.
     *
     * <p>This method creates a MediaSession and determines whether to show notifications
     * based on the presence of external media controllers. If external controllers
     * (such as Android Auto or car systems) are detected, notifications are suppressed
     * to avoid duplication and conflicts.</p>
     *
     * <p><strong>External Media Service Integration:</strong></p>
     * <p>When external media services like Android Auto are active, they typically:</p>
     * <ul>
     *   <li>Connect as MediaSession controllers</li>
     *   <li>Provide their own media UI and notification management</li>
     *   <li>Expect to be the primary interface for media control</li>
     * </ul>
     *
     * <p>This method automatically detects such scenarios and defers notification
     * management to the external service, preventing duplicate notifications and
     * ensuring a cohesive user experience.</p>
     *
     * @param player The ExoPlayer instance to manage notifications for
     * @throws IllegalStateException if the service fails to create the MediaSession
     * @see #shouldShowNotification()
     */
    @SuppressLint("ForegroundServiceType")
    public void setup(Player player) {
        Log.i(TAG, "CPNM created with player " + player);
        if (this.player != null && player != this.player) {
            removePreviousNotification();
        }
        this.player = player;

        //FIXME Session ID Must Be Unique
        mediaSession = new MediaSession.Builder(this, player).setId("CPNMService_" + player.hashCode()).setCallback(new VideoPlaybackCallback()).setCustomLayout(ImmutableList.of(seekForwardBtn, seekBackwardBtn)).build();

        addSession(mediaSession);

        if (shouldShowNotification()) {
            Notification notification;
            try {
                notification = buildNotification(mediaSession);
            } catch (Exception e) {
                Log.w(TAG, "Exception thrown when building notification, running without notifications");
                return;
            }
            startForeground(player.hashCode(), notification);
            isForegroundServiceActive = true;
        }
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(@Nullable Intent intent) {
        super.onBind(intent);
        Log.d(TAG, "CPNM Bind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "CPNM Unbind");
        removeSession(mediaSession);
        stopSelf();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "CPNM destroyed");

        // Stop foreground service before cleaning up
        if (isForegroundServiceActive) {
            stopForeground(true);
            isForegroundServiceActive = false;
        }

        super.onDestroy();
        removePreviousNotification();
        this.mediaSession = null;
        this.player = null;
    }

    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        // Create notification channel
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                    new NotificationChannel(NOTIFICATION_CHANEL_ID, NOTIFICATION_CHANEL_ID,
                            NotificationManager.IMPORTANCE_LOW)
            );
        }

        // Build placeholder notification
        Notification placeholderNotification = new NotificationCompat.Builder(this, NOTIFICATION_CHANEL_ID)
                .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
                .setContentTitle(getString(R.string.media_playback_notification_title))
                .setContentText(getString(R.string.media_playback_notification_text))
                .build();

        // CRITICAL: Must call startForeground() immediately
        startForeground(1, placeholderNotification);
        isForegroundServiceActive = true;

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Updates the media notification when MediaSession state changes.
     *
     * <p>This method is called automatically by the MediaSession framework when
     * playback state, metadata, or other session properties change. It respects
     * external media service coordination by checking if notifications should be
     * displayed before proceeding with the update.</p>
     *
     * <p><strong>External Media Service Behavior:</strong></p>
     * <p>If external media controllers are active (e.g., Android Auto connected),
     * this method will skip notification updates entirely, allowing the external
     * service to handle all user-facing media controls and notifications.</p>
     *
     * @param session                   The MediaSession that triggered the update
     * @param startInForegroundRequired Whether the notification should start as foreground
     * @see #shouldShowNotification()
     */
    @SuppressLint("ForegroundServiceType")
    @Override
    public void onUpdateNotification(@NonNull MediaSession session, boolean startInForegroundRequired) {
        if (!shouldShowNotification()) {
            Log.d(TAG, "Skipping notification update - external controller active");
            // If we were showing notifications but now shouldn't, stop foreground service
            if (isForegroundServiceActive) {
                stopForeground(true);
                isForegroundServiceActive = false;
            }
            return;
        }

        Log.d(TAG, "Notification updated");
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(new NotificationChannel(NOTIFICATION_CHANEL_ID, NOTIFICATION_CHANEL_ID, NotificationManager.IMPORTANCE_LOW));
        }

        Notification notification;
        try {
            notification = buildNotification(session);
        } catch (Exception e) {
            Log.w(TAG, "Exception thrown when updating notification");
            return;
        }

        if (startInForegroundRequired && !isForegroundServiceActive) {
            startForeground(player.hashCode(), notification);
            isForegroundServiceActive = true;
        } else {
            manager.notify(player.hashCode(), notification);
        }
    }

    /**
     * Removes any existing notifications for the current player.
     * Used when switching players or cleaning up resources.
     *
     * Note: Does not delete the notification channel if the service is running
     * as a foreground service, as this would cause a SecurityException.
     */
    private void removePreviousNotification() {
        if (player == null) {
            return;
        }

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(player.hashCode());

        // Only delete the notification channel if we're not running as a foreground service
        // Android doesn't allow deleting a channel while a foreground service is using it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !isForegroundServiceActive) {
            try {
                manager.deleteNotificationChannel(NOTIFICATION_CHANEL_ID);
            } catch (SecurityException e) {
                Log.w(TAG, "Could not delete notification channel - service may still be in foreground", e);
            }
        }
    }

    /**
     * Determines whether this service should show media notifications.
     *
     * <p>This method implements the core logic for coordinating with external media services.
     * It prevents notification conflicts and duplicate interfaces by detecting when external
     * media controllers are active.</p>
     *
     * <h3>Detection Logic</h3>
     * <p>Notifications are <strong>hidden</strong> when:</p>
     * <ul>
     *   <li><strong>External Controllers Present:</strong> The MediaSession has connected
     *       controllers (e.g., Android Auto, car systems, TV interfaces)</li>
     *   <li><strong>Car Mode Active:</strong> The device is in car mode
     *       ({@code UI_MODE_TYPE_CAR}), indicating automotive integration</li>
     * </ul>
     *
     * <p>Notifications are <strong>shown</strong> when:</p>
     * <ul>
     *   <li>No external controllers are connected to the MediaSession</li>
     *   <li>The device is not in car mode</li>
     *   <li>The user is interacting with the app directly on their device</li>
     * </ul>
     *
     * <h3>Why This Coordination Is Important</h3>
     * <ul>
     *   <li><strong>Prevents Duplication:</strong> External services often provide comprehensive
     *       media UIs that would conflict with system notifications</li>
     *   <li><strong>Improves UX:</strong> Users see consistent, context-appropriate controls</li>
     *   <li><strong>Resource Efficiency:</strong> Avoids unnecessary notification processing
     *       when external systems are handling the UI</li>
     * </ul>
     *
     * @return {@code true} if notifications should be displayed, {@code false} if external
     * media services should handle the UI
     * @see MediaSession#getConnectedControllers()
     * @see UiModeManager#getCurrentModeType()
     */
    private boolean shouldShowNotification() {
        if (mediaSession != null) {
            List<MediaSession.ControllerInfo> controllers = mediaSession.getConnectedControllers();

            // Only hide notifications for specific external controllers
            for (MediaSession.ControllerInfo controller : controllers) {
                String packageName = controller.getPackageName();

                // Only hide for known external controllers (Android Auto, car systems, etc.)
                if (packageName.contains("android.auto") ||
                        packageName.contains("car.") ||
                        packageName.contains("automotive")) {
                    Log.d(TAG, "External controller detected, hiding notification");
                    return false;
                }
            }
        }

        // Check UI mode
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        int currentMode = uiModeManager.getCurrentModeType();

        return currentMode != Configuration.UI_MODE_TYPE_CAR;
    }

    /**
     * Builds a media-style notification with playback controls.
     *
     * <p>Creates different notification layouts based on Android version:</p>
     * <ul>
     *   <li><strong>Android 13+ (TIRAMISU):</strong> Uses MediaStyle with automatic control layout</li>
     *   <li><strong>Earlier versions:</strong> Manually creates notification actions for
     *       backward/play/forward controls</li>
     * </ul>
     *
     * @param mediaSession The MediaSession to build notification for
     * @return A configured media notification
     * @throws ExecutionException   if bitmap loading fails
     * @throws InterruptedException if bitmap loading is interrupted
     */
    private Notification buildNotification(MediaSession mediaSession) throws ExecutionException, InterruptedException {

        Intent returnToPlayer = new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new NotificationCompat.Builder(this, NOTIFICATION_CHANEL_ID).setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play).setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession)).setContentIntent(PendingIntent.getActivity(this, 0, returnToPlayer, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build();
        } else {

            int playerId = mediaSession.getPlayer().hashCode();

            // Action for Command.SEEK_BACKWARD
            Intent seekBackwardIntent = new Intent(this, VideoPlaybackService.class);
            seekBackwardIntent.putExtra("PLAYER_ID", playerId);
            seekBackwardIntent.putExtra("ACTION", Command.SEEK_BACKWARD.stringValue);
            PendingIntent seekBackwardPendingIntent = PendingIntent.getService(this, playerId * 10, seekBackwardIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            // ACTION FOR Command.TOGGLE_PLAY
            Intent togglePlayIntent = new Intent(this, VideoPlaybackService.class);
            togglePlayIntent.putExtra("PLAYER_ID", playerId);
            togglePlayIntent.putExtra("ACTION", Command.TOGGLE_PLAY.stringValue);
            PendingIntent togglePlayPendingIntent = PendingIntent.getService(this, playerId * 10 + 1, togglePlayIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            // ACTION FOR Command.SEEK_FORWARD
            Intent seekForwardIntent = new Intent(this, VideoPlaybackService.class);
            seekForwardIntent.putExtra("PLAYER_ID", playerId);
            seekForwardIntent.putExtra("ACTION", Command.SEEK_FORWARD.stringValue);
            PendingIntent seekForwardPendingIntent = PendingIntent.getService(this, playerId * 10 + 2, seekForwardIntent, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANEL_ID)
                    // Show controls on lock screen even when user hides sensitive content.
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
                    // Add media control buttons that invoke intents in your media service
                    .addAction(androidx.media3.session.R.drawable.media3_icon_rewind, "Seek Backward", seekBackwardPendingIntent) // #0
                    .addAction(mediaSession.getPlayer().isPlaying() ? androidx.media3.session.R.drawable.media3_icon_pause : androidx.media3.session.R.drawable.media3_icon_play, "Toggle Play", togglePlayPendingIntent) // #1
                    .addAction(androidx.media3.session.R.drawable.media3_icon_fast_forward, "Seek Forward", seekForwardPendingIntent) // #2
                    // Apply the media style template
                    .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2)).setContentTitle(mediaSession.getPlayer().getMediaMetadata().title).setContentText(mediaSession.getPlayer().getMediaMetadata().description).setContentIntent(PendingIntent.getActivity(this, 0, returnToPlayer, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).setLargeIcon(mediaSession.getPlayer().getMediaMetadata().artworkUri != null ? mediaSession.getBitmapLoader().loadBitmap(mediaSession.getPlayer().getMediaMetadata().artworkUri).get() : null).setOngoing(true);
            return builder.build();
        }
    }

    /**
     * Command enumeration for media playback actions that can be triggered from notifications.
     */
    private enum Command {
        NONE("NONE"), SEEK_FORWARD("Command_SEEK_FORWARD"), SEEK_BACKWARD("Command_SEEK_BACKWARD"), TOGGLE_PLAY("Command_TOGGLE_PLAY"), PLAY("Command_PLAY"), PAUSE("Command_PAUSE");

        public final String stringValue;

        Command(String stringValue) {
            this.stringValue = stringValue;
        }
    }

    /**
     * Binder class that provides access to the CentralizedPlaybackNotificationManager instance.
     * Used by external services to set up notification management for a specific player.
     */
    public static class CPNMBinder extends Binder {
        public final CentralizedPlaybackNotificationManager manager;

        public CPNMBinder(CentralizedPlaybackNotificationManager manager) {
            this.manager = manager;
        }
    }
}
