import { Dropdown, Button } from 'antd'
import { GlobalOutlined, CheckOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

/**
 * Nút chuyển ngôn ngữ — dropdown 2 lựa chọn (vi / en).
 * Dùng được trong Landing navbar (light) hoặc App header (light) với prop variant.
 *
 *   <LanguageSwitcher variant="light" />     → background trắng nhẹ
 *   <LanguageSwitcher variant="dark" />      → background trong suốt cho overlay
 *   <LanguageSwitcher compact />             → chỉ icon, không hiện nhãn
 */
export default function LanguageSwitcher({ variant = 'light', compact = false }) {
  const { i18n, t } = useTranslation()
  const current = (i18n.language || 'vi').slice(0, 2)

  const langs = [
    { code: 'vi', label: t('lang.vi'), flag: '🇻🇳' },
    { code: 'en', label: t('lang.en'), flag: '🇬🇧' },
  ]

  const items = langs.map((l) => ({
    key: l.code,
    label: (
      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 120 }}>
        <span style={{ fontSize: 14 }}>{l.flag}</span>
        <span style={{ flex: 1 }}>{l.label}</span>
        {current === l.code && <CheckOutlined style={{ color: '#1565C0' }} />}
      </span>
    ),
  }))

  function handleClick({ key }) {
    i18n.changeLanguage(key)
  }

  const currentLang = langs.find((l) => l.code === current) || langs[0]

  return (
    <Dropdown
      menu={{ items, onClick: handleClick, selectedKeys: [current] }}
      placement="bottomRight"
      trigger={['click']}
    >
      <Button
        type={variant === 'dark' ? 'text' : 'default'}
        icon={<GlobalOutlined />}
        style={{
          height: 40,
          borderRadius: 10,
          fontWeight: 600,
          ...(variant === 'dark' && {
            color: '#fff',
            background: 'rgba(255,255,255,0.10)',
            border: '1px solid rgba(255,255,255,0.20)',
          }),
        }}
      >
        {!compact && (
          <span style={{ marginLeft: 4 }}>
            {currentLang.flag} {current.toUpperCase()}
          </span>
        )}
      </Button>
    </Dropdown>
  )
}
