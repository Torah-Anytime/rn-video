import {
  AndroidConfig,
  withAndroidManifest,
  withAndroidStyles,
  type ConfigPlugin,
} from '@expo/config-plugins';

export const withFullScreenVideoConfig: ConfigPlugin = (config) => {
  // Modify AndroidManifest.xml
  config = withAndroidManifest(config, (_config) => {
    const manifest = _config.modResults;

    const app = AndroidConfig.Manifest.getMainApplication(manifest);
    if (!app) {
      console.warn('Could not find <application> in AndroidManifest.xml');
      return _config;
    }

    // Define the new activity
    const fullScreenActivity = {
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
        (activity) =>
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
  config = withAndroidStyles(config, (config) => {
    const styles = config.modResults.resources.style || [];

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

    return config;
  });

  return config;
};
