import AVFoundation
import AVKit
import Foundation
import MediaAccessibility
import React

#if os(iOS)
    class RCTPictureInPicture: NSObject, AVPictureInPictureControllerDelegate {
        public private(set) var _pipController: AVPictureInPictureController?
        private var _onPictureInPictureEnter: (() -> Void)?
        private var _onPictureInPictureExit: (() -> Void)?
        private var _onRestoreUserInterfaceForPictureInPictureStop: (() -> Void)?
        private var _restoreUserInterfaceForPIPStopCompletionHandler: ((Bool) -> Void)?
        private var _isPictureInPictureActive: Bool {
            return _pipController?.isPictureInPictureActive ?? false
        }
        private var _isProgrammaticExit: Bool = false  // Track programmatic exits

        init(
            _ onPictureInPictureEnter: (() -> Void)? = nil,
            _ onPictureInPictureExit: (() -> Void)? = nil,
            _ onRestoreUserInterfaceForPictureInPictureStop: (() -> Void)? = nil
        ) {
            _onPictureInPictureEnter = onPictureInPictureEnter
            _onPictureInPictureExit = onPictureInPictureExit
            _onRestoreUserInterfaceForPictureInPictureStop = onRestoreUserInterfaceForPictureInPictureStop
        }

        func pictureInPictureControllerDidStartPictureInPicture(_: AVPictureInPictureController) {
            guard let _onPictureInPictureEnter else { return }
            _onPictureInPictureEnter()
        }

        func pictureInPictureControllerDidStopPictureInPicture(_: AVPictureInPictureController) {
            guard let _onPictureInPictureExit else { return }
            
            // Pass whether this was a programmatic exit
            _onPictureInPictureExit()
            
            // Reset the flag
            _isProgrammaticExit = false
        }

        func pictureInPictureController(
            _: AVPictureInPictureController,
            restoreUserInterfaceForPictureInPictureStopWithCompletionHandler completionHandler: @escaping (Bool) -> Void
        ) {
            guard let _onRestoreUserInterfaceForPictureInPictureStop else { return }

            _onRestoreUserInterfaceForPictureInPictureStop()

            _restoreUserInterfaceForPIPStopCompletionHandler = completionHandler
        }

        func setRestoreUserInterfaceForPIPStopCompletionHandler(_ restore: Bool) {
            guard let _restoreUserInterfaceForPIPStopCompletionHandler else { return }
            _restoreUserInterfaceForPIPStopCompletionHandler(restore)
            self._restoreUserInterfaceForPIPStopCompletionHandler = nil
        }

        func setupPipController(_ playerLayer: AVPlayerLayer?) {
            guard let playerLayer else { return }
            if !AVPictureInPictureController.isPictureInPictureSupported() { return }
            _pipController = AVPictureInPictureController(playerLayer: playerLayer)
            if #available(iOS 14.2, *) {
                _pipController?.canStartPictureInPictureAutomaticallyFromInline = true
            }
            _pipController?.delegate = self
        }

        func deinitPipController() {
            _pipController = nil
        }

        func enterPictureInPicture() {
            guard let _pipController else { return }
            if !_isPictureInPictureActive {
                _pipController.startPictureInPicture()
            }
        }

        func exitPictureInPicture() {
            guard let _pipController else { return }
            if _isPictureInPictureActive {
                _isProgrammaticExit = true  // Mark as programmatic exit
                let state = UIApplication.shared.applicationState
                if state == .background || state == .inactive {
                    deinitPipController()
                    _onPictureInPictureExit?()
                    _onRestoreUserInterfaceForPictureInPictureStop?()
                    _isProgrammaticExit = false
                } else {
                    _pipController.stopPictureInPicture()
                }
            }
        }
        
        func isProgrammaticExit() -> Bool {
            return _isProgrammaticExit
        }
    }
#else
    class RCTPictureInPicture: NSObject {
        public let _pipController: NSObject? = nil

        func setRestoreUserInterfaceForPIPStopCompletionHandler(_: Bool) {}
        func setupPipController(_: AVPlayerLayer?) {}
        func deinitPipController() {}
        func enterPictureInPicture() {}
        func exitPictureInPicture() {}
        func isProgrammaticExit() -> Bool { return false }
    }
#endif
