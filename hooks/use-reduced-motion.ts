import { useEffect, useState } from 'react';
import { AccessibilityInfo } from 'react-native';

export function useReducedMotion() {
  const [motion, setMotion] = useState({ enabled: true, ready: false });
  useEffect(() => {
    let active = true;
    const update = (enabled: boolean) => { if (active) setMotion({ enabled, ready: true }); };
    void AccessibilityInfo.isReduceMotionEnabled().then(update).catch(() => update(true));
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', update);
    return () => { active = false; subscription.remove(); };
  }, []);
  return motion;
}
