import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';

import { PlayerThemeProvider, usePlayerTheme } from '@/hooks/use-player-theme';

export default function RootLayout() {
  return (
    <PlayerThemeProvider><PlayerNavigation /></PlayerThemeProvider>
  );
}

function PlayerNavigation() {
  const { colors, isDark } = usePlayerTheme();
  return <>
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }} />
    <StatusBar style={isDark ? 'light' : 'dark'} />
  </>;
}
