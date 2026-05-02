# BK Diagnostic — Bộ biểu tượng

Hướng **Hex Pulse** · xuất ngày 27/04/2026.

## Bảng màu chính
| Token | Hex | Dùng cho |
|---|---|---|
| `--bk-deep` | `#003E7E` | Màu chính, theme color |
| `--bk-blue` | `#0066B3` | Gradient phụ |
| `--bk-bright` | `#0094D9` | Viền lục giác, accent xanh sáng |
| `--bk-ink` | `#001E3F` | Nền tối, background |
| `--bk-amber` | `#F5B700` | ECG, accent vàng |

## Cấu trúc file

### SVG (vector — ưu tiên dùng cho web)
- `icon.svg` — bản đầy đủ (lục giác + ECG + pattern tổ ong), nền gradient bo góc 96px
- `icon-simple.svg` — bản tối giản, stroke dày — **dùng cho favicon hoặc kích thước nhỏ**
- `icon-on-light.svg` — không nền (transparent), dùng trên header sáng
- `icon-mono.svg` — đơn sắc (`currentColor`), dùng cho in dấu, thêu, embroidery
- `wordmark-horizontal.svg` — logo + chữ "BK Diagnostic" + tagline

### PNG (raster — cho app store, social, fallback)
- `favicon-16.png`, `favicon-32.png`, `favicon-48.png` — favicon trình duyệt
- `apple-touch-icon.png` (180×180) — iOS home screen
- `icon-64.png` → `icon-1024.png` — kích thước phổ biến
- `icon-192.png`, `icon-512.png` — PWA / Android adaptive
- `wordmark-horizontal.png` (1200×320) — header website / OG image

### Khác
- `site.webmanifest` — manifest cho PWA
- `favicon-snippet.html` — đoạn `<link>` paste vào `<head>`

## Web — paste vào `<head>`
```html
<link rel="icon" type="image/svg+xml" href="/icon.svg" />
<link rel="icon" type="image/png" sizes="32x32" href="/favicon-32.png" />
<link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png" />
<link rel="manifest" href="/site.webmanifest" />
<meta name="theme-color" content="#003E7E" />
```

## React Native / Flutter — assets cần copy
- iOS: `apple-touch-icon.png` (180), `icon-1024.png` (App Store)
- Android: `icon-192.png`, `icon-512.png` (adaptive icon foreground)

## Quy tắc sử dụng
1. **Vùng an toàn**: chừa khoảng trống tối thiểu = ½ chiều cao của lục giác xung quanh icon
2. **Kích thước tối thiểu**: dùng `icon-simple.svg` khi nhỏ hơn 48px
3. **Không**: bóp méo tỉ lệ, đổi màu xanh chính, thêm shadow ngoài
