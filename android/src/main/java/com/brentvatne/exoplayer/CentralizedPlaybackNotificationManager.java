package com.brentvatne.exoplayer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

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

import java.util.concurrent.ExecutionException;

public class CentralizedPlaybackNotificationManager extends MediaSessionService {
    @Nullable
    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return null;
    }

    private enum Command {
        NONE("NONE"),
        SEEK_FORWARD("Command_SEEK_FORWARD"),
        SEEK_BACKWARD("Command_SEEK_BACKWARD"),
        TOGGLE_PLAY("Command_TOGGLE_PLAY"),
        PLAY("Command_PLAY"),
        PAUSE("Command_PAUSE");

        Command(String stringValue){
            this.stringValue = stringValue;
        }

        public String stringValue;
    }

    private SessionCommand commandSeekForward = new SessionCommand(Command.SEEK_FORWARD.stringValue, Bundle.EMPTY);
    private SessionCommand commandSeekBackward = new SessionCommand(Command.SEEK_BACKWARD.stringValue, Bundle.EMPTY);

    @SuppressLint("PrivateResource")
    private CommandButton seekForwardBtn = new CommandButton.Builder()
            .setDisplayName("forward")
            .setSessionCommand(commandSeekForward)
            .setIconResId(androidx.media3.ui.R.drawable.exo_notification_fastforward)
            .build();

    @SuppressLint("PrivateResource")
    private CommandButton seekBackwardBtn = new CommandButton.Builder()
            .setDisplayName("backward")
            .setSessionCommand(commandSeekBackward)
            .setIconResId(androidx.media3.ui.R.drawable.exo_notification_rewind)
            .build();

    private String NOTIFICATION_CHANEL_ID = "CPNM_SESSION_NOTIFICATION";

    public CentralizedPlaybackNotificationManager(Player player){
        MediaSession mediaSession = new MediaSession.Builder(this, player)
                .setId("RNVideoPlaybackService_" + player.hashCode())
                .setCallback(new VideoPlaybackCallback())
                .setCustomLayout(ImmutableList.of(seekForwardBtn, seekBackwardBtn))
                .build();


    }

    public void stop(){

    }

    private void buildNotification(MediaSession mediaSession) throws ExecutionException, InterruptedException {
        Intent returnToPlayer = new Intent(this, this.getClass()).addFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANEL_ID)
                    .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
                    .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                    .setContentIntent(PendingIntent.getActivity(this, 0, returnToPlayer, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .build();
        }else{

            int playerId = mediaSession.getPlayer().hashCode();

            // Action for Command.SEEK_BACKWARD
            Intent seekBackwardIntent = new Intent(this, VideoPlaybackService.class);
            seekBackwardIntent.putExtra("PLAYER_ID", playerId);
            seekBackwardIntent.putExtra("ACTION", Command.SEEK_BACKWARD.stringValue);
            PendingIntent seekBackwardPendingIntent = PendingIntent.getService(
                    this,
                    playerId * 10,
                    seekBackwardIntent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            // ACTION FOR Command.TOGGLE_PLAY
            Intent togglePlayIntent = new Intent(this, VideoPlaybackService.class);
            togglePlayIntent.putExtra("PLAYER_ID", playerId);
            togglePlayIntent.putExtra("ACTION", Command.TOGGLE_PLAY.stringValue);
            PendingIntent togglePlayPendingIntent = PendingIntent.getService(
                    this,
                    playerId * 10 + 1,
                    togglePlayIntent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            // ACTION FOR Command.SEEK_FORWARD
            Intent seekForwardIntent = new Intent(this, VideoPlaybackService.class);
            seekForwardIntent.putExtra("PLAYER_ID", playerId);
            seekForwardIntent.putExtra("ACTION", Command.SEEK_FORWARD.stringValue);
            PendingIntent seekForwardPendingIntent = PendingIntent.getService(
                    this,
                    playerId * 10 + 2,
                    seekForwardIntent,
                    PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANEL_ID)
                    // Show controls on lock screen even when user hides sensitive content.
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(androidx.media3.session.R.drawable.media3_icon_circular_play)
                    // Add media control buttons that invoke intents in your media service
                    .addAction(androidx.media3.session.R.drawable.media3_icon_rewind, "Seek Backward", seekBackwardPendingIntent) // #0
                    .addAction(
                            mediaSession.getPlayer().isPlaying() ?
                                    androidx.media3.session.R.drawable.media3_icon_pause :
                                    androidx.media3.session.R.drawable.media3_icon_play,
                            "Toggle Play",
                            togglePlayPendingIntent
                    ) // #1
                    .addAction(androidx.media3.session.R.drawable.media3_icon_fast_forward, "Seek Forward", seekForwardPendingIntent) // #2
                    // Apply the media style template
                    .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession).setShowActionsInCompactView(0, 1, 2))
                    .setContentTitle(mediaSession.getPlayer().getMediaMetadata().title)
                    .setContentText(mediaSession.getPlayer().getMediaMetadata().description)
                    .setContentIntent(PendingIntent.getActivity(this, 0, returnToPlayer, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                    .setLargeIcon(
                mediaSession.getPlayer().getMediaMetadata().artworkUri != null ?
                        mediaSession.getBitmapLoader().loadBitmap(mediaSession.getPlayer().getMediaMetadata().artworkUri).get() :
                        null)
                    .setOngoing(true);
        }
    }
}


