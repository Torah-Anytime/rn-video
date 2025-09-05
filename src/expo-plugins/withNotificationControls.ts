import {withAndroidManifest, type ConfigPlugin} from '@expo/config-plugins';

export const withNotificationControls: ConfigPlugin<boolean> = (
  c: any,
  enableNotificationControls: any,
) => {
  return withAndroidManifest(c, (config: any) => {
    const manifest = config.modResults.manifest;

    if (!enableNotificationControls) {
      return config;
    }

    if (!manifest.application) {
      console.warn(
        'AndroidManifest.xml is missing an <application> element - skipping adding notification controls related config.',
      );
      return config;
    }

    // Add the service to the AndroidManifest.xml
    manifest.application.map((application: any) => {
      function registerApplication(s: string){
        // We check if the VideoPlaybackService is already defined in the AndroidManifest.xml
        // to prevent adding duplicate service entries. If the service exists, we will remove
        // it before adding the updated configuration to ensure there are no conflicts or redundant
        // service declarations in the manifest.
        const existingServiceIndex = application?.service.findIndex(
          (service: any) =>
            service?.$?.['android:name'] ===
            'com.brentvatne.exoplayer.' + s,
        );
        if (existingServiceIndex !== -1) {
          application?.service.splice(existingServiceIndex, 1);
        }

        application?.service.push({
          $: {
            'android:name': 'com.brentvatne.exoplayer.' + s,
            'android:exported': 'false',
            'android:foregroundServiceType': 'mediaPlayback',
          },
          'intent-filter': [
            {
              action: [
                {
                  $: {
                    'android:name': 'androidx.media3.session.MediaSessionService',
                  },
                },
              ],
            },
          ],
        });
      }

      if (!application?.service) {
        application.service = [];
      }

      registerApplication("VideoPlaybackService")
      registerApplication("CentralizedPlaybackNotificationManager")

      return application;
    });

    return config;
  });
};
