import Ionicons from '@expo/vector-icons/Ionicons';
import Slider from '@react-native-community/slider';
import { Image } from 'expo-image';
import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, Animated, BackHandler, Modal, Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from 'react-native';
import { SafeAreaView, useSafeAreaInsets } from 'react-native-safe-area-context';
import { type PlayerColors } from '@/constants/player-theme';
import { useArtworkTint, tintedBackground } from '@/hooks/use-artwork-tint';
import { usePlayback } from '@/hooks/use-playback';
import { usePlayerSwipe } from '@/hooks/use-player-swipe';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { formatTime } from '@/lib/playback';
import { MarqueeTitle } from '@/components/marquee-title';
import { AddToPlaylist } from '@/components/add-to-playlist';
import { useFavorite } from '@/hooks/use-favorite';
import { setMiniDestination } from '@/hooks/use-mini-destination';

const statusLabels = { empty: 'No song selected', loading: 'Loading song', playing: 'Playing', paused: 'Paused', ended: 'Song finished', error: 'Playback interrupted' } as const;

export default function PlayerScreen() {
  const router = useRouter();
  const { colors: c, preference, setPreference, isDark } = usePlayerTheme();
  const player = usePlayback(); const { state, operation, available } = player;
  const [menu, setMenu] = useState<'more' | 'appearance' | 'sleep' | null>(null);
  const [draftPosition, setDraftPosition] = useState<number | null>(null);
  const { width, height, fontScale } = useWindowDimensions(); const insets = useSafeAreaInsets();
  const track = state.track; const tint = useArtworkTint(track?.artworkUri);
  const favorite = useFavorite(track, track?.trackId ?? null); const [addPlaylist, setAddPlaylist] = useState(false);
  const background = tintedBackground(c.background, tint, isDark ? 0.16 : 0.08);
  const s = makeStyles(c); const busy = operation !== null; const position = draftPosition ?? state.positionMs;
  const collapse = () => { setMiniDestination('/player'); if (router.canGoBack()) router.back(); else router.replace('/'); };
  const swipe = usePlayerSwipe({ collapse,
    expand: () => router.push('/queue'),
    entryKey: state.currentEntryId,
    next: !busy && state.canNext ? player.next : undefined,
    previous: !busy && state.canPrevious ? player.previous : undefined }, { width, height, fullPlayer: true });
  const artworkSize = Math.min(width - 48, 360, Math.max(160, height - insets.top - insets.bottom - 408 - Math.max(0, fontScale - 1) * 100));
  const { dismiss } = swipe;
  useFocusEffect(useCallback(() => {
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => { void dismiss(); return true; });
    return () => subscription.remove();
  }, [dismiss]));
  async function finishSeek(value: number) { await player.seek(value); setDraftPosition(null); }
  const playing = state.playWhenReady && state.status !== 'ended' && state.status !== 'error';
  function control(icon: React.ComponentProps<typeof Ionicons>['name'], label: string, action: () => void, disabled = false, selected = false, extra?: React.ReactNode) {
    return <Pressable disabled={busy || disabled} onPress={action} accessibilityRole="button" accessibilityLabel={label}
      accessibilityState={{ disabled: busy || disabled, selected }} style={({ pressed }) => [s.iconButton, disabled && s.disabled, pressed && s.pressed]}>
      <Ionicons name={icon} size={26} color={selected ? c.accent : c.foreground} />{extra}
      {selected && <View style={[s.activeDot, { backgroundColor: c.accent }]} />}
    </Pressable>;
  }
  return <Animated.View style={{ flex: 1, transform: swipe.verticalTransform }}><SafeAreaView style={[s.safe, { backgroundColor: background }]}>
    <ScrollView contentContainerStyle={s.page} showsVerticalScrollIndicator={false}>
      <View style={s.topBar} {...swipe.panHandlers}>
        <Pressable onPress={() => { void swipe.dismiss(); }} accessibilityRole="button" accessibilityLabel="Collapse player to library" style={s.iconButton}><Ionicons name="chevron-down" size={26} color={c.foreground} /></Pressable>
        <View pointerEvents="none" style={[s.handle, { backgroundColor: c.border }]} />
        <Pressable onPress={() => setMenu('more')} accessibilityRole="button" accessibilityLabel="Player options" style={s.iconButton}><Ionicons name="ellipsis-vertical" size={22} color={c.foreground} /></Pressable>
      </View>
      {!available && <Text style={s.noticeText}>Local playback requires the installed Android Keyflow app.</Text>}
      <View style={{ overflow: 'hidden' }}><Animated.View {...swipe.panHandlers} style={[s.artworkWrap, { width: artworkSize, transform: swipe.horizontalTransform }]}
        accessibilityHint="Swipe left for next song, right for previous, or down to collapse the player">
        {track?.artworkUri ? <Image source={{ uri: track.artworkUri }} recyclingKey={state.currentEntryId ?? track.uri} transition={0} style={s.artwork} contentFit="cover" accessibilityLabel="Song artwork" />
          : <View style={[s.artwork, s.placeholder]} accessibilityLabel={track ? 'No song artwork' : 'No song selected'}>
            <View style={s.recordOuter}><View style={s.recordMiddle}><View style={s.recordCenter}><Ionicons name="musical-note" size={38} color={c.accent} /></View></View></View>
          </View>}
      </Animated.View></View>
      <View style={s.trackInfo}>
        <MarqueeTitle key={state.currentEntryId ?? 'empty'} title={track?.title ?? 'Your music, ready to play'} textStyle={s.title} height={32 * fontScale} />
        <Text style={[s.artist, { height: 22 * fontScale }]} numberOfLines={1}>{track?.artist ?? (track ? 'Unknown artist' : 'Choose a song from your library')}</Text>
      </View>
      {player.error && <View style={[s.noticeBox, { backgroundColor: c.dangerSoft }]} accessibilityRole="alert"><Ionicons name="alert-circle-outline" size={20} color={c.danger} /><Text style={[s.noticeText, { color: c.danger }]}>{player.error}</Text></View>}
      {track ? <>
        <View style={s.seekSection}>
          <Slider style={s.slider} minimumValue={0} maximumValue={Math.max(state.durationMs, 1)} value={Math.min(position, Math.max(state.durationMs, 1))} disabled={!state.canSeek || busy}
            onSlidingStart={() => setDraftPosition(state.positionMs)} onValueChange={setDraftPosition} onSlidingComplete={value => { void finishSeek(value); }}
            minimumTrackTintColor={c.accent} maximumTrackTintColor={c.border} thumbTintColor={c.accent}
            accessibilityLabel="Song position" accessibilityValue={{ text: `${formatTime(position)} of ${formatTime(state.durationMs)}` }} />
          <View style={s.timeRow}><Text style={s.time}>{formatTime(position)}</Text><Text style={s.time}>{formatTime(state.durationMs)}</Text></View>
        </View>
        <View style={s.transport}>
          {control('shuffle', state.shuffle ? 'Disable shuffle' : 'Enable shuffle', () => { void player.setShuffle(!state.shuffle); }, false, state.shuffle)}
          {control('play-skip-back', 'Previous or restart song', () => { void player.previous(); }, !state.canPrevious)}
          <Pressable disabled={busy} onPress={() => { void player.toggle(); }} accessibilityRole="button" accessibilityLabel={playing ? 'Pause' : state.status === 'ended' ? 'Replay song' : 'Play'} accessibilityState={{ disabled: busy }} style={({ pressed }) => [s.playButton, pressed && s.pressed]}>
            <Ionicons name={playing ? 'pause' : 'play'} size={38} color={c.onAccent} style={!playing ? { marginLeft: 4 } : undefined} />
          </Pressable>
          {control('play-skip-forward', 'Next song', () => { void player.next(); }, !state.canNext)}
          {control('repeat', `Repeat ${state.repeat}. Change repeat mode`, () => { void player.setRepeat(state.repeat === 'off' ? 'all' : state.repeat === 'all' ? 'one' : 'off'); }, false, state.repeat !== 'off', state.repeat === 'one' && <Text style={s.repeatOne}>1</Text>)}
        </View>
        <View style={s.footer}><Pressable accessibilityRole="button" accessibilityLabel={favorite.favorite ? 'Remove favorite' : 'Favorite song'} accessibilityState={{ selected: favorite.favorite }} disabled={favorite.busy} style={s.iconButton} onPress={() => { void favorite.toggle(); }}><Ionicons name={favorite.favorite ? 'heart' : 'heart-outline'} size={24} color={favorite.favorite ? c.accent : c.secondary} /></Pressable><View style={s.status}>{state.status === 'loading' && <ActivityIndicator size="small" color={c.accent} />}<Text style={s.statusText}>{state.sleepTimer.mode === 'off' ? statusLabels[state.status] : state.sleepTimer.mode === 'end' ? 'Timer · end of song' : `Timer · ${formatTime(state.sleepTimer.remainingMs)}`}</Text></View>
          <Pressable onPress={() => router.push('/queue')} accessibilityRole="button" accessibilityLabel="Open queue" style={s.iconButton}><Ionicons name="list-outline" size={24} color={c.secondary} /></Pressable></View>
        {!!state.notice && <Text accessibilityRole="alert" style={s.noticeText}>{state.notice}</Text>}
        {!!favorite.error && <Text accessibilityRole="alert" style={[s.noticeText, { color: c.danger }]}>{favorite.error}</Text>}
      </> : <Pressable accessibilityRole="button" onPress={collapse} style={s.libraryButton}><Ionicons name="musical-notes-outline" size={20} color={c.accent} /><Text style={{ color: c.accent, fontWeight: '600' }}>Browse library</Text></Pressable>}
    </ScrollView>
    <Modal visible={menu !== null} transparent animationType={swipe.reducedMotion ? 'none' : 'fade'} onRequestClose={() => setMenu(null)}>
      <Pressable style={s.scrim} onPress={() => setMenu(null)} accessibilityLabel="Close player options">
        <Pressable style={[s.sheet, { paddingBottom: Math.max(insets.bottom, 16) + 16 }]} onPress={event => event.stopPropagation()} accessibilityViewIsModal>
          <View style={s.sheetHeader}><Text style={s.sheetTitle}>{menu === 'appearance' ? 'Appearance' : menu === 'sleep' ? 'Sleep timer' : 'Player options'}</Text><Pressable onPress={() => setMenu(null)} accessibilityRole="button" accessibilityLabel="Close player options" style={s.iconButton}><Ionicons name="close" size={22} color={c.foreground} /></Pressable></View>
          {menu === 'sleep' ? <>
            <Text style={s.noticeText}>{state.sleepTimer.mode === 'timed' ? `Stops in ${formatTime(state.sleepTimer.remainingMs)}` : state.sleepTimer.mode === 'end' ? 'Stops at the end of this song' : 'Timer is off'}</Text>
            {([5, 15, 30, 45, 60] as const).map(minutes => <Pressable key={minutes} disabled={busy} accessibilityRole="button" style={s.menuRow} onPress={() => { void player.setSleepTimer('timed', minutes * 60_000).then(ok => { if (ok) setMenu(null); }); }}><Ionicons name="time-outline" size={22} color={c.foreground} /><Text style={s.menuText}>{minutes} minutes</Text></Pressable>)}
            <Pressable disabled={busy || state.status === 'ended'} accessibilityRole="button" style={s.menuRow} onPress={() => { void player.setSleepTimer('end').then(ok => { if (ok) setMenu(null); }); }}><Ionicons name="musical-note-outline" size={22} color={c.foreground} /><Text style={s.menuText}>End of this song</Text>{state.sleepTimer.mode === 'end' && <Ionicons name="checkmark" size={22} color={c.accent} />}</Pressable>
            {state.sleepTimer.mode !== 'off' && <Pressable disabled={busy} accessibilityRole="button" style={s.menuRow} onPress={() => { void player.setSleepTimer('off').then(ok => { if (ok) setMenu(null); }); }}><Ionicons name="close-circle-outline" size={22} color={c.foreground} /><Text style={s.menuText}>Cancel timer</Text></Pressable>}
            <Text style={s.noticeText}>Timed timers continue while paused. Skipping this song cancels its end timer.</Text>
          </> : menu === 'appearance' ? ([{ value: 'system', label: 'Follow device', icon: 'phone-portrait-outline' }, { value: 'light', label: 'Light', icon: 'sunny-outline' }, { value: 'dark', label: 'Dark', icon: 'moon-outline' }] as const).map(option =>
            <Pressable key={option.value} onPress={() => { setPreference(option.value); setMenu(null); }} accessibilityRole="radio" accessibilityState={{ checked: preference === option.value }} style={s.menuRow}>
              <Ionicons name={option.icon} size={22} color={c.foreground} /><Text style={s.menuText}>{option.label}</Text>{preference === option.value && <Ionicons name="checkmark" size={22} color={c.accent} />}</Pressable>) : <>
              <Pressable accessibilityRole="button" onPress={() => setMenu('appearance')} style={s.menuRow}><Ionicons name="contrast-outline" size={22} color={c.foreground} /><Text style={s.menuText}>Appearance</Text><Ionicons name="chevron-forward" size={18} color={c.secondary} /></Pressable>
              {track && <Pressable accessibilityRole="button" onPress={() => setMenu('sleep')} style={s.menuRow}><Ionicons name="timer-outline" size={22} color={state.sleepTimer.mode === 'off' ? c.foreground : c.accent} /><Text style={s.menuText}>Sleep timer</Text><Text style={s.statusText}>{state.sleepTimer.mode === 'timed' ? formatTime(state.sleepTimer.remainingMs) : state.sleepTimer.mode === 'end' ? 'End of song' : 'Off'}</Text></Pressable>}
              {track && <Pressable accessibilityRole="button" onPress={() => { setMenu(null); setAddPlaylist(true); }} style={s.menuRow}><Ionicons name="add-circle-outline" size={22} color={c.foreground} /><Text style={s.menuText}>Add to playlist</Text></Pressable>}
              {track && <View style={s.fileDetails}><Text style={s.detailLabel}>LOCAL FILE</Text><Text selectable style={s.fileName}>{track.fileName}</Text><Text style={s.statusText}>{track.mimeType ?? 'Audio file'}</Text><Text selectable style={s.fileName}>{track.title}</Text></View>}
              {__DEV__ && available && track && <View style={s.checks}><Text style={s.statusText}>Development audio checks · {state.speed.toFixed(2)}× tempo</Text><View style={s.checkButtons}>
                {[{ label: '75% tempo', speed: 0.75, pitch: 0 }, { label: '+2 semitones', speed: 1, pitch: 2 }, { label: 'Both', speed: 0.75, pitch: 2 }, { label: 'Reset', speed: 1, pitch: 0 }].map(check =>
                  <Pressable key={check.label} disabled={busy} onPress={() => { void player.setParameters(check.speed, check.pitch); }} accessibilityRole="button" style={s.checkButton}><Text style={s.statusText}>{check.label}</Text></Pressable>)}
              </View></View>}
            </>}
        </Pressable>
      </Pressable>
    </Modal>
    {addPlaylist && track && <AddToPlaylist audio={track} trackId={track.trackId ?? null} onClose={() => setAddPlaylist(false)} />}
  </SafeAreaView></Animated.View>;
}

