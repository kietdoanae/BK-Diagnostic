import { motion } from 'framer-motion'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useInViewAnimation, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

export default function Footer() {
  const { t } = useTranslation()
  const { ref, inView } = useInViewAnimation(0.1)

  const NAV_LINKS = t('landing.footer.navLinks', { returnObjects: true })
  const descriptionParts = t('landing.footer.description').split('\n')

  return (
    <motion.footer
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUpStagger}
      style={{
        background: 'linear-gradient(180deg, var(--bk-navy-900) 0%, #060f3a 100%)',
        color: '#fff',
        padding: '56px 24px 32px',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {/* Decorative background elements */}
      <div style={{
        position: 'absolute',
        top: 0,
        left: '10%',
        width: 300,
        height: 300,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(21,101,192,0.08) 0%, transparent 70%)',
        pointerEvents: 'none',
      }} />
      <div style={{
        position: 'absolute',
        bottom: 0,
        right: '10%',
        width: 200,
        height: 200,
        borderRadius: '50%',
        background: 'radial-gradient(circle, rgba(212,160,23,0.06) 0%, transparent 70%)',
        pointerEvents: 'none',
      }} />

      <div className="landing-container" style={{ position: 'relative', zIndex: 1 }}>
        <motion.div
          variants={fadeUpStagger}
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 32,
            marginBottom: 32,
          }}
        >
          {/* Logo & Description */}
          <motion.div variants={fadeUpItem}>
            <motion.div
              style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}
              whileHover={{ x: 4 }}
            >
              <img src={logoSvg} alt="BK Diagnostic" style={{ width: 36, height: 36 }} />
              <span style={{ fontWeight: 700, fontSize: 17, color: '#fff' }}>BK Diagnostic</span>
            </motion.div>
            <p style={{
              fontSize: 13,
              color: 'rgba(255,255,255,0.65)',
              lineHeight: 1.7,
              margin: 0,
              maxWidth: 280,
            }}>
              {descriptionParts.map((line, i) => (
                <span key={i}>{line}{i < descriptionParts.length - 1 && <br />}</span>
              ))}
            </p>
          </motion.div>

          {/* Nav Links */}
          <motion.div variants={fadeUpItem}>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>{t('landing.footer.navLabel')}</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {NAV_LINKS.map((link, i) => (
                <li key={i}>
                  <motion.a
                    href={link.href}
                    whileHover={{ x: 6, color: '#fff' }}
                    style={{
                      color: 'rgba(255,255,255,0.75)',
                      fontSize: 14,
                      textDecoration: 'none',
                      display: 'inline-block',
                      transition: 'color 0.2s ease',
                    }}
                  >{link.label}</motion.a>
                </li>
              ))}
            </ul>
          </motion.div>

          {/* Quick Links */}
          <motion.div variants={fadeUpItem}>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>{t('landing.footer.linksLabel')}</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[
                { to: '/login', label: t('landing.footer.linkLogin') },
                { href: '#', label: t('landing.footer.linkDocs') },
                { href: '#', label: t('landing.footer.linkSource') },
                { href: '#', label: t('landing.footer.linkContact') },
              ].map((item, i) => (
                <li key={i}>
                  {item.to ? (
                    <motion.div whileHover={{ x: 6 }} style={{ display: 'inline-block' }}>
                      <Link to={item.to} style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>{item.label}</Link>
                    </motion.div>
                  ) : (
                    <motion.a
                      href={item.href}
                      whileHover={{ x: 6, color: '#fff' }}
                      style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none', display: 'inline-block' }}
                    >{item.label}</motion.a>
                  )}
                </li>
              ))}
            </ul>
          </motion.div>

          {/* School Info */}
          <motion.div variants={fadeUpItem}>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>{t('landing.footer.schoolLabel')}</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {[t('landing.footer.schoolName'), t('landing.footer.facultyName'), t('landing.footer.deptName')].map((text, i) => (
                <motion.li
                  key={i}
                  style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}
                  whileHover={{ x: 4 }}
                >{text}</motion.li>
              ))}
            </ul>
          </motion.div>
        </motion.div>

        {/* Copyright */}
        <motion.div
          variants={fadeUpItem}
          style={{
            borderTop: '1px solid rgba(255,255,255,0.1)',
            paddingTop: 20,
            textAlign: 'center',
            fontSize: 12,
            color: 'rgba(255,255,255,0.4)',
          }}
        >
          {t('landing.footer.copyright')}
        </motion.div>
      </div>
    </motion.footer>
  )
}