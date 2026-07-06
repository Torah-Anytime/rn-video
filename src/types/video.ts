import type {ISO639_1} from './language';
import type {ReactVideoEvents} from './events';
import type {
  ImageProps,
  StyleProp,
  ViewProps,
  ViewStyle,
  ImageRequireSource,
  ImageURISource,
  ImageStyle,
} from 'react-native';
import type {ReactNode} from 'react';
import type VideoResizeMode from './ResizeMode';
import type FilterType from './FilterType';
import type ViewType from './ViewType';

export type Headers = Record<string, string>;

export type EnumValues<T extends string | number> = T extends string
  ? `${T}` | T
  : T;

export type ReactVideoSourceProperties = {
  /**
   * URL (or `require()` asset via `ReactVideoSource`) of the media to play.
   * Supports http(s), file://, asset and raw resource paths. Changing the
   * uri reloads the media.
   */
  uri?: string;
  /**
   * ⭐ Torah-Anytime fork addition, Android only — route playback through
   * the shared "central" ExoPlayer instance instead of a per-view player,
   * so the same playback session survives view remounts and keeps
   * background audio / notification controls alive. Ignored on iOS (no
   * native implementation).
   */
  useCentralPlayer?: boolean;
  /** Force treating the uri as a network resource (usually auto-detected). */
  isNetwork?: boolean;
  /** Force treating the uri as an app asset (usually auto-detected). */
  isAsset?: boolean;
  /** Treat the uri as a local asset file path. */
  isLocalAssetFile?: boolean;
  /** Android: cache the media (ExoPlayer cache) where supported. */
  shouldCache?: boolean;
  /**
   * Override the media container/format inferred from the uri extension,
   * e.g. `'m3u8'` or `'mpd'` when the URL has no telling extension.
   */
  type?: string;
  /** Together with `patchVer`, cache-busting version for Android asset sources. */
  mainVer?: number;
  /** See `mainVer`. */
  patchVer?: number;
  /** Extra HTTP headers to send when requesting the media (e.g. auth). */
  headers?: Headers;
  /**
   * Start playback at this position in **milliseconds** the first time the
   * media loads (preferred over seeking after `onLoad`).
   */
  startPosition?: number;
  /** Trim: treat this millisecond offset as the start of the media. */
  cropStart?: number;
  /** Trim: treat this millisecond offset as the end of the media. */
  cropEnd?: number;
  /**
   * iOS (dynamic ad insertion): offset in ms where actual content starts,
   * used when the stream embeds pre-roll ads.
   */
  contentStartTime?: number; // Android
  /**
   * Title/artist/artwork shown in the media notification (Android) and
   * Now Playing / lock screen (iOS) when `showNotificationControls` is on.
   */
  metadata?: VideoMetadata;
  /** DRM configuration (Widevine / FairPlay / PlayReady / ClearKey). */
  drm?: Drm;
  /** Android: CMCD (Common Media Client Data) reporting configuration. */
  cmcd?: Cmcd; // android
  /** Android/HLS: allow preparing without chunkless text tracks. */
  textTracksAllowChunklessPreparation?: boolean;
  /** Side-loaded subtitle tracks (SRT/TTML/VTT) to offer alongside the media. */
  textTracks?: TextTracks;
  /** Google IMA ads configuration (ad tag URL, language). */
  ad?: AdConfig;
  /**
   * Number of times the native player retries loading before surfacing
   * `onError`. Useful on flaky mobile connections.
   */
  minLoadRetryCount?: number; // Android
  /** Android: ExoPlayer buffer sizing/tuning. */
  bufferConfig?: BufferConfig;
};

export type ReactVideoSource = Readonly<
  Omit<ReactVideoSourceProperties, 'uri'> & {
    uri?: string | NodeRequire;
  }
>;

export type ReactVideoPosterSource = ImageURISource | ImageRequireSource;

export type ReactVideoPoster = Omit<ImageProps, 'source'> & {
  // prevents giving source in the array
  source?: ReactVideoPosterSource;
};

/**
 * Metadata for the media notification (Android) and Now Playing info /
 * lock-screen controls (iOS). Set via `source.metadata`; only used when
 * `showNotificationControls` is enabled.
 */
