import { createContext, useCallback, useContext, useEffect, useRef, useState, type PropsWithChildren } from 'react';
import { AppState } from 'react-native';
import { errorMessage } from '@/lib/playback';
import { library, type LibrarySource, type ScanState } from '@/modules/keyflow-library/src';

const initialScan: ScanState = { revision: 0, status: 'idle', discovered: 0, processed: 0, sourceName: null, error: null };
function useLibraryController() {
  const [scan, setScan] = useState(initialScan);
  const [sources, setSources] = useState<LibrarySource[]>([]);
  const [permission, setPermission] = useState(false);
  const [automaticScanning, setAutomaticScanning] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(false);
  const busyRef = useRef(false);
  const refresh = useCallback(async () => {
    if (!library) return;
    try {
      const [nextSources, nextPermission, nextScan, nextAutomatic] = await Promise.all([library.getSources(), library.getPermission(), library.getScanState(), library.getAutomaticScanning()]);
      if (mounted.current) {
        setSources(nextSources); setPermission(nextPermission.granted);
        setAutomaticScanning(nextAutomatic);
        setScan(current => nextScan.revision >= current.revision ? nextScan : current);
      }
    } catch (failure) { if (mounted.current) setError(errorMessage(failure)); }
  }, []);
  useEffect(() => {
    mounted.current = true;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const changed = library?.addListener('onLibraryChanged', () => {
      if (AppState.currentState !== 'active' || timer) return;
      timer = setTimeout(() => { timer = undefined; void refresh(); }, 750);
    });
    const scanEvents = library?.addListener('onScanState', incoming => {
      if (mounted.current) setScan(current => incoming.revision >= current.revision ? incoming : current);
    });
    const foreground = AppState.addEventListener('change', value => { if (value === 'active') void refresh(); });
    void refresh();
    return () => { mounted.current = false; if (timer) clearTimeout(timer); changed?.remove(); scanEvents?.remove(); foreground.remove(); };
  }, [refresh]);
  const run = useCallback(async (action: () => Promise<unknown>) => {
    if (!library || busyRef.current) return false;
    busyRef.current = true; setBusy(true); setError(null);
    try { await action(); await refresh(); return true; }
    catch (failure) { if (mounted.current) setError(errorMessage(failure)); return false; }
    finally { busyRef.current = false; if (mounted.current) setBusy(false); }
  }, [refresh]);
  const scanDevice = useCallback(() => run(async () => {
    const result = await library!.getPermission();
    const granted = result.granted || (await library!.requestPermission()).granted;
    if (!granted) throw new Error('Audio access wasn’t allowed. Try again, choose a folder, or open a song.');
    await library!.enableDeviceScan();
  }), [run]);
  return { scan, sources, permission, automaticScanning, busy, error, available: library !== null, refresh, run, scanDevice };
}
const LibraryContext = createContext<ReturnType<typeof useLibraryController> | null>(null);
export function LibraryProvider({ children }: PropsWithChildren) {
  return <LibraryContext.Provider value={useLibraryController()}>{children}</LibraryContext.Provider>;
}
export function useLibrary() {
  const value = useContext(LibraryContext);
  if (!value) throw new Error('LibraryProvider is required.');
  return value;
}
