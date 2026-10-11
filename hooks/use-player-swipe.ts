import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, PanResponder } from 'react-native';
import { useReducedMotion } from '@/hooks/use-reduced-motion';

type Action = () => unknown | Promise<unknown>;
type Actions = { next?: Action; previous?: Action; collapse?: Action; expand?: Action; entryKey?: string | null };
type Options = { width?: number; height?: number; fullPlayer?: boolean; active?: boolean };
const easing = Easing.bezier(0.2, 0.8, 0.2, 1);

/** Native-driver settling; gesture surfaces never include seek sliders. */
export function usePlayerSwipe(actions: Actions, options: Options = {}) {
  const motion = useReducedMotion();
  const latest = useRef({ actions, options, reduced: motion.enabled });
  latest.current = { actions, options, reduced: motion.enabled };
  const offset = useRef(new Animated.ValueXY({ x: 0, y: options.fullPlayer ? options.height ?? 0 : 0 })).current;
  const transitioning = useRef(false); const axis = useRef<'x' | 'y' | null>(null);
  const mounted = useRef(true); const entered = useRef(false);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; offset.stopAnimation(); }; }, [offset]);
  useEffect(() => {
    if (options.active === undefined) return;
    // A persistent mini-player hook outlives its native view while the modal is open.
    // Never carry a native-driver drag/settling value into the next visible instance.
    offset.stopAnimation(); offset.setValue({ x: 0, y: 0 }); axis.current = null;
  }, [options.active, offset]);
  useEffect(() => {
    if (!motion.ready || entered.current) return;
    entered.current = true;
    if (motion.enabled) offset.setValue({ x: 0, y: 0 });
    else if (options.fullPlayer) Animated.timing(offset.y, { toValue: 0, duration: 240, easing, useNativeDriver: true }).start();
  }, [motion.ready, motion.enabled, offset, options.fullPlayer]);

  const responder = useMemo(() => {
    const settle = () => {
      if (!mounted.current) return;
      if (latest.current.reduced || latest.current.options.active === false) offset.setValue({ x: 0, y: 0 });
      else Animated.spring(offset, { toValue: { x: 0, y: 0 }, stiffness: 420, damping: 42, mass: 1, useNativeDriver: true }).start();
    };
    const wants = (dx: number, dy: number) => {
      if (transitioning.current || latest.current.options.active === false) return false;
      const a = latest.current.actions;
      if (Math.abs(dx) > 12 && Math.abs(dx) > Math.abs(dy) * 1.4 && !!(dx < 0 ? a.next : a.previous)) { axis.current = 'x'; return true; }
      if (Math.abs(dy) > 16 && Math.abs(dy) > Math.abs(dx) * 1.4 && !!(dy > 0 ? a.collapse : a.expand)) { axis.current = 'y'; return true; }
      return false;
    };
    const animate = (value: Animated.Value, toValue: number, duration: number) => new Promise<boolean>(resolve => {
      Animated.timing(value, { toValue, duration, easing, useNativeDriver: true }).start(({ finished }) => resolve(finished));
    });
    async function complete(dx: number, dy: number, vx: number, vy: number) {
      const { actions: a, options: o, reduced } = latest.current;
      const horizontal = axis.current === 'x' && (Math.abs(dx) > 60 || Math.abs(dx) > 18 && Math.abs(vx) > 0.5);
      const vertical = axis.current === 'y' && (Math.abs(dy) > 72 || Math.abs(dy) > 20 && Math.abs(vy) > 0.65);
      const action = horizontal ? dx < 0 ? a.next : a.previous : vertical ? dy > 0 ? a.collapse : a.expand : undefined;
      if (!action) { settle(); return; }
      transitioning.current = true;
      try {
        if (horizontal && o.width && !reduced) {
          const direction = dx < 0 ? -1 : 1; const entry = a.entryKey;
          if (!await animate(offset.x, direction * o.width, 150) || !mounted.current) return;
          const succeeded = await action();
          // Let the authoritative native snapshot render before bringing the new cover in.
          await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
          if (!mounted.current) return;
          if (succeeded !== false && (!entry || latest.current.actions.entryKey !== entry)) offset.x.setValue(-direction * o.width);
          await animate(offset.x, 0, 190);
        } else if (vertical && dy > 0 && o.fullPlayer && o.height && !reduced) {
          if (await animate(offset.y, o.height, 220) && mounted.current) await action();
        } else if (vertical && dy < 0 && a.expand) {
          // Reset before navigation detaches the mini-player's animated native view.
          offset.stopAnimation(); offset.setValue({ x: 0, y: 0 }); await action();
        } else { await action(); settle(); }
      } finally { transitioning.current = false; }
    }
    return PanResponder.create({
      onMoveShouldSetPanResponder: (_, g) => wants(g.dx, g.dy),
      onMoveShouldSetPanResponderCapture: (_, g) => wants(g.dx, g.dy),
      onPanResponderGrant: () => offset.stopAnimation(),
      onPanResponderMove: (_, { dx, dy }) => {
        if (latest.current.reduced) return;
        const o = latest.current.options;
        offset.setValue(axis.current === 'x'
          ? { x: o.width ? Math.max(-o.width, Math.min(o.width, dx)) : Math.max(-36, Math.min(36, dx * 0.3)), y: 0 }
          : { x: 0, y: o.fullPlayer ? Math.max(0, dy) : Math.max(-40, Math.min(40, dy * 0.3)) });
      },
      onPanResponderRelease: (_, g) => { void complete(g.dx, g.dy, g.vx, g.vy).catch(() => { transitioning.current = false; settle(); }); },
      onPanResponderTerminate: settle,
      onPanResponderTerminationRequest: () => !transitioning.current,
    });
  }, [offset]);
  const dismiss = useCallback(async () => {
    if (transitioning.current) return;
    transitioning.current = true;
    try {
      const { options: o, actions: a, reduced } = latest.current;
      if (o.fullPlayer && o.height && !reduced) await new Promise<void>(resolve => {
        Animated.timing(offset.y, { toValue: o.height!, duration: 220, easing, useNativeDriver: true }).start(() => resolve());
      });
      if (mounted.current) await a.collapse?.();
    } finally { transitioning.current = false; }
  }, [offset]);
  return { panHandlers: responder.panHandlers, transform: offset.getTranslateTransform(), dismiss,
    horizontalTransform: [{ translateX: offset.x }], verticalTransform: [{ translateY: offset.y }], reducedMotion: motion.enabled };
}
