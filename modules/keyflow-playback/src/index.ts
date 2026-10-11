import { NativeModule, requireOptionalNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';

import type { LocalAudio, PlaybackEvents, PlaybackState, QueuePage, TransitionSettings, SoundState, SoundSettings } from './types';

declare class KeyflowPlaybackModule extends NativeModule<PlaybackEvents> {
  getSoundState(): Promise<SoundState>;
  updateSoundSettings(values: Partial<SoundSettings>): Promise<SoundState>;
  selectSoundPreset(id: string): Promise<SoundState>;
  saveSoundPreset(name: string): Promise<SoundState>;
  editSoundPreset(id: string, action: 'update' | 'rename' | 'delete', name: string | null): Promise<SoundState>;
  getTransitionSettings(): Promise<TransitionSettings>;
  updateTransitionSettings(values: Partial<TransitionSettings>): Promise<TransitionSettings>;
  pickAudio(): Promise<LocalAudio | null>;
  openAudio(audio: LocalAudio): Promise<PlaybackState>;
  getState(): Promise<PlaybackState>;
  getArtworkTint(uri: string): Promise<string | null>;
  getArtworkAppearance(uri: string | null, fallbackColor: string): Promise<{ color: string | null; glowUri: string }>;
  openLibraryQueue(query: Record<string, unknown>, selected: string): Promise<PlaybackState>;
  openCollection(kind: 'playlist' | 'favorites' | 'recent', id: string | null, selected: string): Promise<PlaybackState>;
  saveQueueAsPlaylist(name: string): Promise<string>;
  next(): Promise<PlaybackState>;
  previous(): Promise<PlaybackState>;
  setShuffle(enabled: boolean): Promise<PlaybackState>;
  setSleepTimer(mode: 'off' | 'timed' | 'end', durationMs: number): Promise<PlaybackState>;
  setRepeat(mode: 'off' | 'all' | 'one'): Promise<PlaybackState>;
  queryQueue(offset: number, limit: number): Promise<QueuePage>;
  jumpQueueEntry(id: string): Promise<PlaybackState>;
  moveQueueEntry(id: string, delta: number): Promise<PlaybackState>;
  playNextQueueEntry(id: string): Promise<PlaybackState>;
  removeQueueEntry(id: string): Promise<PlaybackState>;
  clearUpcoming(): Promise<PlaybackState>;
  insertLibraryTrack(id: string, playNext: boolean): Promise<PlaybackState>;
  play(): Promise<PlaybackState>;
  pause(): Promise<PlaybackState>;
  seekTo(positionMs: number): Promise<PlaybackState>;
  setPlaybackParameters(speed: number, semitones: number, cents: number): Promise<PlaybackState>;
  getThemePreference(): Promise<'system' | 'light' | 'dark'>;
  setThemePreference(preference: 'system' | 'light' | 'dark'): Promise<void>;
}

// Expo Go/web can render the interface, but cannot pretend to provide native playback.
export const playback = Platform.OS === 'android'
  ? requireOptionalNativeModule<KeyflowPlaybackModule>('KeyflowPlayback')
  : null;

export * from './types';