export type VideoMetadata = Readonly<{
  /** Main title line (e.g. lecture title). */
  title?: string;
  /** Second line under the title. */
  subtitle?: string;
  /** Longer description (Android notification only). */
  description?: string;
  /** Artist/author line (e.g. speaker name). */
  artist?: string;
  /** URL of the artwork image shown in the notification / lock screen. */
  imageUri?: string;
}>;

export type DebugConfig = Readonly<{
  enable?: boolean;
  thread?: boolean;
}>;

export enum DRMType {
  WIDEVINE = 'widevine',
  PLAYREADY = 'playready',
  CLEARKEY = 'clearkey',
  FAIRPLAY = 'fairplay',
}

export type AdConfig = Readonly<{
  adTagUrl?: string;
  adLanguage?: ISO639_1;
}>;

export type Drm = Readonly<{
  type?: DRMType;
  licenseServer?: string;
  headers?: Headers;
  contentId?: string; // ios
  certificateUrl?: string; // ios
  base64Certificate?: boolean; // ios default: false
  multiDrm?: boolean; // android
  localSourceEncryptionKeyScheme?: string; // ios
  /* eslint-disable @typescript-eslint/no-unused-vars */
  getLicense?: (
    spcBase64: string,
    contentId: string,
    licenseUrl: string,
    loadedLicenseUrl: string,
  ) => string | Promise<string>; // ios
  /* eslint-enable @typescript-eslint/no-unused-vars */
}>;

export enum CmcdMode {
  MODE_REQUEST_HEADER = 0,
  MODE_QUERY_PARAMETER = 1,
}
/**
 * Custom key names MUST carry a hyphenated prefix to ensure that there will not be a
 * namespace collision with future revisions to this specification. Clients SHOULD
 * use a reverse-DNS syntax when defining their own prefix.
 *
 * @see https://cdn.cta.tech/cta/media/media/resources/standards/pdfs/cta-5004-final.pdf CTA-5004 Specification (Page 6, Section 3.1)
 */
export type CmcdData = Record<`${string}-${string}`, string | number>;
export type CmcdConfiguration = Readonly<{
  mode?: CmcdMode; // default: MODE_QUERY_PARAMETER
  request?: CmcdData;
  session?: CmcdData;
  object?: CmcdData;
  status?: CmcdData;
}>;
export type Cmcd = boolean | CmcdConfiguration;

export enum BufferingStrategyType {
  DEFAULT = 'Default',
  DISABLE_BUFFERING = 'DisableBuffering',
  DEPENDING_ON_MEMORY = 'DependingOnMemory',
}

export type BufferConfigLive = {
  maxPlaybackSpeed?: number;
  minPlaybackSpeed?: number;
  maxOffsetMs?: number;
  minOffsetMs?: number;
  targetOffsetMs?: number;
};

export type BufferConfig = {
  minBufferMs?: number;
  maxBufferMs?: number;
  bufferForPlaybackMs?: number;
  bufferForPlaybackAfterRebufferMs?: number;
  backBufferDurationMs?: number; // Android
  maxHeapAllocationPercent?: number;
  minBackBufferMemoryReservePercent?: number;
  minBufferMemoryReservePercent?: number;
  initialBitrate?: number; // Android
  cacheSizeMB?: number;
  live?: BufferConfigLive;
};

export enum SelectedTrackType {
  SYSTEM = 'system',
  DISABLED = 'disabled',
  TITLE = 'title',
  LANGUAGE = 'language',
  INDEX = 'index',
}

export type SelectedTrack = {
  type: SelectedTrackType;
  value?: string | number;
};

export enum SelectedVideoTrackType {
  AUTO = 'auto',
  DISABLED = 'disabled',
  RESOLUTION = 'resolution',
  INDEX = 'index',
}

export type SelectedVideoTrack = {
  type: SelectedVideoTrackType;
  value?: string | number;
};

export type SubtitleStyle = {
  fontSize?: number;
  paddingTop?: number;
  paddingBottom?: number;
  paddingLeft?: number;
  paddingRight?: number;
  opacity?: number;
  subtitlesFollowVideo?: boolean;
};

