const { withAndroidManifest, withGradleProperties } = require('expo/config-plugins');

module.exports = function withKeyflowAndroid(config) {
  config = withAndroidManifest(config, mod => {
    const application = mod.modResults.manifest.application?.[0];
    if (application) application.$['android:allowBackup'] = 'false';
    // The playback module supplies its own foreground-service and wake-lock permissions.
    // The library module supplies version-appropriate device-audio permissions.
    return mod;
  });
  return withGradleProperties(config, mod => {
    const versions = { 'android.minSdkVersion': '24', 'android.compileSdkVersion': '36',
      'android.targetSdkVersion': '36' };
    for (const [key, value] of Object.entries(versions)) {
      const existing = mod.modResults.find(entry => entry.type === 'property' && entry.key === key);
      if (existing) existing.value = value;
      else mod.modResults.push({ type: 'property', key, value });
    }
    return mod;
  });
};
