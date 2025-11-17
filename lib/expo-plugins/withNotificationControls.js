"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.withNotificationControls = void 0;
const config_plugins_1 = require("@expo/config-plugins");
const withNotificationControls = (
// eslint-disable-next-line @typescript-eslint/no-explicit-any
c, 
// eslint-disable-next-line @typescript-eslint/no-explicit-any
enableNotificationControls) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return (0, config_plugins_1.withAndroidManifest)(c, (config) => {
        const manifest = config.modResults.manifest;
        if (!enableNotificationControls) {
            return config;
        }
        if (!manifest.application) {
            console.warn('AndroidManifest.xml is missing an <application> element - skipping adding notification controls related config.');
            return config;
        }
        // Add the service to the AndroidManifest.xml
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        manifest.application.map((application) => {
            function registerApplication(s) {
                // We check if the VideoPlaybackService is already defined in the AndroidManifest.xml
                // to prevent adding duplicate service entries. If the service exists, we will remove
                // it before adding the updated configuration to ensure there are no conflicts or redundant
                // service declarations in the manifest.
                const existingServiceIndex = application?.service.findIndex(
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                (service) => service?.$?.['android:name'] === 'com.brentvatne.exoplayer.' + s);
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
            registerApplication('VideoPlaybackService');
            registerApplication('CentralizedPlaybackNotificationManager');
            return application;
        });
        return config;
    });
};
exports.withNotificationControls = withNotificationControls;
//# sourceMappingURL=withNotificationControls.js.map