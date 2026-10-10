import Ionicons from '@expo/vector-icons/Ionicons';
import Slider from '@react-native-community/slider';
import { Image } from 'expo-image';
import { useState } from 'react';
import { ActivityIndicator, Modal, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { type PlayerColors, type ThemePreference } from '@/constants/player-theme';
import { usePlayback } from '@/hooks/use-playback';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { formatTime } from '@/lib/playback';

const statusLabels = { empty: 'Ready when you are', loading: 'Loading your song', playing: 'Playing',
  paused: 'Paused', ended: 'Song finished', error: 'Playback interrupted' } as const;

export default function PlayerScreen() {
  const { colors, isDark, preference, setPreference } = usePlayerTheme();
  const player = usePlayback();
  const { state, operation, available } = player;
  const [themeOpen, setThemeOpen] = useState(false);
  const [checksOpen, setChecksOpen] = useState(false);
  const [draftPosition, setDraftPosition] = useState<number | null>(null);
  const styles = makeStyles(colors);
  const track = state.track;
  const choosing = operation === 'choosing' || operation === 'opening';
  const busy = operation !== null;
  const position = draftPosition ?? state.positionMs;

  async function finishSeek(value: number) {
    await player.seek(value);
    setDraftPosition(null);
  }

  return <SafeAreaView style={styles.safe}>
    <ScrollView contentContainerStyle={styles.page} showsVerticalScrollIndicator={false}>
      <View style={styles.header}>
        <Image source={require('@/assets/branding/keyflow-logo.png')} contentFit="contain"
          tintColor={colors.foreground} style={styles.logo} accessibilityLabel="Keyflow" />
        <Pressable onPress={() => setThemeOpen(true)} accessibilityRole="button" accessibilityLabel="Choose appearance"
          style={({ pressed }) => [styles.iconButton, pressed && styles.pressed]}>
          <Ionicons name={isDark ? 'moon-outline' : 'sunny-outline'} size={22} color={colors.foreground} />
        </Pressable>
      </View>

      {!available && <View style={styles.previewNotice} accessibilityRole="text">
        <Ionicons name="information-circle-outline" size={20} color={colors.secondary} />
        <View style={styles.noticeCopy}>
          <Text style={styles.noticeTitle}>Interface preview</Text>
          <Text style={styles.noticeText}>{Platform.OS === 'android'
            ? 'Local playback is available in the installed Keyflow development app. Expo Go can preview this screen.'
            : 'Keyflow plays local music on Android. This preview shows the player interface.'}</Text>
        </View>
      </View>}

      <View style={styles.nowPlaying}>
        <Text style={styles.eyebrow}>{track ? 'NOW PLAYING' : 'ON YOUR DEVICE'}</Text>
        <View style={styles.status}>
          {state.status === 'loading' && <ActivityIndicator size="small" color={colors.accent} />}
          {state.isPlaying && <Ionicons name="volume-medium-outline" size={15} color={colors.accent} />}
          <Text style={styles.statusText}>{statusLabels[state.status]}</Text>
        </View>
      </View>

      <View style={styles.artworkWrap}>
        {track?.artworkUri
          ? <Image source={{ uri: track.artworkUri }} style={styles.artwork} contentFit="cover" accessibilityLabel="Song artwork" />
          : <View style={[styles.artwork, styles.placeholder]} accessibilityLabel={track ? 'No artwork in this file' : 'Music player'}>
              <View style={styles.recordOuter}><View style={styles.recordMiddle}><View style={styles.recordCenter}>
                <Ionicons name="musical-note" size={36} color={colors.accent} />
              </View></View></View>
            </View>}
      </View>

      <View style={styles.trackInfo}>
        <Text style={styles.title} numberOfLines={3}>{track?.title ?? 'Your music.\nA little closer.'}</Text>
        <Text style={styles.artist} numberOfLines={2}>{track?.artist ?? (track ? 'Unknown artist' : 'Choose a song and make yourself at home.')}</Text>
      </View>

      {player.error && <View style={styles.errorBox} accessibilityRole="alert">
        <Ionicons name="alert-circle-outline" size={21} color={colors.danger} />
        <Text style={styles.errorText}>{player.error}</Text>
      </View>}

      {track && <>
        <View style={styles.seekSection}>
          <Slider style={styles.slider} minimumValue={0} maximumValue={Math.max(state.durationMs, 1)}
            value={Math.min(position, Math.max(state.durationMs, 1))} disabled={!state.canSeek || busy}
            onSlidingStart={() => setDraftPosition(state.positionMs)} onValueChange={setDraftPosition}
            onSlidingComplete={value => { void finishSeek(value); }}
            minimumTrackTintColor={colors.accent} maximumTrackTintColor={colors.border} thumbTintColor={colors.accent}
            accessibilityLabel="Song position" accessibilityValue={{ text: `${formatTime(position)} of ${formatTime(state.durationMs)}` }} />
          <View style={styles.timeRow}><Text style={styles.time}>{formatTime(position)}</Text>
            <Text style={styles.time}>{formatTime(state.durationMs)}</Text></View>
        </View>
        <View style={styles.transport}>
          <Pressable disabled={busy} onPress={() => { void player.toggle(); }} accessibilityRole="button"
            accessibilityLabel={state.playWhenReady && state.status !== 'ended' && state.status !== 'error' ? 'Pause' : state.status === 'ended' ? 'Replay song' : 'Play'}
            style={({ pressed }) => [styles.playButton, busy && styles.disabled, pressed && styles.pressed]}>
            <Ionicons name={state.playWhenReady && state.status !== 'ended' && state.status !== 'error' ? 'pause' : 'play'}
              size={34} color={colors.onAccent} style={!state.playWhenReady ? { marginLeft: 4 } : undefined} />
          </Pressable>
        </View>
      </>}

      {available && <Pressable disabled={busy} onPress={() => { void player.chooseSong(); }} accessibilityRole="button"
        style={({ pressed }) => [track ? styles.secondaryButton : styles.openButton, busy && styles.disabled, pressed && styles.pressed]}>
        {choosing ? <ActivityIndicator color={track ? colors.foreground : colors.onAccent} />
          : <Ionicons name="folder-open-outline" size={20} color={track ? colors.foreground : colors.onAccent} />}
        <Text style={track ? styles.secondaryButtonText : styles.openButtonText}>
          {operation === 'choosing' ? 'Choosing a song…' : operation === 'opening' ? 'Opening your song…' : track ? 'Choose another song' : 'Open a song'}
        </Text>
      </Pressable>}

      <View style={styles.fileNote}>
        <Ionicons name="phone-portrait-outline" size={16} color={colors.muted} />
        <Text style={styles.fileNoteText} numberOfLines={2}>{track ? track.fileName : 'Local files. No account. Just music.'}</Text>
      </View>

      {__DEV__ && available && track && <View style={styles.checks}>
        <Pressable onPress={() => setChecksOpen(value => !value)} accessibilityRole="button" style={styles.checksHeading}>
          <Text style={styles.checksTitle}>Development audio checks</Text>
          <Ionicons name={checksOpen ? 'chevron-up' : 'chevron-down'} size={16} color={colors.secondary} />
        </Pressable>
        {checksOpen && <>
          <Text style={styles.noticeText}>Native tempo: {state.speed.toFixed(2)}× · Native pitch: {state.pitch.toFixed(4)}×</Text>
          <View style={styles.checkButtons}>
            {[{ label: '75% tempo', speed: 0.75, pitch: 0 }, { label: '+2 semitones', speed: 1, pitch: 2 },
              { label: 'Both', speed: 0.75, pitch: 2 }, { label: 'Reset', speed: 1, pitch: 0 }].map(check =>
              <Pressable key={check.label} disabled={busy} onPress={() => { void player.setParameters(check.speed, check.pitch); }}
                accessibilityRole="button" style={[styles.checkButton, busy && styles.disabled]}>
                <Text style={styles.checkButtonText}>{check.label}</Text>
              </Pressable>)}
          </View>
          <Text style={styles.noticeText}>These checks are visible only in development builds. Compare the sound as well as the native values.</Text>
        </>}
      </View>}
    </ScrollView>

    <Modal visible={themeOpen} transparent animationType="fade" onRequestClose={() => setThemeOpen(false)}>
      <Pressable style={styles.scrim} onPress={() => setThemeOpen(false)} accessibilityLabel="Close appearance choices">
        <Pressable style={styles.themeSheet} onPress={event => event.stopPropagation()} accessibilityViewIsModal>
          <View style={styles.sheetHeader}><Text style={styles.sheetTitle}>Appearance</Text>
            <Pressable onPress={() => setThemeOpen(false)} accessibilityRole="button" accessibilityLabel="Close appearance choices" style={styles.iconButton}>
              <Ionicons name="close" size={22} color={colors.foreground} />
            </Pressable></View>
          {([{ value: 'system', label: 'Follow device', icon: 'phone-portrait-outline' },
            { value: 'light', label: 'Light', icon: 'sunny-outline' }, { value: 'dark', label: 'Dark', icon: 'moon-outline' }] as const).map(option =>
            <Pressable key={option.value} onPress={() => { setPreference(option.value as ThemePreference); setThemeOpen(false); }}
              accessibilityRole="radio" accessibilityState={{ checked: preference === option.value }} style={styles.themeOption}>
              <Ionicons name={option.icon} size={21} color={colors.foreground} />
              <Text style={styles.themeOptionText}>{option.label}</Text>
              {preference === option.value && <Ionicons name="checkmark" size={22} color={colors.accent} />}
            </Pressable>)}
        </Pressable>
      </Pressable>
    </Modal>
  </SafeAreaView>;
}

function makeStyles(c: PlayerColors) {
  return StyleSheet.create({
    safe: { flex: 1, backgroundColor: c.background },
    page: { flexGrow: 1, width: '100%', maxWidth: 520, alignSelf: 'center', paddingHorizontal: 28, paddingTop: 14, paddingBottom: 36 },
    header: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 38 },
    logo: { width: 134, height: 45 },
    iconButton: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center', borderRadius: 24, backgroundColor: c.surface },
    previewNotice: { flexDirection: 'row', alignItems: 'flex-start', gap: 10, padding: 15, borderRadius: 16, backgroundColor: c.surface, marginBottom: 24 },
    noticeCopy: { flex: 1, gap: 4 }, noticeTitle: { color: c.foreground, fontWeight: '600', fontSize: 14 },
    noticeText: { fontSize: 13, lineHeight: 20, color: c.secondary },
    nowPlaying: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 },
    eyebrow: { fontSize: 10, letterSpacing: 2, fontWeight: '700', color: c.secondary },
    status: { flexDirection: 'row', alignItems: 'center', gap: 6 }, statusText: { color: c.secondary, fontSize: 12 },
    artworkWrap: { width: '100%', maxWidth: 340, alignSelf: 'center', marginBottom: 30 },
    artwork: { width: '100%', aspectRatio: 1, borderRadius: 28, backgroundColor: c.artwork },
    placeholder: { alignItems: 'center', justifyContent: 'center' },
    recordOuter: { width: '77%', aspectRatio: 1, borderRadius: 200, borderWidth: 1, borderColor: c.artworkLine, alignItems: 'center', justifyContent: 'center' },
    recordMiddle: { width: '80%', aspectRatio: 1, borderRadius: 200, borderWidth: 1, borderColor: c.artworkLine, alignItems: 'center', justifyContent: 'center' },
    recordCenter: { width: '57%', aspectRatio: 1, borderRadius: 100, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' },
    trackInfo: { alignItems: 'center', marginBottom: 24, gap: 10 },
    title: { color: c.foreground, fontSize: 29, lineHeight: 35, fontWeight: '700', letterSpacing: -0.8, textAlign: 'center' },
    artist: { color: c.secondary, fontSize: 15, lineHeight: 22, textAlign: 'center' },
    errorBox: { backgroundColor: c.dangerSoft, borderRadius: 14, padding: 14, gap: 10, flexDirection: 'row', marginBottom: 18 },
    errorText: { flex: 1, fontSize: 14, lineHeight: 21, color: c.danger },
    seekSection: { marginBottom: 16 }, slider: { width: '100%', height: 40 },
    timeRow: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 4 },
    time: { color: c.secondary, fontSize: 12, fontVariant: ['tabular-nums'] },
    transport: { alignItems: 'center', justifyContent: 'center', marginBottom: 32 },
    playButton: { width: 78, height: 78, borderRadius: 39, backgroundColor: c.accent, alignItems: 'center', justifyContent: 'center' },
    openButton: { minHeight: 56, backgroundColor: c.accent, borderRadius: 18, flexDirection: 'row', gap: 10, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
    openButtonText: { fontSize: 16, fontWeight: '600', color: c.onAccent },
    secondaryButton: { minHeight: 52, borderWidth: 1, borderColor: c.border, borderRadius: 16, flexDirection: 'row', gap: 10, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
    secondaryButtonText: { fontSize: 14, fontWeight: '600', color: c.foreground },
    fileNote: { marginTop: 18, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 7 },
    fileNoteText: { color: c.muted, fontSize: 12, lineHeight: 18, flexShrink: 1 },
    pressed: { opacity: 0.7 }, disabled: { opacity: 0.45 },
    checks: { marginTop: 30, paddingTop: 20, borderTopWidth: 1, borderTopColor: c.border, gap: 12 },
    checksHeading: { minHeight: 44, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    checksTitle: { color: c.secondary, fontSize: 13, fontWeight: '600' },
    checkButtons: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    checkButton: { minHeight: 44, paddingHorizontal: 12, borderRadius: 10, backgroundColor: c.accentSoft, justifyContent: 'center' },
    checkButtonText: { fontSize: 12, fontWeight: '600', color: c.foreground },
    scrim: { flex: 1, backgroundColor: 'rgba(0,0,0,0.35)', justifyContent: 'flex-end' },
    themeSheet: { backgroundColor: c.surface, padding: 24, paddingBottom: 40, borderTopLeftRadius: 28, borderTopRightRadius: 28 },
    sheetHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 },
    sheetTitle: { color: c.foreground, fontSize: 22, fontWeight: '700' },
    themeOption: { minHeight: 58, flexDirection: 'row', alignItems: 'center', gap: 14 },
    themeOptionText: { flex: 1, color: c.foreground, fontSize: 16 },
  });
}
