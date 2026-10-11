import { useIsFocused } from '@react-navigation/native';
import { useEffect, useMemo, useState } from 'react';
import { Animated, AppState, Easing, Pressable, ScrollView, StyleSheet, Text, type TextStyle } from 'react-native';
import { useReducedMotion } from '@/hooks/use-reduced-motion';

/** One tap stops at the beginning; the next starts scrolling again. */
export function MarqueeTitle({ title, textStyle, height }: { title: string; textStyle: TextStyle; height: number }) {
  const [width, setWidth] = useState(0);
  const [measured, setMeasured] = useState({ title: '', width: 0 });
  const [paused, setPaused] = useState(false);
  const [active, setActive] = useState(AppState.currentState === 'active');
  const focused = useIsFocused(); const motion = useReducedMotion();
  // Each restart gets a fresh node; delayed native stop callbacks cannot restore
  // the previous scroll offset onto the newly mounted scrolling title.
  // eslint-disable-next-line react-hooks/exhaustive-deps -- Dependencies intentionally control animation-node identity.
  const x = useMemo(() => new Animated.Value(0), [title, paused]);
  const overflow = measured.title === title && width > 0 && measured.width > width + 2;
  useEffect(() => { setPaused(false); }, [title]);
  useEffect(() => {
    const subscription = AppState.addEventListener('change', state => setActive(state === 'active'));
    return () => subscription.remove();
  }, []);
  useEffect(() => {
    x.stopAnimation();
    x.setValue(0);
    if (!overflow || paused || !active || !focused || motion.enabled) return;
    const distance = measured.width - width;
    const animation = Animated.loop(Animated.sequence([
      Animated.delay(1600),
      Animated.timing(x, { toValue: -distance, duration: Math.max(1200, distance / 24 * 1000), easing: Easing.linear, useNativeDriver: true }),
      Animated.delay(1200),
      Animated.timing(x, { toValue: 0, duration: Math.max(800, distance / 60 * 1000), easing: Easing.linear, useNativeDriver: true }),
    ]));
    animation.start(); return () => animation.stop();
  }, [title, width, measured.width, overflow, paused, active, focused, motion.enabled, x]);
  return <Pressable onLayout={event => setWidth(event.nativeEvent.layout.width)} style={{ height, overflow: 'hidden' }}
    onPress={() => { x.stopAnimation(); x.setValue(0); setPaused(value => !value); }} disabled={!overflow || motion.enabled} accessibilityRole={overflow && !motion.enabled ? 'button' : 'text'}
    accessibilityLabel={title} accessibilityHint={overflow && !motion.enabled ? paused ? 'Start title scrolling' : 'Stop scrolling and return to the title beginning' : undefined}>
    <ScrollView key={title} horizontal scrollEnabled={false} pointerEvents="none" showsHorizontalScrollIndicator={false}
      accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.measure}
      onContentSizeChange={naturalWidth => setMeasured({ title, width: naturalWidth })}>
      <Text style={textStyle} numberOfLines={1}>{title}</Text>
    </ScrollView>
    {paused || motion.enabled || !overflow
      ? <Text key="static-title" accessible={false} importantForAccessibility="no" numberOfLines={1} ellipsizeMode="tail"
          style={[textStyle, { width: '100%' }]}>{title}</Text>
      : <Animated.Text key="scrolling-title" accessible={false} importantForAccessibility="no" numberOfLines={1}
          style={[textStyle, { width: measured.width + 1, transform: [{ translateX: x }] }]}>{title}</Animated.Text>}
  </Pressable>;
}
const styles = StyleSheet.create({ measure: { position: 'absolute', left: 0, right: 0, top: 0, opacity: 0 } });
