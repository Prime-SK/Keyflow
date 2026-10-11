import Ionicons from '@expo/vector-icons/Ionicons';
import { useEffect } from 'react';
import Reanimated, { Easing, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

const AnimatedIcon = Reanimated.createAnimatedComponent(Ionicons);
const easing = Easing.bezier(0.22, 0.61, 0.36, 1);

export function ArtworkControlIcon({ color, reduced, ...props }: React.ComponentProps<typeof Ionicons> & { color: string; reduced: boolean }) {
  const current = useSharedValue(color);
  useEffect(() => { current.value = reduced ? color : withTiming(color, { duration: 400, easing }); }, [color, reduced, current]);
  const style = useAnimatedStyle(() => ({ color: current.value }));
  return <AnimatedIcon {...props} style={[props.style, style]} />;
}

export function ArtworkControlFill({ color, reduced, children }: { color: string; reduced: boolean; children: React.ReactNode }) {
  const current = useSharedValue(color);
  useEffect(() => { current.value = reduced ? color : withTiming(color, { duration: 400, easing }); }, [color, reduced, current]);
  const style = useAnimatedStyle(() => ({ backgroundColor: current.value }));
  return <Reanimated.View pointerEvents="none" style={[{ position: 'absolute', inset: 0, borderRadius: 100, alignItems: 'center', justifyContent: 'center' }, style]}>{children}</Reanimated.View>;
}
