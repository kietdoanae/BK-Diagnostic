import { useState, useEffect } from 'react'

/**
 * Hiển thị ảnh thật nếu file tồn tại tại path,
 * fallback về khung "ẢNH SẮP CÓ" nếu chưa có.
 *
 * @param path  Đường dẫn tương đối từ /landing/, vd: "hero/sa-ban-overview.jpg"
 * @param alt   Alt text mô tả ảnh
 * @param ratio Tỷ lệ khung, vd: "16/9", "4/3", "1/1"
 * @param caption Mô tả "Hình X" hiển thị bên dưới (optional)
 */
export default function PlaceholderImage({ path, alt, ratio = '16/9', caption }) {
  const src = `/landing/${path}`
  const [exists, setExists] = useState(true)

  useEffect(() => {
    const img = new Image()
    img.onload = () => setExists(true)
    img.onerror = () => setExists(false)
    img.src = src
  }, [src])

  return (
    <figure style={{ margin: 0 }}>
      <div style={{
        aspectRatio: ratio,
        borderRadius: 'var(--radius-card)',
        border: '1px solid var(--rule)',
        background: exists ? 'transparent' : '#F3F4F6',
        overflow: 'hidden',
        boxShadow: '0 1px 3px rgba(0,0,0,0.04)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        gap: 8,
        padding: 16,
      }}>
        {exists ? (
          <img
            src={src}
            alt={alt}
            style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
            loading="lazy"
          />
        ) : (
          <>
            <div style={{ fontSize: 32, opacity: 0.4 }}>📷</div>
            <div style={{ fontSize: 12, color: 'var(--ink-500)', textAlign: 'center', fontWeight: 600 }}>
              Chờ ảnh
            </div>
            <code style={{ fontSize: 11, color: 'var(--ink-500)', fontFamily: 'var(--font-mono)' }}>
              landing/{path}
            </code>
          </>
        )}
      </div>
      {caption && (
        <figcaption style={{
          marginTop: 8,
          fontSize: 12,
          fontStyle: 'italic',
          color: 'var(--gold-500)',
          fontWeight: 500,
          textAlign: 'center',
        }}>
          {caption}
        </figcaption>
      )}
    </figure>
  )
}
