// ════════════════════════════════════════════════════════════════════════════
//  i18n setup — react-i18next với 2 ngôn ngữ vi (mặc định) + en
//
//  Sử dụng:
//    import { useTranslation } from 'react-i18next'
//    const { t, i18n } = useTranslation()
//    <Text>{t('common.save')}</Text>
//    i18n.changeLanguage('en')
//
//  Hoặc gọi trực tiếp ngoài React:
//    import i18n from '../i18n'
//    i18n.t('common.save')
// ════════════════════════════════════════════════════════════════════════════

import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import LanguageDetector from 'i18next-browser-languagedetector'

import vi from './locales/vi.json'
import en from './locales/en.json'

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      vi: { translation: vi },
      en: { translation: en },
    },
    fallbackLng: 'vi',          // Vietnamese is the primary
    supportedLngs: ['vi', 'en'],
    interpolation: { escapeValue: false }, // React đã tự escape XSS
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage'],
      lookupLocalStorage: 'bk_lang',
    },
  })

export default i18n
