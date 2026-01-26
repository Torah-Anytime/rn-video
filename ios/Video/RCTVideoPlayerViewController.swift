import AVKit

class RCTVideoPlayerViewController: AVPlayerViewController, UIGestureRecognizerDelegate {
    weak var rctDelegate: RCTVideoPlayerViewControllerDelegate?

    // Optional parameters
    var preferredOrientation: String?
    var autorotate: Bool?

    override var shouldAutorotate: Bool {
        // If autorotate is explicitly set, respect that value
        if let autorotate = autorotate {
            return autorotate
        }

        // Otherwise, don't autorotate if a specific orientation is requested
        if let preferredOrientation = preferredOrientation {
            return preferredOrientation.lowercased() == "all"
        }

        // Default to true
        return true
    }

    // Gesture recognizer delegate method to control which gestures are allowed
    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        // Block all swipe gestures completely
        if gestureRecognizer is UISwipeGestureRecognizer {
            return false
        }
        return true
    }

    // This method is called when a gesture is about to begin - we can check state here
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        // Allow tap gestures (for showing/hiding controls)
        if gestureRecognizer is UITapGestureRecognizer {
            return true
        }

        // Block all pan gestures
        if gestureRecognizer is UIPanGestureRecognizer {
            return false
        }

        return true
    }

    // Override to prevent unwanted transition animations
    override func viewWillTransition(to size: CGSize, with coordinator: UIViewControllerTransitionCoordinator) {
        // Don't call super to prevent default transition animations
        coordinator.animate(alongsideTransition: nil) { _ in
            // No animation
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        // Configure gesture recognizers to prevent horizontal swipes
        configureGestureRecognizers()
    }

    // Reconfigure gestures whenever the view layout changes (e.g., when controls show/hide)
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        // Reconfigure gestures as AVPlayerViewController may add new ones when controls change
        configureGestureRecognizers()
    }

    // Configure gesture recognizers to prevent unwanted animations
    private func configureGestureRecognizers() {
        // Recursively search through view hierarchy to configure gestures
        func processView(_ view: UIView) {
            for gesture in view.gestureRecognizers ?? [] {
                // Set ourselves as the delegate for swipe and pan gestures
                // This allows us to selectively block horizontal swipes while allowing vertical ones
                if gesture is UISwipeGestureRecognizer || gesture is UIPanGestureRecognizer {
                    gesture.delegate = self
                }
                // Tap gestures are left alone for showing/hiding controls
            }

            // Recursively process subviews
            for subview in view.subviews {
                processView(subview)
            }
        }

        // Process the entire view hierarchy
        processView(self.view)
    }
    
    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        
        rctDelegate?.videoPlayerViewControllerWillDismiss(playerViewController: self)
        rctDelegate?.videoPlayerViewControllerDidDismiss(playerViewController: self)
    }
    
    #if !os(tvOS)
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        if let preferredOrientation = preferredOrientation {
            switch preferredOrientation.lowercased() {
            case "landscape":
                return .landscape
            case "portrait":
                return .portrait
            default:
                return .all
            }
        }
        return .all
    }
    
    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        if let preferredOrientation = preferredOrientation {
            switch preferredOrientation.lowercased() {
            case "landscape":
                return .landscapeRight
            case "portrait":
                return .portrait
            default:
                break
            }
        }
        
        // Default case
        if #available(iOS 13, tvOS 13, *) {
            return RCTVideoUtils.getCurrentWindow()?.windowScene?.interfaceOrientation ?? .unknown
        } else {
            #if !os(visionOS)
            return UIApplication.shared.statusBarOrientation
            #else
            return .portrait
            #endif
        }
    }
    #endif
}
