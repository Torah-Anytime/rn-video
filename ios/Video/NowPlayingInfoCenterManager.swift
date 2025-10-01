import Foundation
import MediaPlayer

class NowPlayingInfoCenterManager {
    static let shared = NowPlayingInfoCenterManager()
    
    // MARK: - Properties
    private weak var currentPlayer: AVPlayer?
    private var players = NSHashTable<AVPlayer>.weakObjects()
    private weak var currentVideoView: RCTVideo?
    
    private var observers: [Int: NSKeyValueObservation] = [:]
    private var playbackObserver: Any?
    
    // Remote command targets
    private var commandTargets: [Any?] = []
    private let remoteCommandCenter = MPRemoteCommandCenter.shared()
    
    // Debouncing properties
    private var lastUpdateTime: CFTimeInterval = 0
    private var lastRegistrationTime: CFTimeInterval = 0
    private var updateTimer: Timer?
    
    var receivingRemoteControlEvents = false {
        didSet {
            if receivingRemoteControlEvents {
                AudioSessionManager.shared.setRemoteControlEventsActive(true)
                UIApplication.shared.beginReceivingRemoteControlEvents()
            } else {
                UIApplication.shared.endReceivingRemoteControlEvents()
                AudioSessionManager.shared.setRemoteControlEventsActive(false)
            }
        }
    }
    
    deinit {
        // Ensure cleanup happens on main thread to prevent timer-related crashes
        if Thread.isMainThread {
            cleanup()
        } else {
            DispatchQueue.main.sync {
                cleanup()
            }
        }
    }
    
    // MARK: - Player Management
    func registerPlayer(player: AVPlayer, videoView: RCTVideo? = nil) {
        let currentTime = CACurrentMediaTime()
        let isQueueTransition = videoView?._isQueueMode == true && currentPlayer == player
        let debounceTime: CFTimeInterval = isQueueTransition ? 0.3 : (videoView?._isQueueMode == true ? 1.0 : 0.3)
        
        // Skip registration if too frequent
        if currentTime - lastRegistrationTime < debounceTime {
            // Update video view reference for metadata updates even if registration is skipped
            if currentVideoView !== videoView {
                currentVideoView = videoView
                debouncedUpdateNowPlayingInfo()
            }
            return
        }
        
        lastRegistrationTime = currentTime
        let videoViewChanged = currentVideoView !== videoView
        currentVideoView = videoView
        
        // Handle same player with metadata changes
        if currentPlayer == player && players.contains(player) {
            if videoViewChanged || isQueueTransition {
                debouncedUpdateNowPlayingInfo()
            }
            return
        }
        
        // Full registration for new players
        enableRemoteControlEvents()
        setupPlayerObserver(player)
        players.add(player)
        
        if currentPlayer == nil || player.rate > 0 {
            setCurrentPlayer(player: player, videoView: videoView)
        }
    }
    
    func removePlayer(player: AVPlayer) {
        guard players.contains(player) else { return }
        
        removePlayerObserver(player)
        players.remove(player)
        
        if currentPlayer == player {
            currentPlayer = nil
            currentVideoView = nil
            debouncedUpdateNowPlayingInfo()
        }
        
        if players.allObjects.isEmpty {
            cleanup()
        }
    }
    
    // MARK: - Cleanup
    public func cleanup() {
        // Clean up in proper order to prevent crashes
        cleanupTimer()
        invalidateCommandTargets()
        cleanupPlayersAndObservers()
        clearNowPlayingInfo()
        receivingRemoteControlEvents = false
        currentVideoView = nil
        currentPlayer = nil
    }
    
    private func cleanupTimer() {
        // Ensure timer cleanup happens synchronously on main thread to prevent race conditions
        if Thread.isMainThread {
            updateTimer?.invalidate()
            updateTimer = nil
        } else {
            DispatchQueue.main.sync { [weak self] in
                self?.updateTimer?.invalidate()
                self?.updateTimer = nil
            }
        }
    }
    
    private func cleanupPlayersAndObservers() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            self.observers.removeAll()
            self.players.removeAllObjects()
            
