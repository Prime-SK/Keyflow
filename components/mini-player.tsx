import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { usePathname, useRouter } from 'expo-router';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { usePlayback } from '@/hooks/use-playback';
import { usePlayerTheme } from '@/hooks/use-player-theme';

export function MiniPlayer() {
  const { state, toggle, operation } = usePlayback();
  const { colors: c } = usePlayerTheme();
  const pathname = usePathname(); const router = useRouter(); const insets = useSafeAreaInsets();
  if (!state.track || pathname === '/player') return null;
  return <View style={[styles.wrap, { backgroundColor: c.surface, borderColor: c.border, bottom: Math.max(insets.bottom, 12) }]}>
    <Pressable style={styles.song} accessibilityRole="button" accessibilityLabel={`Open player: ${state.track.title}`} onPress={() => router.push('/player')}>
      {state.track.artworkUri ? <Image source={{ uri: state.track.artworkUri }} style={styles.art} />
        : <View style={[styles.art, { backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }]}><Ionicons name="musical-note" size={22} color={c.accent} /></View>}
      <View style={styles.copy}><Text numberOfLines={1} style={{ color: c.foreground, fontWeight: '600', fontSize: 14 }}>{state.track.title}</Text>
        <Text numberOfLines={1} style={{ color: c.secondary, fontSize: 12 }}>{state.track.artist ?? 'Unknown artist'}</Text></View>
    </Pressable>
    <Pressable disabled={operation !== null} onPress={() => { void toggle(); }} style={styles.control} accessibilityRole="button"
      accessibilityLabel={state.playWhenReady && state.status !== 'ended' && state.status !== 'error' ? 'Pause' : 'Play'}>
      <Ionicons name={state.playWhenReady && state.status !== 'ended' && state.status !== 'error' ? 'pause' : 'play'} size={24} color={c.foreground} />
    </Pressable>
    <View pointerEvents="none" style={[styles.progress, { backgroundColor: c.border }]}><View style={{ height: 2, backgroundColor: c.accent, width: `${Math.min(100, Math.max(0, state.durationMs > 0 ? state.positionMs / state.durationMs * 100 : 0))}%` }} /></View>
  </View>;
}
const styles = StyleSheet.create({
  wrap: { position: 'absolute', left: 16, right: 16, borderWidth: 1, borderRadius: 18, flexDirection: 'row', padding: 9, alignItems: 'center', overflow: 'hidden' },
  progress: { position: 'absolute', bottom: 0, left: 0, right: 0, height: 2 },
  song: { flexDirection: 'row', flex: 1, alignItems: 'center', gap: 12, minHeight: 48 }, art: { width: 48, height: 48, borderRadius: 10 },
  copy: { flex: 1, gap: 4 }, control: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
});
