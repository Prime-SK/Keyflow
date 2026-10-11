import { Image } from 'expo-image';
import { useEffect, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { Easing, useAnimatedStyle, useSharedValue, withTiming } from 'react-native-reanimated';

const easing = Easing.bezier(0.22, 0.61, 0.36, 1);

export function AtmosphereBackground({ color, fallback, reduced }: { color: string | null; fallback: string; reduced: boolean }) {
  const background = useSharedValue(fallback);
  useEffect(() => {
    if (color !== null) background.value = reduced ? color : withTiming(color, { duration: 400, easing });
  }, [color, background, reduced]);
  const style = useAnimatedStyle(() => ({ backgroundColor: background.value }));
  return <Animated.View pointerEvents="none" accessible={false} style={[StyleSheet.absoluteFill, style]} />;
}

export function ArtworkGlow({ uri, size, dark, reduced }: { uri: string | undefined; size: number; dark: boolean; reduced: boolean }) {
  const [layers, setLayers] = useState<[string | null, string | null]>([null, null]);
  const active = useRef(0); const current = useRef<string | undefined>(undefined);
  const first = useSharedValue(0); const second = useSharedValue(0);
  useEffect(() => {
    if (uri === undefined || uri === current.current) return;
    current.current = uri; active.current = 1 - active.current;
    const index = active.current;
    // Rapid cover changes can reuse a layer before its old fade has finished.
    // Start the replacement at zero instead of exposing the new glow half-visible.
    if (index === 0) first.value = 0; else second.value = 0;
    setLayers(previous => index === 0 ? [uri, previous[1]] : [previous[0], uri]);
  }, [uri, first, second]);
  useEffect(() => {
    const opacity = active.current === 0 ? [1, 0] : [0, 1];
    first.value = reduced ? opacity[0] : withTiming(opacity[0], { duration: 400, easing });
    second.value = reduced ? opacity[1] : withTiming(opacity[1], { duration: 400, easing });
  }, [layers, reduced, first, second]);
  const styleFirst = useAnimatedStyle(() => ({ opacity: first.value }));
  const styleSecond = useAnimatedStyle(() => ({ opacity: second.value }));
  const diameter = size * 1.6;
  return <View pointerEvents="none" accessible={false} importantForAccessibility="no-hide-descendants"
    style={{ position: 'absolute', alignSelf: 'center', width: diameter, height: diameter, top: (size - diameter) / 2, opacity: dark ? 0.65 : 0.32 }}>
    {[styleFirst, styleSecond].map((style, index) => <Animated.View key={index} style={[StyleSheet.absoluteFill, style]}>
      {layers[index] && <Image source={{ uri: layers[index]! }} recyclingKey={layers[index]!} transition={0} contentFit="fill" style={StyleSheet.absoluteFill} />}
    </Animated.View>)}
  </View>;
}
