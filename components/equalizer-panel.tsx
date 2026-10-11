import Ionicons from '@expo/vector-icons/Ionicons';
import Slider from '@react-native-community/slider';
import { useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, ScrollView, Switch, Text, TextInput, View } from 'react-native';
import { IconButton } from '@/components/library-controls';
import { EqualizerBand } from '@/components/equalizer-band';
import { ConfirmationDialog } from '@/components/confirmation-dialog';
import { Gesture } from 'react-native-gesture-handler';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { useReducedMotion } from '@/hooks/use-reduced-motion';
import { errorMessage } from '@/lib/playback';
import { playback, type SoundSettings, type SoundState, type SoundValues, type SoundPreset } from '@/modules/keyflow-playback/src';

const bands = ['31', '62', '125', '250', '500', '1k', '2k', '4k', '8k', '16k'];
const flat = { bands: Array(10).fill(0) as number[], bass: 0, preamp: 0 };
const same = (a: SoundValues, b: SoundValues) => a.bass === b.bass && a.preamp === b.preamp && a.bands.every((v, i) => v === b.bands[i]);
const db = (value: number) => `${value > 0 ? '+' : ''}${value.toFixed(1)} dB`;

export function EqualizerPanel({ scrollGesture, onDragging }: { scrollGesture: ReturnType<typeof Gesture.Native>; onDragging: (dragging: boolean) => void }) {
  const { colors: c } = usePlayerTheme();
  const motion = useReducedMotion();
  const [state, setState] = useState<SoundState | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<'save' | 'rename' | null>(null);
  const [name, setName] = useState('');
  const [deletePreset, setDeletePreset] = useState<SoundPreset | null>(null);
  const mounted = useRef(true); const writes = useRef(Promise.resolve()); const revision = useRef(0);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pending = useRef<Partial<SoundSettings> | null>(null);
  const latestSettings = useRef<SoundSettings | null>(null);
  useEffect(() => {
    mounted.current = true;
    void playback?.getSoundState().then(value => {
      if (mounted.current) { latestSettings.current = value.settings; setState(value); setSelected(value.presets.find(p => same(p.values, value.settings))?.id ?? null); }
    }).catch(value => { if (mounted.current) setError(errorMessage(value)); });
    return () => { mounted.current = false; if (timer.current) clearTimeout(timer.current); flush(); };
    // The native queue and pending-write refs intentionally outlive rendering.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  function enqueue(action: () => Promise<SoundState>, generation = ++revision.current) {
    if (mounted.current) setError(null);
    writes.current = writes.current.then(async () => {
      try { const value = await action(); if (mounted.current && generation === revision.current) { latestSettings.current = value.settings; setState(value); } }
      catch (value) { if (mounted.current) { setError(errorMessage(value)); void playback?.getSoundState().then(s => { if (mounted.current && generation === revision.current) { latestSettings.current = s.settings; setState(s); } }).catch(() => {}); } }
    });
  }
  function flush() {
    if (timer.current) clearTimeout(timer.current); timer.current = null;
    const values = pending.current; pending.current = null;
    if (values) enqueue(() => playback!.updateSoundSettings(values), revision.current);
  }
  function update(values: Partial<SoundSettings>, immediate = false) {
    ++revision.current;
    const next = latestSettings.current ? { ...latestSettings.current, ...values } : null;
    latestSettings.current = next;
    setState(previous => previous && next ? { ...previous, settings: next } : previous);
    pending.current = { ...pending.current, ...values };
    if (immediate) flush(); else if (!timer.current) timer.current = setTimeout(flush, 70);
  }
  function updateBand(index: number, gain: number, immediate = false) {
    if (!latestSettings.current) return;
    const values = [...latestSettings.current.bands]; values[index] = gain;
    update({ bands: values }, immediate);
  }
  function action(run: () => Promise<SoundState>) { flush(); enqueue(run); }
  const preset = state?.presets.find(p => p.id === selected);
  const dirty = !!state && !!preset && !same(state.settings, preset.values);
  const text = { color: c.foreground, fontSize: 16, fontWeight: '600' as const };
  const detail = { color: c.secondary, fontSize: 13, lineHeight: 20 };
  const card = { backgroundColor: c.surface, padding: 18, borderRadius: 20, gap: 14 };
  function gainSlider(kind: 'bass' | 'preamp', title: string, subtitle: string, min: number) {
    if (!state) return null;
    const value = state.settings[kind];
    return <View style={card}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}><View style={{ flex: 1 }}><Text style={text}>{title}</Text><Text style={detail}>{subtitle}</Text></View><Text style={{ ...text, color: c.accent, fontVariant: ['tabular-nums'] }}>{db(value)}</Text><IconButton icon="refresh-outline" label={`Reset ${title}`} onPress={() => update({ [kind]: 0 }, true)} /></View>
      <Slider minimumValue={min} maximumValue={12} step={.5} value={value} onValueChange={v => update({ [kind]: v })} onSlidingComplete={v => update({ [kind]: v }, true)} minimumTrackTintColor={c.accent} maximumTrackTintColor={c.border} thumbTintColor={c.accent} accessibilityLabel={title} accessibilityValue={{ text: db(value) }} />
    </View>;
  }
  if (!playback) return <Text style={detail}>Equalizer requires the installed Android app.</Text>;
  return <>
    {error && <Text accessibilityRole="alert" style={{ color: c.danger }}>{error}</Text>}
    {!state ? <ActivityIndicator color={c.accent} /> : <>
      <View style={card}><View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Ionicons name="options-outline" size={24} color={c.accent} /><View style={{ flex: 1 }}><Text style={text}>Sound processing</Text><Text style={detail}>{state.settings.enabled ? 'Equalizer, bass and preamp active' : 'Off · original sound'}</Text></View><Switch value={state.settings.enabled} onValueChange={enabled => update({ enabled }, true)} accessibilityLabel="Sound processing" trackColor={{ true: c.accent, false: c.border }} /></View></View>
      <View style={card}>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}><View style={{ flex: 1 }}><Text style={text}>Presets</Text><Text style={detail}>{preset ? `${preset.name}${dirty ? ' · edited' : ''}` : 'Custom sound'}</Text></View><IconButton icon="add-outline" label="Save as new preset" onPress={() => { setName(''); setDialog('save'); }} /></View>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={{ gap: 8 }}>
          {state.presets.map(p => <Pressable key={p.id} accessibilityRole="radio" accessibilityState={{ checked: p.id === selected }} onPress={() => { setSelected(p.id); action(() => playback!.selectSoundPreset(p.id)); }} style={({ pressed }) => ({ paddingHorizontal: 16, paddingVertical: 13, borderRadius: 14, backgroundColor: selected === p.id ? c.accentSoft : c.background, opacity: pressed ? .6 : 1 })}><Text style={{ ...text, fontSize: 14, color: selected === p.id ? c.accent : c.secondary }}>{p.name}</Text></Pressable>)}
        </ScrollView>
        {preset && !preset.builtIn && <View style={{ flexDirection: 'row', alignItems: 'center', gap: 8 }}>
          <Pressable accessibilityRole="button" disabled={!dirty} onPress={() => action(() => playback!.editSoundPreset(preset.id, 'update', null))} style={{ minHeight: 44, flexDirection: 'row', gap: 8, alignItems: 'center', flex: 1, opacity: dirty ? 1 : .4 }}><Ionicons name="save-outline" size={20} color={c.accent} /><Text style={{ ...text, color: c.accent, fontSize: 14 }}>Update preset</Text></Pressable>
          <IconButton icon="pencil-outline" label="Rename preset" onPress={() => { setName(preset.name); setDialog('rename'); }} />
          <IconButton icon="trash-outline" label="Delete preset" danger onPress={() => setDeletePreset(preset)} />
        </View>}
        <Text style={detail}>Edits apply live. Save as new or Update to keep them in a preset. Selecting a preset keeps the processing switch as it is.</Text>
      </View>
      <View style={{ ...card, paddingHorizontal: 0 }}>
        <View style={{ paddingHorizontal: 18, flexDirection: 'row', alignItems: 'center' }}><Text style={{ ...text, flex: 1 }}>10-band equalizer</Text><IconButton icon="refresh-outline" label="Reset all sound settings" onPress={() => update(flat, true)} /></View>
        <View style={{ flexDirection: 'row', paddingHorizontal: 8 }}>
          {bands.map((label, i) => <EqualizerBand key={label} label={label} value={state.settings.bands[i]} scrollGesture={scrollGesture} onDragging={onDragging}
            onChange={v => updateBand(i, v)} onComplete={v => updateBand(i, v, true)} />)}
        </View>
        <Text style={{ ...detail, textAlign: 'center', fontSize: 11 }}>Frequency (Hz) · gain −12 to +12 dB</Text>
      </View>
      {gainSlider('bass', 'Bass', '100 Hz low shelf', -12)}
      {gainSlider('preamp', 'Preamp', 'Overall level before headroom', -24)}
      <View style={card}><Text style={text}>Output headroom</Text><Text style={{ ...text, color: c.accent }}>{(state.headroomDb + state.overlapHeadroomDb).toFixed(1)} dB reduction</Text><Text style={detail}>{state.headroomDb.toFixed(1)} dB for sound boosts · {state.overlapHeadroomDb.toFixed(1)} dB for equal-power fades. Headroom may lower perceived volume; it does not normalize songs.</Text></View>
    </>}
    <ConfirmationDialog destructive visible={deletePreset !== null} title="Delete preset?" message={deletePreset?.name ?? ''} confirmLabel="Delete" onClose={() => setDeletePreset(null)} onConfirm={() => { if (deletePreset) { action(() => playback!.editSoundPreset(deletePreset.id, 'delete', null)); setSelected(current => current === deletePreset.id ? null : current); } }} />
    <Modal visible={dialog !== null} transparent animationType={motion.enabled ? 'none' : 'fade'} onRequestClose={() => setDialog(null)}>
      <Pressable onPress={() => setDialog(null)} style={{ flex: 1, backgroundColor: '#0008', justifyContent: 'center', padding: 24 }}>
        <Pressable accessibilityViewIsModal onPress={event => event.stopPropagation()} style={card}><Text style={text}>{dialog === 'save' ? 'Save as new preset' : 'Rename preset'}</Text><TextInput autoFocus value={name} onChangeText={setName} maxLength={80} placeholder="Preset name" placeholderTextColor={c.muted} accessibilityLabel="Preset name" style={{ color: c.foreground, backgroundColor: c.background, borderRadius: 12, padding: 14, fontSize: 16 }} />
          <View style={{ flexDirection: 'row', justifyContent: 'flex-end', gap: 20 }}><Pressable accessibilityRole="button" onPress={() => setDialog(null)} style={{ padding: 12 }}><Text style={detail}>Cancel</Text></Pressable><Pressable accessibilityRole="button" disabled={!name.trim()} style={{ padding: 12, opacity: name.trim() ? 1 : .4 }} onPress={() => { if (dialog === 'save') action(async () => { const value = await playback!.saveSoundPreset(name.trim()); if (mounted.current) setSelected(value.presets.at(-1)?.id ?? null); return value; }); else if (preset) action(() => playback!.editSoundPreset(preset.id, 'rename', name.trim())); setDialog(null); }}><Text style={{ ...text, color: c.accent }}>{dialog === 'save' ? 'Save' : 'Rename'}</Text></Pressable></View>
        </Pressable>
      </Pressable>
    </Modal>
  </>;
}
