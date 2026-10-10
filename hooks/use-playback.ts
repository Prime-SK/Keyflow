import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';

import { acceptSnapshot, errorMessage } from '@/lib/playback';
import { EMPTY_PLAYBACK_STATE, playback, type PlaybackState } from '@/modules/keyflow-playback/src';

type Operation = 'choosing' | 'opening' | 'updating' | null;

export function usePlayback() {
  const [state, setState] = useState<PlaybackState>(EMPTY_PLAYBACK_STATE);
  const [operation, setOperation] = useState<Operation>(null);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(false);
  const busy = useRef(false);
  const snapshot = useCallback((incoming: PlaybackState) => {
    if (mounted.current) setState(current => acceptSnapshot(current, incoming));
  }, []);

  const refresh = useCallback(async () => {
    if (!playback) return;
    try { snapshot(await playback.getState()); }
    catch (failure) { if (mounted.current) setError(errorMessage(failure)); }
  }, [snapshot]);

  useEffect(() => {
    mounted.current = true;
    if (!playback) return () => { mounted.current = false; };
    const stateSubscription = playback.addListener('onPlaybackState', snapshot);
    const errorSubscription = playback.addListener('onPlaybackError', failure => {
      if (mounted.current) setError(failure.message);
    });
    const appSubscription = AppState.addEventListener('change', status => {
      if (status === 'active') void refresh();
    });
    void refresh();
    return () => {
      mounted.current = false;
      stateSubscription.remove();
      errorSubscription.remove();
      appSubscription.remove();
    };
  }, [refresh, snapshot]);

  const command = useCallback(async (action: () => Promise<PlaybackState>) => {
    if (!playback || busy.current) return;
    busy.current = true;
    setOperation('updating');
    setError(null);
    try { snapshot(await action()); }
    catch (failure) { if (mounted.current) setError(errorMessage(failure)); }
    finally {
      busy.current = false;
      if (mounted.current) setOperation(null);
    }
  }, [snapshot]);

  const chooseSong = useCallback(async () => {
    if (!playback || busy.current) return;
    busy.current = true;
    setOperation('choosing');
    setError(null);
    try {
      const selected = await playback.pickAudio();
      if (selected && mounted.current) {
        setOperation('opening');
        snapshot(await playback.openAudio(selected));
      }
    } catch (failure) {
      if (mounted.current) setError(errorMessage(failure));
    } finally {
      busy.current = false;
      if (mounted.current) setOperation(null);
    }
  }, [snapshot]);

  return {
    state, operation, error: error ?? state.error?.message ?? null,
    available: playback !== null, chooseSong,
    dismissError: () => setError(null),
    toggle: () => command(() => state.playWhenReady && state.status !== 'ended' && state.status !== 'error'
      ? playback!.pause() : playback!.play()),
    seek: (positionMs: number) => command(() => playback!.seekTo(positionMs)),
    setParameters: (speed: number, semitones: number, cents = 0) =>
      command(() => playback!.setPlaybackParameters(speed, semitones, cents)),
  };
}
