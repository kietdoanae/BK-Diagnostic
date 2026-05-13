import { useEffect, useState, useMemo } from 'react'
import { Button } from 'antd'
import { motion } from 'framer-motion'
import { useTranslation } from 'react-i18next'
import PlaceholderImage from '../shared/PlaceholderImage'
import { fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import heroBgSvg from '../../../assets/svg/hero-bg-mesh.svg'

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

/* ── Typewriter hook ────────────────────────────────────── */
function useTypewriter(texts, speed = 60, pause = 2000) {
  const [display, setDisplay] = useState('')
  const [textIdx, setTextIdx] = useState(0)
  const [charIdx, setCharIdx] = useState(0)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    const current = texts[textIdx]
    let timer

    if (!isDeleting && charIdx <= current.length) {
      timer = setTimeout(() => {
        setDisplay(current.slice(0, charIdx))
        setCharIdx(charIdx + 1)
      }, speed)
    } else if (!isDeleting && charIdx > current.length) {
      timer = setTimeout(() => setIsDeleting(true), pause)
    } else if (isDeleting && charIdx > 0) {
      timer = setTimeout(() => {
        setCharIdx(charIdx - 1)
        setDisplay(current.slice(0, charIdx - 1))
      }, speed / 2)
    } else if (isDeleting && charIdx === 0) {
      setIsDeleting(false)
      setTextIdx((textIdx + 1) % texts.length)
    }

    return () => clearTimeout(timer)
  }, [charIdx, isDeleting, textIdx, texts, speed, pause])

  return display
}

/* ── Floating particles ─────────────────────────────────── */
function FloatingParticles() {
  const particles = useMemo(() =>
    Array.from({ length: 20 }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: Math.random() * 4 + 2,
      duration: Math.random() * 8 + 6,
      delay: Math.random() * 3,
      opacity: Math.random() * 0.3 + 0.1,
    })), [])

  return (
    <div style={{ position: 'absolute', inset: 0, overflow: 'hidden', pointerEvents: 'none' }}>
      {particles.map((p) => (
        <motion.div
          key={p.id}
          animate={{
            y: [0, -30, 0],
            x: [0, Math.random() * 20 - 10, 0],
            opacity: [p.opacity, p.opacity * 2, p.opacity],
          }}
          transition={{
            duration: p.duration,
            repeat: Infinity,
            delay: p.delay,
            ease: 'easeInOut',
          }}
          style={{
            position: 'absolute',
            left: `${p.x}%`,
            top: `${p.y}%`,
            width: p.size,
            height: p.size,
            borderRadius: '50%',
            background: p.id % 3 === 0 ? 'var(--bk-blue-500)' : p.id % 3 === 1 ? 'var(--gold-500)' : 'rgba(255,255,255,0.6)',
          }}
        />
      ))}
    </div>
  )
}

/* ── Gradient text animation ────────────────────────────── */
function GradientText({ children, style }) {
  return (
    <motion.span
      animate={{
        backgroundPosition: ['0% 50%', '100% 50%', '0% 50%'],
      }}
      transition={{ duration: 5, repeat: Infinity, ease: 'linear' }}
      style={{
        background: 'linear-gradient(90deg, var(--ink-900), var(--bk-blue-500), var(--bk-navy-700), var(--ink-900))',
        backgroundSize: '200% auto',
        WebkitBackgroundClip: 'text',
        WebkitTextFillColor: 'transparent',
        ...style,
      }}
    >
      {children}
    </motion.span>
  )
}

