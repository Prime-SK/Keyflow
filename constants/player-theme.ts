export type ThemePreference = 'system' | 'light' | 'dark';

export const playerThemes = {
  light: {
    background: '#F6F7F3', surface: '#FFFFFF', foreground: '#18251F',
    secondary: '#56635B', muted: '#66736A', border: '#DCE3DC',
    accent: '#246E50', onAccent: '#FFFFFF', accentSoft: '#E5EFE6',
    artwork: '#EAF0E9', artworkLine: '#D5E0D4', danger: '#A53030', dangerSoft: '#FBECEC',
  },
  dark: {
    background: '#101713', surface: '#19231D', foreground: '#F0F5EF',
    secondary: '#B6C5B9', muted: '#9CAE9F', border: '#304137',
    accent: '#9EDDB8', onAccent: '#102B1C', accentSoft: '#233C2D',
    artwork: '#1F3026', artworkLine: '#344E3D', danger: '#FFB1B1', dangerSoft: '#392424',
  },
} as const;

export type PlayerColors = { [K in keyof typeof playerThemes.light]: string };
