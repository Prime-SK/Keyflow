import type { PlaybackState } from '../modules/keyflow-playback/src/types';

export function formatTime(milliseconds: number): string {
  if (!Number.isFinite(milliseconds) || milliseconds < 0) return '0:00';
  const seconds = Math.floor(milliseconds / 1000);
  const minutes = Math.floor(seconds / 60);
  return `${minutes}:${String(seconds % 60).padStart(2, '0')}`;
}

/** Events and async command responses can arrive out of order across the bridge. */
export function acceptSnapshot(current: PlaybackState, incoming: PlaybackState): PlaybackState {
  return incoming.revision >= current.revision ? incoming : current;
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error && error.message) return error.message;
  return 'Something went wrong. Please try again.';
}
