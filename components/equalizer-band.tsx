import { useEffect } from 'react';
import { Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { runOnJS, useAnimatedStyle, useSharedValue } from 'react-native-reanimated';
import { usePlayerTheme } from '@/hooks/use-player-theme';

const height = 180;
const inset = 12;
const travel = height - 2 * inset;
function gainAt(y: number) {
  'worklet';
  return Math.round(Math.max(-12, Math.min(12, 12 - (y - inset) / travel * 24)) * 2) / 2;
}

/** An actual vertical gesture, rather than a rotated horizontal native slider. */
export function EqualizerBand({ label, value, scrollGesture, onChange, onComplete, onDragging }: {
  label: string; value: number; scrollGesture: ReturnType<typeof Gesture.Native>;
  onChange: (value: number) => void; onComplete: (value: number) => void; onDragging: (dragging: boolean) => void;
}) {
  const { colors: c } = usePlayerTheme();
  const frequency = label.endsWith('k') ? Number(label.slice(0, -1)) * 1000 : Number(label);
  const gain = useSharedValue(value); const dragging = useSharedValue(false);
  useEffect(() => { if (!dragging.value) gain.value = value; }, [value, gain, dragging]);
  const pan = Gesture.Pan().minDistance(0).blocksExternalGesture(scrollGesture)
    .onBegin(event => {
      dragging.value = true; gain.value = gainAt(event.y);
      runOnJS(onDragging)(true); runOnJS(onChange)(gain.value);
    })
    .onUpdate(event => {
      const next = gainAt(event.y);
      if (next !== gain.value) { gain.value = next; runOnJS(onChange)(next); }
    })
    .onFinalize(() => {
      dragging.value = false; runOnJS(onComplete)(gain.value); runOnJS(onDragging)(false);
    });
  const thumb = useAnimatedStyle(() => ({ transform: [{ translateY: (12 - gain.value) / 24 * travel }] }));
  return <View style={{ flex: 1, minWidth: 0, alignItems: 'center', gap: 8 }}>
    <Text numberOfLines={1} maxFontSizeMultiplier={1.2} style={{ fontSize: 10, color: c.accent, fontVariant: ['tabular-nums'] }}>{value > 0 ? '+' : ''}{value}</Text>
    <GestureDetector gesture={pan}>
      <Animated.View accessible accessibilityRole="adjustable" accessibilityLabel={`${frequency} hertz band`} accessibilityValue={{ min: -12, max: 12, now: value, text: `${value} decibels` }} accessibilityActions={[{ name: 'increment', label: 'Increase by half a decibel' }, { name: 'decrement', label: 'Decrease by half a decibel' }]} onAccessibilityAction={event => onComplete(Math.max(-12, Math.min(12, value + (event.nativeEvent.actionName === 'increment' ? .5 : -.5))))}
        style={{ height, width: '100%', alignItems: 'center' }}>
        <View pointerEvents="none" style={{ position: 'absolute', top: inset, bottom: inset, width: 3, borderRadius: 2, backgroundColor: c.border }} />
        <View pointerEvents="none" style={{ position: 'absolute', top: height / 2, width: 12, height: 1, backgroundColor: c.secondary }} />
        <Animated.View pointerEvents="none" style={[{ position: 'absolute', top: inset - 10, width: 20, height: 20, borderRadius: 10, backgroundColor: c.accent, borderWidth: 3, borderColor: c.surface }, thumb]} />
      </Animated.View>
    </GestureDetector>
    <Text numberOfLines={1} maxFontSizeMultiplier={1.2} style={{ fontSize: 11, fontWeight: '600', color: c.foreground }}>{label}</Text>
  </View>;
}
