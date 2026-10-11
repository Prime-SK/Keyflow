import Ionicons from '@expo/vector-icons/Ionicons';
import { useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, FlatList, Modal, Pressable, Switch, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { IconButton } from '@/components/library-controls';
import { useLibrary } from '@/hooks/use-library';
import { useLibraryPage } from '@/hooks/use-library-page';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { errorMessage } from '@/lib/playback';
import { library, type LibrarySource } from '@/modules/keyflow-library/src';
type Rule = { path: string; included: boolean };
function effective(path: string, rules: Rule[]) {
  return rules.filter(rule => path.startsWith(rule.path)).sort((a, b) => b.path.length - a.path.length)[0]?.included ?? true;
}
export function FolderRules({ source, onClose }: { source: LibrarySource | null; onClose: () => void }) {
  const { colors: c } = usePlayerTheme(); const catalog = useLibrary(); const insets = useSafeAreaInsets();
  const [rules, setRules] = useState<Rule[]>([]); const [error, setError] = useState<string | null>(null); const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState(''); const [query, setQuery] = useState(''); const live = useRef(true); const request = useRef(0);
  const folders = useLibraryPage('folders', { ...(source ? { sourceId: source.id } : {}), includeExcluded: true, folderSearch: query });
  useEffect(() => { const timer = setTimeout(() => setQuery(search.trim()), 200); return () => clearTimeout(timer); }, [search]);
  const reload = useCallback(async () => {
    const revision = ++request.current;
    try {
      const next = source ? await library!.getFolderRules(source.id) : await library!.getLibraryFolderRules();
      if (live.current && request.current === revision) { setRules(next); setError(null); }
    } catch (failure) { if (live.current && request.current === revision) setError(errorMessage(failure)); }
    finally { if (live.current) setLoading(false); }
  }, [source]);
  useEffect(() => {
    live.current = true; void reload();
    const subscription = library?.addListener('onLibraryChanged', event => { if (event.invalidatePages) void reload(); });
    return () => { live.current = false; subscription?.remove(); };
  }, [reload]);
  const blocked = catalog.busy || loading || (!!source && catalog.scan.status === 'running');
  async function change(path: string, included: boolean) {
    if (await catalog.run(() => source ? library!.setFolderRule(source.id, path, included) : library!.setLibraryFolderRule(path, included))) await reload();
  }
  async function remove(path: string) {
    if (await catalog.run(() => source ? library!.removeFolderRule(source.id, path) : library!.removeLibraryFolderRule(path))) await reload();
  }
  function row(path: string, title: string, count?: number) {
    const included = effective(path, rules); const direct = rules.some(rule => rule.path === path);
    return <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8, paddingVertical: 11, borderBottomWidth: 0.5, borderColor: c.border }}>
      <Ionicons name={included ? 'folder-outline' : 'eye-off-outline'} size={22} color={included ? c.accent : c.muted} />
      <Pressable style={{ flex: 1, paddingVertical: 5, gap: 4 }} accessibilityRole="button" accessibilityLabel={`${title}, ${included ? 'included' : 'excluded'}, toggle`} disabled={blocked} onPress={() => { void change(path, !included); }}>
        <Text style={{ color: c.foreground, fontWeight: '600', fontSize: 14 }} numberOfLines={2}>{title}</Text>
        <Text style={{ color: c.muted, fontSize: 11, lineHeight: 16 }} numberOfLines={2}>{path.replace(/^primary:/, 'Device / ').replace(/\/$/, '')}</Text>
        <Text style={{ color: included ? c.secondary : c.danger, fontSize: 11 }}>{included ? 'Included' : 'Excluded'}{count === undefined ? ' · Saved rule' : ` · ${count} songs`}</Text>
      </Pressable>
      {direct && <IconButton icon="refresh-outline" label={`Reset rule for ${title}`} disabled={blocked} onPress={() => { void remove(path); }} />}
      <Switch accessibilityLabel={`Include ${title}`} disabled={blocked} value={included} onValueChange={value => { void change(path, value); }} trackColor={{ true: c.accent, false: c.border }} />
    </View>;
  }
  const known = new Set(folders.items.filter(item => 'key' in item).map(item => 'key' in item ? item.key : ''));
  return <Modal visible transparent animationType="fade" onRequestClose={onClose}>
    <Pressable onPress={onClose} accessibilityLabel="Dismiss folder settings" style={{ flex: 1, backgroundColor: '#00000066', justifyContent: 'flex-end' }}>
      <Pressable onPress={event => event.stopPropagation()} accessibilityViewIsModal style={{ backgroundColor: c.surface, borderTopLeftRadius: 24, borderTopRightRadius: 24, height: '88%', paddingBottom: Math.max(insets.bottom, 12) }}>
        <View style={{ alignSelf: 'center', width: 36, height: 4, borderRadius: 2, backgroundColor: c.border, marginTop: 10 }} />
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 20, paddingVertical: 8 }}><View style={{ flex: 1, gap: 4 }}><Text style={{ color: c.foreground, fontSize: 21, fontWeight: '700' }}>{source ? 'Source folders' : 'Folder visibility'}</Text>
          <Text style={{ color: c.secondary, fontSize: 12 }}>{source?.name ?? 'Applies across every scan source'}</Text></View>
          {!source && <IconButton icon="add-outline" label="Choose a folder to exclude" disabled={catalog.busy} onPress={() => { void catalog.run(() => library!.pickExcludedFolder()).then(success => { if (success) void reload(); }); }} />}
          <IconButton icon="close-outline" label="Close folder settings" onPress={onClose} /></View>
        <Text style={{ color: c.secondary, fontSize: 12, lineHeight: 18, paddingHorizontal: 20, paddingBottom: 12 }}>{source ? 'Source rules apply here; library-wide exclusions also apply.' : 'Turn a folder off to hide its songs immediately. Child-folder rules override their parents. Reset removes an override.'}</Text>
        <View style={{ marginHorizontal: 20, flexDirection: 'row', alignItems: 'center', gap: 8, borderRadius: 12, backgroundColor: c.background, paddingHorizontal: 12 }}><Ionicons name="search-outline" size={18} color={c.muted} /><TextInput accessibilityLabel="Search folders" value={search} onChangeText={setSearch} placeholder="Find a folder" placeholderTextColor={c.muted} style={{ flex: 1, color: c.foreground, paddingVertical: 12 }} />{!!search && <IconButton icon="close" label="Clear folder search" onPress={() => setSearch('')} />}</View>
        {(error || catalog.error || folders.error) && <Text accessibilityRole="alert" style={{ color: c.danger, padding: 20 }}>{error ?? catalog.error ?? folders.error}</Text>}
        <FlatList data={folders.items} keyExtractor={item => 'key' in item ? item.key : item.id} initialNumToRender={10} windowSize={7}
          contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 20 }} keyboardShouldPersistTaps="handled" onEndReached={() => { void folders.loadMore(); }} onEndReachedThreshold={0.6}
          ListHeaderComponent={<>{rules.filter(rule => !known.has(rule.path) && (!query || rule.path.toLowerCase().includes(query.toLowerCase()))).map(rule => <View key={rule.path}>{row(rule.path, rule.path.replace(/\/$/, '').split('/').pop() || 'Storage root')}</View>)}</>}
          ListEmptyComponent={loading || folders.loading ? <ActivityIndicator style={{ padding: 30 }} color={c.accent} /> : <View style={{ paddingVertical: 30, gap: 10 }}><Text style={{ color: c.foreground, fontWeight: '600' }}>{query ? 'No matching folders' : 'No scanned folders yet'}</Text><Text style={{ color: c.secondary, lineHeight: 20 }}>{source ? 'Scan this source to list its folders.' : 'Scan your music or use the + icon to exclude a local folder.'}</Text></View>}
          renderItem={({ item }) => 'key' in item ? row(item.key, item.title, item.count) : null}
          ListFooterComponent={folders.loading && folders.items.length > 0 ? <ActivityIndicator style={{ padding: 20 }} color={c.accent} /> : null} />
      </Pressable>
    </Pressable>
  </Modal>;
}