function makeStyles(c: PlayerColors) {
  return StyleSheet.create({
    safe: { flex: 1 }, page: { flexGrow: 1, width: '100%', maxWidth: 520, alignSelf: 'center', paddingHorizontal: 24, paddingTop: 4, paddingBottom: 20 },
    topBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginHorizontal: -12, marginBottom: 18 }, handle: { width: 32, height: 4, borderRadius: 2 },
    iconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24 },
    artworkWrap: { alignSelf: 'center', marginBottom: 26 }, artwork: { width: '100%', aspectRatio: 1, borderRadius: 18, backgroundColor: c.artwork }, placeholder: { alignItems: 'center', justifyContent: 'center' },
    recordOuter: { width: '77%', aspectRatio: 1, borderRadius: 200, borderWidth: 1, borderColor: c.artworkLine, alignItems: 'center', justifyContent: 'center' },
    recordMiddle: { width: '80%', aspectRatio: 1, borderRadius: 200, borderWidth: 1, borderColor: c.artworkLine, alignItems: 'center', justifyContent: 'center' },
    recordCenter: { width: '57%', aspectRatio: 1, borderRadius: 100, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' },
    trackInfo: { gap: 6, marginBottom: 12 }, title: { color: c.foreground, fontSize: 25, lineHeight: 32, fontWeight: '700', letterSpacing: -0.5, includeFontPadding: false }, artist: { color: c.secondary, fontSize: 15, lineHeight: 22, includeFontPadding: false },
    seekSection: { marginHorizontal: -4, marginBottom: 18 }, slider: { width: '100%', height: 40 }, timeRow: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 8 }, time: { color: c.secondary, fontSize: 12, fontVariant: ['tabular-nums'] },
    transport: { marginHorizontal: -8, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 22 }, playButton: { width: 80, height: 80, borderRadius: 40, backgroundColor: c.accent, alignItems: 'center', justifyContent: 'center' },
    activeDot: { position: 'absolute', bottom: 3, width: 4, height: 4, borderRadius: 2 }, repeatOne: { position: 'absolute', color: c.accent, fontSize: 10, fontWeight: '700' },
    footer: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: c.border }, status: { flexDirection: 'row', alignItems: 'center', gap: 6 }, statusText: { color: c.secondary, fontSize: 12 },
    noticeBox: { flexDirection: 'row', gap: 10, backgroundColor: c.surface, padding: 12, borderRadius: 12, marginBottom: 16 }, noticeText: { flexShrink: 1, fontSize: 13, lineHeight: 20, color: c.secondary }, pressed: { opacity: 0.65 }, disabled: { opacity: 0.35 },
    libraryButton: { minHeight: 52, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 10 }, scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.45)', justifyContent: 'flex-end' },
    sheet: { backgroundColor: c.surface, padding: 24, borderTopLeftRadius: 24, borderTopRightRadius: 24 }, sheetHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }, sheetTitle: { color: c.foreground, fontSize: 21, fontWeight: '700' },
    menuRow: { minHeight: 58, flexDirection: 'row', alignItems: 'center', gap: 14 }, menuText: { flex: 1, color: c.foreground, fontSize: 16 }, fileDetails: { gap: 6, marginTop: 12, paddingVertical: 16, borderTopWidth: StyleSheet.hairlineWidth, borderTopColor: c.border },
    detailLabel: { color: c.muted, fontSize: 10, letterSpacing: 1.5, fontWeight: '600' }, fileName: { color: c.foreground, fontSize: 14, lineHeight: 20 }, checks: { gap: 12, marginTop: 12 }, checkButtons: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 }, checkButton: { minHeight: 44, paddingHorizontal: 12, borderRadius: 10, backgroundColor: c.accentSoft, justifyContent: 'center' },
  });
}
