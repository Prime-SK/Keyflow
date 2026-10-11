import { createContext, createElement, useContext, useMemo, useCallback, useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';

import { acceptSnapshot, errorMessage } from '@/lib/playback';
import { EMPTY_PLAYBACK_STATE, playback, type PlaybackState } from '@/modules/keyflow-playback/src';
import { library } from '@/modules/keyflow-library/src';

type Operation = 'choosing' | 'opening' | 'updating' | null;

function usePlaybackController() {
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
    if (!playback || busy.current) return false;
    busy.current = true;
    setOperation('updating');
    setError(null);
    try { snapshot(await action()); return true; }
    catch (failure) { if (mounted.current) setError(errorMessage(failure)); return false; }
    finally {
      busy.current = false;
      if (mounted.current) setOperation(null);
    }
  }, [snapshot]);

  const chooseSong = useCallback(async () => {
    if (!playback || busy.current) return false;
    busy.current = true;
    setOperation('choosing');
    setError(null);
    let opened = false;
    try {
      const selected = await playback.pickAudio();
      if (selected && mounted.current) {
        setOperation('opening');
        snapshot(await playback.openAudio(selected));
        opened = true;
      }
    } catch (failure) {
      if (mounted.current) setError(errorMessage(failure));
    } finally {
      busy.current = false;
      if (mounted.current) setOperation(null);
    }
    return opened;
  }, [snapshot]);

  const openLibraryTrack = useCallback((id: string) => command(async () => {
    if (!library) throw new Error('The music library requires the installed Android app.');
    await library.getArtwork(id);
    return playback!.openAudio(await library.resolveTrack(id));
  }), [command]);
  const toggle = useCallback(() => command(() => state.playWhenReady && state.status !== 'ended' && state.status !== 'error'
    ? playback!.pause() : playback!.play()), [command, state.playWhenReady, state.status]);
  const seek = useCallback((positionMs: number) => command(() => playback!.seekTo(positionMs)), [command]);
  const setParameters = useCallback((speed: number, semitones: number, cents = 0) =>
    command(() => playback!.setPlaybackParameters(speed, semitones, cents)), [command]);
  const dismissError = useCallback(() => setError(null), []);

  return {
    state, operation, error: error ?? state.error?.message ?? null,
    available: playback !== null, chooseSong,
    openLibraryTrack, dismissError, toggle, seek, setParameters,
  };
}

type PlaybackController = ReturnType<typeof usePlaybackController>;
type PlaybackActions = Omit<PlaybackController, 'state'>;
const PlaybackContext = createContext<PlaybackController | null>(null);
const ActionsContext = createContext<PlaybackActions | null>(null);

export function PlaybackProvider({ children }: PropsWithChildren) {
  const controller = usePlaybackController();
  const { operation, error, available, chooseSong, openLibraryTrack, dismissError, toggle, seek, setParameters } = controller;
  const actions = useMemo(() => ({ operation, error, available, chooseSong, openLibraryTrack, dismissError, toggle, seek, setParameters }),
    [operation, error, available, chooseSong, openLibraryTrack, dismissError, toggle, seek, setParameters]);
  return createElement(PlaybackContext.Provider, { value: controller }, createElement(ActionsContext.Provider, { value: actions }, children));
}
export function usePlayback() {
  const controller = useContext(PlaybackContext);
  if (!controller) throw new Error('PlaybackProvider is required.');
  return controller;
}
export function usePlaybackActions() {
  const actions = useContext(ActionsContext);
  if (!actions) throw new Error('PlaybackProvider is required.');
  return actions;
}
