import { useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { errorMessage } from '@/lib/playback';

export function PlaylistNameDialog({ visible, title, initial = '', onClose, onSave }: {
  visible: boolean; title: string; initial?: string; onClose: () => void; onSave: (name: string) => Promise<unknown>;
}) {
  const { colors: c } = usePlayerTheme(); const motion = useReducedMotion();
  const [name, setName] = useState(initial); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);
  useEffect(() => { if (visible) { setName(initial); setError(null); } }, [visible, initial]);
  async function save() {
    if (busy || !name.trim()) return; setBusy(true); setError(null);
    try { await onSave(name.trim()); onClose(); } catch (failure) { setError(errorMessage(failure)); } finally { setBusy(false); }
  }
  return <Modal visible={visible} transparent animationType={motion.enabled ? 'none' : 'fade'} onRequestClose={() => { if (!busy) onClose(); }}>
    <Pressable style={s.scrim} onPress={() => { if (!busy) onClose(); }}><Pressable accessibilityViewIsModal onPress={event => event.stopPropagation()} style={[s.card, { backgroundColor: c.surface }]}>
      <Text style={{ color: c.foreground, fontSize: 21, fontWeight: '700' }}>{title}</Text>
      <TextInput autoFocus value={name} onChangeText={setName} maxLength={120} editable={!busy} placeholder="Playlist name" placeholderTextColor={c.muted} accessibilityLabel="Playlist name" returnKeyType="done" onSubmitEditing={() => { void save(); }} style={[s.input, { color: c.foreground, borderColor: c.border }]} />
      {!!error && <Text accessibilityRole="alert" style={{ color: c.danger }}>{error}</Text>}
      <View style={s.buttons}><Pressable disabled={busy} onPress={onClose} style={s.button} accessibilityRole="button"><Text style={{ color: c.secondary }}>Cancel</Text></Pressable><Pressable disabled={busy || !name.trim()} onPress={() => { void save(); }} style={s.button} accessibilityRole="button">{busy ? <ActivityIndicator color={c.accent} /> : <Text style={{ color: name.trim() ? c.accent : c.muted, fontWeight: '700' }}>Save</Text>}</Pressable></View>
    </Pressable></Pressable>
  </Modal>;
}
const s = StyleSheet.create({ scrim: { flex: 1, justifyContent: 'center', padding: 24, backgroundColor: '#00000070' }, card: { padding: 24, borderRadius: 20, gap: 16 }, input: { fontSize: 17, paddingVertical: 12, borderBottomWidth: 1 }, buttons: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8 }, button: { minHeight: 48, minWidth: 64, alignItems: 'center', justifyContent: 'center' } });
