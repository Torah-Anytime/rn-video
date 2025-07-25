/* eslint-disable @typescript-eslint/ban-types */
import type {HostComponent, ViewProps} from 'react-native';
import {requireNativeComponent} from 'react-native';
import type {
  DirectEventHandler,
  Double,
  Float,
  Int32,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';

// -------- There are types for native component (future codegen) --------
// if you are looking for types for react component, see src/types/video.ts

type Headers = ReadonlyArray<
  Readonly<{
    key: string;
    value: string;
  }>
>;

type VideoMetadata = Readonly<{
  title?: string;
  subtitle?: string;
  description?: string;
  imageUri?: string;
}>;

export type AdsConfig = Readonly<{
  adTagUrl?: string;
  adLanguage?: string;
}>;

export type VideoSrc = Readonly<{
  uri?: string;
  isNetwork?: boolean;
  isAsset?: boolean;
  isLocalAssetFile?: boolean;
  shouldCache?: boolean;
  type?: string;
  mainVer?: Int32;
  patchVer?: Int32;
  requestHeaders?: Headers;
  startPosition?: Float;
  cropStart?: Float;
  cropEnd?: Float;
  contentStartTime?: Int32; // Android
  metadata?: VideoMetadata;
  drm?: Drm;
  cmcd?: NativeCmcdConfiguration; // android
  textTracksAllowChunklessPreparation?: boolean; // android
  textTracks?: TextTracks;
  ad?: AdsConfig;
  minLoadRetryCount?: Int32; // Android
  bufferConfig?: BufferConfig; // Android
}>;

type DRMType = WithDefault<string, 'widevine'>;

type DebugConfig = Readonly<{
  enable?: boolean;
  thread?: boolean;
}>;

type Drm = Readonly<{
  type?: DRMType;
  licenseServer?: string;
  headers?: Headers;
  contentId?: string; // ios
  certificateUrl?: string; // ios
  base64Certificate?: boolean; // ios default: false
  useExternalGetLicense?: boolean; // ios
  multiDrm?: WithDefault<boolean, false>; // android
  localSourceEncryptionKeyScheme?: string; // ios
}>;

type CmcdMode = WithDefault<Int32, 1>;
export type NativeCmcdConfiguration = Readonly<{
  mode?: CmcdMode; // default: MODE_QUERY_PARAMETER
  request?: Headers;
  session?: Headers;
  object?: Headers;
  status?: Headers;
}>;

type TextTracks = ReadonlyArray<
  Readonly<{
    title: string;
    language: string;
    type: string;
    uri: string;
  }>
>;

type SelectedTextTrackType = WithDefault<string, 'system'>;

type SelectedAudioTrackType = WithDefault<string, 'system'>;

type SelectedTextTrack = Readonly<{
  type?: SelectedTextTrackType;
  value?: string;
}>;

type SelectedAudioTrack = Readonly<{
  type?: SelectedAudioTrackType;
  value?: string;
}>;

type SelectedVideoTrackType = WithDefault<string, 'auto'>;

type SelectedVideoTrack = Readonly<{
  type?: SelectedVideoTrackType;
  value?: string;
}>;

type BufferConfigLive = Readonly<{
  maxPlaybackSpeed?: Float;
  minPlaybackSpeed?: Float;
  maxOffsetMs?: Int32;
  minOffsetMs?: Int32;
  targetOffsetMs?: Int32;
}>;

type BufferingStrategyType = WithDefault<string, 'Default'>;

type BufferConfig = Readonly<{
  minBufferMs?: Float;
  maxBufferMs?: Float;
  bufferForPlaybackMs?: Float;
  bufferForPlaybackAfterRebufferMs?: Float;
  maxHeapAllocationPercent?: Float;
  backBufferDurationMs?: Float; // Android
  minBackBufferMemoryReservePercent?: Float;
  minBufferMemoryReservePercent?: Float;
  cacheSizeMB?: Float;
  live?: BufferConfigLive;
}>;

type SubtitleStyle = Readonly<{
  fontSize?: Float;
  paddingTop?: WithDefault<Float, 0>;
  paddingBottom?: WithDefault<Float, 0>;
  paddingLeft?: WithDefault<Float, 0>;
  paddingRight?: WithDefault<Float, 0>;
  opacity?: WithDefault<Float, 1>;
  subtitlesFollowVideo?: WithDefault<boolean, true>;
}>;

type OnLoadData = Readonly<{
  currentTime: Float;
  duration: Float;
  naturalSize: Readonly<{
    width: Float;
    height: Float;
    orientation: WithDefault<string, 'landscape'>;
  }>;
  audioTracks: {
    index: Int32;
    title?: string;
    language?: string;
    bitrate?: Float;
    type?: string;
    selected?: boolean;
  }[];
  textTracks: {
    index: Int32;
    title?: string;
    language?: string;
    /**
     * iOS only supports VTT, Android supports all 3
     */
    type?: WithDefault<string, 'srt'>;
    selected?: boolean;
  }[];
}>;

export type OnLoadStartData = Readonly<{
  isNetwork: boolean;
  type: string;
  uri: string;
}>;

export type OnVideoAspectRatioData = Readonly<{
  width: Float;
  height: Float;
}>;

export type OnBufferData = Readonly<{isBuffering: boolean}>;

export type OnProgressData = Readonly<{
  currentTime: Float;
  playableDuration: Float;
  seekableDuration: Float;
}>;

export type OnBandwidthUpdateData = Readonly<{
  bitrate: Int32;
  width?: Float;
  height?: Float;
  trackId?: Int32;
}>;

export type OnSeekData = Readonly<{
  currentTime: Float;
  seekTime: Float;
}>;

export type OnPlaybackStateChangedData = Readonly<{
  isPlaying: boolean;
  isSeeking: boolean;
}>;

export type OnTimedMetadataData = Readonly<{
  metadata: {
    value?: string;
    identifier: string;
  }[];
}>;

export type OnAudioTracksData = Readonly<{
  audioTracks: {
    index: Int32;
    title?: string;
    language?: string;
    bitrate?: Float;
    type?: string;
    selected?: boolean;
  }[];
}>;

type OnTextTracksData = Readonly<{
  textTracks: {
    index: Int32;
    title?: string;
    language?: string;
    /**
     * iOS only supports VTT, Android supports all 3
     */
    type?: WithDefault<string, 'srt'>;
    selected?: boolean;
  }[];
}>;

export type OnTextTrackDataChangedData = Readonly<{
  subtitleTracks: string;
}>;

export type OnVideoTracksData = Readonly<{
  videoTracks: {
    index: Int32;
    tracksId?: string;
    codecs?: string;
    width?: Float;
    height?: Float;
    bitrate?: Float;
    selected?: boolean;
  }[];
}>;

export type OnPlaybackRateChangeData = Readonly<{
  playbackRate: Float;
}>;

export type OnVolumeChangeData = Readonly<{
  volume: Float;
}>;

export type OnExternalPlaybackChangeData = Readonly<{
  isExternalPlaybackActive: boolean;
}>;

export type OnGetLicenseData = Readonly<{
  licenseUrl: string;
  loadedLicenseUrl: string;
  contentId: string;
  spcBase64: string;
}>;

export type OnPictureInPictureStatusChangedData = Readonly<{
  isActive: boolean;
}>;

type OnReceiveAdEventData = Readonly<{
  data?: {};
  event: WithDefault<string, 'AD_BREAK_ENDED'>;
}>;

export type OnVideoErrorData = Readonly<{
  error: Readonly<{
    errorString?: string; // android | web
    errorException?: string; // android
    errorStackTrace?: string; // android
    errorCode?: string; // android
    error?: string; // ios
    code?: Int32; // ios | web
    localizedDescription?: string; // ios
    localizedFailureReason?: string; // ios
    localizedRecoverySuggestion?: string; // ios
    domain?: string; // ios
  }>;
  target?: Int32; // ios
}>;

export type OnAudioFocusChangedData = Readonly<{
  hasAudioFocus: boolean;
}>;

type ControlsStyles = Readonly<{
  hidePosition?: WithDefault<boolean, false>;
  hidePlayPause?: WithDefault<boolean, false>;
  hideForward?: WithDefault<boolean, false>;
  hideRewind?: WithDefault<boolean, false>;
  hideNext?: WithDefault<boolean, false>;
  hidePrevious?: WithDefault<boolean, false>;
  hideFullscreen?: WithDefault<boolean, false>;
  hideSeekBar?: WithDefault<boolean, false>;
  hideDuration?: WithDefault<boolean, false>;
  hideNavigationBarOnFullScreenMode?: WithDefault<boolean, true>;
  hideNotificationBarOnFullScreenMode?: WithDefault<boolean, true>;
  hideSettingButton?: WithDefault<boolean, true>;
  seekIncrementMS?: Int32;
  liveLabel?: string;
}>;

export type OnControlsVisibilityChange = Readonly<{
  isVisible: boolean;
}>;

export interface VideoNativeProps extends ViewProps {
  src?: VideoSrc;
  allowsExternalPlayback?: boolean; // ios, true
  disableFocus?: boolean; // android
  maxBitRate?: Float;
  resizeMode?: WithDefault<string, 'none'>;
  repeat?: boolean;
  automaticallyWaitsToMinimizeStalling?: boolean;
  shutterColor?: Int32;
  audioOutput?: WithDefault<string, 'speaker'>;
  selectedTextTrack?: SelectedTextTrack;
  selectedAudioTrack?: SelectedAudioTrack;
  selectedVideoTrack?: SelectedVideoTrack; // android
  paused?: boolean;
  muted?: boolean;
  controls?: boolean;
  filter?: WithDefault<string, ''>;
  filterEnabled?: boolean;
  volume?: Float; // default 1.0
  playInBackground?: boolean;
  preventsDisplaySleepDuringVideoPlayback?: boolean;
  preferredForwardBufferDuration?: Float; //ios, 0
  playWhenInactive?: boolean; // ios, false
  enterPictureInPictureOnLeave?: boolean; // default false
  ignoreSilentSwitch?: WithDefault<string, 'inherit'>; // ios, 'inherit'
  mixWithOthers?: WithDefault<string, 'inherit'>; // ios, 'inherit'
  rate?: Float;
  fullscreen?: boolean; // ios, false
  fullscreenAutorotate?: boolean;
  fullscreenOrientation?: WithDefault<string, 'all'>;
  progressUpdateInterval?: Float;
  restoreUserInterfaceForPIPStopCompletionHandler?: boolean;
  debug?: DebugConfig;
  showNotificationControls?: WithDefault<boolean, false>; // Android, iOS
  currentPlaybackTime?: Double; // Android
  disableDisconnectError?: boolean; // Android
  focusable?: boolean; // Android
  hideShutterView?: boolean; //	Android
  reportBandwidth?: boolean; //Android
  subtitleStyle?: SubtitleStyle; // android
  viewType?: Int32; // Android
  bufferingStrategy?: BufferingStrategyType; // Android
  controlsStyles?: ControlsStyles; // Android
  disableAudioSessionManagement?: boolean; // iOS
  onControlsVisibilityChange?: DirectEventHandler<OnControlsVisibilityChange>;
  onVideoLoad?: DirectEventHandler<OnLoadData>;
  onVideoLoadStart?: DirectEventHandler<OnLoadStartData>;
  onVideoAspectRatio?: DirectEventHandler<OnVideoAspectRatioData>;
  onVideoBuffer?: DirectEventHandler<OnBufferData>;
  onVideoError?: DirectEventHandler<OnVideoErrorData>;
  onVideoProgress?: DirectEventHandler<OnProgressData>;
  onVideoBandwidthUpdate?: DirectEventHandler<OnBandwidthUpdateData>;
  onVideoSeek?: DirectEventHandler<OnSeekData>;
  onVideoEnd?: DirectEventHandler<{}>; // all
  onVideoAudioBecomingNoisy?: DirectEventHandler<{}>;
  onVideoFullscreenPlayerWillPresent?: DirectEventHandler<{}>; // ios, android
  onVideoFullscreenPlayerDidPresent?: DirectEventHandler<{}>; // ios, android
  onVideoFullscreenPlayerWillDismiss?: DirectEventHandler<{}>; // ios, android
  onVideoFullscreenPlayerDidDismiss?: DirectEventHandler<{}>; // ios, android
  onReadyForDisplay?: DirectEventHandler<{}>;
  onPlaybackRateChange?: DirectEventHandler<OnPlaybackRateChangeData>; // all
  onVolumeChange?: DirectEventHandler<OnVolumeChangeData>; // android, ios
  onVideoExternalPlaybackChange?: DirectEventHandler<OnExternalPlaybackChangeData>;
  onGetLicense?: DirectEventHandler<OnGetLicenseData>;
  onPictureInPictureStatusChanged?: DirectEventHandler<OnPictureInPictureStatusChangedData>;
  onRestoreUserInterfaceForPictureInPictureStop?: DirectEventHandler<{}>;
  onReceiveAdEvent?: DirectEventHandler<OnReceiveAdEventData>;
  onVideoPlaybackStateChanged?: DirectEventHandler<OnPlaybackStateChangedData>; // android only
  onVideoIdle?: DirectEventHandler<{}>; // android only (nowhere in document, so do not use as props. just type declaration)
  onAudioFocusChanged?: DirectEventHandler<OnAudioFocusChangedData>; // android only (nowhere in document, so do not use as props. just type declaration)
  onTimedMetadata?: DirectEventHandler<OnTimedMetadataData>; // ios, android
  onAudioTracks?: DirectEventHandler<OnAudioTracksData>; // android
  onTextTracks?: DirectEventHandler<OnTextTracksData>; // android
  onTextTrackDataChanged?: DirectEventHandler<OnTextTrackDataChangedData>; // iOS
  onVideoTracks?: DirectEventHandler<OnVideoTracksData>; // android
  onNextTrack?: DirectEventHandler<{}> // ios only 
  onPreviousTrack?: DirectEventHandler<{}> // ios only
}

type NativeVideoComponentType = HostComponent<VideoNativeProps>;

export default requireNativeComponent<VideoNativeProps>(
  'RCTVideo',
) as NativeVideoComponentType;