export enum TextTrackType {
  SUBRIP = 'application/x-subrip',
  TTML = 'application/ttml+xml',
  VTT = 'text/vtt',
}

export type TextTracks = {
  title: string;
  language: ISO639_1;
  type: TextTrackType;
  uri: string;
}[];

export type TextTrackSelectionType =
  | 'system'
  | 'disabled'
  | 'title'
  | 'language'
  | 'index';

export type SelectedTextTrack = Readonly<{
  type: TextTrackSelectionType;
  value?: string | number;
}>;

export type AudioTrackSelectionType =
  | 'system'
  | 'disabled'
  | 'title'
  | 'language'
  | 'index';

export type SelectedAudioTrack = Readonly<{
  type: AudioTrackSelectionType;
  value?: string | number;
}>;

export type Chapters = {
  title: string;
  startTime: number;
  endTime: number;
  uri?: string;
};

export enum FullscreenOrientationType {
  ALL = 'all',
  LANDSCAPE = 'landscape',
  PORTRAIT = 'portrait',
}

export enum IgnoreSilentSwitchType {
  INHERIT = 'inherit',
  IGNORE = 'ignore',
  OBEY = 'obey',
}

export enum MixWithOthersType {
  INHERIT = 'inherit',
  MIX = 'mix',
  DUCK = 'duck',
}

export enum PosterResizeModeType {
  CONTAIN = 'contain',
  CENTER = 'center',
  COVER = 'cover',
  NONE = 'none',
  REPEAT = 'repeat',
  STRETCH = 'stretch',
}

export type AudioOutput = 'speaker' | 'earpiece';

export type ControlsStyles = {
  hideSeekBar?: boolean;
  hideDuration?: boolean;
  hidePosition?: boolean;
  hidePlayPause?: boolean;
  hideForward?: boolean;
  hideRewind?: boolean;
  hideNext?: boolean;
  hidePrevious?: boolean;
  hideFullscreen?: boolean;
  hideNavigationBarOnFullScreenMode?: boolean;
  hideNotificationBarOnFullScreenMode?: boolean;
  hideSettingButton?: boolean;
  seekIncrementMS?: number;
  liveLabel?: string;
};

export interface ReactVideoRenderLoaderProps {
  source?: ReactVideoSource;
  style?: StyleProp<ImageStyle>;
  resizeMode?: EnumValues<VideoResizeMode>;
}

/**
 * Props for the `<Video>` component (Torah-Anytime/rn-video fork of
 * react-native-video v6 — ExoPlayer on Android, AVPlayer on iOS).
 *
 * Also accepts all `ViewProps` and every playback event in
 * {@link ReactVideoEvents}. Imperative control (seek, PiP, fullscreen,
 * source/queue swaps) lives on the `VideoRef` obtained via `ref`.
 *
 * Full upstream docs: https://docs.thewidlarzgroup.com/react-native-video/
 * Props marked ⭐ exist only in the Torah-Anytime fork.
 */
