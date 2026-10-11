import Ionicons from '@expo/vector-icons/Ionicons';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useCollectionPage } from '@/hooks/use-collection-page';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { library } from '@/modules/keyflow-library/src';
import { type LocalAudio } from '@/modules/keyflow-playback/src';
import { errorMessage } from '@/lib/playback';
import { PlaylistNameDialog } from './playlist-name-dialog';

export function AddToPlaylist({ audio, trackId, onClose }: { audio: LocalAudio; trackId: string | null; onClose: () => void }) {
  const { colors: c } = usePlayerTheme(); const motion = useReducedMotion(); const insets = useSafeAreaInsets();
  const query = useCallback((offset: number) => library!.queryPlaylists({ offset, limit: 50 }), []);
  const page = useCollectionPage(query); const [create, setCreate] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);
  async function add(id: string) { if (busy) return; setBusy(true); try { await library!.addPlaylistEntry(id, audio, trackId); onClose(); } catch (failure) { setError(errorMessage(failure)); } finally { setBusy(false); } }
  return <><Modal visible={!create} transparent animationType={motion.enabled ? 'none' : 'fade'} onRequestClose={() => { if (!busy) onClose(); }}>
    <Pressable onPress={() => { if (!busy) onClose(); }} style={{ flex: 1, backgroundColor: '#00000070', justifyContent: 'flex-end' }}><Pressable accessibilityViewIsModal onPress={event => event.stopPropagation()} style={{ backgroundColor: c.surface, borderTopLeftRadius: 24, borderTopRightRadius: 24, padding: 24, paddingBottom: insets.bottom + 20, maxHeight: '70%' }}>
      <Text style={{ color: c.foreground, fontSize: 21, fontWeight: '700', marginBottom: 6 }}>Add to playlist</Text><Text numberOfLines={1} style={{ color: c.secondary }}>{audio.title}</Text>
      <Pressable disabled={busy} accessibilityRole="button" onPress={() => setCreate(true)} style={{ minHeight: 56, flexDirection: 'row', gap: 16, alignItems: 'center' }}><Ionicons name="add" size={24} color={c.accent} /><Text style={{ color: c.accent }}>New playlist</Text></Pressable>
      {!!(error || page.error) && <Text accessibilityRole="alert" style={{ color: c.danger }}>{error ?? page.error}</Text>}
      <FlatList data={page.items} keyExtractor={item => item.id} onEndReached={page.loadMore} renderItem={({ item }) => <Pressable disabled={busy} accessibilityRole="button" onPress={() => { void add(item.id); }} style={{ minHeight: 64, flexDirection: 'row', gap: 16, alignItems: 'center' }}><Ionicons name="musical-notes-outline" size={24} color={c.secondary} /><View style={{ flex: 1 }}><Text numberOfLines={1} style={{ color: c.foreground, fontSize: 16 }}>{item.name}</Text><Text style={{ color: c.secondary, fontSize: 12 }}>{item.count} songs</Text></View></Pressable>} ListEmptyComponent={page.loading ? <ActivityIndicator color={c.accent} /> : <Text style={{ color: c.secondary, paddingVertical: 16 }}>Create a playlist to start a collection.</Text>} />
    </Pressable></Pressable>
  </Modal><PlaylistNameDialog visible={create} title="New playlist" onClose={() => setCreate(false)} onSave={async name => { const id = await library!.createPlaylist(name); await library!.addPlaylistEntry(id, audio, trackId); onClose(); }} /></>;
}
