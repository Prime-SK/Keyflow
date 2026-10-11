export type PlaybackStatus = 'empty' | 'loading' | 'playing' | 'paused' | 'ended' | 'error';

export interface LocalAudio {
  trackId?: string | null;
  uri: string;
  fileName: string;
  title: string;
  artist: string | null;
  durationMs: number;
  artworkUri: string | null;
  mimeType: string | null;
  persistedPermission: boolean;
}

export interface PlaybackError {
  code: string;
  message: string;
}
export interface QueueEntry {
  id: string; trackId: string | null; title: string; artist: string | null; index: number; priority: boolean;
}
export interface QueuePage { revision: number; total: number; hasMore: boolean; items: QueueEntry[] }

/** All playback values come from Android's media controller, never a JS timer. */
export interface PlaybackState {
  sleepTimer: { mode: 'off' | 'timed' | 'end'; remainingMs: number };
  revision: number;
  currentEntryId: string | null;
  queueCount: number;
  queueIndex: number;
  queueRevision: number;
  shuffle: boolean;
  repeat: 'off' | 'all' | 'one';
  canNext: boolean;
  canPrevious: boolean;
  notice: string | null;
  track: LocalAudio | null;
  status: PlaybackStatus;
  isPlaying: boolean;
  playWhenReady: boolean;
  positionMs: number;
  durationMs: number;
  bufferedPositionMs: number;
  canSeek: boolean;
  speed: number;
  pitch: number;
  error: PlaybackError | null;
}

export type PlaybackEvents = {
  onPlaybackState: (state: PlaybackState) => void;
  onPlaybackError: (error: PlaybackError) => void;
};

export const EMPTY_PLAYBACK_STATE: PlaybackState = {
  currentEntryId: null, queueCount: 0, queueIndex: 0, queueRevision: 0, shuffle: false, repeat: 'off', canNext: false, canPrevious: false, notice: null,
  sleepTimer: { mode: 'off', remainingMs: 0 },
  revision: 0, track: null, status: 'empty', isPlaying: false, playWhenReady: false,
  positionMs: 0, durationMs: 0, bufferedPositionMs: 0, canSeek: false,
  speed: 1, pitch: 1, error: null,
};
