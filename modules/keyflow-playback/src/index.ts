import { NativeModule, requireOptionalNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';

import type { LocalAudio, PlaybackEvents, PlaybackState } from './types';

declare class KeyflowPlaybackModule extends NativeModule<PlaybackEvents> {
  pickAudio(): Promise<LocalAudio | null>;
  openAudio(audio: LocalAudio): Promise<PlaybackState>;
  getState(): Promise<PlaybackState>;
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