            if let playbackObserver = self.playbackObserver {
                self.currentPlayer?.removeTimeObserver(playbackObserver)
                self.playbackObserver = nil
            }
        }
    }
    
    // MARK: - Private Player Management
    private func setCurrentPlayer(player: AVPlayer, videoView: RCTVideo? = nil) {
        guard player != currentPlayer || currentVideoView !== videoView else { return }
        
        removePlaybackObserver()
        currentPlayer = player
        currentVideoView = videoView
        
        registerCommandTargets()
        debouncedUpdateNowPlayingInfo()
        setupPlaybackObserver(player)
    }
    
    private func enableRemoteControlEvents() {
        if !receivingRemoteControlEvents {
            receivingRemoteControlEvents = true
        }
    }
    
    private func setupPlayerObserver(_ player: AVPlayer) {
        if let oldObserver = observers[player.hashValue] {
            oldObserver.invalidate()
        }
        observers[player.hashValue] = observePlayerRate(player)
    }
    
    private func removePlayerObserver(_ player: AVPlayer) {
        observers[player.hashValue]?.invalidate()
        observers.removeValue(forKey: player.hashValue)
    }
    
    private func setupPlaybackObserver(_ player: AVPlayer) {
        playbackObserver = player.addPeriodicTimeObserver(
            forInterval: CMTime(value: 1, timescale: 2),
            queue: .global(),
            using: { [weak self] _ in
                self?.debouncedUpdateNowPlayingInfo()
            }
        )
    }
    
    private func removePlaybackObserver() {
        if let observer = playbackObserver {
            currentPlayer?.removeTimeObserver(observer)
            playbackObserver = nil
        }
    }
    
    // MARK: - Command Registration
    private func registerCommandTargets() {
        invalidateCommandTargets()
        
        let commands = [
            (remoteCommandCenter.playCommand, createPlayTarget()),
            (remoteCommandCenter.pauseCommand, createPauseTarget()),
            (remoteCommandCenter.previousTrackCommand, createPreviousTrackTarget()),
            (remoteCommandCenter.nextTrackCommand, createNextTrackTarget()),
            (remoteCommandCenter.changePlaybackPositionCommand, createPlaybackPositionTarget()),
            (remoteCommandCenter.togglePlayPauseCommand, createTogglePlayPauseTarget())
        ]
        
        commandTargets = commands.map { command, target in
            command.addTarget(handler: target)
        }
        
        enableRemoteCommands()
    }
    
    private func enableRemoteCommands() {
        remoteCommandCenter.playCommand.isEnabled = true
        remoteCommandCenter.pauseCommand.isEnabled = true
        remoteCommandCenter.nextTrackCommand.isEnabled = true
        remoteCommandCenter.previousTrackCommand.isEnabled = true
        remoteCommandCenter.changePlaybackPositionCommand.isEnabled = true
        remoteCommandCenter.togglePlayPauseCommand.isEnabled = true
    }
    
    private func invalidateCommandTargets() {
        let commands = [
            remoteCommandCenter.playCommand,
            remoteCommandCenter.pauseCommand,
            remoteCommandCenter.nextTrackCommand,
            remoteCommandCenter.previousTrackCommand,
            remoteCommandCenter.changePlaybackPositionCommand,
            remoteCommandCenter.togglePlayPauseCommand
        ]
        
        for (command, target) in zip(commands, commandTargets) {
            command.removeTarget(target)
        }
        commandTargets.removeAll()
        
        if !receivingRemoteControlEvents {
            commands.forEach { $0.isEnabled = false }
        }
    }
    
    // MARK: - Command Target Factories
    private func createPlayTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] _ in
            guard let player = self?.currentPlayer, player.rate == 0 else {
                return .commandFailed
            }
            player.play()
            self?.updateNowPlayingInfo()
            return .success
        }
    }
    
    private func createPauseTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] _ in
            guard let player = self?.currentPlayer, player.rate != 0 else {
                return .commandFailed
            }
            player.pause()
            self?.updateNowPlayingInfo()
            return .success
        }
    }
    
    private func createPreviousTrackTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] _ in
            guard let self = self else { return .commandFailed }
            
            if let videoView = self.currentVideoView, videoView._isQueueMode {
                DispatchQueue.main.async {
                    videoView.handlePreviousTrack()
                }
            } else {
                NotificationCenter.default.post(name: NSNotification.Name("RCTVideo.previousTrack"), object: nil)
            }
            
            return .success
        }
    }
    
    private func createNextTrackTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] _ in
            guard let self = self else { return .commandFailed }
            
            if let videoView = self.currentVideoView, videoView._isQueueMode {
                DispatchQueue.main.async {
                    videoView.handleNextTrack()
                }
            } else {
                NotificationCenter.default.post(name: NSNotification.Name("RCTVideo.nextTrack"), object: nil)
            }
            
            return .success
        }
    }
    
    private func createPlaybackPositionTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] event in
            guard let self = self,
                  let player = self.currentPlayer,
                  let positionEvent = event as? MPChangePlaybackPositionCommandEvent else {
                return .commandFailed
            }
            
            let time = CMTime(seconds: positionEvent.positionTime, preferredTimescale: CMTimeScale.max)
            player.seek(to: time, completionHandler: { _ in
                self.updateNowPlayingInfo()
            })
            return .success
        }
    }
    
    private func createTogglePlayPauseTarget() -> (MPRemoteCommandEvent) -> MPRemoteCommandHandlerStatus {
        return { [weak self] _ in
            guard let player = self?.currentPlayer else {
                return .commandFailed
            }
            
            if player.rate == 0 {
                player.play()
            } else {
                player.pause()
            }
            
            self?.updateNowPlayingInfo()
            return .success
        }
    }
    
    // MARK: - Now Playing Info Updates
    private func debouncedUpdateNowPlayingInfo() {
        DispatchQueue.main.async { [weak self] in
            guard let self = self else { return }
            
            // Safely invalidate existing timer
            self.updateTimer?.invalidate()
            self.updateTimer = nil
            
            let delay = self.currentVideoView?._isQueueMode == true ? 0.5 : 0.3
            
            // Create timer with additional safety checks
            guard delay > 0 else {
                self.updateNowPlayingInfo()
                return
            }
            
            self.updateTimer = Timer.scheduledTimer(withTimeInterval: delay, repeats: false) { [weak self] timer in
                // Extra safety: ensure timer is still valid when firing
                guard timer.isValid, let strongSelf = self else { return }
                strongSelf.updateNowPlayingInfo()
            }
        }
    }
    
    public func updateNowPlayingInfo() {
        let currentTime = CACurrentMediaTime()
        
        // Throttle updates to max 3 times per second
        guard currentTime - lastUpdateTime >= 0.33 else { return }
        lastUpdateTime = currentTime
        
        guard let player = currentPlayer,
              let currentItem = player.currentItem,
              currentItem.status == .readyToPlay else {
            clearNowPlayingInfo()
            return
        }
        
        let metadata = extractMetadata(from: currentItem)
        let title = extractTitle(from: metadata)
        let artist = extractArtist(from: metadata)
        let artwork = extractArtwork(from: metadata)
        
        let duration = currentItem.duration.seconds
        let currentTimeSeconds = currentItem.currentTime().seconds
        
        let newNowPlayingInfo: [String: Any] = [
            MPMediaItemPropertyTitle: title,
            MPMediaItemPropertyArtist: artist,
            MPMediaItemPropertyArtwork: artwork,
            MPMediaItemPropertyPlaybackDuration: duration.isFinite ? duration : 0,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: currentTimeSeconds.isFinite ? currentTimeSeconds.rounded() : 0,
            MPNowPlayingInfoPropertyPlaybackRate: player.rate,
            MPNowPlayingInfoPropertyIsLiveStream: CMTIME_IS_INDEFINITE(currentItem.asset.duration)
        ]
        
        updateNowPlayingInfoIfNeeded(newInfo: newNowPlayingInfo, title: title, artist: artist)
    }
    
    // MARK: - Metadata Extraction
    private func extractMetadata(from item: AVPlayerItem) -> [AVMetadataItem] {
        let commonItems = processMetadataItems(item.asset.commonMetadata)
        let externalItems = processMetadataItems(item.externalMetadata)
        
        return Array(commonItems.merging(externalItems) { _, new in new }.values)
    }
    
    private func processMetadataItems(_ items: [AVMetadataItem]) -> [String: AVMetadataItem] {
        var result = [String: AVMetadataItem]()
        
        for item in items {
            guard let id = item.identifier?.rawValue,
                  !id.isEmpty,
                  result[id] == nil,
                  !id.contains("iTunSMPB"),
                  !id.contains("iTunNORM") else {
                continue
            }
            result[id] = item
        }
        
        return result
    }
    
    private func extractTitle(from metadata: [AVMetadataItem]) -> String {
        return AVMetadataItem.metadataItems(from: metadata, filteredByIdentifier: .commonIdentifierTitle)
            .first?.stringValue ?? "Unknown Title"
    }
    
    private func extractArtist(from metadata: [AVMetadataItem]) -> String {
        return AVMetadataItem.metadataItems(from: metadata, filteredByIdentifier: .commonIdentifierArtist)
            .first?.stringValue ?? "Unknown Artist"
    }
    
    private func extractArtwork(from metadata: [AVMetadataItem]) -> MPMediaItemArtwork {
        if let imageData = AVMetadataItem.metadataItems(from: metadata, filteredByIdentifier: .commonIdentifierArtwork)
            .first?.dataValue,
           let image = UIImage(data: imageData) {
            return MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        } else {
            let defaultImage = UIImage(systemName: "music.note") ?? UIImage()
            return MPMediaItemArtwork(boundsSize: defaultImage.size) { _ in defaultImage }
        }
    }
    
    private func updateNowPlayingInfoIfNeeded(newInfo: [String: Any], title: String, artist: String) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = newInfo
    }
    
    private func clearNowPlayingInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [:]
    }
    
    // MARK: - Player Observation
    private func findNewCurrentPlayer() {
        if let newPlayer = players.allObjects.first(where: { $0.rate != 0 }) {
            setCurrentPlayer(player: newPlayer)
        }
    }
    
    private func observePlayerRate(_ player: AVPlayer) -> NSKeyValueObservation {
        return player.observe(\.rate) { [weak self] observedPlayer, change in
            guard let self = self, let rate = change.newValue else { return }
            
            if rate != 0 && self.currentPlayer != observedPlayer {
                self.setCurrentPlayer(player: observedPlayer)
                return
            }
            
            if rate == 0 && self.currentPlayer == observedPlayer {
                self.findNewCurrentPlayer()
            }
            
            self.debouncedUpdateNowPlayingInfo()
        }
    }
}
