import { useInView } from 'react-intersection-observer'

/**
 * Trả về { ref, inView } cùng variants chuẩn cho fade-in + slide-up.
 * Trigger 1 lần khi 20% phần tử vào viewport.
 */
export function useInViewAnimation(threshold = 0.2) {
  const { ref, inView } = useInView({ triggerOnce: true, threshold })
  return { ref, inView }
}

export const fadeUp = {
  hidden:  { opacity: 0, y: 16 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.4, ease: 'easeOut' } },
}

export const fadeUpStagger = {
  visible: {
    transition: { staggerChildren: 0.08 },
  },
}

export const fadeUpItem = {
  hidden:  { opacity: 0, y: 12 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.35, ease: 'easeOut' } },
}
