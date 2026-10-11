import { useEffect, useState } from 'react';
import { playback } from '@/modules/keyflow-playback/src';

type Appearance = { color: string | null; glowUri: string };

export function useArtworkAppearance(uri: string | null | undefined, fallback: string) {
  const key = `${uri ?? ''}|${fallback}`;
  const [result, setResult] = useState<{ key: string; appearance: Appearance } | null>(null);
  useEffect(() => {
    let active = true;
    if (playback) void playback.getArtworkAppearance(uri ?? null, fallback).then(appearance => {
      if (active) setResult({ key, appearance });
    }).catch(() => {
      if (!active) return;
      // Unreadable artwork can still use the native theme glow.
      void playback?.getArtworkAppearance(null, fallback).then(appearance => {
        if (active) setResult({ key, appearance });
      }).catch(() => { if (active) setResult(null); });
    });
    return () => { active = false; };
  }, [uri, fallback, key]);
  const ready = result?.key === key;
  return { ready, color: ready ? result.appearance.color : null, glowUri: ready ? result.appearance.glowUri : undefined };
}
