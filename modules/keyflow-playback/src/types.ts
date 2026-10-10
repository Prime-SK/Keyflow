export type PlaybackStatus = 'empty' | 'loading' | 'playing' | 'paused' | 'ended' | 'error';

export interface LocalAudio {
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

/** All playback values come from Android's media controller, never a JS timer. */
export interface PlaybackState {
  revision: number;
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
  revision: 0, track: null, status: 'empty', isPlaying: false, playWhenReady: false,
  positionMs: 0, durationMs: 0, bufferedPositionMs: 0, canSeek: false,
  speed: 1, pitch: 1, error: null,
};
