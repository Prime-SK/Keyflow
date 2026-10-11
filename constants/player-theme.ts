export type ThemePreference = 'system' | 'light' | 'dark';

export const playerThemes = {
  light: {
    background: '#F4F7FD', surface: '#FFFFFF', foreground: '#101A34',
    secondary: '#4D5D7A', muted: '#63728E', border: '#DCE4F3',
    accent: '#1E40AF', onAccent: '#FFFFFF', accentSoft: '#E5ECFF',
    artwork: '#E8EEFA', artworkLine: '#CBD7EE', danger: '#A53030', dangerSoft: '#FBECEC',
  },
  dark: {
    background: '#0B1020', surface: '#18233B', foreground: '#F2F5FF',
    secondary: '#B4C2DC', muted: '#92A3C2', border: '#2C3C5C',
    accent: '#6D91FF', onAccent: '#09132F', accentSoft: '#202F58',
    artwork: '#16213A', artworkLine: '#334B77', danger: '#FFB1B1', dangerSoft: '#392424',
  },
} as const;

export type PlayerColors = { [K in keyof typeof playerThemes.light]: string };
