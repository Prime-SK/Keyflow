import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { DarkTheme, DefaultTheme, ThemeProvider } from '@react-navigation/native';
import { View } from 'react-native';
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
  const base = isDark ? DarkTheme : DefaultTheme;
  const navigationTheme = { ...base, colors: { ...base.colors, background: colors.background,
    card: colors.surface, text: colors.foreground, primary: colors.accent, border: colors.border, notification: colors.accent } };
  return <ThemeProvider value={navigationTheme}><View style={{ flex: 1, backgroundColor: colors.background }}>
    <Stack screenOptions={{ headerShown: false, animation: motion.enabled ? 'none' : 'slide_from_right', contentStyle: { backgroundColor: colors.background } }}>
      <Stack.Screen name="player" options={{ presentation: 'transparentModal', animation: 'none', contentStyle: { backgroundColor: 'transparent' } }} />
      <Stack.Screen name="queue" options={{ presentation: 'transparentModal', animation: 'none', contentStyle: { backgroundColor: 'transparent' } }} />
    </Stack>
    <StatusBar style={isDark ? 'light' : 'dark'} />
    <MiniPlayer />
  </View></ThemeProvider>;
}
