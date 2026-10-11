import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LibraryArtwork } from '@/components/library-artwork';
import { useLibrary } from '@/hooks/use-library';
import { useLibraryPage, type LibraryItem } from '@/hooks/use-library-page';
import { usePlaybackActions } from '@/hooks/use-playback';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { formatTime } from '@/lib/playback';
import { type LibraryGroup, type LibrarySection, type SongSort } from '@/modules/keyflow-library/src';

const sections: LibrarySection[] = ['songs', 'albums', 'artists', 'folders'];
const sorts: [SongSort, string][] = [['title', 'Title'], ['artist', 'Artist'], ['album', 'Album'], ['duration', 'Duration'], ['fileName', 'Filename'], ['dateAdded', 'Date added']];
const reasons = { missing: 'File missing', disconnected: 'Storage disconnected', permission: 'Access needs renewing', excluded: 'Excluded folder', available: '' };
export default function LibraryScreen() {
  const router = useRouter(); const { colors: c } = usePlayerTheme(); const player = usePlaybackActions(); const catalog = useLibrary();
  const [section, setSection] = useState<LibrarySection>('songs');
  const [group, setGroup] = useState<{ kind: Exclude<LibrarySection, 'songs'>; value: LibraryGroup } | null>(null);
  const [text, setText] = useState(''); const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SongSort>('title'); const [descending, setDescending] = useState(false); const [sortOpen, setSortOpen] = useState(false);
  useEffect(() => { const timer = setTimeout(() => setSearch(text.trim()), 250); return () => clearTimeout(timer); }, [text]);
  const query = useMemo(() => ({ search, sort, descending, ...(group ? { groupKind: group.kind, groupKey: group.value.key } : {}) }), [search, sort, descending, group]);
  const page = useLibraryPage(group ? 'songs' : section, query);
  const scanning = catalog.scan.status === 'running';
  const noSources = !catalog.sources.some(source => source.enabled);
  async function open(item: LibraryItem) {
    if ('id' in item) { if (await player.openLibraryTrack(item.id)) router.push('/player'); }
    else if (section !== 'songs') setGroup({ kind: section, value: item });
  }
  const action = (label: string, onPress: () => void, disabled = false) => <Pressable accessibilityRole="button" disabled={disabled} onPress={onPress}
    style={[styles.button, { backgroundColor: c.accentSoft, opacity: disabled ? 0.5 : 1 }]}><Text style={{ color: c.accent, fontWeight: '600' }}>{label}</Text></Pressable>;
  return <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
    <View style={styles.header}><Image source={require('@/assets/branding/keyflow-logo.png')} tintColor={c.foreground} contentFit="contain" style={{ width: 106, height: 36 }} />
      <View style={{ flexDirection: 'row', gap: 4 }}><Pressable accessibilityRole="button" accessibilityLabel="Open a local song" disabled={!player.available || player.operation !== null}
        onPress={() => { void player.chooseSong().then(opened => { if (opened) router.push('/player'); }); }} style={[styles.icon, { opacity: !player.available || player.operation !== null ? 0.4 : 1 }]}><Ionicons name="folder-open-outline" size={22} color={c.foreground} /></Pressable>
        <Pressable accessibilityRole="button" accessibilityLabel="Library sources and settings" onPress={() => router.push('/sources')} style={styles.icon}><Ionicons name="options-outline" size={22} color={c.foreground} /></Pressable></View></View>
    <View style={styles.heading}><Text style={{ color: c.foreground, fontSize: 26, fontWeight: '700', letterSpacing: -0.5 }}>Library</Text></View>
    <View style={[styles.search, { backgroundColor: c.surface, borderColor: c.border }]}><Ionicons name="search-outline" size={20} color={c.secondary} />
      <TextInput style={{ flex: 1, color: c.foreground, paddingVertical: 12 }} placeholder="Search songs, artists, albums…" placeholderTextColor={c.muted} value={text} onChangeText={setText} accessibilityLabel="Search library" returnKeyType="search" />
      {!!text && <Pressable onPress={() => setText('')} accessibilityLabel="Clear search" style={styles.icon}><Ionicons name="close" size={20} color={c.secondary} /></Pressable>}</View>
    <View style={styles.tabs}>{sections.map(value => <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: section === value }}
      onPress={() => { setSection(value); setGroup(null); }} style={[styles.tab, { borderBottomColor: section === value ? c.accent : 'transparent' }]}>
      <Ionicons name={value === 'songs' ? 'musical-notes-outline' : value === 'albums' ? 'disc-outline' : value === 'artists' ? 'person-outline' : 'folder-outline'} size={18} color={section === value ? c.accent : c.muted} />
      <Text style={{ color: section === value ? c.accent : c.secondary, fontSize: 12, fontWeight: '600', textTransform: 'capitalize' }}>{value}</Text></Pressable>)}</View>
    {group && <Pressable style={styles.group} onPress={() => setGroup(null)} accessibilityLabel="Back to groups"><Ionicons name="chevron-back" size={20} color={c.accent} /><Text numberOfLines={1} style={{ flex: 1, color: c.foreground, fontSize: 18, fontWeight: '600' }}>{group.value.title}</Text></Pressable>}
    <View style={styles.summary}><Text style={{ color: c.secondary }}>{page.total.toLocaleString()} {group || section === 'songs' ? 'songs' : section}</Text>
      {(group || section === 'songs') && <Pressable onPress={() => setSortOpen(true)} accessibilityRole="button" accessibilityLabel={`Sort by ${sort}, ${descending ? 'descending' : 'ascending'}`} style={styles.sort}><Text style={{ color: c.secondary, fontSize: 12 }}>{sorts.find(value => value[0] === sort)?.[1]}</Text><Ionicons name={descending ? 'arrow-down' : 'arrow-up'} size={16} color={c.accent} /></Pressable>}</View>
    {scanning && <View style={[styles.notice, { backgroundColor: c.accentSoft }]}><ActivityIndicator color={c.accent} /><Text style={{ flex: 1, color: c.foreground }}>Scanning {catalog.scan.sourceName ?? 'local audio'} · {catalog.scan.processed} files</Text>
      <Pressable onPress={() => router.push('/sources')} accessibilityLabel="Manage scan"><Ionicons name="chevron-forward" size={20} color={c.accent} /></Pressable></View>}
    {(player.error || catalog.error || page.error || catalog.scan.error) && <Text accessibilityRole="alert" style={[styles.message, { color: c.danger }]}>{player.error ?? catalog.error ?? page.error ?? catalog.scan.error}</Text>}
    {page.needsRefresh && action('Library updated · Refresh', () => { void page.refresh(); })}
    <FlatList data={page.items} key={`${section}:${group?.value.key ?? ''}`} keyExtractor={item => 'id' in item ? item.id : item.key}
      initialNumToRender={12} maxToRenderPerBatch={12} windowSize={7} onEndReached={() => { void page.loadMore(); }} onEndReachedThreshold={0.6}
      refreshing={page.loading && page.items.length === 0} onRefresh={() => { void page.refresh(); }} keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 110, flexGrow: 1 }}
      renderItem={({ item }) => <Pressable onPress={() => { void open(item); }} disabled={player.operation !== null} accessibilityRole="button"
        style={({ pressed }) => [styles.row, { borderBottomColor: c.border, backgroundColor: pressed ? c.accentSoft : 'transparent' }]}>
        {'id' in item ? <LibraryArtwork track={item} /> : <View style={[styles.groupArt, { backgroundColor: c.accentSoft }]}><Ionicons name={section === 'folders' ? 'folder-outline' : section === 'artists' ? 'person-outline' : 'disc-outline'} size={24} color={c.accent} /></View>}
        <View style={{ flex: 1, gap: 4 }}><Text numberOfLines={1} style={{ color: c.foreground, fontWeight: '600', fontSize: 15 }}>{item.title}</Text>
          <Text numberOfLines={1} style={{ color: 'id' in item && item.availability !== 'available' ? c.danger : c.secondary, fontSize: 12 }}>{'id' in item ? reasons[item.availability] || `${item.artist} · ${item.album}` : `${item.subtitle ? item.subtitle + ' · ' : ''}${item.count} songs`}</Text></View>
        {'id' in item ? <Text style={{ color: c.muted, fontSize: 12 }}>{formatTime(item.durationMs)}</Text> : <Ionicons name="chevron-forward" size={18} color={c.muted} />}
      </Pressable>}
      ListFooterComponent={page.loading && page.items.length > 0 ? <ActivityIndicator style={{ padding: 20 }} color={c.accent} /> : null}
      ListEmptyComponent={<View style={styles.empty}><Ionicons name="musical-notes-outline" size={42} color={c.accent} /><Text style={{ color: c.foreground, fontSize: 21, fontWeight: '600' }}>{!catalog.available ? 'Install Keyflow on Android' : page.loading || scanning ? 'Finding your music' : search ? 'No matches' : noSources ? 'Music, from your device' : 'No songs found'}</Text>
        <Text style={{ color: c.secondary, textAlign: 'center', lineHeight: 22 }}>{!catalog.available ? 'Library scanning and playback need the installed Android app.' : search ? 'Try a title, artist, album, or filename.' : noSources ? 'Allow audio access to discover local music. You can add folders and exclude recordings later. Your files stay untouched.' : 'Check source access, add a folder, or rescan from library settings.'}</Text>
        {catalog.available && noSources && <>{action('Scan device audio', () => { void catalog.scanDevice(); }, catalog.busy || scanning)}{action('Choose a folder', () => router.push('/sources'))}</>}
        {catalog.available && !noSources && !page.loading && !search && action('Manage sources', () => router.push('/sources'))}</View>} />
    <Modal visible={sortOpen} transparent animationType="fade" onRequestClose={() => setSortOpen(false)}><Pressable style={styles.overlay} onPress={() => setSortOpen(false)} accessibilityLabel="Dismiss sort menu">
      <Pressable onPress={event => event.stopPropagation()} accessibilityViewIsModal style={[styles.sheet, { backgroundColor: c.surface }]}><Text style={{ color: c.foreground, fontSize: 18, fontWeight: '600', marginBottom: 8 }}>Sort songs</Text>
      <ScrollView>{sorts.map(([value, label]) => <Pressable key={value} accessibilityRole="button" accessibilityLabel={`${label}${sort === value ? `, ${descending ? 'descending' : 'ascending'}, tap to reverse` : ', ascending'}`}
        style={[styles.option, { backgroundColor: sort === value ? c.accentSoft : 'transparent' }]} onPress={() => { setDescending(value === sort ? !descending : false); setSort(value); setSortOpen(false); }}><Text style={{ color: sort === value ? c.accent : c.foreground }}>{label}</Text>{sort === value && <Ionicons name={descending ? 'arrow-down' : 'arrow-up'} size={18} color={c.accent} />}</Pressable>)}</ScrollView>
      </Pressable></Pressable></Modal>
  </SafeAreaView>;
}
const styles = StyleSheet.create({ header: { paddingHorizontal: 20, paddingTop: 8, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }, icon: { padding: 12 },
  heading: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 16 }, button: { paddingHorizontal: 14, paddingVertical: 13, borderRadius: 12, minHeight: 44 },
  search: { marginHorizontal: 20, flexDirection: 'row', alignItems: 'center', gap: 10, paddingLeft: 12, borderWidth: 1, borderRadius: 12 }, tabs: { flexDirection: 'row', paddingHorizontal: 16, marginTop: 12 },
  tab: { flex: 1, alignItems: 'center', gap: 6, paddingVertical: 12, borderBottomWidth: 2 }, summary: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 6 }, sort: { flexDirection: 'row', gap: 8, minHeight: 44, alignItems: 'center', paddingLeft: 12 },
  row: { minHeight: 72, flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 9, borderBottomWidth: StyleSheet.hairlineWidth }, groupArt: { width: 48, height: 48, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  group: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingTop: 14, gap: 6 }, notice: { marginHorizontal: 20, borderRadius: 12, padding: 12, flexDirection: 'row', gap: 10, alignItems: 'center' },
  message: { paddingHorizontal: 20, paddingVertical: 10 }, empty: { paddingTop: 32, gap: 18, alignItems: 'center', maxWidth: 400, alignSelf: 'center' }, overlay: { flex: 1, justifyContent: 'center', padding: 32, backgroundColor: '#00000066' }, sheet: { borderRadius: 18, padding: 18, gap: 4, maxHeight: '85%' }, option: { paddingVertical: 15, paddingHorizontal: 12, borderRadius: 10, flexDirection: 'row', justifyContent: 'space-between' } });
