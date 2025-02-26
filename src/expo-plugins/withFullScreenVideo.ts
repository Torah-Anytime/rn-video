import {
    AndroidConfig,
    withAndroidManifest,
    withAndroidStyles,
    type ConfigPlugin,
  } from '@expo/config-plugins';
  
interface Config {
    modResults: any;
}

interface Activity {
    $: {
        'android:name': string;
        'android:configChanges': string;
        'android:theme': string;
    };
}

interface StyleItem {
    _: string;
    $: { name: string };
}

interface Style {
    $: { name: string; parent: string };
    item: StyleItem[];
}

interface AndroidStylesConfig {
    modResults: {
        resources: {
            style: Style[];
        };
    };
}

export const withFullScreenVideoConfig: ConfigPlugin = (config: any) => {
    // Modify AndroidManifest.xml
    config = withAndroidManifest(config, (_config: Config) => {
        const manifest: any = _config.modResults;

        const app: any = AndroidConfig.Manifest.getMainApplication(manifest);
        if (!app) {
            console.warn('Could not find <application> in AndroidManifest.xml');
            return _config;
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

        return _config;
    });

    // Modify styles.xml
    config = withAndroidStyles(config, (config: AndroidStylesConfig) => {
        const styles: Style[] = config.modResults.resources.style || [];

        // Check if FullScreenTheme already exists
        if (!styles.some((s: Style) => s.$.name === 'FullScreenTheme')) {
            const fullScreenTheme: Style = {
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
  