import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import 'react-native-reanimated';
import { GestureHandlerRootView } from 'react-native-gesture-handler';

import { PlayerThemeProvider, usePlayerTheme } from '@/hooks/use-player-theme';
import { PlaybackProvider } from '@/hooks/use-playback';
import { LibraryProvider } from '@/hooks/use-library';
import { MiniPlayer } from '@/components/mini-player';
import { useReducedMotion } from '@/hooks/use-reduced-motion';

export default function RootLayout() {
  return (
    <GestureHandlerRootView style={{ flex: 1 }}><PlayerThemeProvider><PlaybackProvider><LibraryProvider><PlayerNavigation /></LibraryProvider></PlaybackProvider></PlayerThemeProvider></GestureHandlerRootView>
  );
}

function PlayerNavigation() {
  const { colors, isDark } = usePlayerTheme();
  const motion = useReducedMotion();
  return <>
    <Stack screenOptions={{ headerShown: false, contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Screen name="player" options={{ presentation: 'transparentModal', animation: 'none', contentStyle: { backgroundColor: 'transparent' } }} />
      <Stack.Screen name="queue" options={{ presentation: 'transparentModal', animation: 'none', contentStyle: { backgroundColor: 'transparent' } }} />
      <Stack.Screen name="collections" options={{ animation: motion.enabled ? 'none' : 'slide_from_right' }} />
      <Stack.Screen name="collection" options={{ animation: motion.enabled ? 'none' : 'slide_from_right' }} />
    </Stack>
    <StatusBar style={isDark ? 'light' : 'dark'} />
    <MiniPlayer />
  </>;
}