export interface ReactVideoProps extends ReactVideoEvents, ViewProps {
  /**
   * The media to play: `{ uri, headers?, metadata?, startPosition?, ... }`
   * or a `require()` asset. Changing it (deep-compared) reloads the media;
   * to swap media without remounting the native view use
   * `videoRef.setSource()` instead. See `ReactVideoSourceProperties` for
   * every option (including the fork-only `useCentralPlayer`).
   */
  source?: ReactVideoSource;
  /** @deprecated Use source.drm */
  drm?: Drm;
  style?: StyleProp<ViewStyle>;
  /** @deprecated Use source.ad.adTagUrl */
  adTagUrl?: string;
  /** @deprecated Use source.ad.adLanguage */
  adLanguage?: ISO639_1;
  /** Route audio to `'speaker'` (default) or `'earpiece'`. */
  audioOutput?: AudioOutput; // Mobile
  /**
   * iOS: mirrors `AVPlayer.automaticallyWaitsToMinimizeStalling`. Set
   * `false` to start playback immediately even if buffering isn't complete
   * (needed for low-latency live streams). Default `true`.
   */
  automaticallyWaitsToMinimizeStalling?: boolean; // iOS
  /** @deprecated Use source.bufferConfig */
  bufferConfig?: BufferConfig; // Android
  /**
   * Android: how ExoPlayer buffers — `Default`, `DisableBuffering`, or
   * `DependingOnMemory` (backs off allocation under memory pressure).
   */
  bufferingStrategy?: BufferingStrategyType;
  /** iOS: chapter markers exposed to the native player UI. */
  chapters?: Chapters[]; // iOS
  /** @deprecated Use source.contentStartTime */
  contentStartTime?: number; // Android
  /**
   * Show the platform's built-in playback controls (ExoPlayer controller /
   * AVPlayerViewController). Leave off when rendering custom controls —
   * the two fight over touches.
   */
  controls?: boolean;
  /** iOS (live + dynamic ad insertion): current playback time hint in epoch seconds. */
  currentPlaybackTime?: number; // Android
  /**
   * Android: when `true` the player does NOT request audio focus, so other
   * apps' audio keeps playing and this player won't be paused by focus
   * loss. Also disables the wake-lock the focus request implies.
   */
  disableFocus?: boolean;
  /**
   * Android: don't surface an error (and kill playback) when the network
   * drops — keep the player alive and let it retry/rebuffer.
   */
  disableDisconnectError?: boolean; // Android
  /** iOS: CoreImage filter to apply (local, non-HLS media only). */
  filter?: EnumValues<FilterType>; // iOS
  /** iOS: master switch for `filter`. */
  filterEnabled?: boolean; // iOS
  /** Android: whether the view is focusable (TV / d-pad navigation). */
  focusable?: boolean; // Android
  /**
   * Declaratively enter/exit native fullscreen. Prefer driving this (or
   * `videoRef.setFullScreen`) and listening to the
   * `onFullscreenPlayer*` events to keep app state in sync.
   */
  fullscreen?: boolean; // iOS
  /** iOS: allow autorotation while in native fullscreen. Default `true`. */
  fullscreenAutorotate?: boolean; // iOS
  /** iOS: orientation forced in native fullscreen: `all` | `landscape` | `portrait`. */
  fullscreenOrientation?: EnumValues<FullscreenOrientationType>; // iOS
  /**
   * Android: hide the black "shutter" view shown between source changes /
   * before the first frame renders. Useful to avoid a black flash when
   * swapping sources.
   */
  hideShutterView?: boolean; //	Android
  /**
   * iOS: behavior under the hardware mute switch — `'ignore'` keeps
   * playing audio with the switch on (right for speech/lecture content),
   * `'obey'` silences, `'inherit'` (default) uses the AVAudioSession as-is.
   */
  ignoreSilentSwitch?: EnumValues<IgnoreSilentSwitchType>; // iOS
  /** @deprecated Use source.minLoadRetryCount */
  minLoadRetryCount?: number; // Android
  /** Cap adaptive-streaming bitrate, in bits/sec (0 = no cap). */
  maxBitRate?: number;
  /**
   * iOS: how this player's audio coexists with other apps' audio —
   * `'mix'` (play over), `'duck'` (lower others), `'inherit'` (default).
   * Requires an appropriate `disableFocus` setting on Android instead.
   */
  mixWithOthers?: EnumValues<MixWithOthersType>; // iOS
  /** Mute audio without stopping playback. */
  muted?: boolean;
  /**
   * Declarative play/pause — the primary playback switch. `true` pauses,
   * `false` plays. For one-off imperative control (e.g. from outside
   * React) `videoRef.pause()`/`resume()` exist, but they don't update
   * this prop's owning state.
   */
  paused?: boolean;
  /**
   * Automatically enter Picture-in-Picture when the user leaves the app
   * (home swipe / app switch) while playing. iOS 14.2+ / Android 12+.
   */
  enterPictureInPictureOnLeave?: boolean;
  /**
   * Keep playing (audio) when the app is backgrounded. iOS requires the
   * `audio` UIBackgroundMode; video content continues as audio-only until
   * the app returns.
   */
  playInBackground?: boolean;
  /**
   * iOS: keep playing when the app is "inactive" — notification center or
   * control center pulled over the app.
   */
  playWhenInactive?: boolean; // iOS
  /** Image shown until the first video frame; a uri string or full poster config. */
  poster?: string | ReactVideoPoster; // string is deprecated
  /** @deprecated use **resizeMode** key in **poster** props instead */
  posterResizeMode?: EnumValues<PosterResizeModeType>;
  /** iOS: seconds of media to buffer ahead (0 = let AVPlayer decide). */
  preferredForwardBufferDuration?: number; // iOS
  /** Keep the screen awake while video plays. Default `true`. */
  preventsDisplaySleepDuringVideoPlayback?: boolean;
  /**
   * Milliseconds between `onProgress` events. Default 250. Lower it for
   * fine-grained position tracking (clip boundaries, scrubbers); raise it
   * to cut JS-thread traffic.
   */
  progressUpdateInterval?: number;
  /**
   * Playback speed multiplier: 1.0 normal, 0.5–2.0 typical range.
   * Note: the native player reports rate 0 while paused/buffering via
   * `onPlaybackRateChange` — don't mirror that back into this prop.
   */
  rate?: number;
  /** Custom loading UI rendered while the media loads (replaces poster logic). */
  renderLoader?: ReactNode | ((arg0: ReactVideoRenderLoaderProps) => ReactNode);
  /** Loop playback when the media ends (suppresses `onEnd`-driven advance). */
  repeat?: boolean;
  /** Android: enable `onBandwidthUpdate` events with measured bandwidth. */
  reportBandwidth?: boolean; //Android
  /**
   * How the video fills its view: `'none'`, `'contain'` (letterbox),
   * `'cover'` (crop), or `'stretch'` (distort — safe when the view already
   * matches the media's aspect ratio exactly).
   */
  resizeMode?: EnumValues<VideoResizeMode>;
  /**
   * Show system media controls: media-session notification on Android,
   * Now Playing / lock-screen controls on iOS. Title/artist/artwork come
   * from `source.metadata`.
   */
  showNotificationControls?: boolean; // Android, iOS
  /** Select an audio track by system/disabled/title/language/index. */
  selectedAudioTrack?: SelectedTrack;
  /** Select a subtitle track by system/disabled/title/language/index. */
  selectedTextTrack?: SelectedTrack;
  /** Select a video quality/track by auto/disabled/resolution/index. */
  selectedVideoTrack?: SelectedVideoTrack; // android
  /** Android: styling for rendered subtitles. */
  subtitleStyle?: SubtitleStyle; // android
  /** Android: color of the shutter view (default black). See `hideShutterView`. */
  shutterColor?: string; // Android
  /** @deprecated Use source.textTracks */
  textTracks?: TextTracks;
  testID?: string;
  /**
   * Android: which native view hosts the video — `TEXTURE` (TextureView:
   * animatable/transformable, no secure playback) or `SURFACE`
   * (SurfaceView: cheaper, required for DRM) or `SURFACE_SECURE`.
   */
  viewType?: ViewType;
  /** @deprecated Use viewType */
  useTextureView?: boolean; // Android
  /** @deprecated Use viewType*/
  useSecureView?: boolean; // Android
  /** Playback volume 0.0–1.0 (independent of device volume). */
  volume?: number;
  /** @deprecated use **localSourceEncryptionKeyScheme** key in **drm** props instead */
  localSourceEncryptionKeyScheme?: string;
  /** Enable verbose native logging (`{ enable, thread }`). */
  debug?: DebugConfig;
  /**
   * iOS: allow AirPlay/external playback of the video. `false` keeps
   * playback on-device (audio can still route to external outputs).
   */
  allowsExternalPlayback?: boolean; // iOS
  /** Android: fine-tune which buttons/bars the native controls show (`controls` must be on). */
  controlsStyles?: ControlsStyles; // Android
  /**
   * iOS: don't let the library configure/activate the shared
   * AVAudioSession — the app takes full responsibility for it.
   */
  disableAudioSessionManagement?: boolean; // iOS
}
