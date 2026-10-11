import Ionicons from '@expo/vector-icons/Ionicons';
import { useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { PlaylistNameDialog } from '@/components/playlist-name-dialog';
import { useCollectionPage } from '@/hooks/use-collection-page';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { library, type CollectionKind } from '@/modules/keyflow-library/src';

export default function CollectionsScreen() {
  const router = useRouter(); const { colors: c } = usePlayerTheme(); const [create, setCreate] = useState(false);
  const query = useCallback((offset: number) => library!.queryPlaylists({ offset, limit: 50 }), []); const page = useCollectionPage(query);
  const open = (kind: CollectionKind, id?: string) => router.push({ pathname: '/collection', params: { kind, ...(id ? { id } : {}) } });
  const shortcut = (kind: 'favorites' | 'recent', title: string, icon: 'heart-outline' | 'time-outline') => <Pressable accessibilityRole="button" onPress={() => open(kind)} style={{ minHeight: 76, flexDirection: 'row', gap: 16, alignItems: 'center', borderBottomWidth: 1, borderColor: c.border }}><View style={{ width: 48, height: 48, borderRadius: 14, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}><Ionicons name={icon} size={24} color={c.accent} /></View><Text style={{ flex: 1, color: c.foreground, fontSize: 18, fontWeight: '600' }}>{title}</Text><Ionicons name="chevron-forward" size={20} color={c.secondary} /></Pressable>;
  return <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', padding: 12 }}><Pressable accessibilityRole="button" accessibilityLabel="Back" onPress={() => router.back()} style={{ padding: 12 }}><Ionicons name="chevron-back" size={25} color={c.foreground} /></Pressable><Text style={{ flex: 1, color: c.foreground, fontSize: 26, fontWeight: '700' }}>Playlists</Text><Pressable accessibilityRole="button" accessibilityLabel="Create playlist" onPress={() => setCreate(true)} style={{ padding: 12 }}><Ionicons name="add" size={26} color={c.accent} /></Pressable></View>
    {!!page.error && <Text accessibilityRole="alert" style={{ color: c.danger, padding: 20 }}>{page.error}</Text>}
    <FlatList data={page.items} keyExtractor={item => item.id} onEndReached={page.loadMore} initialNumToRender={12} windowSize={7} contentContainerStyle={{ paddingHorizontal: 24, paddingBottom: 120 }}
      ListHeaderComponent={<>{shortcut('favorites', 'Favorites', 'heart-outline')}{shortcut('recent', 'Recently played', 'time-outline')}<Text style={{ color: c.secondary, marginTop: 28, marginBottom: 12 }}>PLAYLISTS · {page.total}</Text></>}
      renderItem={({ item }) => <Pressable accessibilityRole="button" onPress={() => open('playlist', item.id)} style={{ minHeight: 76, flexDirection: 'row', gap: 16, alignItems: 'center' }}><Ionicons name="musical-notes-outline" size={28} color={c.accent} /><View style={{ flex: 1, gap: 4 }}><Text numberOfLines={1} style={{ color: c.foreground, fontSize: 17, fontWeight: '600' }}>{item.name}</Text><Text style={{ color: c.secondary, fontSize: 12 }}>{item.count} songs</Text></View><Ionicons name="chevron-forward" size={20} color={c.secondary} /></Pressable>}
      ListEmptyComponent={page.loading ? <ActivityIndicator color={c.accent} style={{ padding: 30 }} /> : <Text style={{ color: c.secondary, paddingVertical: 20, lineHeight: 22 }}>Keep favorite sequences here. Create a playlist or save the current queue.</Text>} />
    <PlaylistNameDialog visible={create} title="New playlist" onClose={() => setCreate(false)} onSave={name => library!.createPlaylist(name)} />
  </SafeAreaView>;
}
