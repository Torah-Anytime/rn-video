#import "React/RCTViewManager.h"
#import <React/RCTBridgeModule.h>

@interface RCT_EXTERN_MODULE (RCTVideoManager, RCTViewManager)

RCT_EXPORT_VIEW_PROPERTY(src, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(drm, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(maxBitRate, float);
RCT_EXPORT_VIEW_PROPERTY(resizeMode, NSString);
RCT_EXPORT_VIEW_PROPERTY(repeat, BOOL);
RCT_EXPORT_VIEW_PROPERTY(automaticallyWaitsToMinimizeStalling, BOOL);
RCT_EXPORT_VIEW_PROPERTY(allowsExternalPlayback, BOOL);
RCT_EXPORT_VIEW_PROPERTY(textTracks, NSArray);
RCT_EXPORT_VIEW_PROPERTY(selectedTextTrack, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(selectedAudioTrack, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(chapters, NSArray);
RCT_EXPORT_VIEW_PROPERTY(paused, BOOL);
RCT_EXPORT_VIEW_PROPERTY(muted, BOOL);
RCT_EXPORT_VIEW_PROPERTY(controls, BOOL);
RCT_EXPORT_VIEW_PROPERTY(audioOutput, NSString);
RCT_EXPORT_VIEW_PROPERTY(volume, float);
RCT_EXPORT_VIEW_PROPERTY(playInBackground, BOOL);
RCT_EXPORT_VIEW_PROPERTY(preventsDisplaySleepDuringVideoPlayback, BOOL);
RCT_EXPORT_VIEW_PROPERTY(preferredForwardBufferDuration, float);
RCT_EXPORT_VIEW_PROPERTY(playWhenInactive, BOOL);
RCT_EXPORT_VIEW_PROPERTY(enterPictureInPictureOnLeave, BOOL);
RCT_EXPORT_VIEW_PROPERTY(ignoreSilentSwitch, NSString);
RCT_EXPORT_VIEW_PROPERTY(mixWithOthers, NSString);
RCT_EXPORT_VIEW_PROPERTY(rate, float);
RCT_EXPORT_VIEW_PROPERTY(fullscreen, BOOL);
RCT_EXPORT_VIEW_PROPERTY(fullscreenAutorotate, BOOL);
RCT_EXPORT_VIEW_PROPERTY(fullscreenOrientation, NSString);
RCT_EXPORT_VIEW_PROPERTY(filter, NSString);
RCT_EXPORT_VIEW_PROPERTY(filterEnabled, BOOL);
RCT_EXPORT_VIEW_PROPERTY(progressUpdateInterval, float);
RCT_EXPORT_VIEW_PROPERTY(restoreUserInterfaceForPIPStopCompletionHandler, BOOL);
RCT_EXPORT_VIEW_PROPERTY(localSourceEncryptionKeyScheme, NSString);
RCT_EXPORT_VIEW_PROPERTY(subtitleStyle, NSDictionary);
RCT_EXPORT_VIEW_PROPERTY(showNotificationControls, BOOL);
RCT_EXPORT_VIEW_PROPERTY(disableAudioSessionManagement, BOOL);
/* Should support: onLoadStart, onLoad, and onError to stay consistent with Image */
RCT_EXPORT_VIEW_PROPERTY(onVideoLoadStart, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoLoad, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoBuffer, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoError, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoProgress, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoBandwidthUpdate, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoSeek, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoEnd, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onTimedMetadata, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoAudioBecomingNoisy, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoFullscreenPlayerWillPresent, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoFullscreenPlayerDidPresent, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoFullscreenPlayerWillDismiss, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoFullscreenPlayerDidDismiss, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onReadyForDisplay, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPlaybackStalled, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPlaybackResume, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPlaybackRateChange, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVolumeChange, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoPlaybackStateChanged, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onVideoExternalPlaybackChange, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onGetLicense, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPictureInPictureStatusChanged, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onRestoreUserInterfaceForPictureInPictureStop, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onReceiveAdEvent, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onTextTracks, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onAudioTracks, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onTextTrackDataChanged, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onNextTrack, RCTDirectEventBlock);
RCT_EXPORT_VIEW_PROPERTY(onPreviousTrack, RCTDirectEventBlock);

RCT_EXTERN_METHOD(seekCmd : (nonnull NSNumber*)reactTag time : (nonnull NSNumber*)time tolerance : (nonnull NSNumber*)tolerance)
RCT_EXTERN_METHOD(setLicenseResultCmd : (nonnull NSNumber*)reactTag license : (NSString*)license licenseUrl : (NSString*)licenseUrl)
RCT_EXTERN_METHOD(setLicenseResultErrorCmd : (nonnull NSNumber*)reactTag error : (NSString*)error licenseUrl : (NSString*)licenseUrl)
RCT_EXTERN_METHOD(setPlayerPauseStateCmd : (nonnull NSNumber*)reactTag paused : (nonnull BOOL)paused)
RCT_EXTERN_METHOD(setVolumeCmd : (nonnull NSNumber*)reactTag volume : (nonnull float*)volume)
RCT_EXTERN_METHOD(setFullScreenCmd : (nonnull NSNumber*)reactTag fullscreen : (nonnull BOOL)fullScreen)
RCT_EXTERN_METHOD(enterPictureInPictureCmd : (nonnull NSNumber*)reactTag)
RCT_EXTERN_METHOD(exitPictureInPictureCmd : (nonnull NSNumber*)reactTag)
RCT_EXTERN_METHOD(setSourceCmd : (nonnull NSNumber*)reactTag source : (NSDictionary*)source)

RCT_EXTERN_METHOD(save
                  : (nonnull NSNumber*)reactTag options
                  : (NSDictionary*)options resolve
                  : (RCTPromiseResolveBlock)resolve reject
                  : (RCTPromiseRejectBlock)reject)
RCT_EXTERN_METHOD(getCurrentPosition
                  : (nonnull NSNumber*)reactTag resolve
                  : (RCTPromiseResolveBlock)resolve reject
                  : (RCTPromiseRejectBlock)reject)

@end
