import {
    AndroidConfig,
    withAndroidManifest,
    withAndroidStyles,
    type ConfigPlugin,
  } from '@expo/config-plugins';

interface Activity {
    $: {
        'android:name': string;
        'android:configChanges': string;
        'android:theme': string;
    };
}


export const withFullScreenVideoConfig: ConfigPlugin = (config) => {
    // Modify AndroidManifest.xml
    config = withAndroidManifest(config, (config) => {
        const manifest = config.modResults;

        const app: any = AndroidConfig.Manifest.getMainApplication(manifest);
        if (!app) {
            console.warn('Could not find <application> in AndroidManifest.xml');
            return config;
        }

        // Define the new activity
        const fullScreenActivity: Activity = {
            $: {
                'android:name': 'com.brentvatne.exoplayer.ExoPlayerFullscreenVideoActivity',
                'android:configChanges':
                    'keyboard|keyboardHidden|orientation|screenSize|screenLayout|smallestScreenSize|uiMode',
                'android:theme': '@style/FullScreenTheme',
            },
        };

        // Avoid duplicate entries
        if (
            !app.activity?.some(
                (activity: Activity) =>
                    activity.$['android:name'] ===
                    'com.brentvatne.exoplayer.ExoPlayerFullscreenVideoActivity'
            )
        ) {
            app.activity = app.activity || [];
            app.activity.push(fullScreenActivity);
        }

        return config;
    });

    // Modify styles.xml
    config = withAndroidStyles(config, (config) => {
        const styles = config.modResults.resources.style || [];

        // Check if FullScreenTheme already exists
        if (!styles.some((s: any) => s.$.name === 'FullScreenTheme')) {
            const fullScreenTheme = {
                $: { name: 'FullScreenTheme', parent: 'Theme.AppCompat.NoActionBar' },
                item: [
                    { _: 'true', $: { name: 'android:windowNoTitle' } },
                    { _: 'true', $: { name: 'android:windowFullscreen' } },
                    { _: '@null', $: { name: 'android:windowContentOverlay' } },
                    { _: '@android:color/black', $: { name: 'android:windowBackground' } },
                ],
            };
            styles.push(fullScreenTheme);
        }

        return config;
    });

    return config;
};
  