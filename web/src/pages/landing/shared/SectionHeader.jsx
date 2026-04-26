/**
 * Header chuẩn cho mỗi section: eyebrow (uppercase nhỏ), title (lớn), sub (mô tả).
 *
 * @param eyebrow Chữ uppercase nhỏ phía trên title
 * @param title   Tiêu đề section
 * @param sub     Phụ đề mô tả ngắn (optional)
 * @param align   'center' | 'left' (default 'center')
 */
export default function SectionHeader({ eyebrow, title, sub, align = 'center' }) {
  return (
    <div style={{
      textAlign: align,
      marginBottom: 48,
      maxWidth: align === 'center' ? 720 : '100%',
      marginLeft: align === 'center' ? 'auto' : 0,
      marginRight: align === 'center' ? 'auto' : 0,
    }}>
      {eyebrow && (
        <div style={{
          color: 'var(--bk-blue-500)',
          fontWeight: 700,
          fontSize: 12,
          textTransform: 'uppercase',
          letterSpacing: 2,
          marginBottom: 8,
        }}>
          {eyebrow}
        </div>
      )}
      <h2 style={{
        fontSize: 'clamp(24px, 4vw, 36px)',
        fontWeight: 800,
        margin: '0 0 12px',
        color: 'var(--ink-900)',
        letterSpacing: '-0.02em',
        lineHeight: 1.2,
      }}>
        {title}
      </h2>
      {sub && (
        <p style={{
          fontSize: 16,
          lineHeight: 1.7,
          color: 'var(--ink-500)',
          margin: 0,
        }}>
          {sub}
        </p>
      )}
    </div>
  )
}
