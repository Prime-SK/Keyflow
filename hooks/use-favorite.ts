import { useCallback, useEffect, useRef, useState } from 'react';
import { library } from '@/modules/keyflow-library/src';
import { type LocalAudio } from '@/modules/keyflow-playback/src';
import { errorMessage } from '@/lib/playback';

export function useFavorite(audio: LocalAudio | null, trackId: string | null) {
  const [favorite, setFavorite] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState<string | null>(null);
  // Metadata refreshes need not reset the favorite button every playback tick.
  const key = audio?.uri; const snapshot = audio;
  const generation = useRef(0); const pending = useRef(false);
  useEffect(() => {
    generation.current++; let active = true; setFavorite(false); setError(null);
    async function refresh() { if (!library || !key) return; try { const value = await library.isFavorite({ uri: key }, trackId); if (active) setFavorite(value); } catch (failure) { if (active) setError(errorMessage(failure)); } }
    void refresh(); const subscription = library?.addListener('onCollectionsChanged', () => { void refresh(); });
    return () => { active = false; generation.current++; subscription?.remove(); };
  }, [key, trackId]);
  const toggle = useCallback(async () => {
    if (!library || !snapshot || pending.current) return; pending.current = true; const token = generation.current; setBusy(true); setError(null);
    try { await library.setFavorite(snapshot, trackId, !favorite); if (token === generation.current) setFavorite(!favorite); } catch (failure) { if (token === generation.current) setError(errorMessage(failure)); } finally { pending.current = false; setBusy(false); }
  }, [snapshot, trackId, favorite]);
  return { favorite, busy, error, toggle };
}
