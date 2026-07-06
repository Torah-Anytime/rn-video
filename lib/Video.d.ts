import React from 'react';
import { VideoRef } from './types';
import type { ReactVideoProps } from './types';
/**
 * React Native video player (Torah-Anytime/rn-video fork of
 * react-native-video v6) — ExoPlayer on Android, AVPlayer on iOS.
 *
 * Declarative control via {@link ReactVideoProps} (`source`, `paused`,
 * `rate`, `muted`, ...); playback callbacks via `ReactVideoEvents`
 * (`onLoad`, `onProgress`, `onError`, ...); imperative control (seek, PiP,
 * fullscreen, source/queue swaps) via the {@link VideoRef} obtained
 * through `ref`.
 *
 * Upstream docs: https://docs.thewidlarzgroup.com/react-native-video/
 */
declare const Video: React.ForwardRefExoticComponent<ReactVideoProps & React.RefAttributes<VideoRef>>;
export default Video;
