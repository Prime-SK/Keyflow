import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState } from 'react-native';
import { library, type LibraryPage } from '@/modules/keyflow-library/src';
import { errorMessage } from '@/lib/playback';

export function useCollectionPage<T>(query: (offset: number) => Promise<LibraryPage<T>>) {
  const [items, setItems] = useState<T[]>([]); const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false); const [error, setError] = useState<string | null>(null);
  const generation = useRef(0); const pending = useRef(false); const offset = useRef(0); const more = useRef(false);
  const load = useCallback(async (reset: boolean) => {
    if (!reset && (pending.current || !more.current)) return;
    const token = reset ? ++generation.current : generation.current;
    pending.current = true; setLoading(true); setError(null);
    if (reset) { offset.current = 0; more.current = false; setItems([]); }
    try {
      const page = await query(reset ? 0 : offset.current);
      if (token !== generation.current) return;
      offset.current = page.offset + page.items.length; more.current = page.hasMore;
      setItems(current => reset ? page.items : [...current, ...page.items]); setTotal(page.total);
    } catch (failure) { if (token === generation.current) setError(errorMessage(failure)); }
    finally { if (token === generation.current) { pending.current = false; setLoading(false); } }
  }, [query]);
  useEffect(() => {
    void load(true);
    const subscription = library?.addListener('onCollectionsChanged', () => { void load(true); });
    const foreground = AppState.addEventListener('change', value => { if (value === 'active') void load(true); });
    return () => { generation.current++; subscription?.remove(); foreground.remove(); };
  }, [load]);
  return { items, total, loading, error, refresh: () => load(true), loadMore: () => { void load(false); } };
}
