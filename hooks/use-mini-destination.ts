import { useSyncExternalStore } from 'react';
let destination: '/player' | '/queue' = '/player';
const listeners = new Set<() => void>();
export function setMiniDestination(value: typeof destination) { destination = value; listeners.forEach(listener => listener()); }
export function useMiniDestination() { return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener); }; }, () => destination, () => destination); }
