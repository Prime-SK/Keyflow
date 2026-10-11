import Ionicons from '@expo/vector-icons/Ionicons';
import { Pressable, Text, View } from 'react-native';
import { usePlayerTheme } from '@/hooks/use-player-theme';
type Icon = React.ComponentProps<typeof Ionicons>['name'];
export function IconButton({ icon, label, onPress, disabled = false, danger = false }: { icon: Icon; label: string; onPress: () => void; disabled?: boolean; danger?: boolean }) {
  const { colors: c } = usePlayerTheme();
  return <Pressable onPress={onPress} disabled={disabled} accessibilityRole="button" accessibilityLabel={label}
    style={({ pressed }) => ({ width: 44, height: 44, borderRadius: 12, alignItems: 'center', justifyContent: 'center', opacity: disabled ? 0.35 : pressed ? 0.6 : 1 })}>
    <Ionicons name={icon} size={22} color={danger ? c.danger : c.foreground} />
  </Pressable>;
}
export function SettingsRow({ icon, title, subtitle, onPress, trailing, disabled = false }: { icon: Icon; title: string; subtitle?: string; onPress?: () => void; trailing?: React.ReactNode; disabled?: boolean }) {
  const { colors: c } = usePlayerTheme();
  const content = <><View style={{ width: 36, alignItems: 'center' }}><Ionicons name={icon} size={22} color={c.accent} /></View>
    <View style={{ flex: 1, gap: 4 }}><Text style={{ color: c.foreground, fontSize: 15, fontWeight: '600' }}>{title}</Text>{subtitle && <Text style={{ color: c.secondary, fontSize: 12, lineHeight: 18 }}>{subtitle}</Text>}</View>
    {trailing ?? (onPress && <Ionicons name="chevron-forward" size={18} color={c.muted} />)}</>;
  const style = { flexDirection: 'row' as const, alignItems: 'center' as const, gap: 10, paddingVertical: 14, paddingHorizontal: 12, minHeight: 62, opacity: disabled ? 0.45 : 1 };
  return onPress ? <Pressable accessibilityRole="button" onPress={onPress} disabled={disabled} style={({ pressed }) => [style, { backgroundColor: pressed ? c.accentSoft : 'transparent' }]}>{content}</Pressable> : <View style={style}>{content}</View>;
}
