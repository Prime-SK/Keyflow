import { createContext, useContext, useEffect, useMemo, useRef, useState, type PropsWithChildren } from 'react';
import { useColorScheme } from 'react-native';

import { playerThemes, type PlayerColors, type ThemePreference } from '@/constants/player-theme';
import { playback } from '@/modules/keyflow-playback/src';

interface PlayerTheme {
  colors: PlayerColors;
  isDark: boolean;
  preference: ThemePreference;
  setPreference: (preference: ThemePreference) => void;
}

const ThemeContext = createContext<PlayerTheme | null>(null);

export function PlayerThemeProvider({ children }: PropsWithChildren) {
  const systemTheme = useColorScheme();
  const [preference, updatePreference] = useState<ThemePreference>('system');
  const userChanged = useRef(false);
  useEffect(() => {
    let active = true;
    void playback?.getThemePreference().then(saved => {
      if (active && !userChanged.current) updatePreference(saved);
    }).catch(() => { /* Keep the system theme if preference storage is unavailable. */ });
    return () => { active = false; };
  }, []);
  const isDark = preference === 'dark' || (preference === 'system' && systemTheme === 'dark');
  const value = useMemo(() => ({ colors: playerThemes[isDark ? 'dark' : 'light'],
    isDark, preference, setPreference: (next: ThemePreference) => {
      userChanged.current = true;
      updatePreference(next);
      void playback?.setThemePreference(next).catch(() => { /* Appearance still updates for this session. */ });
    } }), [isDark, preference]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function usePlayerTheme() {
  const theme = useContext(ThemeContext);
  if (!theme) throw new Error('usePlayerTheme must be used inside PlayerThemeProvider');
  return theme;
}
