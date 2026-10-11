import { useEffect, useState } from 'react';
import { playback } from '@/modules/keyflow-playback/src';

export function useArtworkTint(uri: string | null | undefined) {
  const [result, setResult] = useState<{ uri: string; color: string | null } | null>(null);
  useEffect(() => {
    let active = true;
    if (uri && playback) void playback.getArtworkTint(uri).then(color => {
      if (active) setResult({ uri, color });
    }).catch(() => { if (active) setResult({ uri, color: null }); });
    return () => { active = false; };
  }, [uri]);
  return result && result.uri === uri ? result.color : null;
}

// A restrained wash leaves foreground/accent contrast controlled by the theme.
export function tintedBackground(base: string, tint: string | null, amount: number) {
  if (!tint) return base;
  const mix = (index: number) => Math.round(parseInt(base.slice(index, index + 2), 16) * (1 - amount)
    + parseInt(tint.slice(index, index + 2), 16) * amount).toString(16).padStart(2, '0');
  return `#${mix(1)}${mix(3)}${mix(5)}`;
}
