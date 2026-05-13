import { useInView } from 'react-intersection-observer'

/**
 * Trả về { ref, inView } cùng variants chuẩn cho fade-in + slide-up.
 * Trigger 1 lần khi 20% phần tử vào viewport.
 */
export function useInViewAnimation(threshold = 0.2) {
  const { ref, inView } = useInView({ triggerOnce: true, threshold })
  return { ref, inView }
}

/* ── Basic fade-up ──────────────────────────────────────── */
export const fadeUp = {
  hidden:  { opacity: 0, y: 30 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Stagger children ───────────────────────────────────── */
export const fadeUpStagger = {
  visible: {
    transition: { staggerChildren: 0.1 },
  },
}

export const fadeUpItem = {
  hidden:  { opacity: 0, y: 20 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Fade-in from left ──────────────────────────────────── */
export const fadeInLeft = {
  hidden:  { opacity: 0, x: -40 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Fade-in from right ─────────────────────────────────── */
export const fadeInRight = {
  hidden:  { opacity: 0, x: 40 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Scale up ───────────────────────────────────────────── */
export const scaleUp = {
  hidden:  { opacity: 0, scale: 0.9 },
  visible: { opacity: 1, scale: 1, transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Stagger from left ──────────────────────────────────── */
export const staggerFromLeft = {
  visible: {
    transition: { staggerChildren: 0.12 },
  },
}

export const staggerFromLeftItem = {
  hidden:  { opacity: 0, x: -20 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Stagger from right ─────────────────────────────────── */
export const staggerFromRight = {
  visible: {
    transition: { staggerChildren: 0.12 },
  },
}

export const staggerFromRightItem = {
  hidden:  { opacity: 0, x: 20 },
  visible: { opacity: 1, x: 0, transition: { duration: 0.5, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Blur in ────────────────────────────────────────────── */
export const blurIn = {
  hidden:  { opacity: 0, filter: 'blur(10px)' },
  visible: { opacity: 1, filter: 'blur(0px)', transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1] } },
}

/* ── Rotate in ──────────────────────────────────────────── */
export const rotateIn = {
  hidden:  { opacity: 0, rotate: -5, scale: 0.95 },
  visible: { opacity: 1, rotate: 0, scale: 1, transition: { duration: 0.6, ease: [0.22, 1, 0.36, 1] } },
}