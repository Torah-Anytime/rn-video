import type { WithDefault } from 'react-native/Libraries/Types/CodegenTypes';
import type { OnAudioFocusChangedData, OnAudioTracksData, OnBandwidthUpdateData, OnBufferData, OnControlsVisibilityChange, OnExternalPlaybackChangeData, OnLoadStartData, OnPictureInPictureStatusChangedData, OnPlaybackRateChangeData, OnPlaybackStateChangedData, OnProgressData, OnSeekData, OnTextTrackDataChangedData, OnTimedMetadataData, OnVideoAspectRatioData, OnVideoErrorData, OnVideoTracksData, OnVolumeChangeData } from '../specs/VideoNativeComponent';
export type * from '../specs/VideoNativeComponent';
export type AudioTrack = OnAudioTracksData['audioTracks'][number];
export type TextTrack = OnTextTracksData['textTracks'][number];
export type VideoTrack = OnVideoTracksData['videoTracks'][number];
export type OnLoadData = Readonly<{
    currentTime: number;
    duration: number;
    naturalSize: Readonly<{
        width: number;
        height: number;
        orientation: WithDefault<'landscape' | 'portrait', 'landscape'>;
    }>;
    audioTracks: {
        index: number;
        title?: string;
        language?: string;
        bitrate?: number;
        type?: string;
        selected?: boolean;
    }[];
    textTracks: {
        index: number;
        title?: string;
        language?: string;
        /**
         * iOS only supports VTT, Android supports all 3
         */
        type?: WithDefault<'srt' | 'ttml' | 'vtt', 'srt'>;
        selected?: boolean;
    }[];
    videoTracks: {
        index: number;
        tracksID?: string;
        codecs?: string;
        width?: number;
        height?: number;
        bitrate?: number;
        selected?: boolean;
    }[];
}>;
export type OnTextTracksData = Readonly<{
    textTracks: {
        index: number;
        title?: string;
        language?: string;
        /**
         * iOS only supports VTT, Android supports all 3
         */
        type?: WithDefault<string, 'srt'>;
        selected?: boolean;
    }[];
}>;
export type OnReceiveAdEventData = Readonly<{
    data?: object;
    event: WithDefault<
    /**
     * iOS only: Fired the first time each ad break ends. Applications must reenable seeking when this occurs (only used for dynamic ad insertion).
     */ 'AD_BREAK_ENDED'
    /**
     * Fires when an ad rule or a VMAP ad break would have played if autoPlayAdBreaks is false.
     */
     | 'AD_BREAK_READY'
    /**
     * iOS only: Fired first time each ad break begins playback. If an ad break is watched subsequent times this will not be fired. Applications must disable seeking when this occurs (only used for dynamic ad insertion).
     */
     | 'AD_BREAK_STARTED'
    /**
     * Android only: Fires when the ad has stalled playback to buffer.
     */
     | 'AD_BUFFERING'
    /**
     * Android only: Fires when the ad is ready to play without buffering, either at the beginning of the ad or after buffering completes.
     */
     | 'AD_CAN_PLAY'
    /**
     * Android only: Fires when an ads list is loaded.
     */
     | 'AD_METADATA'
    /**
     * iOS only: Fired every time the stream switches from advertising or slate to content. This will be fired even when an ad is played a second time or when seeking into an ad (only used for dynamic ad insertion).
     */
     | 'AD_PERIOD_ENDED'
    /**
     * iOS only: Fired every time the stream switches from content to advertising or slate. This will be fired even when an ad is played a second time or when seeking into an ad (only used for dynamic ad insertion).
     */
     | 'AD_PERIOD_STARTED'
    /**
     * Android only: Fires when the ad's current time value changes. The event `data` will be populated with an AdProgressData object.
     */
     | 'AD_PROGRESS'
    /**
     * Fires when the ads manager is done playing all the valid ads in the ads response, or when the response doesn't return any valid ads.
     */
     | 'ALL_ADS_COMPLETED'
    /**
     * Fires when the ad is clicked.
     */
     | 'CLICK'
    /**
     * Fires when the ad completes playing.
     */
     | 'COMPLETED'
    /**
     * Android only: Fires when content should be paused. This usually happens right before an ad is about to cover the content.
     */
     | 'CONTENT_PAUSE_REQUESTED'
    /**
     * Android only: Fires when content should be resumed. This usually happens when an ad finishes or collapses.
     */
     | 'CONTENT_RESUME_REQUESTED'
    /**
     * iOS only: Cuepoints changed for VOD stream (only used for dynamic ad insertion).
     */
     | 'CUEPOINTS_CHANGED'
    /**
     * Android only: Fires when the ad's duration changes.
     */
     | 'DURATION_CHANGE'
    /**
     * Fires when an error is encountered and the ad can't be played.
     */
     | 'ERROR'
    /**
     * Fires when the ad playhead crosses first quartile.
     */
     | 'FIRST_QUARTILE'
    /**
     * Android only: Fires when the impression URL has been pinged.
     */
     | 'IMPRESSION'
    /**
     * Android only: Fires when an ad triggers the interaction callback. Ad interactions contain an interaction ID string in the ad data.
     */
     | 'INTERACTION'
    /**
     * Android only: Fires when the displayed ad changes from linear to nonlinear, or the reverse.
     */
     | 'LINEAR_CHANGED'
    /**
     * Fires when ad data is available.
     */
     | 'LOADED'
    /**
     * Fires when a non-fatal error is encountered. The user need not take any action since the SDK will continue with the same or next ad playback depending on the error situation.
     */
     | 'LOG'
    /**
     * Fires when the ad playhead crosses midpoint.
     */
     | 'MIDPOINT'
    /**
     * Fires when the ad is paused.
     */
     | 'PAUSED'
    /**
     * Fires when the ad is resumed.
     */
     | 'RESUMED'
    /**
     * Android only: Fires when the displayed ads skippable state is changed.
     */
     | 'SKIPPABLE_STATE_CHANGED'
    /**
     * Fires when the ad is skipped by the user.
     */
     | 'SKIPPED'
    /**
     * Fires when the ad starts playing.
     */
     | 'STARTED'
    /**
     * iOS only: Stream request has loaded (only used for dynamic ad insertion).
     */
     | 'STREAM_LOADED'
    /**
     * iOS only: Fires when the ad is tapped.
     */
     | 'TAPPED'
    /**
     * Fires when the ad playhead crosses third quartile.
     */
     | 'THIRD_QUARTILE'
    /**
     * iOS only: An unknown event has fired
     */
     | 'UNKNOWN'
    /**
     * Android only: Fires when the ad is closed by the user.
     */
     | 'USER_CLOSE'
    /**
     * Android only: Fires when the non-clickthrough portion of a video ad is clicked.
     */
     | 'VIDEO_CLICKED'
    /**
     * Android only: Fires when a user clicks a video icon.
     */
     | 'VIDEO_ICON_CLICKED'
    /**
     * Android only: Fires when the ad volume has changed.
     */
     | 'VOLUME_CHANGED'
    /**
     * Android only: Fires when the ad volume has been muted.
     */
     | 'VOLUME_MUTED', 'AD_BREAK_ENDED'>;
}>;
/**
 * Playback event callbacks accepted by `<Video>` (all optional).
 * Events marked ⭐ exist only in the Torah-Anytime fork.
 */
