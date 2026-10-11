export type LibrarySection = 'songs' | 'albums' | 'artists' | 'folders';
export type SongSort = 'title' | 'artist' | 'album' | 'duration' | 'fileName' | 'dateAdded';
export type Availability = 'available' | 'missing' | 'permission' | 'disconnected' | 'excluded';
export interface LibraryTrack {
  id: string; title: string; artist: string; album: string; albumArtist: string;
  fileName: string; durationMs: number; folderKey: string; folderName: string;
  artworkUri: string | null; artworkVersion: string; availability: Availability; dateAdded: number;
}
export interface LibraryGroup { key: string; title: string; subtitle: string; count: number }
export interface LibraryPage<T> { items: T[]; total: number; offset: number; hasMore: boolean }
export interface LibraryQuery {
  offset?: number; limit?: number; search?: string; sort?: SongSort; descending?: boolean;
  groupKind?: Exclude<LibrarySection, 'songs'>; groupKey?: string;
  sourceId?: string; includeExcluded?: boolean;
  folderSearch?: string;
}
export interface LibrarySource {
  id: string; kind: 'device' | 'folder'; name: string; locator: string; enabled: boolean;
  access: Availability; lastCompleted: number; error: string | null;
}
export interface ScanState {
  revision: number; status: 'idle' | 'running' | 'completed' | 'cancelled' | 'interrupted' | 'error';
  discovered: number; processed: number; sourceName: string | null; error: string | null;
}