/* ── Hero Component ─────────────────────────────────────── */
export default function Hero() {
  const { t } = useTranslation()
  const typewriterTexts = useMemo(() => [
    'CAN Bus',
    'OBD-II',
    'STM32',
    'UART',
    'MCP2515',
  ], [])
  const typewriterDisplay = useTypewriter(typewriterTexts, 80, 1500)

  return (
    <section id="tong-quan" style={{
      position: 'relative',
      paddingTop: 128,
      paddingBottom: 64,
      backgroundImage: `url(${heroBgSvg})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      overflow: 'hidden',
    }}>
      <FloatingParticles />

      {/* Gradient overlay at bottom for smooth transition */}
      <div style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        height: 120,
        background: 'linear-gradient(to bottom, transparent, var(--paper))',
        pointerEvents: 'none',
        zIndex: 2,
      }} />

      <motion.div
        className="landing-container"
        style={{ textAlign: 'center', position: 'relative', zIndex: 3 }}
        initial="hidden"
        animate="visible"
        variants={fadeUpStagger}
      >
        <motion.div variants={fadeUpItem} style={{
          display: 'inline-block',
          background: 'linear-gradient(135deg, var(--bk-blue-100), rgba(212, 160, 23, 0.1))',
          color: 'var(--bk-navy-700)',
          padding: '8px 20px',
          borderRadius: 24,
          fontSize: 12,
          fontWeight: 700,
          letterSpacing: 2,
          marginBottom: 24,
          border: '1px solid rgba(21, 101, 192, 0.15)',
          backdropFilter: 'blur(10px)',
        }}>
          <motion.span
            animate={{ opacity: [1, 0.6, 1] }}
            transition={{ duration: 2, repeat: Infinity }}
            style={{ display: 'inline-block', width: 8, height: 8, borderRadius: '50%', background: 'var(--green-600)', marginRight: 8, verticalAlign: 'middle' }}
          />
          {t('landing.hero.badge')}
        </motion.div>

        <motion.h1 variants={fadeUpItem} style={{
          fontSize: 'clamp(36px, 6vw, 56px)',
          fontWeight: 800,
          lineHeight: 1.15,
          margin: '0 0 20px',
          letterSpacing: '-0.02em',
          maxWidth: 800,
          marginLeft: 'auto',
          marginRight: 'auto',
        }}>
          <GradientText>
            {t('landing.hero.title').split('\n').map((line, i) => (
              <span key={i}>{line}{i === 0 && <br />}</span>
            ))}
          </GradientText>
        </motion.h1>

        {/* Typewriter subtitle */}
        <motion.div variants={fadeUpItem} style={{
          fontSize: 'clamp(16px, 2.5vw, 22px)',
          fontWeight: 600,
          color: 'var(--bk-blue-500)',
          marginBottom: 16,
          minHeight: '1.5em',
        }}>
          <span style={{ opacity: 0.6 }}>Protocols: </span>
          <span style={{ fontFamily: 'var(--font-mono)' }}>{typewriterDisplay}</span>
          <motion.span
            animate={{ opacity: [1, 0, 1] }}
            transition={{ duration: 0.8, repeat: Infinity }}
            style={{ color: 'var(--bk-blue-500)', fontWeight: 300 }}
          >|</motion.span>
        </motion.div>

        <motion.p variants={fadeUpItem} style={{
          fontSize: 'clamp(15px, 2vw, 18px)',
          lineHeight: 1.7,
          color: 'var(--ink-700)',
          maxWidth: 720,
          margin: '0 auto 32px',
        }}>
          {t('landing.hero.desc')}
        </motion.p>

        <motion.div variants={fadeUpItem} style={{ display: 'flex', gap: 16, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 56 }}>
          <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.98 }}>
            <Button
              type="primary"
              size="large"
              onClick={() => scrollTo('kien-truc')}
              style={{
                borderRadius: 'var(--radius-btn)',
                fontWeight: 700,
                height: 52,
                padding: '0 32px',
                background: 'linear-gradient(135deg, var(--bk-navy-700), var(--bk-blue-500))',
                border: 'none',
                boxShadow: '0 4px 16px rgba(0, 50, 145, 0.3)',
                fontSize: 15,
              }}
            >
              {t('landing.hero.btnExplore')}
            </Button>
          </motion.div>
          <motion.div whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.98 }}>
            <Button
              size="large"
              onClick={() => scrollTo('lab')}
              style={{
                borderRadius: 'var(--radius-btn)',
                fontWeight: 600,
                height: 52,
                padding: '0 32px',
                background: 'rgba(255,255,255,0.8)',
                backdropFilter: 'blur(10px)',
                border: '1px solid var(--rule)',
                fontSize: 15,
              }}
            >
              {t('landing.hero.btnLab')}
            </Button>
          </motion.div>
        </motion.div>

        <motion.div
          variants={fadeUpItem}
          whileHover={{ scale: 1.02 }}
          transition={{ duration: 0.3 }}
        >
          <PlaceholderImage
            path="hero/sa-ban-overview.jpg"
            alt={t('landing.hero.imgAlt')}
            ratio="16/9"
            caption={t('landing.hero.imgCaption')}
          />
        </motion.div>
      </motion.div>
    </section>
  )
}