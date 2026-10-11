import { Modal, Pressable, Text, View } from 'react-native';
import { usePlayerTheme } from '@/hooks/use-player-theme';
import { useReducedMotion } from '@/hooks/use-reduced-motion';

export function ConfirmationDialog({ visible, title, message, confirmLabel, cancelLabel = 'Cancel', onClose, onConfirm, busy = false, destructive = false }: {
  visible: boolean; title: string; message: string; confirmLabel: string; cancelLabel?: string;
  onClose: () => void; onConfirm: () => void; busy?: boolean; destructive?: boolean;
}) {
  const { colors: c } = usePlayerTheme(); const motion = useReducedMotion();
  return <Modal visible={visible} transparent animationType={motion.enabled ? 'none' : 'fade'} onRequestClose={onClose}>
    <Pressable accessible={false} onPress={onClose} style={{ flex: 1, backgroundColor: '#00000070', justifyContent: 'center', padding: 24 }}>
      <Pressable accessible={false} accessibilityViewIsModal onPress={event => event.stopPropagation()} style={{ width: '100%', maxWidth: 340, alignSelf: 'center', paddingHorizontal: 24, paddingTop: 24, paddingBottom: 12, borderRadius: 20, backgroundColor: c.surface }}>
        <Text accessibilityRole="header" style={{ color: c.foreground, fontSize: 20, lineHeight: 28, fontWeight: '600' }}>{title}</Text>
        <Text style={{ color: c.secondary, fontSize: 14, lineHeight: 21, marginTop: 10 }}>{message}</Text>
        <View style={{ flexDirection: 'row', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end', marginTop: 16, marginRight: -8 }}>
          <Pressable accessibilityRole="button" onPress={onClose} style={({ pressed }) => ({ minHeight: 48, paddingHorizontal: 14, paddingVertical: 14, borderRadius: 10, backgroundColor: pressed ? c.background : 'transparent' })}><Text style={{ color: c.secondary, fontSize: 14, fontWeight: '600' }}>{cancelLabel}</Text></Pressable>
          <Pressable accessibilityRole="button" disabled={busy} onPress={() => { onClose(); onConfirm(); }} style={({ pressed }) => ({ minHeight: 48, paddingHorizontal: 14, paddingVertical: 14, borderRadius: 10, backgroundColor: pressed ? (destructive ? c.dangerSoft : c.accentSoft) : 'transparent', opacity: busy ? .4 : 1 })}><Text style={{ color: destructive ? c.danger : c.accent, fontSize: 14, fontWeight: '600' }}>{confirmLabel}</Text></Pressable>
        </View>
      </Pressable>
    </Pressable>
  </Modal>;
}
