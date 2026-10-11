import Ionicons from '@expo/vector-icons/Ionicons';
import { useReorderableDrag } from 'react-native-reorderable-list';
import { memo, useCallback, useMemo } from 'react';
import { Pressable, Text, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, runOnJS, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { type QueueEntry } from '@/modules/keyflow-playback/src';

export const QueueRow = memo(function QueueRow({ item, current, busy, rowHeight, width, reduced, onPlay, onRemove, onMove }: {
  item: QueueEntry; current: boolean; busy: boolean; rowHeight: number; width: number; reduced: boolean;
  onPlay: (id: string) => Promise<unknown>; onRemove: (id: string) => Promise<unknown>; onMove: (id: string, delta: number) => Promise<unknown>;
}) {
  const drag = useReorderableDrag();
  const { colors: c } = usePlayerTheme(); const x = useSharedValue(0); const dismissing = useSharedValue(false);
  const remove = useCallback(async () => {
    const removed = await onRemove(item.id);
    if (removed === false) { x.value = 0; dismissing.value = false; }
  }, [onRemove, item.id, x, dismissing]);
  const swipe = useMemo(() => Gesture.Pan().enabled(!busy).activeOffsetX([-12, 12]).failOffsetY([-9, 9])
    .onUpdate(event => { if (!dismissing.value) x.value = event.translationX; })
    .onEnd(event => {
      if (Math.abs(event.translationX) > 88 || Math.abs(event.translationX) > 28 && Math.abs(event.velocityX) > 650) {
        dismissing.value = true;
        x.value = withTiming(event.translationX < 0 ? -width : width, { duration: reduced ? 0 : 150, easing: Easing.out(Easing.cubic) }, finished => { if (finished) runOnJS(remove)(); });
      } else x.value = withSpring(0, { stiffness: 500, damping: 42, mass: 1 });
    })
    .onFinalize((_, success) => { if (!success && !dismissing.value) x.value = withTiming(0, { duration: reduced ? 0 : 120 }); }), [busy, dismissing, x, width, reduced, remove]);
  const slide = useAnimatedStyle(() => ({ transform: [{ translateX: x.value }] }));
  const reveal = useAnimatedStyle(() => ({ opacity: Math.min(1, Math.abs(x.value) / 24) }));
  return <View>
    <Animated.View pointerEvents="none" style={[{ position: 'absolute', inset: 0, borderRadius: 12, backgroundColor: c.dangerSoft, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', paddingHorizontal: 24 }, reveal]}><Ionicons name="remove-circle-outline" size={24} color={c.danger} /><Ionicons name="remove-circle-outline" size={24} color={c.danger} /></Animated.View>
    <GestureDetector gesture={swipe}><Animated.View style={[{ height: rowHeight, flexDirection: 'row', alignItems: 'center', backgroundColor: current ? c.accentSoft : c.background, borderRadius: 12 }, slide]}>
      <Pressable disabled={busy} onPress={() => { void onPlay(item.id); }} accessibilityRole="button" accessibilityLabel={`Play ${item.title}`} style={{ flex: 1, height: rowHeight, flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 12 }}>
        <View style={{ width: 36, height: 36, borderRadius: 10, backgroundColor: c.surface, justifyContent: 'center', alignItems: 'center' }}><Ionicons name={current ? 'volume-medium-outline' : 'musical-note-outline'} size={20} color={current ? c.accent : c.secondary} /></View>
        <View style={{ flex: 1, gap: 4 }}><Text numberOfLines={1} style={{ color: c.foreground, fontSize: 15, fontWeight: '600' }}>{item.title}</Text><Text numberOfLines={1} style={{ color: c.secondary, fontSize: 12 }}>{item.priority ? 'Play next · ' : ''}{item.artist ?? 'Unknown artist'}</Text></View>
      </Pressable>
      <Pressable disabled={busy} onLongPress={drag} delayLongPress={140} accessibilityRole="adjustable" accessibilityLabel={`Reorder ${item.title}`} accessibilityHint="Hold and drag up or down. Swipe the song to remove it." accessibilityActions={[{ name: 'increment', label: 'Move down' }, { name: 'decrement', label: 'Move up' }, { name: 'remove', label: 'Remove from queue' }]} onAccessibilityAction={event => { if (busy) return; if (event.nativeEvent.actionName === 'remove') void onRemove(item.id); else void onMove(item.id, event.nativeEvent.actionName === 'increment' ? 1 : -1); }} style={{ width: 48, height: rowHeight, alignItems: 'center', justifyContent: 'center' }}><Ionicons name="reorder-three" size={24} color={c.secondary} /></Pressable>
    </Animated.View></GestureDetector>
  </View>;
});
