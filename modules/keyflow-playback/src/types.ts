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
export interface SoundValues { bands: number[]; bass: number; preamp: number }
export interface SoundSettings extends SoundValues { enabled: boolean }
export interface SoundPreset { id: string; name: string; builtIn: boolean; values: SoundValues }
export interface SoundState { settings: SoundSettings; presets: SoundPreset[]; headroomDb: number; overlapHeadroomDb: number }

export interface TransitionSettings {
  automatic: boolean; automaticMs: number; automaticCurve: 'linear' | 'equal-power';
  manual: boolean; manualMs: number; manualCurve: 'linear' | 'equal-power'; gapless: boolean;
}

/** All playback values come from Android's media controller, never a JS timer. */
export interface PlaybackState {
  transition: { incomingEntryId: string | null; outgoingEntryId: string | null; progress: number; kind: 'automatic' | 'manual' | null };
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
  transition: { incomingEntryId: null, outgoingEntryId: null, progress: 0, kind: null },
  currentEntryId: null, queueCount: 0, queueIndex: 0, queueRevision: 0, shuffle: false, repeat: 'off', canNext: false, canPrevious: false, notice: null,
  sleepTimer: { mode: 'off', remainingMs: 0 },
  revision: 0, track: null, status: 'empty', isPlaying: false, playWhenReady: false,
  positionMs: 0, durationMs: 0, bufferedPositionMs: 0, canSeek: false,
  speed: 1, pitch: 1, error: null,
};
