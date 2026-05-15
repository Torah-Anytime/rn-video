import AVKit

class RCTVideoPlayerViewController: AVPlayerViewController, UIGestureRecognizerDelegate {
    weak var rctDelegate: RCTVideoPlayerViewControllerDelegate?

    // Optional parameters
    var preferredOrientation: String?
    var autorotate: Bool?

    // Track which gesture recognizers originally had no delegate so we only
    // override those (AVPlayerViewController's own delegates must be preserved
    // for the native scrubber / transport controls to keep working).
    private var managedGestures = NSHashTable<UIGestureRecognizer>.weakObjects()

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
        // Block unowned swipe gestures that cause the app to become unresponsive
        if gestureRecognizer is UISwipeGestureRecognizer {
            return false
        }
        return true
    }

    // This method is called when a gesture is about to begin
    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        // Allow tap gestures (for showing/hiding controls)
        if gestureRecognizer is UITapGestureRecognizer {
            return true
        }

        // For pan gestures, block only horizontal movement
        if let pan = gestureRecognizer as? UIPanGestureRecognizer {
            let velocity = pan.velocity(in: pan.view)
            let translation = pan.translation(in: pan.view)

            // If primarily horizontal movement, block it
            if abs(velocity.x) > abs(velocity.y) || abs(translation.x) > abs(translation.y) {
                return false
            }
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
        configureGestureRecognizers()
    }

    // Reconfigure gestures whenever the view layout changes (e.g., when controls show/hide)
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        configureGestureRecognizers()
    }

    // Only take over delegate-less swipe/pan gestures that are NOT part of
    // AVPlayerViewController's own transport controls (scrubber, etc.).
    // Gestures that already have a delegate are owned by the native player
    // UI and must be left alone.
    private func configureGestureRecognizers() {
        func processView(_ view: UIView) {
            for gesture in view.gestureRecognizers ?? [] {
                if gesture is UISwipeGestureRecognizer || gesture is UIPanGestureRecognizer {
                    // Only take over gestures that have no existing delegate
                    // and that we haven't already claimed.
                    if gesture.delegate == nil && !managedGestures.contains(gesture) {
                        gesture.delegate = self
                        managedGestures.add(gesture)
                    }
                }
            }

            for subview in view.subviews {
                processView(subview)
            }
        }

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