export interface ReactVideoEvents {
    /**
     * Android: audio is about to play through the device speaker because the
     * output route disappeared (headphones unplugged, Bluetooth dropped).
     * Conventionally used to pause playback.
     */
    onAudioBecomingNoisy?: () => void;
    /**
     * Android: system audio focus gained/lost (`e.hasAudioFocus`) — e.g.
     * another app started playing. Only fires when the player requests
     * focus (`disableFocus` not set).
     */
    onAudioFocusChanged?: (e: OnAudioFocusChangedData) => void;
    /** Android: the player entered the idle state (no media / stopped). */
    onIdle?: () => void;
    /** Android: measured network bandwidth (requires `reportBandwidth`). */
    onBandwidthUpdate?: (e: OnBandwidthUpdateData) => void;
    /** Buffering started/stopped (`e.isBuffering`) — drive spinners from this. */
    onBuffer?: (e: OnBufferData) => void;
    /** Native controls were shown or hidden (requires `controls`). */
    onControlsVisibilityChange?: (e: OnControlsVisibilityChange) => void;
    /** Playback reached the end of the media (not fired when `repeat` loops). */
    onEnd?: () => void;
    /**
     * Fatal playback error — media failed to load or playback died.
     * `e.error` carries the platform error details. The player will not
     * recover on its own; reload/replace the source.
     */
    onError?: (e: OnVideoErrorData) => void;
    /** iOS: AirPlay/external playback started or stopped. */
    onExternalPlaybackChange?: (e: OnExternalPlaybackChangeData) => void;
    /** Native fullscreen is about to be presented. */
    onFullscreenPlayerWillPresent?: () => void;
    /** Native fullscreen finished presenting. */
    onFullscreenPlayerDidPresent?: () => void;
    /** Native fullscreen is about to be dismissed. */
    onFullscreenPlayerWillDismiss?: () => void;
    /** Native fullscreen finished dismissing — restore inline layout here. */
    onFullscreenPlayerDidDismiss?: () => void;
    /**
     * Media loaded and is playable: `e.duration`, `e.currentTime`,
     * `e.naturalSize` (intrinsic dimensions/orientation) and the available
     * audio/text/video tracks.
     */
    onLoad?: (e: OnLoadData) => void;
    /** The player started loading a (new) source — earliest "loading" signal. */
    onLoadStart?: (e: OnLoadStartData) => void;
    /** Picture-in-Picture started/stopped (`e.isActive`). */
    onPictureInPictureStatusChanged?: (e: OnPictureInPictureStatusChangedData) => void;
    /**
     * The native playback rate changed (`e.playbackRate`). Note: rate `0`
     * means paused/buffering and non-zero means playing — it is NOT only
     * fired for `rate`-prop changes.
     */
    onPlaybackRateChange?: (e: OnPlaybackRateChangeData) => void;
    /** Player volume changed (`e.volume`). */
    onVolumeChange?: (e: OnVolumeChangeData) => void;
    /**
     * Periodic position tick, every `progressUpdateInterval` ms while
     * playing: `e.currentTime`, `e.playableDuration`, `e.seekableDuration`
     * (seconds). This is the backbone of progress bars and resume state.
     */
    onProgress?: (e: OnProgressData) => void;
    /** First video frame is ready to display — good moment to hide posters. */
    onReadyForDisplay?: () => void;
    /** Google IMA ad lifecycle events (see `OnReceiveAdEventData.event`). */
    onReceiveAdEvent?: (e: OnReceiveAdEventData) => void;
    /**
     * iOS: user tapped "return to app" from PiP and AVKit is waiting for the
     * app to restore its UI. You MUST call
     * `videoRef.restoreUserInterfaceForPictureInPictureStopCompleted(true)`
     * when done, or the player stays frozen.
     */
    onRestoreUserInterfaceForPictureInPictureStop?: () => void;
    /** A seek completed: `e.currentTime` (landed) and `e.seekTime` (requested). */
    onSeek?: (e: OnSeekData) => void;
    /**
     * Playing/paused/seeking state changed at the native layer
     * (`e.isPlaying`, `e.isSeeking`) — fires for lock-screen/notification
     * and PiP controls too, so use it to keep app state in sync with
     * playback the user triggered outside the app's UI. CAUTION: also fires
     * `isPlaying: false` during rebuffering stalls — guard before treating
     * it as a user pause.
     */
    onPlaybackStateChanged?: (e: OnPlaybackStateChangedData) => void;
    /** Timed/ID3 metadata encountered in the stream. */
    onTimedMetadata?: (e: OnTimedMetadataData) => void;
    /** Available audio tracks changed/known. */
    onAudioTracks?: (e: OnAudioTracksData) => void;
    /** Available subtitle tracks changed/known. */
    onTextTracks?: (e: OnTextTracksData) => void;
    /** Android: active subtitle text changed (for custom subtitle rendering). */
    onTextTrackDataChanged?: (e: OnTextTrackDataChangedData) => void;
    /** Available video quality tracks changed/known. */
    onVideoTracks?: (e: OnVideoTracksData) => void;
    /** The media's aspect ratio is known/changed (`e.width`, `e.height`). */
    onAspectRatio?: (e: OnVideoAspectRatioData) => void;
    /**
     * ⭐ Torah-Anytime fork, iOS: user pressed "next track" on the lock
     * screen / control center / headphones. The app decides what "next"
     * means (e.g. play the next lecture).
     */
    onNextTrack?: () => void;
    /** ⭐ Torah-Anytime fork, iOS: "previous track" remote command. See `onNextTrack`. */
    onPreviousTrack?: () => void;
}
