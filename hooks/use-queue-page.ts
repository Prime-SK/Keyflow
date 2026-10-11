import { useCallback, useEffect, useRef, useState } from 'react';
import { playback, type QueueEntry } from '@/modules/keyflow-playback/src';
import { errorMessage } from '@/lib/playback';
import { moveQueueWindow, reconcileQueueWindow, removeQueueWindow } from '@/lib/queue-view';

export function useQueuePage(revision: number, currentIndex: number) {
  const [items, setItems] = useState<QueueEntry[]>([]);
  const [loading, setLoading] = useState(false); const [error, setError] = useState<string | null>(null);
  const [hasMore, setHasMore] = useState(false); const [start, setStart] = useState(0);
  const generation = useRef(0); const pending = useRef(false); const mounted = useRef(false);
  const range = useRef({ start: 0, end: 0, revision: -1 });
  const current = useRef({ revision, currentIndex }); current.current = { revision, currentIndex };
  const fetch = useCallback(async (kind: 'reset' | 'next' | 'previous', from?: number) => {
    if (!playback || kind !== 'reset' && pending.current) return;
    const token = kind === 'reset' ? ++generation.current : generation.current;
    const offset = kind === 'reset' ? from ?? current.current.currentIndex
      : kind === 'next' ? range.current.end : Math.max(0, range.current.start - 50);
    const limit = kind === 'previous' ? range.current.start - offset : 50;
    if (limit <= 0) return;
    pending.current = true; setLoading(true); setError(null);
    try {
      const result = await playback.queryQueue(offset, limit);
      if (!mounted.current || token !== generation.current) return;
      if (kind === 'reset' && from !== undefined) {
        const wanted = Math.max(50, range.current.end - offset);
        while (result.hasMore && result.items.length < wanted) {
          const next = await playback.queryQueue(offset + result.items.length, Math.min(50, wanted - result.items.length));
          if (!mounted.current || token !== generation.current) return;
          if (next.revision !== result.revision) { pending.current = false; void fetch('reset', offset); return; }
          result.items.push(...next.items); result.hasMore = next.hasMore;
          if (!next.items.length) break;
        }
      }
      if (kind === 'reset' && result.items.length === 0 && result.total > 0 && offset > 0) {
        pending.current = false; void fetch('reset', Math.min(current.current.currentIndex, result.total - 1)); return;
      }
      if (kind !== 'reset' && result.revision !== range.current.revision) {
        pending.current = false; void fetch('reset'); return;
      }
      const first = kind === 'next' ? range.current.start : offset;
      const end = kind === 'previous' ? range.current.end : offset + result.items.length;
      range.current = { start: first, end, revision: result.revision }; setStart(first);
      setItems(old => kind === 'reset' ? reconcileQueueWindow(old, result.items) : kind === 'next' ? [...old, ...result.items] : [...result.items, ...old]);
      if (kind !== 'previous') setHasMore(result.hasMore);
    } catch (failure) { if (mounted.current && token === generation.current) setError(errorMessage(failure)); }
    finally { if (mounted.current && token === generation.current) { pending.current = false; setLoading(false); } }
  }, []);
  useEffect(() => {
    mounted.current = true; void fetch('reset', range.current.revision < 0 ? undefined : range.current.start);
    return () => { mounted.current = false; generation.current++; };
  }, [revision, fetch]);
  const refreshWindow = useCallback(() => fetch('reset', range.current.start), [fetch]);
  const presentRemove = useCallback((id: string) => {
    generation.current++; pending.current = false; setLoading(false);
    setItems(old => removeQueueWindow(old, id));
  }, []);
  return { items, loading, error, start, hasMore,
    // Presentation only: native playback still owns the queue. Its revision refresh
    // confirms this order; a rejected command explicitly reloads it.
    presentMove: (id: string, delta: number) => {
      generation.current++; pending.current = false; setLoading(false);
      setItems(old => moveQueueWindow(old, id, delta));
    },
    presentRemove, refreshWindow,
    refresh: () => fetch('reset'), fromBeginning: () => fetch('reset', 0),
    loadMore: () => { if (hasMore) void fetch('next'); }, loadEarlier: () => fetch('previous') };
}
