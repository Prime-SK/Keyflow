import Ionicons from '@expo/vector-icons/Ionicons';
import { useRouter } from 'expo-router';
import { useState } from 'react';
import { Alert, Linking, Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { FolderRules } from '@/components/folder-rules';
import { IconButton, SettingsRow } from '@/components/library-controls';
import { useLibrary } from '@/hooks/use-library';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { library, type LibrarySource } from '@/modules/keyflow-library/src';

const accessLabels = { available: 'Accessible', permission: 'Renew access', disconnected: 'Storage disconnected', missing: 'Folder missing', excluded: 'Excluded' };
export default function SourcesScreen() {
  const router = useRouter(); const { colors: c, preference, setPreference } = usePlayerTheme(); const catalog = useLibrary();
  const [ruleScope, setRuleScope] = useState<LibrarySource | null | undefined>();
  const scanning = catalog.scan.status === 'running'; const disabled = catalog.busy || scanning || !catalog.available;
  const section = (title: string) => <Text style={{ color: c.muted, fontSize: 11, fontWeight: '700', letterSpacing: 1.4, marginTop: 24, marginBottom: 8, textTransform: 'uppercase' }}>{title}</Text>;
  const surface = { backgroundColor: c.surface, borderRadius: 16, overflow: 'hidden' as const };
  const divider = <View style={{ marginLeft: 58, height: 0.5, backgroundColor: c.border }} />;
  return <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, paddingVertical: 4, gap: 6 }}><IconButton icon="chevron-back" label="Back to library" onPress={() => router.canGoBack() ? router.back() : router.replace('/')} /><Text style={{ color: c.foreground, fontSize: 22, fontWeight: '700' }}>Library settings</Text></View>
    <ScrollView contentContainerStyle={{ paddingHorizontal: 20, paddingBottom: 110 }}>
      {!catalog.available && <Text style={{ color: c.secondary, paddingVertical: 12 }}>Library settings require the installed Android app.</Text>}
      {(catalog.error || catalog.scan.error) && <Text accessibilityRole="alert" style={{ color: c.danger, paddingVertical: 12 }}>{catalog.error ?? catalog.scan.error}</Text>}
      {section('Library')}
      <View style={surface}>
        <SettingsRow icon={scanning ? 'stop-circle-outline' : 'refresh-outline'} title={scanning ? 'Scanning music' : 'Rescan sources'} subtitle={scanning ? `${catalog.scan.sourceName ?? 'Local audio'} · ${catalog.scan.processed} files` : 'Find new and changed music'} disabled={catalog.busy || !catalog.available || (!scanning && !catalog.sources.some(source => source.enabled))}
          onPress={() => { void catalog.run(() => scanning ? library!.cancelScan() : library!.startScan()); }} trailing={<Ionicons name={scanning ? 'stop' : 'refresh-outline'} size={20} color={c.accent} />} />
        {divider}
        <SettingsRow icon="sync-outline" title="Automatic rescanning" subtitle="Refresh on reopening and device audio changes" trailing={<Switch accessibilityLabel="Automatic rescanning" value={catalog.automaticScanning} disabled={catalog.busy || !catalog.available} onValueChange={value => { void catalog.run(() => library!.setAutomaticScanning(value)); }} trackColor={{ true: c.accent, false: c.border }} />} />
        {divider}
        <SettingsRow icon="eye-off-outline" title="Folder visibility" subtitle="Include or exclude folders across the library" disabled={!catalog.available} onPress={() => setRuleScope(null)} />
      </View>
      {section('Sources')}
      <View style={surface}>
        <SettingsRow icon="phone-portrait-outline" title="Device audio" subtitle={catalog.permission ? 'Discover music on available storage' : 'Allow access to your audio files'} disabled={disabled} onPress={() => { void catalog.scanDevice(); }} trailing={<Ionicons name={catalog.permission ? 'refresh-outline' : 'add-outline'} size={22} color={c.accent} />} />
        {divider}
        <SettingsRow icon="folder-open-outline" title="Add local folder" subtitle="Device storage or an accessible SD card" disabled={disabled} onPress={() => { void catalog.run(() => library!.addFolder()); }} trailing={<Ionicons name="add-outline" size={22} color={c.accent} />} />
        {!catalog.permission && <>{divider}<SettingsRow icon="shield-checkmark-outline" title="Android permissions" onPress={() => { void Linking.openSettings(); }} disabled={!catalog.available} /></>}
      </View>
      <View style={{ marginTop: 12, gap: 12 }}>{catalog.sources.map(source => <View key={source.id} style={[surface, { paddingHorizontal: 14, paddingVertical: 8 }]}>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Ionicons name={source.kind === 'device' ? 'phone-portrait-outline' : 'folder-outline'} size={22} color={c.accent} /><View style={{ flex: 1, gap: 4 }}><Text style={{ color: c.foreground, fontSize: 15, fontWeight: '600' }}>{source.name}</Text><Text style={{ color: source.access === 'available' ? c.muted : c.danger, fontSize: 12 }}>{source.enabled ? accessLabels[source.access] : 'Disabled'}</Text></View>
          <Switch accessibilityLabel={`Enable ${source.name}`} value={source.enabled} disabled={disabled} onValueChange={value => { void catalog.run(() => library!.setSourceEnabled(source.id, value)); }} trackColor={{ true: c.accent, false: c.border }} /></View>
        {source.error && <Text style={{ color: c.danger, fontSize: 12, marginVertical: 8 }}>{source.error}</Text>}
        <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}><Text style={{ flex: 1, color: c.muted, fontSize: 11 }}>{source.lastCompleted ? `Scanned ${new Date(source.lastCompleted).toLocaleDateString()}` : 'Not yet scanned'}</Text>
          <IconButton icon="filter-outline" label={`Folder rules for ${source.name}`} disabled={disabled} onPress={() => setRuleScope(source)} />
          {source.kind === 'folder' && source.access !== 'available' && <IconButton icon="key-outline" label={`Renew access to ${source.name}`} disabled={disabled} onPress={() => { void catalog.run(() => library!.addFolder()); }} />}
          <IconButton icon="trash-outline" label={`Remove source ${source.name}`} disabled={disabled} danger onPress={() => Alert.alert('Remove this source?', 'Audio files stay on your device. You can add the source again later.', [{ text: 'Keep', style: 'cancel' }, { text: 'Remove', onPress: () => { void catalog.run(() => library!.removeSource(source.id)); } }])} />
        </View>
      </View>)}</View>
      {!catalog.sources.length && <Text style={{ color: c.muted, fontSize: 12, paddingTop: 12 }}>Add a source to begin. Your original files stay untouched.</Text>}
      {section('Appearance')}
      <View style={[surface, { flexDirection: 'row', padding: 6 }]}>{(['system', 'light', 'dark'] as const).map(value => <Pressable key={value} accessibilityRole="button" accessibilityState={{ selected: preference === value }} onPress={() => setPreference(value)} style={({ pressed }) => ({ flex: 1, alignItems: 'center', paddingVertical: 12, borderRadius: 12, gap: 7, backgroundColor: preference === value ? c.accentSoft : 'transparent', opacity: pressed ? 0.6 : 1 })}><Ionicons name={value === 'system' ? 'phone-portrait-outline' : value === 'light' ? 'sunny-outline' : 'moon-outline'} size={20} color={preference === value ? c.accent : c.muted} /><Text style={{ color: preference === value ? c.accent : c.secondary, fontSize: 12, textTransform: 'capitalize' }}>{value}</Text></Pressable>)}</View>
    </ScrollView>
    {ruleScope !== undefined && <FolderRules source={ruleScope} onClose={() => setRuleScope(undefined)} />}
  </SafeAreaView>;
}
