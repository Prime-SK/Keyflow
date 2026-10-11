import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { library, type LibraryGroup, type LibraryQuery, type LibrarySection, type LibraryTrack } from '@/modules/keyflow-library/src';
import { errorMessage } from '@/lib/playback';

export type LibraryItem = LibraryTrack | LibraryGroup;
export function useLibraryPage(section: LibrarySection, query: LibraryQuery) {
  const key = `${section}:${JSON.stringify(query)}`;
  const [items, setItems] = useState<LibraryItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const generation = useRef(0);
  const current = useRef({ key, section, query }); current.current = { key, section, query };
  const count = useRef(0);
  const more = useRef(false);
  const fetching = useRef(false);
  const mounted = useRef(false);
  const fetchPage = useCallback(async (reset: boolean) => {
    if (!library) return;
    if (!reset && (fetching.current || !more.current)) return;
    const request = reset ? ++generation.current : generation.current;
    const options = current.current;
    const offset = reset ? 0 : count.current;
    fetching.current = true; setLoading(true); setError(null);
    try {
      const q = { ...options.query, offset, limit: 50 };
      const page = options.section === 'songs' ? await library.queryTracks(q) : await library.queryGroups(options.section, q);
      if (!mounted.current || request !== generation.current || options.key !== current.current.key) return;
      more.current = page.hasMore; count.current = offset + page.items.length;
      setTotal(page.total);
      setItems(previous => {
        if (reset) return page.items;
        const seen = new Set(previous.map(item => 'id' in item ? item.id : item.key));
        return [...previous, ...page.items.filter(item => {
          const id = 'id' in item ? item.id : item.key;
          if (seen.has(id)) return false;
          seen.add(id); return true;
        })];
      });
      if (reset) setNeedsRefresh(false);
    } catch (failure) { if (mounted.current && request === generation.current) setError(errorMessage(failure)); }
    finally { if (mounted.current && request === generation.current) { fetching.current = false; setLoading(false); } }
  }, []);
  useEffect(() => {
    mounted.current = true; count.current = 0; more.current = false; setItems([]); setTotal(0);
    void fetchPage(true);
    return () => { mounted.current = false; generation.current++; };
  }, [key, fetchPage]);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | undefined;
    const changed = library?.addListener('onLibraryChanged', event => {
      if (event.invalidatePages && current.current.query.includeExcluded) return;
      if (event.invalidatePages && AppState.currentState === 'active') { void fetchPage(true); return; }
      if (AppState.currentState !== 'active' || timer) return;
      timer = setTimeout(() => {
        timer = undefined;
        if (count.current <= 50) void fetchPage(true); else setNeedsRefresh(true);
      }, 750);
    });
    const foreground = AppState.addEventListener('change', value => { if (value === 'active') void fetchPage(true); });
    return () => { if (timer) clearTimeout(timer); changed?.remove(); foreground.remove(); };
  }, [fetchPage]);
  return { items, total, loading, error, needsRefresh, refresh: () => fetchPage(true), loadMore: () => fetchPage(false) };
}
