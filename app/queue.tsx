import Ionicons from '@expo/vector-icons/Ionicons';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, BackHandler, FlatList, Pressable, Text, View, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Animated, { Easing, LinearTransition, runOnJS, useAnimatedScrollHandler, useAnimatedStyle, useSharedValue, withSpring, withTiming } from 'react-native-reanimated';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { usePlayback } from '@/hooks/use-playback';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { useQueuePage } from '@/hooks/use-queue-page';
import ReorderableList, { type ReorderableListReorderEvent } from 'react-native-reorderable-list';
import { type QueueEntry } from '@/modules/keyflow-playback/src';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { setMiniDestination } from '@/hooks/use-mini-destination';
import { QueueRow } from '@/components/queue-row';
import { playback } from '@/modules/keyflow-playback/src';
import { PlaylistNameDialog } from '@/components/playlist-name-dialog';
import { ConfirmationDialog } from '@/components/confirmation-dialog';

const dragCellStyle = { opacity: 1, transform: [], elevation: 8 };

export default function QueueScreen() {
  const router = useRouter(); const player = usePlayback(); const { state } = player;
  const { colors: c } = usePlayerTheme(); const insets = useSafeAreaInsets(); const { height, width, fontScale } = useWindowDimensions();
  const reduced = useReducedMotion(); const page = useQueuePage(state.queueRevision, state.queueIndex);
  const rowHeight = Math.max(76, Math.ceil(56 * fontScale + 20));
  const list = useRef<FlatList<QueueEntry>>(null);
  const queueScroll = useSharedValue(0); const queueDragging = useSharedValue(false);
  const panGesture = useMemo(() => Gesture.Pan().activateAfterLongPress(160), []);
  const onScroll = useAnimatedScrollHandler(event => { queueScroll.value = event.contentOffset.y; });
  const onDragStart = useCallback(() => { 'worklet'; queueDragging.value = true; }, [queueDragging]);
  const onDragEnd = useCallback(() => { 'worklet'; queueDragging.value = false; }, [queueDragging]);
  const reorder = ({ from, to }: ReorderableListReorderEvent) => {
    const entry = page.items[from]; if (!entry || from === to) return;
    page.presentMove(entry.id, to - from);
    void player.moveQueueEntry(entry.id, to - from).then(moved => { if (!moved) void page.refreshWindow(); });
  };
  const { presentRemove, refreshWindow } = page;
  const { removeQueueEntry } = player;
  const removeEntry = useCallback(async (id: string) => {
    presentRemove(id);
    const removed = await removeQueueEntry(id);
    if (!removed) await refreshWindow();
    return removed;
  }, [presentRemove, refreshWindow, removeQueueEntry]);
  const [save, setSave] = useState(false); const busy = player.operation !== null;
  const [clear, setClear] = useState(false);
  const offset = useSharedValue(height); const closing = useSharedValue(false); const touchY = useSharedValue(0); const touchX = useSharedValue(0);
  const close = useCallback(() => { setMiniDestination('/queue'); if (router.canGoBack()) router.back(); else router.replace('/'); }, [router]);
  const entered = useRef(false);
  useEffect(() => { if (reduced.ready && !entered.current) { entered.current = true; offset.value = withTiming(0, { duration: reduced.enabled ? 0 : 230, easing: Easing.out(Easing.cubic) }); } }, [reduced.ready, reduced.enabled, offset]);
  const dismiss = useCallback(() => { if (closing.value) return; closing.value = true; offset.value = withTiming(height, { duration: reduced.enabled ? 0 : 220 }, finished => { if (finished) runOnJS(close)(); }); }, [close, closing, offset, height, reduced.enabled]);
  useFocusEffect(useCallback(() => { const subscription = BackHandler.addEventListener('hardwareBackPress', () => { dismiss(); return true; }); return () => subscription.remove(); }, [dismiss]));
  const sheetStyle = useAnimatedStyle(() => ({ transform: [{ translateY: offset.value }] }));
  const dismissGesture = Gesture.Pan().manualActivation(true)
    .onTouchesDown((event, manager) => {
      const t = event.allTouches[0];
      if (!t || closing.value || busy || queueDragging.value || t.absoluteY > insets.top + 128 && (queueScroll.value > 1 || t.absoluteX > width - 72)) { manager.fail(); return; }
      touchY.value = t.absoluteY; touchX.value = t.absoluteX;
    })
    .onTouchesMove((event, manager) => {
      const t = event.allTouches[0]; if (!t) return;
      const dx = Math.abs(t.absoluteX - touchX.value); const dy = t.absoluteY - touchY.value;
      if (dx > 12 || dy < -8) manager.fail(); else if (dy > 14) manager.activate();
    })
    .onUpdate(event => { if (!reduced.enabled) offset.value = Math.max(0, event.absoluteY - touchY.value); })
    .onEnd(event => {
      if (event.absoluteY - touchY.value > 72 || event.velocityY > 700) {
        closing.value = true; offset.value = withTiming(height, { duration: reduced.enabled ? 0 : 220 }, finished => { if (finished) runOnJS(close)(); });
      } else offset.value = withSpring(0, { stiffness: 500, damping: 44, mass: 1 });
    })
    .onFinalize((_, success) => { if (!success && !closing.value) offset.value = withTiming(0, { duration: reduced.enabled ? 0 : 150 }); });
  const icon = { width: 48, height: 48, alignItems: 'center' as const, justifyContent: 'center' as const };
  return <GestureDetector gesture={dismissGesture}><Animated.View style={[{ flex: 1 }, sheetStyle]}><SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingTop: 8, gap: 6 }}>
      <Pressable style={icon} accessibilityRole="button" accessibilityLabel="Close queue" onPress={dismiss}><Ionicons name="chevron-down" size={25} color={c.foreground} /></Pressable>
      <View style={{ flex: 1 }}><Text style={{ color: c.foreground, fontSize: 24, fontWeight: '700' }}>Queue</Text><Text style={{ color: c.secondary, fontSize: 12 }}>{state.queueCount} songs</Text></View>
      <Pressable style={icon} disabled={busy || !state.queueCount} accessibilityRole="button" accessibilityLabel="Save queue as playlist" onPress={() => setSave(true)}><Ionicons name="save-outline" size={22} color={state.queueCount ? c.foreground : c.muted} /></Pressable>
      <Pressable style={icon} disabled={busy || state.queueIndex + 1 >= state.queueCount} accessibilityRole="button" accessibilityLabel="Clear upcoming songs" onPress={() => setClear(true)}><Ionicons name="trash-outline" size={22} color={state.queueIndex + 1 >= state.queueCount ? c.muted : c.foreground} /></Pressable>
    </View>
    <View style={{ flexDirection: 'row', paddingHorizontal: 24, justifyContent: 'space-between' }}>
      <Pressable style={{ minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: 8 }} onPress={() => { void page.refresh(); }} accessibilityRole="button"><Ionicons name="locate-outline" size={18} color={c.accent} /><Text style={{ color: c.accent }}>Current song</Text></Pressable>
      {page.start > 0 && <Pressable style={{ minHeight: 48, justifyContent: 'center' }} onPress={() => { void page.fromBeginning(); }} accessibilityRole="button"><Text style={{ color: c.secondary }}>From beginning</Text></Pressable>}
    </View>
    {!!(page.error || player.error) && <Text accessibilityRole="alert" style={{ color: c.danger, padding: 20 }}>{page.error ?? player.error}</Text>}
    <View style={{ flex: 1 }}>
      <ReorderableList ref={list} data={page.items} keyExtractor={item => item.id} initialNumToRender={12} maxToRenderPerBatch={12} windowSize={7}
        onReorder={reorder} onScroll={onScroll} onDragStart={onDragStart} onDragEnd={onDragEnd} panGesture={panGesture} dragEnabled={!busy}
        animationDuration={reduced.enabled ? 0 : 180} cellAnimations={dragCellStyle}
        autoscrollThreshold={0.1} autoscrollSpeedScale={0.7}
        itemLayoutAnimation={reduced.enabled ? undefined : LinearTransition.duration(180)}
        contentContainerStyle={{ paddingHorizontal: 16, paddingBottom: insets.bottom + 24, flexGrow: 1 }} onEndReached={page.loadMore} onEndReachedThreshold={0.6}
        ListHeaderComponent={page.start > 0 ? <Pressable disabled={page.loading || busy} accessibilityRole="button" style={{ height: 48, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 }} onPress={() => { void page.loadEarlier(); }}><Ionicons name="chevron-up" size={18} color={c.accent} /><Text style={{ color: c.accent }}>Show earlier songs</Text></Pressable> : null}
        renderItem={({ item }) => <QueueRow item={item} current={item.id === state.currentEntryId} busy={busy} rowHeight={rowHeight} width={width} reduced={reduced.enabled} onPlay={player.jumpQueueEntry} onRemove={removeEntry} onMove={player.moveQueueEntry} />}
        ListFooterComponent={page.loading && page.items.length > 0 ? <ActivityIndicator color={c.accent} style={{ padding: 20 }} /> : null}
        ListEmptyComponent={<View style={{ flex: 1, gap: 16, alignItems: 'center', justifyContent: 'center', padding: 32 }}>{page.loading ? <ActivityIndicator color={c.accent} /> : <><Ionicons name="list-outline" size={44} color={c.accent} /><Text style={{ color: c.foreground, fontSize: 21, fontWeight: '600' }}>Your queue is empty</Text><Text style={{ color: c.secondary, textAlign: 'center' }}>Play or add songs from the library.</Text></>}</View>} />
    </View>
    <PlaylistNameDialog visible={save} title="Save queue as playlist" onClose={() => setSave(false)} onSave={async name => { await playback!.saveQueueAsPlaylist(name); }} />
    <ConfirmationDialog visible={clear} title="Clear upcoming songs?" message="Your current song and earlier songs stay in the queue." confirmLabel="Clear" busy={busy} onClose={() => setClear(false)} onConfirm={() => { void player.clearUpcoming(); }} />
  </SafeAreaView></Animated.View></GestureDetector>;
}
