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

export { tintedBackground } from '@/lib/artwork-controls';
