import { type QueueEntry } from '@/modules/keyflow-playback/src';

/** Reorder only the loaded display window; never identifies occurrences by song URI. */
export function moveQueueWindow(items: QueueEntry[], id: string, delta: number): QueueEntry[] {
  const from = items.findIndex(item => item.id === id);
  const to = Math.max(0, Math.min(items.length - 1, from + delta));
  if (from < 0 || from === to) return items;
  const next = [...items]; const [entry] = next.splice(from, 1); next.splice(to, 0, entry);
  const start = items[0].index;
  return next.map((item, index) => item.index === start + index ? item : { ...item, index: start + index });
}

/** Native confirmation of the display order should not redraw unchanged rows. */
export function reconcileQueueWindow(previous: QueueEntry[], incoming: QueueEntry[]): QueueEntry[] {
  const byId = new Map(previous.map(item => [item.id, item]));
  const next = incoming.map(item => {
    const old = byId.get(item.id);
    return old && old.index === item.index && old.title === item.title && old.artist === item.artist
      && old.trackId === item.trackId && old.priority === item.priority ? old : item;
  });
  return previous.length === next.length && next.every((item, index) => item === previous[index]) ? previous : next;
}

export function removeQueueWindow(items: QueueEntry[], id: string): QueueEntry[] {
  if (!items.some(item => item.id === id)) return items;
  const start = items[0].index;
  return items.filter(item => item.id !== id).map((item, index) => item.index === start + index ? item : { ...item, index: start + index });
}
