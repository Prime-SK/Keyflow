import Ionicons from '@expo/vector-icons/Ionicons';
import Slider from '@react-native-community/slider';
import { useRouter } from 'expo-router';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { ActivityIndicator, Pressable, ScrollView, Switch, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { IconButton } from '@/components/library-controls';
import { EqualizerPanel } from '@/components/equalizer-panel';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { playback, type TransitionSettings } from '@/modules/keyflow-playback/src';
import { errorMessage } from '@/lib/playback';

export default function AudioScreen() {
  const router = useRouter(); const { colors: c } = usePlayerTheme();
  const [settings, setSettings] = useState<TransitionSettings | null>(null);
  const [tab, setTab] = useState<'transitions' | 'equalizer'>('transitions');
  const [bandDragging, setBandDragging] = useState(false);
  const scrollGesture = useMemo(() => Gesture.Native(), []);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState<{ automaticMs?: number; manualMs?: number }>({});
  const mounted = useRef(true); const writes = useRef(Promise.resolve());
  useEffect(() => {
    mounted.current = true;
    if (playback) void playback.getTransitionSettings().then(value => { if (mounted.current) setSettings(value); }).catch(value => { if (mounted.current) setError(errorMessage(value)); });
    return () => { mounted.current = false; };
  }, []);
  function update(values: Partial<TransitionSettings>) {
    setError(null);
    // Serialize edits so quick toggles cannot overwrite a newer native result.
    writes.current = writes.current.then(async () => {
      try { const value = await playback!.updateTransitionSettings(values); if (mounted.current) { setSettings(value); setDraft({}); } }
      catch (value) { if (mounted.current) setError(errorMessage(value)); }
    });
  }
  const text = { color: c.foreground, fontSize: 17, fontWeight: '600' as const };
  const detail = { color: c.secondary, fontSize: 13, lineHeight: 20 };
  const card = { backgroundColor: c.surface, padding: 20, borderRadius: 20, gap: 16 };
  function fade(kind: 'automatic' | 'manual', title: string, description: string) {
    if (!settings) return null;
    const durationKey = kind === 'automatic' ? 'automaticMs' : 'manualMs';
    const curveKey = kind === 'automatic' ? 'automaticCurve' : 'manualCurve';
    const duration = draft[durationKey] ?? settings[durationKey]; const enabled = settings[kind];
    return <View style={card}>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
        <Ionicons name={kind === 'automatic' ? 'layers-outline' : 'swap-horizontal-outline'} size={24} color={c.accent} />
        <View style={{ flex: 1, gap: 4 }}><Text style={text}>{title}</Text><Text style={detail}>{description}</Text></View>
        <Switch accessibilityLabel={title} value={enabled} onValueChange={value => update({ [kind]: value })} trackColor={{ true: c.accent, false: c.border }} />
      </View>
      <View style={{ flexDirection: 'row', justifyContent: 'space-between' }}><Text style={detail}>Duration</Text><Text style={{ ...text, fontVariant: ['tabular-nums'] }}>{duration < 1000 && kind === 'manual' ? `${Math.round(duration)} ms` : `${(duration / 1000).toFixed(1)} s`}</Text></View>
      <Slider minimumValue={0} maximumValue={kind === 'automatic' ? 12000 : 2000} step={kind === 'automatic' ? 100 : 50} value={duration}
        onValueChange={value => setDraft(previous => ({ ...previous, [durationKey]: value }))} onSlidingComplete={value => update({ [durationKey]: value })}
        minimumTrackTintColor={c.accent} maximumTrackTintColor={c.border} thumbTintColor={c.accent} accessibilityLabel={`${title} duration`}
        accessibilityValue={{ text: `${duration / 1000} seconds` }} />
      <View style={{ flexDirection: 'row', gap: 8 }}>{(['linear', 'equal-power'] as const).map(value => <Pressable key={value} accessibilityRole="radio" accessibilityState={{ checked: settings[curveKey] === value }}
        onPress={() => update({ [curveKey]: value })} style={({ pressed }) => ({ flex: 1, minHeight: 48, padding: 12, alignItems: 'center', justifyContent: 'center', borderRadius: 12, opacity: pressed ? .65 : 1, backgroundColor: settings[curveKey] === value ? c.accentSoft : c.background })}>
        <Text style={{ color: settings[curveKey] === value ? c.accent : c.secondary, fontWeight: '600' }}>{value === 'linear' ? 'Linear' : 'Equal-power'}</Text>
      </Pressable>)}</View>
      <Text style={detail}>{kind === 'automatic' ? 'Overlaps queued songs, including repeat. Short songs use a shorter fade.' : 'Fades song changes, restarts, and play/pause.'}</Text>
    </View>;
  }
  return <SafeAreaView style={{ flex: 1, backgroundColor: c.background }}>
    <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 12, gap: 6 }}><IconButton icon="chevron-back" label="Back" onPress={() => router.back()} /><Text style={{ ...text, fontSize: 22 }}>Audio</Text></View>
    <View style={{ flexDirection: 'row', marginHorizontal: 20, marginTop: 12, padding: 4, gap: 4, backgroundColor: c.surface, borderRadius: 16 }}>{(['transitions', 'equalizer'] as const).map(value => <Pressable key={value} accessibilityRole="tab" accessibilityState={{ selected: value === tab }} onPress={() => setTab(value)} style={{ flex: 1, padding: 14, alignItems: 'center', borderRadius: 12, backgroundColor: value === tab ? c.accentSoft : 'transparent' }}><Text style={{ ...text, fontSize: 15, color: value === tab ? c.accent : c.secondary }}>{value === 'transitions' ? 'Transitions' : 'Equalizer'}</Text></Pressable>)}</View>
    <GestureDetector gesture={scrollGesture}><ScrollView scrollEnabled={!bandDragging} contentContainerStyle={{ padding: 20, paddingBottom: 120, gap: 16 }}>
      {tab === 'equalizer' ? <EqualizerPanel scrollGesture={scrollGesture} onDragging={setBandDragging} /> : <>
      <Text style={{ color: c.muted, fontSize: 12, letterSpacing: 1.5, fontWeight: '700' }}>TRANSITIONS</Text>
      {error && <Text accessibilityRole="alert" style={{ color: c.danger }}>{error}</Text>}
      {!playback ? <Text style={detail}>Audio controls require the installed Android app.</Text> : !settings ? <ActivityIndicator color={c.accent} /> : <>
        {fade('automatic', 'Automatic crossfade', 'Blend the end into the next song')}
        {fade('manual', 'Manual-switch fade', 'Soften song changes and play/pause')}
        <View style={card}><View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}><Ionicons name="infinite-outline" size={24} color={c.accent} /><View style={{ flex: 1, gap: 4 }}><Text style={text}>Gapless</Text><Text style={detail}>Keep album tracks connected</Text></View><Switch accessibilityLabel="Gapless playback" value={settings.gapless} onValueChange={value => update({ gapless: value })} trackColor={{ true: c.accent, false: c.border }} /></View><Text style={detail}>Enabling gapless turns automatic crossfade off. Manual-switch fade stays independent.</Text></View>
        <Text style={detail}>Durations and curves are remembered while switches are off. Equal-power fades reserve 3 dB of output headroom.</Text>
      </>}
      </>}
    </ScrollView></GestureDetector>
  </SafeAreaView>;
}
