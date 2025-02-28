import AVKit

class RCTVideoPlayerViewController: AVPlayerViewController {
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
    
    // Helper method to lock orientation
    func lockToLandscape() {
        if preferredOrientation?.lowercased() == "landscape" {
            // Lock to landscape orientation
            let value = UIInterfaceOrientation.landscapeRight.rawValue
            UIDevice.current.setValue(value, forKey: "orientation")
            
            // Force update
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        
        // Force desired orientation when the view is about to appear
        if let preferredOrientation = preferredOrientation, preferredOrientation.lowercased() == "landscape" {
            let value = UIInterfaceOrientation.landscapeRight.rawValue
            UIDevice.current.setValue(value, forKey: "orientation")
            UIViewController.attemptRotationToDeviceOrientation()
        }
    }
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        
        // Ensure we're in the correct orientation after view appears
        if let preferredOrientation = preferredOrientation, preferredOrientation.lowercased() == "landscape" {
            if UIDevice.current.orientation.isPortrait {
                let value = UIInterfaceOrientation.landscapeRight.rawValue
                UIDevice.current.setValue(value, forKey: "orientation")
                UIViewController.attemptRotationToDeviceOrientation()
            }
        }
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
