import type { PlayerColors } from '@/constants/player-theme';
export function tintedBackground(base: string, tint: string | null, amount: number) {
  if (!tint) return base;
  const mix = (index: number) => Math.round(parseInt(base.slice(index, index + 2), 16) * (1 - amount)
    + parseInt(tint.slice(index, index + 2), 16) * amount).toString(16).padStart(2, '0');
  return `#${mix(1)}${mix(3)}${mix(5)}`;
}

function luminance(hex: string) {
  const channels = [1, 3, 5].map(index => {
    const value = parseInt(hex.slice(index, index + 2), 16) / 255;
    return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
  });
  return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

export function contrastRatio(a: string, b: string) {
  const first = luminance(a); const second = luminance(b);
  return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
}

// Retain the cover's hue while lifting dark colors or deepening pale ones.
export function artworkControls(base: PlayerColors, tint: string | null, dark: boolean): PlayerColors {
  if (!tint || !/^#[0-9a-f]{6}$/i.test(tint)) return base;
  const background = tintedBackground(base.background, tint, dark ? 0.16 : 0.08);
  const destination = dark ? '#FFFFFF' : '#000000';
  let accent = tint;
  for (let step = 0; step <= 100; step++) {
    accent = tintedBackground(tint, destination, step / 100);
    if (contrastRatio(accent, background) >= 4.5 && contrastRatio(accent, base.surface) >= 4.5) break;
  }
  const onAccent = contrastRatio(accent, '#FFFFFF') >= contrastRatio(accent, '#0B1020') ? '#FFFFFF' : '#0B1020';
  return { ...base, accent, onAccent, accentSoft: tintedBackground(base.background, accent, 0.18) };
}
