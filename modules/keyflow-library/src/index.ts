import { NativeModule, requireOptionalNativeModule } from 'expo-modules-core';
import { Platform } from 'react-native';
import type { LocalAudio } from '@/modules/keyflow-playback/src';
import type { LibraryGroup, LibraryPage, LibraryQuery, LibrarySource, LibraryTrack, ScanState } from './types';

declare class KeyflowLibraryModule extends NativeModule<{
  onLibraryChanged: (event: { revision: number; invalidatePages?: boolean }) => void;
  onScanState: (event: ScanState) => void;
}> {
  queryTracks(query: LibraryQuery): Promise<LibraryPage<LibraryTrack>>;
  queryGroups(kind: 'albums' | 'artists' | 'folders', query: LibraryQuery): Promise<LibraryPage<LibraryGroup>>;
  getTrack(id: string): Promise<LibraryTrack | null>;
  resolveTrack(id: string): Promise<LocalAudio>;
  getSources(): Promise<LibrarySource[]>;
  getPermission(): Promise<{ granted: boolean }>;
  getAutomaticScanning(): Promise<boolean>;
  setAutomaticScanning(enabled: boolean): Promise<void>;
  requestPermission(): Promise<{ granted: boolean; canAskAgain: boolean }>;
  enableDeviceScan(): Promise<ScanState>;
  addFolder(): Promise<string | null>;
  removeSource(id: string): Promise<void>;
  setSourceEnabled(id: string, enabled: boolean): Promise<void>;
  getFolderRules(id: string): Promise<{ path: string; included: boolean }[]>;
  setFolderRule(id: string, path: string, included: boolean): Promise<void>;
  removeFolderRule(id: string, path: string): Promise<void>;
  getLibraryFolderRules(): Promise<{ path: string; included: boolean }[]>;
  setLibraryFolderRule(path: string, included: boolean): Promise<void>;
  removeLibraryFolderRule(path: string): Promise<void>;
  pickExcludedFolder(): Promise<string | null>;
  startScan(): Promise<ScanState>;
  cancelScan(): Promise<ScanState>;
  getScanState(): Promise<ScanState>;
  getArtwork(id: string): Promise<string | null>;
}
export const library = Platform.OS === 'android' ? requireOptionalNativeModule<KeyflowLibraryModule>('KeyflowLibrary') : null;
export * from './types';
