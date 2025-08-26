import AVFoundation
import AVKit
import Foundation
import React

#if USE_GOOGLE_IMA
    import GoogleInteractiveMediaAds
#endif

class RCTVideo: UIView, RCTVideoPlayerViewControllerDelegate,
    RCTPlayerObserverHandler
{

    var _player: AVPlayer?
    private var _playerItem: AVPlayerItem?
    private var _source: VideoSource?
    private var _playerLayer: AVPlayerLayer?
    private var _chapters: [Chapter]?
    private var _playerViewController: RCTVideoPlayerViewController?
    private var _videoURL: NSURL?
    private var _eventDispatcher: RCTEventDispatcher?

    private var _videoQueue: [NSDictionary] = []
    private var _currentQueueIndex: Int = 0
    var _isQueueMode: Bool = false

    private var _videoLoadStarted = false
    private var _pendingSeek = false
    private var _pendingSeekTime: Float = 0.0
    private var _lastSeekTime: Float = 0.0
    private var _controls = false
    private var _muted = false
    private var _paused = false
    private var _repeat = false
    private var _isPlaying = false
    private var _playbackStalled = false
    private var _userExplicitlyPaused = false
    private var _isBuffering = false {
        didSet {
            onVideoBuffer?([
                "isBuffering": _isBuffering, "target": reactTag as Any,
            ])
        }
    }

    var _audioOutput: String = "speaker"
    private var _volume: Float = 1.0
    private var _rate: Float = 1.0
    private var _maxBitRate: Float?
    private var _automaticallyWaitsToMinimizeStalling = true
    private var _allowsExternalPlayback = true
    var _playInBackground = false
    private var _preventsDisplaySleepDuringVideoPlayback = true
    private var _preferredForwardBufferDuration: Float = 0.0
    private var _playWhenInactive = false
    var _ignoreSilentSwitch: String = "inherit"
    var _mixWithOthers: String = "inherit"
    var _disableAudioSessionManagement: Bool = false
    var _showNotificationControls = false

    private var _audioSessionInterrupted = false
    private var _wasPlayingBeforeInterruption = false

    private var nowPlayingUpdateTimer: Timer?
    private var isNowPlayingRegistered = false

    private func resetUserPause() {
        if !_isQueueMode {
            _userExplicitlyPaused = false
        }
    }

    private var _resizeMode: String = "cover"
    private var _fullscreen = false
    private var _fullscreenAutorotate = true
    private var _fullscreenOrientation: String = "all"
    private var _fullscreenPlayerPresented = false
    private var _fullscreenUncontrolPlayerPresented = false
    private var _filterName: String!
    private var _filterEnabled = false
    private var _presentingViewController: UIViewController?
    private var _startPosition: Float64 = -1

    private var _selectedTextTrackCriteria: SelectedTrackCriteria = .none()
    private var _selectedAudioTrackCriteria: SelectedTrackCriteria = .none()

    private var _enterPictureInPictureOnLeave = false {
        didSet {
            updatePictureInPictureSettings()
        }
    }
    private var _pip: RCTPictureInPicture?

    private var _lastBitrate = -2.0
    private var _didRequestAds = false
    private var _adPlaying = false

    private let instanceId = UUID().uuidString
    private lazy var _drmManager: DRMManagerSpec? = ReactNativeVideoManager
        .shared.getDRMManager()
    private var _playerObserver: RCTPlayerObserver = .init()

    #if USE_VIDEO_CACHING
        private let _videoCache: RCTVideoCachingHandler = .init()
    #endif

    #if USE_GOOGLE_IMA
        private var _imaAdsManager: RCTIMAAdsManager!
        private var _contentPlayhead: IMAAVPlayerContentPlayhead?
    #endif

    var isSetSourceOngoing = false
    var nextSource: NSDictionary?

    @objc var onVideoLoadStart: RCTDirectEventBlock?
    @objc var onVideoLoad: RCTDirectEventBlock?
    @objc var onVideoBuffer: RCTDirectEventBlock?
    @objc var onVideoError: RCTDirectEventBlock?
    @objc var onVideoProgress: RCTDirectEventBlock?
    @objc var onVideoBandwidthUpdate: RCTDirectEventBlock?
    @objc var onVideoSeek: RCTDirectEventBlock?
    @objc var onVideoEnd: RCTDirectEventBlock?
    @objc var onTimedMetadata: RCTDirectEventBlock?
    @objc var onVideoAudioBecomingNoisy: RCTDirectEventBlock?
    @objc var onVideoFullscreenPlayerWillPresent: RCTDirectEventBlock?
    @objc var onVideoFullscreenPlayerDidPresent: RCTDirectEventBlock?
    @objc var onVideoFullscreenPlayerWillDismiss: RCTDirectEventBlock?
    @objc var onVideoFullscreenPlayerDidDismiss: RCTDirectEventBlock?
    @objc var onReadyForDisplay: RCTDirectEventBlock?
    @objc var onPlaybackStalled: RCTDirectEventBlock?
    @objc var onPlaybackResume: RCTDirectEventBlock?
    @objc var onPlaybackRateChange: RCTDirectEventBlock?
    @objc var onVolumeChange: RCTDirectEventBlock?
    @objc var onVideoPlaybackStateChanged: RCTDirectEventBlock?
    @objc var onVideoExternalPlaybackChange: RCTDirectEventBlock?
    @objc var onPictureInPictureStatusChanged: RCTDirectEventBlock?
    @objc var onRestoreUserInterfaceForPictureInPictureStop:
        RCTDirectEventBlock?
    @objc var onGetLicense: RCTDirectEventBlock?
    @objc var onReceiveAdEvent: RCTDirectEventBlock?
    @objc var onTextTracks: RCTDirectEventBlock?
    @objc var onAudioTracks: RCTDirectEventBlock?
    @objc var onTextTrackDataChanged: RCTDirectEventBlock?
    @objc var onNextTrack: RCTDirectEventBlock?
    @objc var onPreviousTrack: RCTDirectEventBlock?

    init(eventDispatcher: RCTEventDispatcher!) {
        super.init(frame: CGRect(x: 0, y: 0, width: 100, height: 100))

        _eventDispatcher = eventDispatcher

        setupManagers()
        setupNotificationObservers()

        _playerObserver._handlers = self
        #if USE_VIDEO_CACHING
            _videoCache.playerItemPrepareText = playerItemPrepareText
        #endif
    }

    required init?(coder aDecoder: NSCoder) {
        super.init(coder: aDecoder)
        #if USE_GOOGLE_IMA
            _imaAdsManager = RCTIMAAdsManager(
                video: self,
                isPictureInPictureActive: isPictureInPictureActive
            )
        #endif
    }

    deinit {
        cleanup()
    }

    private func setupManagers() {
        ReactNativeVideoManager.shared.registerView(newInstance: self)
        AudioSessionManager.shared.registerView(view: self)

        #if USE_GOOGLE_IMA
            _imaAdsManager = RCTIMAAdsManager(
                video: self,
                isPictureInPictureActive: isPictureInPictureActive
            )
        #endif

        #if os(iOS)
            if _enterPictureInPictureOnLeave {
                initPictureinPicture()
            }
        #endif
    }

    private func setupNotificationObservers() {
        let notificationObservers = [
            (
                UIApplication.willResignActiveNotification,
                #selector(applicationWillResignActive)
            ),
            (
                UIApplication.didBecomeActiveNotification,
                #selector(applicationDidBecomeActive)
            ),
            (
                UIApplication.didEnterBackgroundNotification,
                #selector(applicationDidEnterBackground)
            ),
            (
                UIApplication.willEnterForegroundNotification,
                #selector(applicationWillEnterForeground)
            ),
            (
                UIApplication.protectedDataWillBecomeUnavailableNotification,
                #selector(screenWillLock)
            ),
            (
                UIApplication.protectedDataDidBecomeAvailableNotification,
                #selector(screenDidUnlock)
            ),
            (
                AVAudioSession.routeChangeNotification,
                #selector(audioRouteChanged)
            ),
            (
                AVAudioSession.interruptionNotification,
                #selector(audioSessionInterruption)
            ),
            (
                NSNotification.Name("RCTVideo.nextTrack"),
                #selector(handleNextTrack)
            ),
            (
                NSNotification.Name("RCTVideo.previousTrack"),
                #selector(handlePreviousTrack)
            ),
        ]

        for (name, selector) in notificationObservers {
            NotificationCenter.default.addObserver(
                self,
                selector: selector,
                name: name,
                object: nil
            )
        }

        #if os(iOS)
            NotificationCenter.default.addObserver(
                self,
                selector: #selector(handleRotation),
                name: UIDevice.orientationDidChangeNotification,
                object: nil
            )
        #endif
    }

    private func cleanup() {
        #if USE_GOOGLE_IMA
            _imaAdsManager?.releaseAds()
            _imaAdsManager = nil
        #endif

        AudioSessionManager.shared.unregisterView(view: self)
        NotificationCenter.default.removeObserver(self)
        removePlayerLayer()
        _playerObserver.clearPlayer()

        cleanupNowPlaying()

        #if os(iOS)
            _pip = nil
        #endif

        ReactNativeVideoManager.shared.unregisterView(newInstance: self)
    }

    private func scheduleNowPlayingUpdate() {
        nowPlayingUpdateTimer?.invalidate()
        nowPlayingUpdateTimer = nil

        guard _showNotificationControls else {
            // Only clear if we're turning off notification controls
            if !_showNotificationControls {
                cleanupNowPlaying()
            }
            return
        }

        nowPlayingUpdateTimer = Timer.scheduledTimer(
            withTimeInterval: 0.2,
            repeats: false
        ) { [weak self] _ in
            self?.updateNowPlayingIfNeeded()
        }
    }

    private func updateNowPlayingIfNeeded() {
        guard _showNotificationControls,
            let player = _player,
            player.currentItem?.status == .readyToPlay
        else {
            // Don't cleanup if we're just waiting for player to be ready
            return
        }

        // Always re-register to ensure current player takes precedence
        NowPlayingInfoCenterManager.shared.registerPlayer(
            player: player,
            videoView: self
        )
        isNowPlayingRegistered = true

        // Update metadata immediately after registration
        NowPlayingInfoCenterManager.shared.updateNowPlayingInfo()
    }
    private func cleanupNowPlaying() {
        nowPlayingUpdateTimer?.invalidate()
        nowPlayingUpdateTimer = nil

        // Only remove if we're the currently registered player
        if isNowPlayingRegistered, let player = _player {
            NowPlayingInfoCenterManager.shared.removePlayer(player: player)
            isNowPlayingRegistered = false
        }
    }

    private func updatePictureInPictureSettings() {
        guard !isPictureInPictureActive() else { return }

        if _enterPictureInPictureOnLeave {
            initPictureinPicture()
            if #available(iOS 9.0, tvOS 14.0, *) {
                _playerViewController?.allowsPictureInPicturePlayback = true
            }
        } else {
            _pip?.deinitPipController()
            if #available(iOS 9.0, tvOS 14.0, *) {
                _playerViewController?.allowsPictureInPicturePlayback = false
            }
        }
    }

    func isPictureInPictureActive() -> Bool {
        #if os(iOS)
            return _pip?._pipController?.isPictureInPictureActive == true
        #else
            return false
        #endif
    }

    func initPictureinPicture() {
        #if os(iOS)
            if _pip == nil {
                _pip = RCTPictureInPicture(
                    { [weak self] in self?._onPictureInPictureEnter() },
                    { [weak self] in self?._onPictureInPictureExit() },
                    { [weak self] in
                        self?.onRestoreUserInterfaceForPictureInPictureStop?([:]
                        )
                    }
                )
            }

            if _playerLayer != nil && !_controls && _pip?._pipController == nil
            {
                _pip?.setupPipController(_playerLayer)
            }
        #endif
    }

    @objc func _onPictureInPictureEnter() {
        onPictureInPictureStatusChanged?(["isActive": NSNumber(value: true)])
    }

    @objc func _onPictureInPictureExit() {
        onPictureInPictureStatusChanged?(["isActive": NSNumber(value: false)])

        let appState = UIApplication.shared.applicationState
        if _playInBackground && appState == .background {
            _playerLayer?.player = nil
            _playerViewController?.player = nil
            _player?.play()
        }
    }

    func handlePictureInPictureEnter() {
        onPictureInPictureStatusChanged?(["isActive": NSNumber(value: true)])
    }

    func handlePictureInPictureExit() {
        onPictureInPictureStatusChanged?(["isActive": NSNumber(value: false)])

        let appState = UIApplication.shared.applicationState
        if _playInBackground && appState == .background {
            _playerLayer?.player = nil
            _playerViewController?.player = nil
            _player?.play()
        }
    }

    func handleRestoreUserInterfaceForPictureInPictureStop() {
        onRestoreUserInterfaceForPictureInPictureStop?([:])
    }

    @objc func setQueue(_ queue: NSArray!) {
        guard let queue = queue as? [NSDictionary], !queue.isEmpty else {
            resetQueue()
            return
        }

        _videoQueue = queue
        _isQueueMode = true

        if _player?.currentItem != nil && _player?.rate ?? 0 > 0 {
            _currentQueueIndex = -1
        } else {
            _currentQueueIndex = 0
            if let firstSource = queue.first {
                setSrc(firstSource)
            }
        }
    }

    private func resetQueue() {
        _videoQueue = []
        _currentQueueIndex = 0
        _isQueueMode = false
    }

    @objc func handleNextTrack() {
        if _isQueueMode {
            playNextInQueue()
        } else {
            onNextTrack?(["target": reactTag as Any, "nativeHandled": false])
        }
    }

    @objc func handlePreviousTrack() {
        if _isQueueMode {
            playPreviousInQueue()
        } else {
            onPreviousTrack?([
                "target": reactTag as Any, "nativeHandled": false,
            ])
        }
    }

    private func playNextInQueue() {
        guard _isQueueMode else { return }

        _currentQueueIndex += 1

        if _currentQueueIndex >= 0 && _currentQueueIndex < _videoQueue.count {
            let nextSource = _videoQueue[_currentQueueIndex]

            onNextTrack?([
                "target": reactTag as Any,
                "queueIndex": _currentQueueIndex,
                "lectureId": nextSource["id"] as Any,
                "nativeHandled": true,
            ])

            // Don't clear Now Playing info here - wait until new source is ready
            setSrc(nextSource)

            // Keep the current Now Playing info until new source is ready
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                [weak self] in
                guard let self = self else { return }

                self._player?.play()
                self._player?.rate = self._rate
            }
        } else {
            endQueue()
        }
    }
    private func playPreviousInQueue() {
        guard _isQueueMode else { return }

        if _currentQueueIndex <= 0 {
            restartCurrentTrack()
            return
        }

        _currentQueueIndex -= 1

        if _currentQueueIndex >= 0 && _currentQueueIndex < _videoQueue.count {
            let previousSource = _videoQueue[_currentQueueIndex]

            onPreviousTrack?([
                "target": reactTag as Any,
                "queueIndex": _currentQueueIndex,
                "lectureId": previousSource["id"] as Any,
                "nativeHandled": true,
            ])

            if _showNotificationControls {
                cleanupNowPlaying()
            }

            setSrc(previousSource)

            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                [weak self] in
                guard let self = self else { return }

                self._player?.play()
                self._player?.rate = self._rate

                if self._showNotificationControls, let player = self._player,
                    player.currentItem?.status == .readyToPlay
                {
                    NowPlayingInfoCenterManager.shared.registerPlayer(
                        player: player,
                        videoView: self
                    )
                    self.isNowPlayingRegistered = true
                    NowPlayingInfoCenterManager.shared.updateNowPlayingInfo()
                }
            }
        }
    }

    private func restartCurrentTrack() {
        _player?.seek(to: CMTime.zero)
        _player?.play()
        _player?.rate = _rate
    }

    private func endQueue() {
        _isQueueMode = false
        _currentQueueIndex = 0
        _videoQueue = []
        _player?.pause()
        _player?.rate = 0.0
    }

    private func configureAudioSession() {
        guard !_disableAudioSessionManagement else { return }

        let audioSession = AVAudioSession.sharedInstance()

        if audioSession.category == .playback {
            return
        }

        do {
            try audioSession.setCategory(.playback, mode: .default, options: [])

            if !audioSession.isOtherAudioPlaying {
                try audioSession.setActive(true)
            }
        } catch let error as NSError {
            if error.code == -50 {
                attemptAudioSessionReset(audioSession)
            }
        }
    }

    private func attemptAudioSessionReset(_ audioSession: AVAudioSession) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            do {
                try audioSession.setActive(
                    false,
                    options: .notifyOthersOnDeactivation
                )
                try audioSession.setCategory(.playback)
                try audioSession.setActive(true)
            } catch {
            }
        }
    }

    @objc func applicationWillResignActive(notification _: NSNotification!) {
        let isExternalPlaybackActive = getIsExternalPlaybackActive()
        if _playInBackground || _playWhenInactive || !_isPlaying
            || isExternalPlaybackActive
        {
            return
        }

        pausePlayer()
    }

    @objc func applicationDidBecomeActive(notification _: NSNotification!) {
        let isExternalPlaybackActive = getIsExternalPlaybackActive()

        if _playInBackground || _playWhenInactive || !_isPlaying
            || isExternalPlaybackActive || _audioSessionInterrupted
        {
            return
        }

        resumePlayer()
    }

    @objc func applicationDidEnterBackground(notification _: NSNotification!) {
        handleBackgroundTransition()
    }

    @objc func applicationWillEnterForeground(notification _: NSNotification!) {
        handleForegroundTransition()
    }

    @objc func screenWillLock() {
        if _isQueueMode && _playInBackground && !_paused {
            return
        }

        let isActiveBackgroundPip =
            isPictureInPictureActive()
            && UIApplication.shared.applicationState != .active
        if _playInBackground || !_isPlaying || isActiveBackgroundPip {
            return
        }

        pausePlayer()
    }

    @objc func screenDidUnlock() {
        if _isQueueMode && _playInBackground && !_paused {
            return
        }

        let isActiveBackgroundPip =
            isPictureInPictureActive()
            && UIApplication.shared.applicationState != .active
        if _paused || isActiveBackgroundPip { return }

        resumePlayer()
    }

    private func handleBackgroundTransition() {
        if !_paused && isPictureInPictureActive() {
            resumePlayer()
        }

        let isExternalPlaybackActive = getIsExternalPlaybackActive()

        if _playInBackground && _isQueueMode && !isExternalPlaybackActive
            && !isPictureInPictureActive()
        {
            configureAudioSession()
            _player?.play()
            _player?.rate = _rate
            scheduleNowPlayingUpdate()
            return
        }

        if !_playInBackground || isExternalPlaybackActive
            || isPictureInPictureActive()
        {
            return
        }

        clearPlayerFromViews()
    }

    private func handleForegroundTransition() {
        if _isQueueMode && _playInBackground {
            configureAudioSession()
            if !_paused {
                _player?.play()
                _player?.rate = _rate
            }
            scheduleNowPlayingUpdate()
        }

        applyModifiers()
        restorePlayerToViews()
        scheduleNowPlayingUpdate()
    }

    private func getIsExternalPlaybackActive() -> Bool {
        #if os(visionOS)
            return false
        #else
            return _player?.isExternalPlaybackActive ?? false
        #endif
    }

    private func pausePlayer() {
        _player?.pause()
        _player?.rate = 0.0
    }

    private func resumePlayer() {
        _player?.play()
        _player?.rate = _rate
    }

    private func clearPlayerFromViews() {
        _playerLayer?.player = nil
        _playerViewController?.player = nil
    }

    private func restorePlayerToViews() {
        _playerLayer?.player = _player
        _playerViewController?.player = _player

        if _userExplicitlyPaused {
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }
                self._player?.pause()
                self._player?.rate = 0.0
                self._paused = true
            }
        }
    }

    @objc func audioRouteChanged(notification: NSNotification!) {
        if let userInfo = notification.userInfo,
            let reason = userInfo[AVAudioSessionRouteChangeReasonKey]
                as? AVAudioSession.RouteChangeReason,
            reason == .oldDeviceUnavailable,
            let onVideoAudioBecomingNoisy
        {
            onVideoAudioBecomingNoisy(["target": reactTag as Any])
        }
    }

    @objc func audioSessionInterruption(notification: NSNotification) {
        guard let userInfo = notification.userInfo,
            let typeValue = userInfo[AVAudioSessionInterruptionTypeKey]
                as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: typeValue)
        else {
            return
        }

        handleInterruption(type: type)
    }

    private func handleInterruption(type: AVAudioSession.InterruptionType) {
        switch type {
        case .began:
            handleInterruptionBegan()
        case .ended:
            handleInterruptionEnded()
        @unknown default:
            break
        }
    }

    private func handleInterruptionBegan() {
        _audioSessionInterrupted = true
        _wasPlayingBeforeInterruption = !_paused

        if !_paused {
            pausePlayer()
            _paused = true
            onVideoPlaybackStateChanged?([
                "isPlaying": false, "isSeeking": false,
                "target": reactTag as Any,
            ])
        }
    }

    private func handleInterruptionEnded() {
        _audioSessionInterrupted = false

        if _wasPlayingBeforeInterruption {
            configureAudioSession()
            scheduleNowPlayingUpdate()

            _paused = false
            resumePlayer()
            onVideoPlaybackStateChanged?([
                "isPlaying": true, "isSeeking": false,
                "target": reactTag as Any,
            ])
        }

        _wasPlayingBeforeInterruption = false
    }

    @objc func handleRotation() {
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }

            self.setNeedsLayout()
            self.layoutIfNeeded()

            if let playerViewController = self._playerViewController {
                self.updatePlayerViewControllerFrame(playerViewController)
            }
        }
    }

    private func updatePlayerViewControllerFrame(
        _ playerViewController: RCTVideoPlayerViewController
    ) {
        let bounds = UIScreen.main.bounds
        playerViewController.view.frame = bounds
        playerViewController.view.setNeedsLayout()
        playerViewController.view.layoutIfNeeded()

        playerViewController.contentOverlayView?.frame = bounds
        for subview in playerViewController.contentOverlayView?.subviews ?? [] {
            subview.frame = bounds
        }
    }

    func applyNextSource() {
        if let nextSource = nextSource {
            isSetSourceOngoing = false
            let nextSrc = nextSource
            self.nextSource = nil
            setSrc(nextSrc)
        }
    }

    @objc func setSrc(_ source: NSDictionary!) {
        if isSetSourceOngoing || nextSource != nil {
            handlePendingSource(source)
            return
        }

        isSetSourceOngoing = true
        initializeSource(source)
    }

    private func handlePendingSource(_ source: NSDictionary!) {
        if !_isQueueMode {
            _player?.replaceCurrentItem(with: nil)
        }
        nextSource = source
    }

    private func initializeSource(_ source: NSDictionary!) {
        DispatchQueue.global(qos: .default).async { [weak self] in
            guard let self = self else { return }

            self._source = VideoSource(source)

            guard let sourceUri = self._source?.uri, !sourceUri.isEmpty else {
                self.handleEmptySource()
                return
            }

            if self._showNotificationControls {
                DispatchQueue.main.async {
                    self.cleanupNowPlaying()
                }
            }

            self.resetUserPause()

            if !self._isQueueMode {
                self.removePlayerLayer()
                self._playerObserver.player = nil
                self._drmManager = nil
                self._playerObserver.playerItem = nil
            }

            RCTVideoUtils.delay { [weak self] in
                self?.prepareAndSetupPlayer()
            }

            self._videoLoadStarted = true
            self.applyNextSource()
        }
    }

    private func handleEmptySource() {
        _player?.replaceCurrentItem(with: nil)
        isSetSourceOngoing = false
        applyNextSource()

        cleanupNowPlaying()
    }

    private func prepareAndSetupPlayer() {
        Task { [weak self] in
            guard let self = self else { return }

            do {
                let playerItem = try await self.preparePlayerItem()
                try await self.setupPlayer(playerItem: playerItem)
            } catch {
                self.handlePlayerSetupError(error)
            }
        }
    }

    private func handlePlayerSetupError(_ error: Error) {
        onVideoError?(["error": error.localizedDescription])
        isSetSourceOngoing = false
        applyNextSource()

        cleanupNowPlaying()
    }

    func preparePlayerItem() async throws -> AVPlayerItem {
        guard let source = _source else {
            isSetSourceOngoing = false
            applyNextSource()
            throw NSError(domain: "", code: 0, userInfo: nil)
        }

        dispatchLoadStartEvent()

        if let uri = source.uri, uri.starts(with: "ph://") {
            return try await preparePHAssetPlayerItem(uri: uri, source: source)
        }

        return try await prepareRegularPlayerItem(source: source)
    }

    private func dispatchLoadStartEvent() {
        onVideoLoadStart?([
            "src": [
                "uri": _source?.uri ?? NSNull() as Any,
                "type": _source?.type ?? NSNull(),
                "isNetwork": NSNumber(value: _source?.isNetwork ?? false),
            ],
            "drm": _source?.drm.json ?? NSNull(),
            "target": reactTag as Any,
        ])
    }

    private func preparePHAssetPlayerItem(uri: String, source: VideoSource)
        async throws -> AVPlayerItem
    {
        guard let photoAsset = await RCTVideoUtils.preparePHAsset(uri: uri)
        else {
            throw NSError(domain: "", code: 0, userInfo: nil)
        }

        if let overridePlayerAsset = await ReactNativeVideoManager.shared
            .overridePlayerAsset(source: source, asset: photoAsset)
        {
            if overridePlayerAsset.type == .full {
                return AVPlayerItem(asset: overridePlayerAsset.asset)
            }
            return await playerItemPrepareText(
                source: source,
                asset: overridePlayerAsset.asset,
                assetOptions: nil,
                uri: source.uri ?? ""
            )
        }

        return await playerItemPrepareText(
            source: source,
            asset: photoAsset,
            assetOptions: nil,
            uri: source.uri ?? ""
        )
    }

    private func prepareRegularPlayerItem(source: VideoSource) async throws
        -> AVPlayerItem
    {
        guard let assetResult = RCTVideoUtils.prepareAsset(source: source),
            let asset = assetResult.asset,
            let assetOptions = assetResult.assetOptions
        else {
            isSetSourceOngoing = false
            applyNextSource()
            throw NSError(domain: "", code: 0, userInfo: nil)
        }

        if let startPosition = _source?.startPosition {
            _startPosition = startPosition / 1000
        }

        #if USE_VIDEO_CACHING
            if _videoCache.shouldCache(source: source) {
                return try await _videoCache.playerItemForSourceUsingCache(
                    source: source,
                    assetOptions: assetOptions
                )
            }
        #endif

        if source.drm.json != nil {
            setupDRM(asset: asset, source: source)
        }

        if let overridePlayerAsset = await ReactNativeVideoManager.shared
            .overridePlayerAsset(source: source, asset: asset)
        {
            if overridePlayerAsset.type == .full {
                return AVPlayerItem(asset: overridePlayerAsset.asset)
            }
            return await playerItemPrepareText(
                source: source,
                asset: overridePlayerAsset.asset,
                assetOptions: assetOptions,
                uri: source.uri ?? ""
            )
        }

        return await playerItemPrepareText(
            source: source,
            asset: asset,
            assetOptions: assetOptions,
            uri: source.uri ?? ""
        )
    }

    private func setupDRM(asset: AVAsset, source: VideoSource) {
        if _drmManager == nil {
            _drmManager = ReactNativeVideoManager.shared.getDRMManager()
        }

        _drmManager?.createContentKeyRequest(
            asset: asset as! AVContentKeyRecipient,
            drmParams: source.drm,
            reactTag: reactTag,
            onVideoError: onVideoError,
            onGetLicense: onGetLicense
        )
    }

    func setupPlayer(playerItem: AVPlayerItem) async throws {
        guard isSetSourceOngoing else { return }

        let wasPlayingBeforeTransition = !_paused && _isPlaying
        let isBackgroundMode = UIApplication.shared.applicationState != .active
        let shouldMaintainPlayback =
            _isQueueMode && _playInBackground && wasPlayingBeforeTransition
            && !_userExplicitlyPaused

        if !shouldMaintainPlayback {
            _player?.pause()
        }

        configurePlayerItem(playerItem)

        if _player == nil {
            createNewPlayer(with: playerItem)
        } else {
            updateExistingPlayer(with: playerItem)
        }

        finalizePlayerSetup()

        if shouldMaintainPlayback {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                [weak self] in
                guard let self = self else { return }

                self._player?.play()
                self._player?.rate = self._rate
                self._paused = false

                self.configureAudioSession()
            }
        }
    }

    private func configurePlayerItem(_ playerItem: AVPlayerItem) {
        _playerItem = playerItem
        _playerObserver.playerItem = _playerItem
        setPreferredForwardBufferDuration(_preferredForwardBufferDuration)
        setPlaybackRange(
            playerItem,
            withCropStart: _source?.cropStart,
            withCropEnd: _source?.cropEnd
        )
        setFilter(_filterName)

        if let maxBitRate = _maxBitRate {
            _playerItem?.preferredPeakBitRate = Double(maxBitRate)
        }
    }

    private func createNewPlayer(with playerItem: AVPlayerItem) {
        _player = AVPlayer()
        ReactNativeVideoManager.shared.onInstanceCreated(
            id: instanceId,
            player: _player as Any
        )
        _player!.replaceCurrentItem(with: playerItem)

        addUnwantedResumeObservers()

        if _showNotificationControls {
            DispatchQueue.main.async { [weak self] in
                guard let self = self, let player = self._player else { return }

                // Only update if player item is ready
                if player.currentItem?.status == .readyToPlay {
                    NowPlayingInfoCenterManager.shared.registerPlayer(
                        player: player,
                        videoView: self
                    )
                    self.isNowPlayingRegistered = true
                    NowPlayingInfoCenterManager.shared.updateNowPlayingInfo()
                }
            }
        }
    }

    private func updateExistingPlayer(with playerItem: AVPlayerItem) {
        _player?.replaceCurrentItem(with: playerItem)

        addUnwantedResumeObservers()

        if _showNotificationControls {
            DispatchQueue.main.async { [weak self] in
                guard let self = self, let player = self._player else { return }
                NowPlayingInfoCenterManager.shared.registerPlayer(
                    player: player,
                    videoView: self
                )
                self.isNowPlayingRegistered = true
                NowPlayingInfoCenterManager.shared.updateNowPlayingInfo()
            }
        }

        if _isQueueMode && _playInBackground {
            resumePlayer()
        }
    }

    private func addUnwantedResumeObservers() {
        guard let player = _player else { return }
        player.addObserver(
            self,
            forKeyPath: #keyPath(AVPlayer.rate),
            options: [.new, .old],
            context: &playerContext
        )
    }

    private var playerContext = 0

    override func observeValue(
        forKeyPath keyPath: String?,
        of object: Any?,
        change: [NSKeyValueChangeKey: Any]?,
        context: UnsafeMutableRawPointer?
    ) {

        if context == &playerContext {
            if keyPath == #keyPath(AVPlayer.rate) {
                guard let player = object as? AVPlayer,
                    let change = change,
                    let newRate = change[.newKey] as? Float,
                    let oldRate = change[.oldKey] as? Float
                else { return }

                if newRate > 0 && oldRate == 0 && _userExplicitlyPaused {
                    DispatchQueue.main.async { [weak self] in
                        guard let self = self else { return }
                        self._player?.pause()
                        self._player?.rate = 0.0
                        self._paused = true
                    }
                }
            }
        } else {
            super.observeValue(
                forKeyPath: keyPath,
                of: object,
                change: change,
                context: context
            )
        }
    }

    private func finalizePlayerSetup() {
        _playerObserver.player = _player
        applyModifiers()
        _player?.actionAtItemEnd = .none

        if #available(iOS 10.0, *) {
            setAutomaticallyWaitsToMinimizeStalling(
                _automaticallyWaitsToMinimizeStalling
            )
        }

        #if USE_GOOGLE_IMA
            if _source?.adParams.adTagUrl != nil {
                _contentPlayhead = IMAAVPlayerContentPlayhead(
                    avPlayer: _player!
                )
                _imaAdsManager.setUpAdsLoader()
            }
        #endif

        isSetSourceOngoing = false
        applyNextSource()
    }

    func playerItemPrepareText(
        source: VideoSource,
        asset: AVAsset!,
        assetOptions: NSDictionary?,
        uri: String
    ) async -> AVPlayerItem {
        let playerItem: AVPlayerItem

        if source.textTracks.isEmpty == true || uri.hasSuffix(".m3u8") {
            playerItem = AVPlayerItem(asset: asset)
        } else {
            _allowsExternalPlayback = false
            let mixComposition = await RCTVideoUtils.generateMixComposition(
                asset
            )
            let validTextTracks = await RCTVideoUtils.getValidTextTracks(
                asset: asset,
                assetOptions: assetOptions,
                mixComposition: mixComposition,
                textTracks: source.textTracks
            )

            if validTextTracks.count != source.textTracks.count {
                setSelectedTextTrack(_selectedTextTrackCriteria)
            }

            playerItem = AVPlayerItem(asset: mixComposition)
        }

        return await playerItemPropagateMetadata(playerItem)
    }

    func playerItemPropagateMetadata(_ playerItem: AVPlayerItem!) async
        -> AVPlayerItem
    {
        var mapping: [AVMetadataIdentifier: Any] = [:]

        if let customMetadata = _source?.customMetadata {
            if let title = customMetadata.title {
                mapping[.commonIdentifierTitle] = title
            }
            if let artist = customMetadata.artist {
                mapping[.commonIdentifierArtist] = artist
            }
            if let subtitle = customMetadata.subtitle {
                mapping[.iTunesMetadataTrackSubTitle] = subtitle
            }
            if let description = customMetadata.description {
                mapping[.commonIdentifierDescription] = description
            }
            if let autoplay = customMetadata.autoPlay {
                mapping[.commonIdentifierDescription] = autoplay
            }
            if let imageUri = customMetadata.imageUri,
                let imageData = await RCTVideoUtils.createImageMetadataItem(
                    imageUri: imageUri
                )
            {
                mapping[.commonIdentifierArtwork] = imageData
            }
        }

        if #available(iOS 12.2, *), !mapping.isEmpty {
            playerItem.externalMetadata = RCTVideoUtils.createMetadataItems(
                for: mapping
            )
        }

        #if os(tvOS)
            if let chapters = _chapters {
                playerItem.navigationMarkerGroups =
                    RCTVideoTVUtils.makeNavigationMarkerGroups(chapters)
            }
        #endif

        return playerItem
    }

    func sendProgressUpdate(didEnd: Bool = false) {
        #if !USE_GOOGLE_IMA
            guard onVideoProgress != nil else { return }
        #endif

        guard let video = _player?.currentItem, video.status == .readyToPlay
        else { return }

        let playerDuration = RCTVideoUtils.playerItemDuration(_player)
        guard !CMTIME_IS_INVALID(playerDuration) else { return }

        var currentTime = _player?.currentTime()
        if currentTime != nil && _source?.cropStart != nil {
            currentTime = CMTimeSubtract(
                currentTime!,
                CMTimeMake(value: _source?.cropStart ?? 0, timescale: 1000)
            )
        }

        let currentPlaybackTime = _player?.currentItem?.currentDate()
        let duration = CMTimeGetSeconds(playerDuration)
        var currentTimeSecs = CMTimeGetSeconds(currentTime ?? .zero)

        if currentTimeSecs > duration || didEnd {
            currentTimeSecs = duration
        }

        guard currentTimeSecs >= 0 else { return }

        #if USE_GOOGLE_IMA
            if !_didRequestAds && currentTimeSecs >= 0.0001
                && _source?.adParams.adTagUrl != nil
            {
                _imaAdsManager.requestAds()
                _didRequestAds = true
            }
        #endif

        onVideoProgress?([
            "currentTime": currentTimeSecs,
            "playableDuration": RCTVideoUtils.calculatePlayableDuration(
                _player,
                withSource: _source
            ),
            "atValue": currentTime?.value ?? .zero,
            "currentPlaybackTime": NSNumber(
                value: Double(
                    currentPlaybackTime?.timeIntervalSince1970 ?? 0 * 1000
                )
            ).int64Value,
            "target": reactTag as Any,
            "seekableDuration": RCTVideoUtils.calculateSeekableDuration(
                _player
            ),
        ])
    }

    @objc func setPaused(_ paused: Bool) {
        _userExplicitlyPaused = paused

        if paused {
            handlePause()
        } else {
            handleResume()
        }

        _paused = paused
        AudioSessionManager.shared.playerPropertiesChanged(view: self)
    }

    private func handlePause() {
        if _adPlaying {
            #if USE_GOOGLE_IMA
                _imaAdsManager.getAdsManager()?.pause()
            #endif
        } else {
            pausePlayer()
        }
    }

    private func handleResume() {
        guard !_audioSessionInterrupted else { return }

        if _player?.rate == 0 {
            configureAudioSession()
        }

        if _adPlaying {
            #if USE_GOOGLE_IMA
                _imaAdsManager.getAdsManager()?.resume()
            #endif
        } else {
            if #available(iOS 10.0, *), !_automaticallyWaitsToMinimizeStalling {
                _player?.playImmediately(atRate: _rate)
            } else {
                resumePlayer()
            }
        }
    }

    @objc func setSeek(_ time: NSNumber, _ tolerance: NSNumber) {
        guard let item = _player?.currentItem, let player = _player,
            item.status == .readyToPlay
        else {
            _pendingSeekTime = time.floatValue
            _pendingSeek = true
            return
        }

        _pendingSeek = true

        RCTPlayerOperations.seek(
            player: player,
            playerItem: item,
            paused: _paused,
            seekTime: time.floatValue,
            seekTolerance: tolerance.floatValue
        ) { [weak self] _ in
            guard let self else { return }

            self._playerObserver.addTimeObserverIfNotSet()
            self.setPaused(self._paused)
            self.onVideoSeek?([
                "currentTime": NSNumber(
                    value: Float(CMTimeGetSeconds(item.currentTime()))
                ),
                "seekTime": time,
                "target": self.reactTag as Any,
            ])
        }

        _pendingSeek = false
    }

    @objc func setRate(_ rate: Float) {
        if _rate != 1 {
            _player?.rate = 1
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                self._rate = rate
                self.applyModifiers()
            }
        } else {
            _rate = rate
            applyModifiers()
        }
    }

    @objc func setMuted(_ muted: Bool) {
        _muted = muted
        applyModifiers()
    }

    @objc func setVolume(_ volume: Float) {
        _volume = volume
        applyModifiers()
    }

    @objc func setAudioOutput(_ audioOutput: String) {
        _audioOutput = audioOutput
        AudioSessionManager.shared.playerPropertiesChanged(view: self)
    }

    func applyModifiers() {
        guard let video = _player?.currentItem, video.status == .readyToPlay
        else { return }

        configureMuteAndVolume()
        configureDisplaySleep()
        configureExternalPlayback()

        if let maxBitRate = _maxBitRate {
            setMaxBitRate(maxBitRate)
        }

        applyTrackSelections()
        applyUISettings()

        AudioSessionManager.shared.playerPropertiesChanged(view: self)
    }

    private func configureMuteAndVolume() {
        if _muted {
            if !_controls {
                _player?.volume = 0
            }
            _player?.isMuted = true
        } else {
            _player?.volume = _volume
            _player?.isMuted = false
        }
    }

    private func configureDisplaySleep() {
        if #available(iOS 12.0, tvOS 12.0, *) {
            #if !os(visionOS)
                _player?.preventsDisplaySleepDuringVideoPlayback =
                    _preventsDisplaySleepDuringVideoPlayback
            #endif
        }
    }

    private func configureExternalPlayback() {
        #if !os(visionOS)
            _player?.allowsExternalPlayback = _allowsExternalPlayback
        #endif
    }

    private func applyTrackSelections() {
        setSelectedTextTrack(_selectedTextTrackCriteria)
        setSelectedAudioTrack(_selectedAudioTrackCriteria)
    }

    private func applyUISettings() {
        setResizeMode(_resizeMode)
        setRepeat(_repeat)
        setControls(_controls)
        setPaused(_paused)
        setAllowsExternalPlayback(_allowsExternalPlayback)
    }

    @objc func setResizeMode(_ mode: String) {
        let resizeMode: AVLayerVideoGravity = {
            switch mode {
            case "contain", "none": return .resizeAspect
            case "cover": return .resizeAspectFill
            case "stretch": return .resize
            default: return .resizeAspect
            }
        }()

        if _controls {
            _playerViewController?.videoGravity = resizeMode
        } else {
            _playerLayer?.videoGravity = resizeMode
        }

        _resizeMode = mode
    }

    @objc func setControls(_ controls: Bool) {
        guard
            _controls != controls
                || (_playerLayer == nil && _playerViewController == nil)
        else { return }

        _controls = controls

        if _controls {
            removePlayerLayer()
            usePlayerViewController()
        } else {
            removePlayerViewController()
            usePlayerLayer()
        }
    }

    private func removePlayerViewController() {
        _playerViewController?.view.removeFromSuperview()
        _playerViewController?.removeFromParent()
        _playerViewController = nil
        _playerObserver.playerViewController = nil
    }

    @objc func setShowNotificationControls(_ showNotificationControls: Bool) {
        _showNotificationControls = showNotificationControls
        scheduleNowPlayingUpdate()
    }

    func usePlayerViewController() {
        guard let _player, let _playerItem else { return }

        if _playerViewController == nil {
            _playerViewController = createPlayerViewController(
                player: _player,
                withPlayerItem: _playerItem
            )
        }

        setResizeMode(_resizeMode)

        guard let playerViewController = _playerViewController else { return }

        if _controls {
            addPlayerViewControllerToView(playerViewController)
        }

        _playerObserver.playerViewController = playerViewController
    }

    private func addPlayerViewControllerToView(
        _ playerViewController: RCTVideoPlayerViewController
    ) {
        let viewController = self.reactViewController()
        viewController?.addChild(playerViewController)
        addSubview(playerViewController.view)

        NSLayoutConstraint.activate([
            playerViewController.view.leadingAnchor.constraint(
                equalTo: self.leadingAnchor
            ),
            playerViewController.view.trailingAnchor.constraint(
                equalTo: self.trailingAnchor
            ),
            playerViewController.view.topAnchor.constraint(
                equalTo: self.topAnchor
            ),
            playerViewController.view.bottomAnchor.constraint(
                equalTo: self.bottomAnchor
            ),
        ])
    }

    func createPlayerViewController(
        player: AVPlayer,
        withPlayerItem _: AVPlayerItem
    ) -> RCTVideoPlayerViewController {
        let viewController = RCTVideoPlayerViewController()
        viewController.showsPlaybackControls = _controls
        #if !os(tvOS)
            viewController.updatesNowPlayingInfoCenter = false
        #endif
        viewController.rctDelegate = self
        viewController.preferredOrientation = _fullscreenOrientation
        viewController.view.frame = bounds
        viewController.player = player

        if #available(iOS 16.0, tvOS 16.0, *) {
            if let initialSpeed = viewController.speeds.first(where: {
                $0.rate == _rate
            }) {
                viewController.selectSpeed(initialSpeed)
            }
        }

        if #available(iOS 9.0, tvOS 14.0, *) {
            viewController.allowsPictureInPicturePlayback =
                _enterPictureInPictureOnLeave
        }

        return viewController
    }

    func usePlayerLayer() {
        guard let _player else { return }

        _playerLayer = AVPlayerLayer(player: _player)
        _playerLayer?.frame = bounds
        _playerLayer?.needsDisplayOnBoundsChange = true

        setResizeMode(_resizeMode)
        _playerObserver.playerLayer = _playerLayer

        if let playerLayer = _playerLayer {
            layer.addSublayer(playerLayer)
        }

        layer.needsDisplayOnBoundsChange = true

        #if os(iOS)
            if _enterPictureInPictureOnLeave {
                _pip?.setupPipController(_playerLayer)
            }
        #endif
    }

    func removePlayerLayer() {
        _playerLayer?.removeFromSuperlayer()
        _playerLayer = nil
        _playerObserver.playerLayer = nil
    }

    @objc func setFullscreen(_ fullscreen: Bool) {
        let alreadyFullscreenPresented =
            _presentingViewController?.presentedViewController != nil

        if fullscreen && !_fullscreenPlayerPresented && _player != nil
            && !alreadyFullscreenPresented
        {
            presentFullscreen()
        } else if !fullscreen && _fullscreenPlayerPresented,
            let playerViewController = _playerViewController
        {
            dismissFullscreen(playerViewController)
        }
    }

    private func presentFullscreen() {
        if _playerViewController == nil {
            usePlayerViewController()
        }

        _playerViewController?.modalPresentationStyle = .fullScreen

        guard let viewController = findPresentingViewController(),
            let playerViewController = _playerViewController
        else { return }

        _presentingViewController = viewController
        onVideoFullscreenPlayerWillPresent?(["target": reactTag as Any])

        if _controls {
            _playerViewController?.removeFromParent()
        }

        viewController.present(playerViewController, animated: true) {
            [weak self] in
            self?.configureFullscreenPresentation()
        }
    }

    private func configureFullscreenPresentation() {
        _playerViewController?.showsPlaybackControls = true
        _fullscreenPlayerPresented = true
        _playerViewController?.autorotate = _fullscreenAutorotate

        DispatchQueue.main.async { [weak self] in
            self?.updateFullscreenLayout()
            self?.onVideoFullscreenPlayerDidPresent?([
                "target": self?.reactTag as Any
            ])
        }
    }

    private func updateFullscreenLayout() {
        let bounds = UIScreen.main.bounds
        _playerViewController?.view.frame = bounds
        _playerViewController?.view.setNeedsLayout()
        _playerViewController?.view.layoutIfNeeded()

        _playerViewController?.contentOverlayView?.frame = bounds
        for subview in _playerViewController?.contentOverlayView?.subviews ?? []
        {
            subview.frame = bounds
        }
    }

    private func dismissFullscreen(
        _ playerViewController: RCTVideoPlayerViewController
    ) {
        videoPlayerViewControllerWillDismiss(
            playerViewController: playerViewController
        )
        _presentingViewController?.dismiss(animated: true) { [weak self] in
            self?.videoPlayerViewControllerDidDismiss(
                playerViewController: playerViewController
            )
        }
        setControls(_controls)

        DispatchQueue.main.async { [weak self] in
            self?.setNeedsLayout()
            self?.layoutIfNeeded()
        }
    }

    private func findPresentingViewController() -> UIViewController? {
        var viewController = firstAvailableUIViewController()

        if viewController == nil,
            let keyWindow = RCTVideoUtils.getCurrentWindow()
        {
            viewController = keyWindow.rootViewController
            if !viewController!.children.isEmpty {
                viewController = viewController!.children.last
            }
        }

        return viewController
    }

    func setSelectedTextTrack(_ selectedTextTrack: SelectedTrackCriteria?) {
        _selectedTextTrackCriteria =
            selectedTextTrack ?? SelectedTrackCriteria.none()
        guard let source = _source else { return }

        if !source.textTracks.isEmpty {
            RCTPlayerOperations.setSideloadedText(
                player: _player,
                textTracks: source.textTracks,
                criteria: _selectedTextTrackCriteria
            )
        } else {
            Task { [weak self] in
                guard let self, let player = _player else { return }

                await RCTPlayerOperations
                    .setMediaSelectionTrackForCharacteristic(
                        player: player,
                        characteristic: .legible,
                        criteria: self._selectedTextTrackCriteria
                    )
            }
        }
    }

    func setSelectedAudioTrack(_ selectedAudioTrack: SelectedTrackCriteria?) {
        _selectedAudioTrackCriteria =
            selectedAudioTrack ?? SelectedTrackCriteria.none()
        Task {
            await RCTPlayerOperations.setMediaSelectionTrackForCharacteristic(
                player: _player,
                characteristic: AVMediaCharacteristic.audible,
                criteria: _selectedAudioTrackCriteria
            )
        }
    }

    @objc func setSelectedTextTrack(_ selectedTextTrack: NSDictionary?) {
        setSelectedTextTrack(SelectedTrackCriteria(selectedTextTrack))
    }

    @objc func setSelectedAudioTrack(_ selectedAudioTrack: NSDictionary?) {
        setSelectedAudioTrack(SelectedTrackCriteria(selectedAudioTrack))
    }

    @objc func setChapters(_ chapters: [NSDictionary]?) {
        setChapters(chapters?.map { Chapter($0) })
    }

    func setChapters(_ chapters: [Chapter]?) {
        _chapters = chapters
    }

    @objc func setPlayInBackground(_ playInBackground: Bool) {
        _playInBackground = playInBackground
    }

    @objc func setPreventsDisplaySleepDuringVideoPlayback(
        _ preventsDisplaySleepDuringVideoPlayback: Bool
    ) {
        _preventsDisplaySleepDuringVideoPlayback =
            preventsDisplaySleepDuringVideoPlayback
        applyModifiers()
    }

    @objc func setAllowsExternalPlayback(_ allowsExternalPlayback: Bool) {
        _allowsExternalPlayback = allowsExternalPlayback
        #if !os(visionOS)
            _player?.allowsExternalPlayback = _allowsExternalPlayback
        #endif
    }

    @objc func setPlayWhenInactive(_ playWhenInactive: Bool) {
        _playWhenInactive = playWhenInactive
    }

    @objc func setEnterPictureInPictureOnLeave(
        _ enterPictureInPictureOnLeave: Bool
    ) {
        #if os(iOS)
            if _enterPictureInPictureOnLeave != enterPictureInPictureOnLeave {
                _enterPictureInPictureOnLeave = enterPictureInPictureOnLeave
                AudioSessionManager.shared.playerPropertiesChanged(view: self)
            }
        #endif
    }

    @objc func setRestoreUserInterfaceForPIPStopCompletionHandler(
        _ restore: Bool
    ) {
        #if os(iOS)
            if _pip != nil {
                _pip?.setRestoreUserInterfaceForPIPStopCompletionHandler(
                    restore
                )
            } else {
                _playerObserver
                    .setRestoreUserInterfaceForPIPStopCompletionHandler(restore)
            }
        #endif
    }

    @objc func setIgnoreSilentSwitch(_ ignoreSilentSwitch: String?) {
        _ignoreSilentSwitch = ignoreSilentSwitch ?? "inherit"
        AudioSessionManager.shared.playerPropertiesChanged(view: self)
    }

    @objc func setMixWithOthers(_ mixWithOthers: String?) {
        _mixWithOthers = mixWithOthers ?? "inherit"
        AudioSessionManager.shared.playerPropertiesChanged(view: self)
    }

    @objc func setMaxBitRate(_ maxBitRate: Float) {
        _maxBitRate = maxBitRate
        _playerItem?.preferredPeakBitRate = Double(maxBitRate)
    }

    @objc func setPreferredForwardBufferDuration(
        _ preferredForwardBufferDuration: Float
    ) {
        _preferredForwardBufferDuration = preferredForwardBufferDuration
        if #available(iOS 10.0, *) {
            _playerItem?.preferredForwardBufferDuration = TimeInterval(
                preferredForwardBufferDuration
            )
        }
    }

    @objc func setAutomaticallyWaitsToMinimizeStalling(_ waits: Bool) {
        _automaticallyWaitsToMinimizeStalling = waits
        if #available(iOS 10.0, *) {
            _player?.automaticallyWaitsToMinimizeStalling = waits
        }
    }

    @objc func setRepeat(_ repeat: Bool) {
        _repeat = `repeat`
    }

    @objc func setDisableAudioSessionManagement(
        _ disableAudioSessionManagement: Bool
    ) {
        _disableAudioSessionManagement = disableAudioSessionManagement
    }

    @objc func setProgressUpdateInterval(_ progressUpdateInterval: Float) {
        _playerObserver.replaceTimeObserverIfSet(
            Float64(progressUpdateInterval)
        )
    }

    @objc func setSubtitleStyle(_ style: [String: Any]) {
        let subtitleStyle = SubtitleStyle.parse(from: style)
        _playerObserver.subtitleStyle = subtitleStyle
    }

    @objc func setFilter(_ filterName: String!) {
        _filterName = filterName

        guard _filterEnabled,
            let uri = _source?.uri, !uri.contains("m3u8"),
            let asset = _playerItem?.asset,
            let filter = CIFilter(name: filterName)
        else { return }

        Task {
            let composition = await RCTVideoUtils.generateVideoComposition(
                asset: asset,
                filter: filter
            )
            self._playerItem?.videoComposition = composition
        }
    }

    @objc func setFilterEnabled(_ filterEnabled: Bool) {
        _filterEnabled = filterEnabled
    }

    @objc func setFullscreenAutorotate(_ autorotate: Bool) {
        _fullscreenAutorotate = autorotate
        if _fullscreenPlayerPresented {
            _playerViewController?.autorotate = autorotate
        }
    }

    @objc func setFullscreenOrientation(_ orientation: String?) {
        _fullscreenOrientation = orientation ?? "all"
        if _fullscreenPlayerPresented {
            _playerViewController?.preferredOrientation = _fullscreenOrientation
        }
    }

    @objc func isMuted() -> Bool {
        return _muted
    }

    func setPlaybackRange(
        _ item: AVPlayerItem!,
        withCropStart cropStart: Int64?,
        withCropEnd cropEnd: Int64?
    ) {
        if let cropStart = cropStart {
            let start = CMTimeMake(value: cropStart, timescale: 1000)
            item.reversePlaybackEndTime = start
            _pendingSeekTime = Float(CMTimeGetSeconds(start))
            _pendingSeek = true
        }
        if let cropEnd = cropEnd {
            item.forwardPlaybackEndTime = CMTimeMake(
                value: cropEnd,
                timescale: 1000
            )
        }
    }

    @objc func enterPictureInPicture() {
        if _pip?._pipController == nil {
            initPictureinPicture()
            if #available(iOS 9.0, tvOS 14.0, *) {
                _playerViewController?.allowsPictureInPicturePlayback = true
            }
        }
        _pip?.enterPictureInPicture()
    }

    @objc func exitPictureInPicture() {
        guard isPictureInPictureActive() else { return }

        _pip?.exitPictureInPicture()
        updatePictureInPictureSettings()
    }

    @objc func getCurrentPlaybackTime(
        _ resolve: @escaping RCTPromiseResolveBlock,
        _ reject: @escaping RCTPromiseRejectBlock
    ) {
        if let player = _playerItem {
            let currentTime = RCTVideoUtils.getCurrentTime(playerItem: player)
            resolve(currentTime)
        } else {
            reject("PLAYER_NOT_AVAILABLE", "Player is not initialized.", nil)
        }
    }

    @objc func save(
        _ options: NSDictionary!,
        _ resolve: @escaping RCTPromiseResolveBlock,
        _ reject: @escaping RCTPromiseRejectBlock
    ) {
        RCTVideoSave.save(
            options: options,
            resolve: resolve,
            reject: reject,
            playerItem: _playerItem
        )
    }

    func setLicenseResult(_ license: String, _ licenseUrl: String) {
        _drmManager?.setJSLicenseResult(
            license: license,
            licenseUrl: licenseUrl
        )
    }

    func setLicenseResultError(_ error: String, _ licenseUrl: String) {
        _drmManager?.setJSLicenseError(error: error, licenseUrl: licenseUrl)
    }

    @objc func setOnClick(_: Any) {}

    func handleBluetoothDisconnect() {
        AudioSessionManager.shared.updateAudioSessionConfiguration()
    }

    override func layoutSubviews() {
        super.layoutSubviews()

        if _controls, let playerViewController = _playerViewController {
            updatePlayerViewControllerLayout(playerViewController)
        } else {
            updatePlayerLayerLayout()
        }
    }

    private func updatePlayerViewControllerLayout(
        _ playerViewController: RCTVideoPlayerViewController
    ) {
        playerViewController.view.frame = bounds
        playerViewController.view.setNeedsLayout()
        playerViewController.view.layoutIfNeeded()

        playerViewController.contentOverlayView?.frame = bounds

        for subview in playerViewController.contentOverlayView?.subviews ?? [] {
            subview.frame = bounds
        }

        if _fullscreenPlayerPresented {
            playerViewController.preferredContentSize = CGSize(
                width: UIScreen.main.bounds.width,
                height: UIScreen.main.bounds.height
            )
        }
    }

    private func updatePlayerLayerLayout() {
        CATransaction.begin()
        CATransaction.setAnimationDuration(0)
        _playerLayer?.frame = bounds
        CATransaction.commit()
    }

    override func removeFromSuperview() {
        cleanupPlayer()
        cleanupReferences()
        super.removeFromSuperview()
    }

    private func cleanupPlayer() {
        _player?.replaceCurrentItem(with: nil)

        if let player = _player {
            player.pause()
            player.removeObserver(
                self,
                forKeyPath: #keyPath(AVPlayer.rate),
                context: &playerContext
            )
        }

        cleanupNowPlaying()

        ReactNativeVideoManager.shared.onInstanceRemoved(
            id: instanceId,
            player: _player as Any
        )
        _player = nil
    }

    private func cleanupReferences() {
        AudioSessionManager.shared.unregisterView(view: self)
        _playerItem = nil
        _source = nil
        _chapters = nil
        _selectedTextTrackCriteria = SelectedTrackCriteria.none()
        _selectedAudioTrackCriteria = SelectedTrackCriteria.none()
        _presentingViewController = nil
        _drmManager = nil
        _playerObserver.clearPlayer()

        removePlayerLayer()
        removePlayerViewController()

        _eventDispatcher = nil
        NotificationCenter.default.removeObserver(self)
    }

    func insertReactSubview(view: UIView!, atIndex: Int) {
        if _controls {
            view.frame = bounds
            _playerViewController?.contentOverlayView?.insertSubview(
                view,
                at: atIndex
            )
        } else {
            RCTLogError("video cannot have any subviews")
        }
    }

    func removeReactSubview(subview: UIView!) {
        if _controls {
            subview.removeFromSuperview()
        } else {
            RCTLog("video cannot have any subviews")
        }
    }

    func videoPlayerViewControllerWillDismiss(
        playerViewController: AVPlayerViewController
    ) {
        if _playerViewController == playerViewController
            && _fullscreenPlayerPresented
        {
            _playerObserver.removePlayerViewControllerObservers()
            onVideoFullscreenPlayerWillDismiss?(["target": reactTag as Any])
        }
    }

    func videoPlayerViewControllerDidDismiss(
        playerViewController: AVPlayerViewController
    ) {
        if _playerViewController == playerViewController
            && _fullscreenPlayerPresented
        {
            _fullscreenPlayerPresented = false
            _presentingViewController = nil
            _playerViewController = nil
            _playerObserver.playerViewController = nil
            applyModifiers()
            onVideoFullscreenPlayerDidDismiss?(["target": reactTag as Any])
        }
    }

    func getAdLanguage() -> String? {
        return _source?.adParams.adLanguage
    }

    func getAdTagUrl() -> String? {
        return _source?.adParams.adTagUrl
    }

    #if USE_GOOGLE_IMA
        func getContentPlayhead() -> IMAAVPlayerContentPlayhead? {
            return _contentPlayhead
        }
    #endif

    func setAdPlaying(_ adPlaying: Bool) {
        _adPlaying = adPlaying
    }

    func extractJsonWithIndex(from tracks: [TextTrack]) -> [NSDictionary]? {
        guard !tracks.isEmpty else { return nil }

        return tracks.enumerated().compactMap { index, track -> NSDictionary? in
            guard let json = track.json?.mutableCopy() as? NSMutableDictionary
            else { return nil }
            json["index"] = index
            return json
        }
    }

    func handleReadyToPlay() {
        guard let playerItem = _playerItem, let source = _source else { return }

        Task {
            await processReadyToPlay(playerItem: playerItem, source: source)
        }
    }

    private func processReadyToPlay(
        playerItem: AVPlayerItem,
        source: VideoSource
    ) async {
        if _pendingSeek {
            setSeek(NSNumber(value: _pendingSeekTime), NSNumber(value: 100))
            _pendingSeek = false
        }

        if _startPosition >= 0 {
            setSeek(NSNumber(value: _startPosition), NSNumber(value: 100))
            _startPosition = -1
        }

        if onVideoLoad != nil, _videoLoadStarted {
            await dispatchVideoLoadEvent(playerItem: playerItem, source: source)
        }

        _videoLoadStarted = false
        _playerObserver.attachPlayerEventListeners()
        applyModifiers()

        // Update Now Playing info when player item is ready
        if _showNotificationControls, let player = _player {
            DispatchQueue.main.async { [weak self] in
                guard let self = self else { return }

                // Re-register to ensure this player and its metadata take precedence
                NowPlayingInfoCenterManager.shared.registerPlayer(
                    player: player,
                    videoView: self
                )
                self.isNowPlayingRegistered = true

                // Force immediate metadata update with current video info
                NowPlayingInfoCenterManager.shared.updateNowPlayingInfo()
            }
        }
    }

    private func dispatchVideoLoadEvent(
        playerItem: AVPlayerItem,
        source: VideoSource
    ) async {
        var duration = Float(CMTimeGetSeconds(playerItem.asset.duration))

        if duration.isNaN || duration == 0 {
            duration =
                RCTVideoUtils.calculateSeekableDuration(_player).floatValue
            if duration.isNaN {
                duration = 0
            }
        }

        let (width, height, orientation) = await getVideoProperties(
            playerItem: playerItem
        )
        let audioTracks = await RCTVideoUtils.getAudioTrackInfo(_player)
        let textTracks = await RCTVideoUtils.getTextTrackInfo(_player)

        onVideoLoad?([
            "duration": NSNumber(value: duration),
            "currentTime": NSNumber(
                value: Float(CMTimeGetSeconds(playerItem.currentTime()))
            ),
            "canPlayReverse": NSNumber(value: playerItem.canPlayReverse),
            "canPlayFastForward": NSNumber(
                value: playerItem.canPlayFastForward
            ),
            "canPlaySlowForward": NSNumber(
                value: playerItem.canPlaySlowForward
            ),
            "canPlaySlowReverse": NSNumber(
                value: playerItem.canPlaySlowReverse
            ),
            "canStepBackward": NSNumber(value: playerItem.canStepBackward),
            "canStepForward": NSNumber(value: playerItem.canStepForward),
            "naturalSize": [
                "width": width,
                "height": height,
                "orientation": orientation,
            ],
            "audioTracks": audioTracks,
            "textTracks": extractJsonWithIndex(from: source.textTracks)
                ?? textTracks.map(\.json),
            "target": reactTag as Any,
        ])
    }

    private func getVideoProperties(playerItem: AVPlayerItem) async -> (
        Float, Float, String
    ) {
        var width: Float = 0
        var height: Float = 0

        let tracks = await RCTVideoAssetsUtils.getTracks(
            asset: playerItem.asset,
            withMediaType: .video
        )
        let presentationSize = playerItem.presentationSize

        if presentationSize.height != 0.0 {
            width = Float(presentationSize.width)
            height = Float(presentationSize.height)
        } else if let videoTrack = tracks?.first {
            let naturalSize = videoTrack.naturalSize
            width = Float(naturalSize.width)
            height = Float(naturalSize.height)
        }

        let orientation =
            width > height
            ? "landscape" : width == height ? "square" : "portrait"
        return (width, height, orientation)
    }

    func handlePlaybackFailed() {
        cleanupNowPlaying()

        guard let playerItem = _playerItem, let error = playerItem.error else {
            return
        }

        onVideoError?([
            "error": [
                "code": NSNumber(value: (error as NSError).code),
                "localizedDescription": error.localizedDescription,
                "localizedFailureReason": (error as NSError)
                    .localizedFailureReason ?? "",
                "localizedRecoverySuggestion": (error as NSError)
                    .localizedRecoverySuggestion ?? "",
                "domain": (error as NSError).domain,
            ],
            "target": reactTag as Any,
        ])
    }

    private func handleRepeat(notification: NSNotification!) {
        guard let item = notification.object as? AVPlayerItem else { return }

        let seekTime =
            _source?.cropStart != nil
            ? CMTime(value: _source!.cropStart!, timescale: 1000) : CMTime.zero

        item.seek(
            to: seekTime,
            toleranceBefore: CMTime.zero,
            toleranceAfter: CMTime.zero
        ) { [weak self] _ in
            self?.applyModifiers()
        }
    }

    func handleTimeUpdate(time _: CMTime) {
        sendProgressUpdate()
    }

    func handleReadyForDisplay(
        changeObject _: Any,
        change _: NSKeyValueObservedChange<Bool>
    ) {
        if _isBuffering {
            _isBuffering = false
        }
        onReadyForDisplay?(["target": reactTag as Any])
    }

    func handleTimeMetadataChange(timedMetadata: [AVMetadataItem]) {
        guard onTimedMetadata != nil else { return }

        let metadata = timedMetadata.compactMap { item -> [String: String?]? in
            guard let value = item.value as? String else { return nil }
            let identifier = item.identifier?.rawValue
            return ["value": value, "identifier": identifier]
        }

        onTimedMetadata?(["target": reactTag as Any, "metadata": metadata])
    }

    func handlePlayerItemStatusChange(
        playerItem _: AVPlayerItem,
        change _: NSKeyValueObservedChange<AVPlayerItem.Status>
    ) {
        guard let playerItem = _playerItem else { return }

        switch playerItem.status {
        case .readyToPlay:
            handleReadyToPlay()
        case .failed:
            handlePlaybackFailed()
        default:
            break
        }
    }

    func handlePlaybackBufferKeyEmpty(
        playerItem _: AVPlayerItem,
        change: NSKeyValueObservedChange<Bool>
    ) {
        if !_isBuffering && change.newValue == true {
            _isBuffering = true
        }
    }

    func handlePlaybackLikelyToKeepUp(
        playerItem _: AVPlayerItem,
        change _: NSKeyValueObservedChange<Bool>
    ) {
        if _isBuffering {
            _isBuffering = false
        }
    }

    func handleTimeControlStatusChange(
        player: AVPlayer,
        change: NSKeyValueObservedChange<AVPlayer.TimeControlStatus>
    ) {
        guard player.timeControlStatus != change.oldValue,
            [.paused, .playing].contains(player.timeControlStatus)
        else { return }

        let isPlaying = player.timeControlStatus == .playing

        guard _isPlaying != isPlaying else { return }

        _isPlaying = isPlaying

        if _controls {
            let wasPreviouslyPlaying = change.oldValue == .playing
            let isNowPaused = player.timeControlStatus == .paused

            if wasPreviouslyPlaying && isNowPaused && !_audioSessionInterrupted
            {
                _userExplicitlyPaused = true
            } else if !isNowPaused {
                _userExplicitlyPaused = false
            }

            _paused = !isPlaying
        }

        onVideoPlaybackStateChanged?([
            "isPlaying": isPlaying,
            "isSeeking": _pendingSeek,
            "target": reactTag as Any,
        ])
    }

    func handlePlaybackRateChange(
        player: AVPlayer,
        change: NSKeyValueObservedChange<Float>
    ) {
        guard player.rate != change.oldValue else { return }

        onPlaybackRateChange?([
            "playbackRate": NSNumber(value: player.rate),
            "target": reactTag as Any,
        ])

        if _playbackStalled && player.rate > 0 {
            onPlaybackResume?([
                "playbackRate": NSNumber(value: player.rate),
                "target": reactTag as Any,
            ])
            _playbackStalled = false
        }
    }

    func handleVolumeChange(
        player: AVPlayer,
        change: NSKeyValueObservedChange<Float>
    ) {
        guard onVolumeChange != nil, player.volume != change.oldValue else {
            return
        }

        onVolumeChange?([
            "volume": NSNumber(value: player.volume),
            "target": reactTag as Any,
        ])
    }

    func handleExternalPlaybackActiveChange(
        player: AVPlayer,
        change _: NSKeyValueObservedChange<Bool>
    ) {
        #if !os(visionOS)
            if !_playInBackground
                && UIApplication.shared.applicationState == .background
            {
                clearPlayerFromViews()
            }

            guard onVideoExternalPlaybackChange != nil else { return }

            onVideoExternalPlaybackChange?([
                "isExternalPlaybackActive": NSNumber(
                    value: player.isExternalPlaybackActive
                ),
                "target": reactTag as Any,
            ])
        #endif
    }

    func handleViewControllerOverlayViewFrameChange(
        overlayView _: UIView,
        change: NSKeyValueObservedChange<CGRect>
    ) {
        guard let oldRect = change.oldValue,
            let newRect = change.newValue,
            !oldRect.equalTo(newRect),
            let bounds = RCTVideoUtils.getCurrentWindow()?.bounds
        else { return }

        if newRect.equalTo(bounds) {
            if !_fullscreenUncontrolPlayerPresented {
                _fullscreenUncontrolPlayerPresented = true
                onVideoFullscreenPlayerWillPresent?(["target": reactTag as Any])
                onVideoFullscreenPlayerDidPresent?(["target": reactTag as Any])
            }
        } else {
            if _fullscreenUncontrolPlayerPresented {
                _fullscreenUncontrolPlayerPresented = false
                onVideoFullscreenPlayerWillDismiss?(["target": reactTag as Any])
                onVideoFullscreenPlayerDidDismiss?(["target": reactTag as Any])
            }
        }

        if let reactVC = reactViewController() {
            reactVC.view.frame = bounds
            reactVC.view.setNeedsLayout()
        }
    }

    func handleTracksChange(
        playerItem _: AVPlayerItem,
        change _: NSKeyValueObservedChange<[AVPlayerItemTrack]>
    ) {
        guard let source = _source else { return }

        if onTextTracks != nil {
            Task {
                let textTracks = await RCTVideoUtils.getTextTrackInfo(_player)
                self.onTextTracks?([
                    "textTracks": extractJsonWithIndex(from: source.textTracks)
                        ?? textTracks.compactMap(\.json)
                ])
            }
        }

        if onAudioTracks != nil {
            Task {
                let audioTracks = await RCTVideoUtils.getAudioTrackInfo(_player)
                self.onAudioTracks?(["audioTracks": audioTracks])
            }
        }
    }

    func handleLegibleOutput(strings: [NSAttributedString]) {
        guard onTextTrackDataChanged != nil, let subtitles = strings.first
        else { return }

        onTextTrackDataChanged?(["subtitleTracks": subtitles.string])
    }

    @objc func handleDidFailToFinishPlaying(notification: NSNotification!) {
        guard onVideoError != nil,
            let error = notification.userInfo?[
                AVPlayerItemFailedToPlayToEndTimeErrorKey
            ] as? NSError
        else { return }

        onVideoError?([
            "error": [
                "code": NSNumber(value: error.code),
                "localizedDescription": error.localizedDescription,
                "localizedFailureReason": error.localizedFailureReason ?? "",
                "localizedRecoverySuggestion": error.localizedRecoverySuggestion
                    ?? "",
                "domain": error.domain,
            ],
            "target": reactTag as Any,
        ])
    }

    @objc func handlePlaybackStalled(notification _: NSNotification!) {
        onPlaybackStalled?(["target": reactTag as Any])
        _playbackStalled = true
    }

    @objc func handlePlayerItemDidReachEnd(notification: NSNotification!) {
        sendProgressUpdate(didEnd: true)
        onVideoEnd?(["target": reactTag as Any])

        #if USE_GOOGLE_IMA
            if notification.object as? AVPlayerItem == _player?.currentItem {
                _imaAdsManager.getAdsLoader()?.contentComplete()
            }
        #endif

        if _repeat {
            handleRepeat(notification: notification)
            return
        }
        if _source?.customMetadata?.autoPlay ?? false {
            playNextInQueue()
            return
        }

        pausePlayer()
    }

    @objc func handleAVPlayerAccess(notification: NSNotification!) {
        guard onVideoBandwidthUpdate != nil,
            let accessLog = (notification.object as? AVPlayerItem)?.accessLog(),
            let lastEvent = accessLog.events.last
        else { return }

        if lastEvent.indicatedBitrate != _lastBitrate {
            _lastBitrate = lastEvent.indicatedBitrate
            onVideoBandwidthUpdate?([
                "bitrate": _lastBitrate, "target": reactTag as Any,
            ])
        }
    }

}
