"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.withFullScreenVideoConfig = void 0;
const config_plugins_1 = require("@expo/config-plugins");
const withFullScreenVideoConfig = (config) => {
    // Modify AndroidManifest.xml
    config = (0, config_plugins_1.withAndroidManifest)(config, (_config) => {
        const manifest = _config.modResults;
        const app = config_plugins_1.AndroidConfig.Manifest.getMainApplication(manifest);
        if (!app) {
            console.warn('Could not find <application> in AndroidManifest.xml');
            return _config;
        }
        // Define the new activity
        const fullScreenActivity = {
            $: {
                'android:name': 'com.brentvatne.exoplayer.ExoPlayerFullscreenVideoActivity',
                'android:configChanges': 'keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode',
                'android:theme': '@style/FullScreenTheme',
            },
        };
        // Avoid duplicate entries
        if (!app.activity?.some((activity) => activity.$['android:name'] ===
            'com.brentvatne.exoplayer.ExoPlayerFullscreenVideoActivity')) {
            app.activity = app.activity || [];
            app.activity.push(fullScreenActivity);
        }
        return _config;
    });
    // Modify styles.xml
    config = (0, config_plugins_1.withAndroidStyles)(config, (stylesConfig) => {
        const styles = stylesConfig.modResults.resources.style || [];
        // Check if FullScreenTheme already exists
        if (!styles.some((s) => s.$.name === 'FullScreenTheme')) {
            styles.push({
                $: { name: 'FullScreenTheme', parent: 'Theme.AppCompat.NoActionBar' },
                item: [
                    { _: 'true', $: { name: 'android:windowNoTitle' } },
                    { _: 'true', $: { name: 'android:windowFullscreen' } },
                    { _: '@null', $: { name: 'android:windowContentOverlay' } },
                    { _: '@android:color/black', $: { name: 'android:windowBackground' } },
                ],
            });
        }
        return stylesConfig;
    });
    return config;
};
exports.withFullScreenVideoConfig = withFullScreenVideoConfig;
//# sourceMappingURL=withFullScreenVideo.js.map