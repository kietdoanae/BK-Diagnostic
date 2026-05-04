import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

export default function Footer() {
  const { t } = useTranslation()

  const NAV_LINKS = t('landing.footer.navLinks', { returnObjects: true })
  const descriptionParts = t('landing.footer.description').split('\n')

  return (
    <footer style={{
      background: 'var(--bk-navy-900)',
      color: '#fff',
      padding: '56px 24px 32px',
    }}>
      <div className="landing-container">
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
          gap: 32,
          marginBottom: 32,
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
              <img src={logoSvg} alt="BK Diagnostic" style={{ width: 36, height: 36 }} />
              <span style={{ fontWeight: 700, fontSize: 17, color: '#fff' }}>BK Diagnostic</span>
            </div>
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
          </div>

          <div>
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
                  <a href={link.href} style={{
                    color: 'rgba(255,255,255,0.75)',
                    fontSize: 14,
                    textDecoration: 'none',
                  }}>{link.label}</a>
                </li>
              ))}
            </ul>
          </div>

          <div>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>{t('landing.footer.linksLabel')}</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li><Link to="/login" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>{t('landing.footer.linkLogin')}</Link></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>{t('landing.footer.linkDocs')}</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>{t('landing.footer.linkSource')}</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>{t('landing.footer.linkContact')}</a></li>
            </ul>
          </div>

          <div>
            <h4 style={{
              margin: '0 0 12px',
              fontSize: 11,
              color: 'rgba(255,255,255,0.5)',
              letterSpacing: 2,
              fontWeight: 700,
              textTransform: 'uppercase',
            }}>{t('landing.footer.schoolLabel')}</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>{t('landing.footer.schoolName')}</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>{t('landing.footer.facultyName')}</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>{t('landing.footer.deptName')}</li>
            </ul>
          </div>
        </div>

        <div style={{
          borderTop: '1px solid rgba(255,255,255,0.15)',
          paddingTop: 20,
          textAlign: 'center',
          fontSize: 12,
          color: 'rgba(255,255,255,0.5)',
        }}>
          {t('landing.footer.copyright')}
        </div>
      </div>
    </footer>
  )
}