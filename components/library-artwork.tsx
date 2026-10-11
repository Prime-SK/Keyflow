import Ionicons from '@expo/vector-icons/Ionicons';
import { Image } from 'expo-image';
import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { library, type LibraryTrack } from '@/modules/keyflow-library/src';

type Job = { key: string; id: string; listeners: Set<(uri: string | null) => void> };
const cache = new Map<string, string | null>();
const jobs = new Map<string, Job>();
const queue: Job[] = [];
let active = 0;
function pump() {
  while (active < 2 && queue.length) {
    const job = queue.shift()!;
    if (!job.listeners.size) { jobs.delete(job.key); continue; }
    active++;
    void library!.getArtwork(job.id).then(uri => {
      cache.set(job.key, uri);
      if (cache.size > 300) cache.delete(cache.keys().next().value!);
      job.listeners.forEach(listener => listener(uri));
    }).catch(() => { /* Keep the artwork fallback when storage is unavailable. */ }).finally(() => {
      jobs.delete(job.key); active--; pump();
    });
  }
}
export function LibraryArtwork({ track }: { track: LibraryTrack }) {
  const { colors: c } = usePlayerTheme();
  const key = `${track.id}:${track.artworkVersion}`;
  const [result, setResult] = useState<{ key: string; uri: string | null } | null>(null);
  const uri = result?.key === key ? result.uri : track.artworkUri;
  useEffect(() => {
    if (!library || track.availability !== 'available') return;
    const listener = (value: string | null) => setResult({ key, uri: value });
    if (cache.has(key)) { listener(cache.get(key)!); return; }
    let job = jobs.get(key);
    if (!job) { job = { key, id: track.id, listeners: new Set() }; jobs.set(key, job); queue.push(job); }
    job.listeners.add(listener); pump();
    return () => { job.listeners.delete(listener); };
  }, [key, track.id, track.availability]);
  return uri ? <Image source={{ uri }} recyclingKey={key} style={{ width: 48, height: 48, borderRadius: 10 }} />
    : <View style={{ width: 48, height: 48, borderRadius: 10, backgroundColor: c.accentSoft, alignItems: 'center', justifyContent: 'center' }}>
      <Ionicons name="musical-note" size={22} color={c.accent} />
    </View>;
}
