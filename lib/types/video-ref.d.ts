import type { RefObject } from 'react';
import { ReactVideoSource } from './video';
/** Result of `VideoRef.save()` — location of the written media file. */
export type VideoSaveData = {
    uri: string;
};
/**
 * Imperative API of the `<Video>` component, obtained via `ref`.
 * Use it for actions that don't map to props: seeking, PiP, fullscreen,
 * and swapping the source/queue without remounting the native view.
 * Methods marked ⭐ exist only in the Torah-Anytime fork.
 */
export interface VideoRef {
    /**
     * Jump to `time` (seconds from the start of the media). Fires `onSeek`
     * when the player lands.
     * @param time Target position in seconds.
     * @param tolerance iOS only: allowed inaccuracy in seconds — larger
     *   values seek faster by snapping to a nearby keyframe. Ignored on
     *   Android (always exact).
     */
    seek: (time: number, tolerance?: number) => void;
    /**
     * Resume playback directly on the native player. Does NOT change the
     * `paused` prop — if React state drives `paused`, update that instead
     * or the next render will override this call.
     */
    resume: () => void;
    /** Pause playback directly on the native player. Same caveat as `resume`. */
    pause: () => void;
    /** Enter native fullscreen. Prefer `setFullScreen(true)` in new code. */
    presentFullscreenPlayer: () => void;
    /** Exit native fullscreen. Prefer `setFullScreen(false)` in new code. */
    dismissFullscreenPlayer: () => void;
    /**
     * iOS PiP handshake: after `onRestoreUserInterfaceForPictureInPictureStop`
     * fires (user tapped "return to app" in PiP), call this with `true` once
     * the app UI is restored — AVKit waits for it and the player stays
     * frozen otherwise.
     */
    restoreUserInterfaceForPictureInPictureStopCompleted: (restore: boolean) => void;
    /** iOS: write the currently loaded media to a file; resolves with its uri. */
    save: (options: object) => Promise<VideoSaveData> | void;
    /** Set player volume 0.0–1.0 (independent of device volume). */
    setVolume: (volume: number) => void;
    /**
     * Read the current playback position (seconds) from the native player.
     * Rejects when no media is loaded — callers tracking `onProgress` can
     * fall back to their last known position.
     */
    getCurrentPosition: () => Promise<number>;
    /**
     * Enter/exit native fullscreen. Listen to the `onFullscreenPlayer*`
     * events to keep app state in sync.
     */
    setFullScreen: (fullScreen: boolean) => void;
    /**
     * Replace the media on the existing native player WITHOUT remounting the
     * view (changing the `source` prop re-prepares, and a changed `key`
     * remounts). Useful for gapless swaps and error recovery.
     */
    setSource: (source?: ReactVideoSource) => void;
    /**
     * ⭐ Torah-Anytime fork, iOS: hand the native player an ordered list of
     * upcoming sources so lock-screen/remote next & previous keep working
     * while the app is backgrounded and the JS thread is suspended. Each
     * entry is `source`-shaped (uri + metadata etc.).
     */
    setQueue: (queue?: ReactVideoSource[]) => void;
    /**
     * Start Picture-in-Picture. Status arrives via
     * `onPictureInPictureStatusChanged`; see also the
     * `enterPictureInPictureOnLeave` prop for automatic entry.
     */
    enterPictureInPicture: () => void;
    /** Close Picture-in-Picture and return playback to the inline view. */
    exitPictureInPicture: () => void;
    /** Web only: the underlying `<video>` element. */
    nativeHtmlVideoRef?: RefObject<HTMLVideoElement | null>;
}
