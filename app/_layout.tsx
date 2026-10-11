import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';

import { PlayerThemeProvider, usePlayerTheme } from '@/hooks/use-player-theme';
import { PlaybackProvider } from '@/hooks/use-playback';
import { LibraryProvider } from '@/hooks/use-library';
import { MiniPlayer } from '@/components/mini-player';

export default function RootLayout() {
  return (
    <PlayerThemeProvider><PlaybackProvider><LibraryProvider><PlayerNavigation /></LibraryProvider></PlaybackProvider></PlayerThemeProvider>
  );
}

function PlayerNavigation() {
  const { colors, isDark } = usePlayerTheme();
  return <>
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }} />
    <StatusBar style={isDark ? 'light' : 'dark'} />
    <MiniPlayer />
  </>;
}
