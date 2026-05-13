# Landing Page Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Triển khai lại trang chủ `/` theo phong cách Lab Whitepaper, mở rộng nội dung 3 trụ cột (Hardware/Mobile/Web Platform) và bản địa hóa toàn bộ tiếng Việt theo spec `docs/superpowers/specs/2026-04-26-landing-redesign-design.md`.

**Architecture:** Tách trang thành 12 section component riêng biệt trong `web/src/pages/landing/sections/`, dùng shared components (Navbar/SectionHeader/FigureCaption/AnimatedNumber/PipelineDiagram/PlaceholderImage). Style dùng inline styles tham chiếu CSS variables trong `design-tokens.css`. Animation bằng framer-motion + react-intersection-observer. Giữ Ant Design cho Button/Avatar/Tag.

**Tech Stack:** React 19 · Vite 8 · Ant Design 6 · framer-motion 11 · react-intersection-observer 9 · Be Vietnam Pro (Google Fonts) · React Router 7

---

## Setup

### Task S0: Tạo branch dev

**Files:** Không

- [ ] **Step 1: Tạo và chuyển sang branch mới**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git checkout main
git pull origin main
git checkout -b feature/landing-redesign
```

Expected: `Switched to a new branch 'feature/landing-redesign'`

---

## PHASE 1 — Foundation

Mục tiêu: Skeleton trang chạy được với placeholder, content tiếng Việt, không animation. Verify ở 3 breakpoint trước khi thêm animation.

### Task 1.1: Cài dependencies

**Files:** `web/package.json` (auto-update)

- [ ] **Step 1: Cài framer-motion + react-intersection-observer**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web
npm install framer-motion@^11.0.0 react-intersection-observer@^9.5.0
```

Expected: `added 2 packages`

- [ ] **Step 2: Verify đã có trong package.json**

```bash
node -e "const p=require('./package.json'); console.log(p.dependencies['framer-motion'], p.dependencies['react-intersection-observer'])"
```

Expected: in ra 2 phiên bản, không lỗi.

- [ ] **Step 3: Commit**

```bash
git add package.json package-lock.json
git commit -m "feat(landing): add framer-motion and intersection-observer deps"
```

---

### Task 1.2: Thêm Google Fonts Be Vietnam Pro + JetBrains Mono

**Files:**
- Modify: `web/index.html`

- [ ] **Step 1: Thay link Inter bằng Be Vietnam Pro + JetBrains Mono**

Mở `web/index.html`, thay dòng 9 (link Inter) bằng:

```html
<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet" />
```

- [ ] **Step 2: Đổi `<html lang="en">` thành `<html lang="vi">`**

Sửa dòng 2 từ `<html lang="en">` thành `<html lang="vi">`.

- [ ] **Step 3: Cập nhật `<title>`**

Sửa dòng 6 từ `<title>BK Diagnostic</title>` thành:

```html
<title>BK Diagnostic — Hệ thống chẩn đoán xe và đào tạo CAN bus</title>
```

- [ ] **Step 4: Thêm meta description**

Thêm sau dòng `<title>`:

```html
<meta name="description" content="Nền tảng tích hợp ba thành phần — phần cứng nhúng, ứng dụng Android và web platform — phục vụ giảng dạy giao thức CAN bus cho sinh viên ngành Kỹ thuật Ô tô tại HCMUT." />
```

- [ ] **Step 5: Verify dev server chạy**

```bash
cd web
npm run dev
```

Mở `http://localhost:5173`, mở DevTools → Network, filter "fonts.googleapis", confirm font Be Vietnam Pro được load. Tab tiêu đề hiển thị "BK Diagnostic — Hệ thống...".

- [ ] **Step 6: Commit**

```bash
git add web/index.html
git commit -m "feat(landing): switch to Be Vietnam Pro font, set lang=vi"
```

---

### Task 1.3: Tạo design-tokens.css

**Files:**
- Create: `web/src/styles/design-tokens.css`
- Modify: `web/src/main.jsx`

- [ ] **Step 1: Tạo file design-tokens.css**

Tạo `web/src/styles/design-tokens.css` với nội dung:

```css
/* BK Diagnostic — Design Tokens
   Reference: docs/superpowers/specs/2026-04-26-landing-redesign-design.md */

:root {
  /* Primary — BK Identity */
  --bk-navy-900: #0A1E6E;
  --bk-navy-700: #003291;
  --bk-blue-500: #1565C0;
  --bk-blue-100: #DBEAFE;

  /* Neutral — Whitepaper */
  --paper:        #FFFFFF;
  --paper-soft:   #F9FAFB;
  --ink-900:      #111827;
  --ink-700:      #374151;
  --ink-500:      #6B7280;
  --rule:         #E5E7EB;

  /* Accent */
  --gold-500:     #D4A017;
  --green-600:    #16A34A;
  --red-600:      #DC2626;

  /* Typography */
  --font-sans: 'Be Vietnam Pro', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --font-mono: 'JetBrains Mono', 'Courier New', monospace;

  /* Spacing */
  --container-max: 1200px;
  --section-pad-y: 64px;
  --gutter:        24px;
  --radius-card:   12px;
  --radius-btn:    8px;
  --radius-tag:    4px;
}

@media (min-width: 768px) {
  :root {
    --section-pad-y: 112px;
    --gutter:        48px;
  }
}

/* Global resets specific to landing page */
.landing-root {
  font-family: var(--font-sans);
  color: var(--ink-700);
  background: var(--paper);
  -webkit-font-smoothing: antialiased;
}

.landing-root h1, .landing-root h2, .landing-root h3, .landing-root h4 {
  color: var(--ink-900);
  letter-spacing: -0.02em;
  font-weight: 800;
}

.landing-container {
  max-width: var(--container-max);
  margin: 0 auto;
  padding-left: var(--gutter);
  padding-right: var(--gutter);
}

.landing-section {
  padding-top: var(--section-pad-y);
  padding-bottom: var(--section-pad-y);
}

/* Animation defaults — respect reduced motion */
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

- [ ] **Step 2: Import vào main.jsx**

Mở `web/src/main.jsx`, thêm dòng import sau dòng 5 (`import './index.css'`):

```jsx
import './styles/design-tokens.css'
```

- [ ] **Step 3: Verify không vỡ build**

```bash
cd web
npm run build
```

Expected: build thành công, không error.

- [ ] **Step 4: Commit**

```bash
git add web/src/styles/design-tokens.css web/src/main.jsx
git commit -m "feat(landing): add design tokens CSS variables"
```

---

### Task 1.4: Tạo cấu trúc thư mục landing

**Files:** Tạo các thư mục rỗng

- [ ] **Step 1: Tạo cấu trúc thư mục**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web\src
mkdir pages\landing
mkdir pages\landing\sections
mkdir pages\landing\shared
mkdir assets\svg
mkdir assets\svg\three-pillars
```

- [ ] **Step 2: Verify tồn tại**

```bash
ls pages/landing/sections pages/landing/shared assets/svg/three-pillars
```

Expected: các thư mục liệt kê được (chưa có file).

---

### Task 1.5: Tạo SVG logo

**Files:**
- Create: `web/src/assets/svg/bk-diagnostic-logo.svg`

- [ ] **Step 1: Tạo logo SVG**

Tạo file `web/src/assets/svg/bk-diagnostic-logo.svg` với nội dung:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
  <!-- Outer rounded square -->
  <rect x="4" y="4" width="56" height="56" rx="12" fill="#003291" stroke="#003291"/>
  <!-- Letter B -->
  <path d="M16 18 L16 46 L26 46 C30 46 32 43 32 40 C32 37 30 34 26 34 L16 34 M16 34 L26 34 C30 34 32 31 32 28 C32 25 30 22 26 22 L16 22 L16 18 Z" stroke="#FFFFFF" stroke-width="3" fill="none"/>
  <!-- Letter K -->
  <path d="M40 18 L40 46 M40 32 L52 18 M40 32 L52 46" stroke="#FFFFFF" stroke-width="3" fill="none"/>
  <!-- Small CAN line decoration at bottom -->
  <path d="M14 52 L50 52" stroke="#D4A017" stroke-width="2" stroke-dasharray="3 3"/>
</svg>
```

- [ ] **Step 2: Verify hiển thị trong browser**

Mở `http://localhost:5173/src/assets/svg/bk-diagnostic-logo.svg` trong dev server. Expected: thấy hình vuông xanh navy có chữ "BK" trắng và đường vàng đứt dưới.

- [ ] **Step 3: Commit**

```bash
git add web/src/assets/svg/bk-diagnostic-logo.svg
git commit -m "feat(landing): add BK Diagnostic SVG logo"
```

---

### Task 1.6: Tạo SVG hero background mesh

**Files:**
- Create: `web/src/assets/svg/hero-bg-mesh.svg`

- [ ] **Step 1: Tạo hero-bg-mesh.svg**

Tạo file `web/src/assets/svg/hero-bg-mesh.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 720" preserveAspectRatio="xMidYMid slice">
  <defs>
    <radialGradient id="g1" cx="20%" cy="30%" r="60%">
      <stop offset="0%" stop-color="#1565C0" stop-opacity="0.35"/>
      <stop offset="100%" stop-color="#1565C0" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="g2" cx="80%" cy="20%" r="50%">
      <stop offset="0%" stop-color="#0A1E6E" stop-opacity="0.45"/>
      <stop offset="100%" stop-color="#0A1E6E" stop-opacity="0"/>
    </radialGradient>
    <radialGradient id="g3" cx="60%" cy="80%" r="55%">
      <stop offset="0%" stop-color="#003291" stop-opacity="0.3"/>
      <stop offset="100%" stop-color="#003291" stop-opacity="0"/>
    </radialGradient>
    <pattern id="dots" x="0" y="0" width="32" height="32" patternUnits="userSpaceOnUse">
      <circle cx="2" cy="2" r="1" fill="#003291" fill-opacity="0.06"/>
    </pattern>
  </defs>
  <rect width="1440" height="720" fill="#FFFFFF"/>
  <rect width="1440" height="720" fill="url(#dots)"/>
  <rect width="1440" height="720" fill="url(#g1)"/>
  <rect width="1440" height="720" fill="url(#g2)"/>
  <rect width="1440" height="720" fill="url(#g3)"/>
</svg>
```

- [ ] **Step 2: Commit**

```bash
git add web/src/assets/svg/hero-bg-mesh.svg
git commit -m "feat(landing): add hero gradient mesh SVG"
```

---

### Task 1.7: Tạo SVG context-diagram

**Files:**
- Create: `web/src/assets/svg/context-diagram.svg`

- [ ] **Step 1: Tạo context-diagram.svg**

Tạo file `web/src/assets/svg/context-diagram.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 280" fill="none">
  <style>
    .lbl { font: 600 13px 'Be Vietnam Pro', sans-serif; fill: #003291; }
    .sub { font: 400 11px 'Be Vietnam Pro', sans-serif; fill: #6B7280; }
  </style>

  <!-- Box 1 — Vấn đề -->
  <rect x="20" y="100" width="120" height="80" rx="12" fill="#FEF2F2" stroke="#DC2626" stroke-width="2"/>
  <text x="80" y="130" class="lbl" text-anchor="middle">Vấn đề</text>
  <text x="80" y="148" class="sub" text-anchor="middle">Thiết bị</text>
  <text x="80" y="162" class="sub" text-anchor="middle">đắt &amp; đóng kín</text>

  <!-- Arrow 1 -->
  <path d="M145 140 L175 140" stroke="#6B7280" stroke-width="2"/>
  <polygon points="175,135 185,140 175,145" fill="#6B7280"/>

  <!-- Box 2 — Giải pháp -->
  <rect x="190" y="100" width="120" height="80" rx="12" fill="#DBEAFE" stroke="#1565C0" stroke-width="2"/>
  <text x="250" y="130" class="lbl" text-anchor="middle">Giải pháp</text>
  <text x="250" y="148" class="sub" text-anchor="middle">Hệ thống mở</text>
  <text x="250" y="162" class="sub" text-anchor="middle">tự thiết kế</text>

  <!-- Arrow 2 -->
  <path d="M315 140 L345 140" stroke="#6B7280" stroke-width="2"/>
  <polygon points="345,135 355,140 345,145" fill="#6B7280"/>

  <!-- Box 3 — Mục tiêu -->
  <rect x="360" y="100" width="120" height="80" rx="12" fill="#F0FDF4" stroke="#16A34A" stroke-width="2"/>
  <text x="420" y="130" class="lbl" text-anchor="middle">Mục tiêu</text>
  <text x="420" y="148" class="sub" text-anchor="middle">Đào tạo CAN</text>
  <text x="420" y="162" class="sub" text-anchor="middle">cho sinh viên</text>
</svg>
```

- [ ] **Step 2: Commit**

```bash
git add web/src/assets/svg/context-diagram.svg
git commit -m "feat(landing): add context diagram SVG"
```

---

### Task 1.8: Tạo SVG pipeline-diagram

**Files:**
- Create: `web/src/assets/svg/pipeline-diagram.svg`

- [ ] **Step 1: Tạo pipeline-diagram.svg**

Tạo file `web/src/assets/svg/pipeline-diagram.svg`:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1080 220" fill="none">
  <style>
    .node-label  { font: 700 13px 'Be Vietnam Pro', sans-serif; fill: #003291; }
    .node-icon   { font: 600 24px 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif; }
    .edge-label  { font: 500 11px 'JetBrains Mono', monospace; fill: #6B7280; }
  </style>

  <!-- 6 nodes -->
  <g id="n1">
    <circle cx="80" cy="100" r="44" fill="#EFF6FF" stroke="#1565C0" stroke-width="2"/>
    <text x="80" y="92" text-anchor="middle" class="node-icon">🚗</text>
    <text x="80" y="170" text-anchor="middle" class="node-label">Xe</text>
  </g>

  <g id="n2">
    <circle cx="260" cy="100" r="44" fill="#FFF7ED" stroke="#EA580C" stroke-width="2"/>
    <text x="260" y="92" text-anchor="middle" class="node-icon">🔌</text>
    <text x="260" y="170" text-anchor="middle" class="node-label">MCP2515</text>
  </g>

  <g id="n3">
    <circle cx="440" cy="100" r="44" fill="#F0FDF4" stroke="#16A34A" stroke-width="2"/>
    <text x="440" y="92" text-anchor="middle" class="node-icon">⚙️</text>
    <text x="440" y="170" text-anchor="middle" class="node-label">STM32</text>
  </g>

  <g id="n4">
    <circle cx="620" cy="100" r="44" fill="#FAF5FF" stroke="#7C3AED" stroke-width="2"/>
    <text x="620" y="92" text-anchor="middle" class="node-icon">🔗</text>
    <text x="620" y="170" text-anchor="middle" class="node-label">CP2102</text>
  </g>

  <g id="n5">
    <circle cx="800" cy="100" r="44" fill="#EFF6FF" stroke="#1565C0" stroke-width="2"/>
    <text x="800" y="92" text-anchor="middle" class="node-icon">📱</text>
    <text x="800" y="170" text-anchor="middle" class="node-label">App</text>
  </g>

  <g id="n6">
    <circle cx="980" cy="100" r="44" fill="#DBEAFE" stroke="#0A1E6E" stroke-width="2"/>
    <text x="980" y="92" text-anchor="middle" class="node-icon">☁️</text>
    <text x="980" y="170" text-anchor="middle" class="node-label">Web</text>
  </g>

  <!-- Connectors -->
  <path id="e1" d="M124 100 L216 100" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <text x="170" y="92" text-anchor="middle" class="edge-label">CAN</text>

  <path id="e2" d="M304 100 L396 100" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <text x="350" y="92" text-anchor="middle" class="edge-label">SPI</text>

  <path id="e3" d="M484 100 L576 100" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <text x="530" y="92" text-anchor="middle" class="edge-label">UART</text>

  <path id="e4" d="M664 100 L756 100" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <text x="710" y="92" text-anchor="middle" class="edge-label">USB</text>

  <path id="e5" d="M844 100 L936 100" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <text x="890" y="92" text-anchor="middle" class="edge-label">HTTPS</text>
</svg>
```

- [ ] **Step 2: Commit**

```bash
git add web/src/assets/svg/pipeline-diagram.svg
git commit -m "feat(landing): add pipeline diagram SVG"
```

---

### Task 1.9: Tạo 3 SVG icon trụ cột

**Files:**
- Create: `web/src/assets/svg/three-pillars/icon-hardware.svg`
- Create: `web/src/assets/svg/three-pillars/icon-mobile.svg`
- Create: `web/src/assets/svg/three-pillars/icon-web.svg`

- [ ] **Step 1: Tạo icon-hardware.svg**

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" stroke="#003291" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="10" y="14" width="44" height="36" rx="3"/>
  <rect x="18" y="22" width="14" height="10"/>
  <rect x="36" y="22" width="10" height="6"/>
  <circle cx="41" cy="38" r="3"/>
  <line x1="14" y1="50" x2="14" y2="56"/>
  <line x1="22" y1="50" x2="22" y2="56"/>
  <line x1="30" y1="50" x2="30" y2="56"/>
  <line x1="38" y1="50" x2="38" y2="56"/>
  <line x1="46" y1="50" x2="46" y2="56"/>
  <line x1="50" y1="22" x2="54" y2="22" stroke="#D4A017"/>
  <line x1="50" y1="28" x2="54" y2="28" stroke="#D4A017"/>
</svg>
```

- [ ] **Step 2: Tạo icon-mobile.svg**

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" stroke="#003291" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="18" y="6" width="28" height="52" rx="4"/>
  <line x1="28" y1="52" x2="36" y2="52"/>
  <rect x="22" y="14" width="20" height="14" rx="2"/>
  <path d="M26 24 L26 22 Q26 18 30 18 L34 18 Q38 18 38 22 L38 24" stroke="#D4A017"/>
  <line x1="22" y1="34" x2="42" y2="34"/>
  <line x1="22" y1="40" x2="34" y2="40"/>
  <line x1="22" y1="46" x2="38" y2="46"/>
</svg>
```

- [ ] **Step 3: Tạo icon-web.svg**

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" fill="none" stroke="#003291" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
  <rect x="6" y="10" width="52" height="40" rx="3"/>
  <line x1="6" y1="20" x2="58" y2="20"/>
  <circle cx="12" cy="15" r="1.2" fill="#003291"/>
  <circle cx="17" cy="15" r="1.2" fill="#003291"/>
  <circle cx="22" cy="15" r="1.2" fill="#003291"/>
  <rect x="12" y="26" width="14" height="18" fill="#DBEAFE" stroke="none"/>
  <rect x="30" y="26" width="22" height="8" fill="#DBEAFE" stroke="none"/>
  <rect x="30" y="38" width="22" height="6" fill="#DBEAFE" stroke="none"/>
  <line x1="14" y1="56" x2="50" y2="56" stroke="#D4A017"/>
</svg>
```

- [ ] **Step 4: Commit**

```bash
git add web/src/assets/svg/three-pillars/
git commit -m "feat(landing): add 3 pillar icon SVGs"
```

---

### Task 1.10: Tạo SVG timeline-arrow

**Files:**
- Create: `web/src/assets/svg/timeline-arrow.svg`

- [ ] **Step 1: Tạo timeline-arrow.svg**

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 80 24" fill="none">
  <line x1="4" y1="12" x2="68" y2="12" stroke="#9CA3AF" stroke-width="2" stroke-dasharray="4 4"/>
  <polygon points="68,6 76,12 68,18" fill="#9CA3AF"/>
</svg>
```

- [ ] **Step 2: Commit**

```bash
git add web/src/assets/svg/timeline-arrow.svg
git commit -m "feat(landing): add timeline arrow SVG"
```

---

### Task 1.11: Shared component PlaceholderImage

**Files:**
- Create: `web/src/pages/landing/shared/PlaceholderImage.jsx`

- [ ] **Step 1: Tạo PlaceholderImage.jsx**

Tạo file `web/src/pages/landing/shared/PlaceholderImage.jsx`:

```jsx
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
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/PlaceholderImage.jsx
git commit -m "feat(landing): add PlaceholderImage shared component"
```

---

### Task 1.12: Shared component SectionHeader

**Files:**
- Create: `web/src/pages/landing/shared/SectionHeader.jsx`

- [ ] **Step 1: Tạo SectionHeader.jsx**

```jsx
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
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/SectionHeader.jsx
git commit -m "feat(landing): add SectionHeader shared component"
```

---

### Task 1.13: Shared component AnimatedNumber (static version)

**Files:**
- Create: `web/src/pages/landing/shared/AnimatedNumber.jsx`

- [ ] **Step 1: Tạo AnimatedNumber.jsx (giai đoạn 1: chỉ render giá trị tĩnh)**

```jsx
/**
 * Hiển thị số nổi bật. Phase 1: render tĩnh, phase 2 sẽ thêm count-up animation.
 *
 * @param value   Số hoặc text (vd: "8+", "50+")
 * @param label   Nhãn dưới số (uppercase)
 */
export default function AnimatedNumber({ value, label }) {
  return (
    <div style={{ textAlign: 'center', padding: '0 8px' }}>
      <div style={{
        fontSize: 'clamp(32px, 5vw, 48px)',
        fontWeight: 900,
        color: '#FFFFFF',
        lineHeight: 1,
        marginBottom: 6,
      }}>
        {value}
      </div>
      <div style={{
        fontSize: 11,
        color: 'rgba(255,255,255,0.7)',
        textTransform: 'uppercase',
        letterSpacing: 2,
        fontWeight: 600,
        lineHeight: 1.4,
      }}>
        {label}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/AnimatedNumber.jsx
git commit -m "feat(landing): add AnimatedNumber static version"
```

---

### Task 1.14: Shared component PipelineDiagram (static)

**Files:**
- Create: `web/src/pages/landing/shared/PipelineDiagram.jsx`

- [ ] **Step 1: Tạo PipelineDiagram.jsx (wrap SVG + caption)**

```jsx
import pipelineSvg from '../../../assets/svg/pipeline-diagram.svg'

export default function PipelineDiagram() {
  return (
    <figure style={{ margin: '24px 0 0', textAlign: 'center' }}>
      <img
        src={pipelineSvg}
        alt="Pipeline dữ liệu từ xe đến cloud: Xe → MCP2515 → STM32 → CP2102 → App → Web"
        style={{ maxWidth: '100%', height: 'auto' }}
      />
      <figcaption style={{
        marginTop: 12,
        fontSize: 12,
        fontStyle: 'italic',
        color: 'var(--gold-500)',
        fontWeight: 500,
      }}>
        Hình 3 — Pipeline dữ liệu từ xe đến cloud.
      </figcaption>
    </figure>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/PipelineDiagram.jsx
git commit -m "feat(landing): add PipelineDiagram component"
```

---

### Task 1.15: Shared component Navbar

**Files:**
- Create: `web/src/pages/landing/shared/Navbar.jsx`

- [ ] **Step 1: Tạo Navbar.jsx**

```jsx
import { useState } from 'react'
import { Button, Avatar, Drawer } from 'antd'
import { MenuOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../../../hooks/useAuth'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

const NAV_ITEMS = [
  { href: '#tong-quan',  label: 'Tổng quan' },
  { href: '#kien-truc',  label: 'Kiến trúc' },
  { href: '#mobile',     label: 'Mobile App' },
  { href: '#web',        label: 'Web Platform' },
  { href: '#lab',        label: 'Lab' },
  { href: '#team',       label: 'Team' },
]

const AVATAR_COLORS = ['#1565C0','#0097A7','#2E7D32','#6A1B9A','#AD1457','#E65100']
function avatarColor(u = '') {
  let h = 0; for (const c of u) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}

export default function Navbar() {
  const { session, profile } = useAuth()
  const navigate = useNavigate()
  const [drawerOpen, setDrawerOpen] = useState(false)

  const username = profile?.username ?? session?.user?.email?.split('@')[0] ?? 'User'
  const initial = username[0]?.toUpperCase() ?? 'U'

  return (
    <>
      <nav style={{
        position: 'fixed', top: 0, left: 0, right: 0, zIndex: 50,
        background: 'rgba(255,255,255,0.92)',
        backdropFilter: 'blur(8px)',
        WebkitBackdropFilter: 'blur(8px)',
        borderBottom: '1px solid var(--rule)',
        height: 64,
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        padding: '0 24px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <img src={logoSvg} alt="BK Diagnostic" style={{ width: 36, height: 36 }} />
          <span style={{ fontWeight: 700, color: 'var(--bk-navy-700)', fontSize: 17 }}>
            BK Diagnostic
          </span>
        </div>

        <div className="nav-desktop" style={{ display: 'none', gap: 28 }}>
          {NAV_ITEMS.map(it => (
            <a key={it.href} href={it.href} style={{
              color: 'var(--ink-700)', fontSize: 14, fontWeight: 500, textDecoration: 'none',
            }}>{it.label}</a>
          ))}
        </div>

        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {session ? (
            <>
              <Avatar
                size={32}
                style={{ background: avatarColor(username), fontWeight: 700, cursor: 'pointer' }}
                onClick={() => navigate('/dashboard')}
              >{initial}</Avatar>
              <Button type="primary" onClick={() => navigate('/dashboard')}>Bảng điều khiển</Button>
            </>
          ) : (
            <Button type="primary" onClick={() => navigate('/login')}>Đăng nhập</Button>
          )}
          <Button
            className="nav-mobile-btn"
            icon={<MenuOutlined />}
            onClick={() => setDrawerOpen(true)}
            style={{ display: 'inline-flex' }}
          />
        </div>
      </nav>

      <Drawer
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={260}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {NAV_ITEMS.map(it => (
            <a key={it.href} href={it.href} onClick={() => setDrawerOpen(false)} style={{
              color: 'var(--ink-700)', fontSize: 16, fontWeight: 500, textDecoration: 'none',
              padding: '8px 0', borderBottom: '1px solid var(--rule)',
            }}>{it.label}</a>
          ))}
        </div>
      </Drawer>

      <style>{`
        @media (min-width: 768px) {
          .nav-desktop { display: flex !important; }
          .nav-mobile-btn { display: none !important; }
        }
      `}</style>
    </>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/Navbar.jsx
git commit -m "feat(landing): add new Navbar with VN nav items + mobile drawer"
```

---

### Task 1.16: Section Hero

**Files:**
- Create: `web/src/pages/landing/sections/Hero.jsx`

- [ ] **Step 1: Tạo Hero.jsx**

```jsx
import { Button } from 'antd'
import PlaceholderImage from '../shared/PlaceholderImage'
import heroBgSvg from '../../../assets/svg/hero-bg-mesh.svg'

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth' })
}

export default function Hero() {
  return (
    <section id="tong-quan" style={{
      position: 'relative',
      paddingTop: 128,
      paddingBottom: 64,
      backgroundImage: `url(${heroBgSvg})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
    }}>
      <div className="landing-container" style={{ textAlign: 'center' }}>
        <div style={{
          display: 'inline-block',
          background: 'var(--bk-blue-100)',
          color: 'var(--bk-navy-700)',
          padding: '6px 16px',
          borderRadius: 20,
          fontSize: 11,
          fontWeight: 700,
          letterSpacing: 2,
          marginBottom: 24,
        }}>
          ĐỒ ÁN TỐT NGHIỆP · HCMUT · 2026
        </div>

        <h1 style={{
          fontSize: 'clamp(36px, 6vw, 56px)',
          fontWeight: 800,
          lineHeight: 1.15,
          margin: '0 0 20px',
          color: 'var(--ink-900)',
          letterSpacing: '-0.02em',
          maxWidth: 800,
          marginLeft: 'auto',
          marginRight: 'auto',
        }}>
          Hệ thống chẩn đoán xe<br />và đào tạo CAN bus
        </h1>

        <p style={{
          fontSize: 'clamp(15px, 2vw, 18px)',
          lineHeight: 1.7,
          color: 'var(--ink-700)',
          maxWidth: 720,
          margin: '0 auto 32px',
        }}>
          Nền tảng tích hợp ba thành phần — phần cứng nhúng, ứng dụng Android và web platform —
          phục vụ giảng dạy giao thức CAN bus và quy trình chẩn đoán xe ô tô cho sinh viên
          ngành Kỹ thuật Ô tô.
        </p>

        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 56 }}>
          <Button
            type="primary"
            size="large"
            onClick={() => scrollTo('kien-truc')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 700, height: 48, padding: '0 24px' }}
          >
            Khám phá kiến trúc →
          </Button>
          <Button
            size="large"
            onClick={() => scrollTo('lab')}
            style={{ borderRadius: 'var(--radius-btn)', fontWeight: 600, height: 48, padding: '0 24px' }}
          >
            Xem hệ thống Lab
          </Button>
        </div>

        <PlaceholderImage
          path="hero/sa-ban-overview.jpg"
          alt="Sa bàn thực hành CAN bus tại phòng lab Bộ môn Kỹ thuật Ô tô"
          ratio="16/9"
          caption="Hình 1 — Sa bàn thực hành CAN bus tại phòng lab Bộ môn Kỹ thuật Ô tô, Khoa Kỹ thuật Giao thông HCMUT."
        />
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/Hero.jsx
git commit -m "feat(landing): add Hero section"
```

---

### Task 1.17: Section MetricStrip

**Files:**
- Create: `web/src/pages/landing/sections/MetricStrip.jsx`

- [ ] **Step 1: Tạo MetricStrip.jsx**

```jsx
import AnimatedNumber from '../shared/AnimatedNumber'

const METRICS = [
  { value: '8+',  label: 'Hãng xe\nhỗ trợ' },
  { value: '50+', label: 'Thông số\ncảm biến' },
  { value: '6',   label: 'Buổi học\nthực hành' },
  { value: '3',   label: 'Thành phần\ntích hợp' },
  { value: '5',   label: 'Giao thức\nchuẩn đoán' },
]

export default function MetricStrip() {
  return (
    <section style={{
      background: 'var(--bk-navy-700)',
      padding: '40px 24px',
    }}>
      <div className="landing-container" style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(120px, 1fr))',
        gap: 24,
        alignItems: 'center',
      }}>
        {METRICS.map((m, i) => (
          <div key={i} style={{
            borderRight: i < METRICS.length - 1 ? '1px solid rgba(255,255,255,0.15)' : 'none',
          }}>
            <AnimatedNumber
              value={m.value}
              label={m.label.split('\n').map((t, j) => (
                <span key={j} style={{ display: 'block' }}>{t}</span>
              ))}
            />
          </div>
        ))}
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/MetricStrip.jsx
git commit -m "feat(landing): add MetricStrip section"
```

---

### Task 1.18: Section Context (Bối cảnh & Mục tiêu)

**Files:**
- Create: `web/src/pages/landing/sections/Context.jsx`

- [ ] **Step 1: Tạo Context.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'
import contextDiagramSvg from '../../../assets/svg/context-diagram.svg'

export default function Context() {
  return (
    <section className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="BỐI CẢNH"
          title="Vì sao cần đồ án này?"
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))',
          gap: 48,
          alignItems: 'center',
        }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Các thiết bị chẩn đoán xe thương mại (Autel, Launch, Bosch ESI…) có giá thành cao
              và đóng kín mã nguồn. Sinh viên ngành Kỹ thuật Ô tô khó tiếp cận tầng thấp của giao
              thức CAN, không thể quan sát trực tiếp khung dữ liệu thô hay can thiệp vào thuật
              toán giải mã.
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Đồ án xây dựng một hệ thống mở cho phòng thí nghiệm: phần cứng tự thiết kế dùng
              linh kiện phổ thông, mã nguồn được cung cấp đầy đủ, đi kèm tài liệu thực hành 6
              buổi học từ cơ bản đến nâng cao.
            </p>
            <p style={{ fontSize: 16, lineHeight: 1.7, color: 'var(--ink-700)', margin: 0 }}>
              Phạm vi triển khai bao gồm xe Ford Ranger 2.0L Bi-Turbo (2018–2024) làm đối tượng
              thực nghiệm, hỗ trợ chuẩn OBD-II và mở rộng sang UDS Mode 22 với các DID đặc thù
              của Ford.
            </p>
          </div>

          <figure style={{ margin: 0, textAlign: 'center' }}>
            <img src={contextDiagramSvg} alt="Sơ đồ bối cảnh: Vấn đề → Giải pháp → Mục tiêu"
              style={{ maxWidth: '100%', height: 'auto' }} />
            <figcaption style={{
              marginTop: 12,
              fontSize: 12,
              fontStyle: 'italic',
              color: 'var(--gold-500)',
              fontWeight: 500,
            }}>
              Hình 2 — Sơ đồ bối cảnh: Vấn đề → Giải pháp → Mục tiêu.
            </figcaption>
          </figure>
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/Context.jsx
git commit -m "feat(landing): add Context section"
```

---

### Task 1.19: Section Architecture

**Files:**
- Create: `web/src/pages/landing/sections/Architecture.jsx`

- [ ] **Step 1: Tạo Architecture.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'
import PipelineDiagram from '../shared/PipelineDiagram'
import iconHardware from '../../../assets/svg/three-pillars/icon-hardware.svg'
import iconMobile from '../../../assets/svg/three-pillars/icon-mobile.svg'
import iconWeb from '../../../assets/svg/three-pillars/icon-web.svg'

const PILLARS = [
  {
    icon: iconHardware,
    title: 'PHẦN CỨNG',
    tech: 'STM32F103 + MCP2515 + CP2102',
    desc: 'Đọc & giải mã khung CAN từ ECU',
  },
  {
    icon: iconMobile,
    title: 'MOBILE APP',
    tech: 'Android Kotlin · Jetpack Compose',
    desc: 'Hiển thị live data, ghi log, chế độ Lab',
  },
  {
    icon: iconWeb,
    title: 'WEB PLATFORM',
    tech: 'React + Supabase · Ant Design 5',
    desc: 'Quản trị người dùng và hệ thống Lab cho giảng viên',
  },
]

export default function Architecture() {
  return (
    <section id="kien-truc" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="KIẾN TRÚC"
          title="Ba trụ cột tích hợp"
          sub="Phần cứng đo lường, ứng dụng di động hiển thị, web platform quản trị — kết nối qua giao thức CAN, USB và HTTPS."
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
          gap: 24,
        }}>
          {PILLARS.map((p, i) => (
            <div key={i} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              border: '1px solid var(--rule)',
              padding: 28,
              textAlign: 'center',
              transition: 'transform 200ms ease-out, box-shadow 200ms ease-out',
              cursor: 'default',
            }}
            onMouseEnter={e => {
              e.currentTarget.style.transform = 'translateY(-4px)'
              e.currentTarget.style.boxShadow = '0 12px 24px rgba(0,0,0,0.08)'
            }}
            onMouseLeave={e => {
              e.currentTarget.style.transform = 'translateY(0)'
              e.currentTarget.style.boxShadow = 'none'
            }}>
              <img src={p.icon} alt="" style={{ width: 56, height: 56, marginBottom: 16 }} />
              <h3 style={{
                fontSize: 14,
                fontWeight: 800,
                color: 'var(--bk-navy-700)',
                letterSpacing: 2,
                margin: '0 0 8px',
              }}>{p.title}</h3>
              <div style={{
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
                color: 'var(--ink-500)',
                marginBottom: 12,
              }}>{p.tech}</div>
              <p style={{ fontSize: 13, color: 'var(--ink-700)', lineHeight: 1.6, margin: 0 }}>
                {p.desc}
              </p>
            </div>
          ))}
        </div>

        <PipelineDiagram />
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/Architecture.jsx
git commit -m "feat(landing): add Architecture section with 3 pillars"
```

---

### Task 1.20: Section HardwarePillar

**Files:**
- Create: `web/src/pages/landing/sections/HardwarePillar.jsx`

- [ ] **Step 1: Tạo HardwarePillar.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const COMPONENTS = [
  'STM32F103C8T6 — Vi điều khiển Cortex-M3, 72 MHz, 64 KB Flash',
  'MCP2515 — Bộ điều khiển CAN 2.0B, giao tiếp SPI',
  'TJA1050 — CAN transceiver chuẩn ISO 11898, tốc độ tới 1 Mbps',
  'CP2102 — Cầu USB-UART Silicon Labs, driver có sẵn Android',
  'Khối nguồn — 12 V DC → 5 V (LM7805) → 3.3 V (AMS1117)',
]

const SPECS = [
  ['CAN baudrate',     '250 / 500 kbps (auto-detect)'],
  ['UART baud',        '115200 8N1'],
  ['Frame protocol',   '[0xAA][TYPE][LEN][PAYLOAD][XOR][0x55]'],
  ['Nguồn vào',        '12 V DC (cigarette lighter / OBD-II pin 16)'],
  ['Dòng tiêu thụ',    '~120 mA active, ~25 mA idle'],
  ['Kích thước PCB',   '50 × 40 × 18 mm (kèm vỏ in 3D)'],
]

const PROTOCOLS = [
  'OBD-II Mode 01 — Live data (tiêu chuẩn ISO 15765-4)',
  'OBD-II Mode 03/04 — Đọc / xóa mã lỗi DTC',
  'UDS ISO 14229 — Diagnostic & Communication Management',
  'Ford UDS Mode 22 — Read Data By Identifier (DID độc quyền)',
  'ISO-TP 15765-2 — Truyền payload >8 byte qua CAN',
]

export default function HardwarePillar() {
  return (
    <section className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 1 · PHẦN CỨNG"
          title="Mạch giao tiếp CAN bus tự thiết kế"
          sub="Bộ chuyển đổi CAN ↔ USB với khung dữ liệu nhị phân, xác thực checksum và auto-detect baudrate."
          align="left"
        />

        {/* Hàng trên: Ảnh + Thành phần */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 32,
        }} className="responsive-grid">
          <PlaceholderImage
            path="hardware/pcb-top.jpg"
            alt="Mạch in giao tiếp CAN bus, kích thước 50×40 mm"
            ratio="4/3"
            caption="Hình 4 — Mạch in giao tiếp CAN bus, kích thước 50×40 mm."
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Thành phần</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 12 }}>
              {COMPONENTS.map((c, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{c}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Bảng spec */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
          marginBottom: 32,
        }}>
          <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Bảng thông số kỹ thuật</h3>
          <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: 14 }}>
            <tbody>
              {SPECS.map(([k, v], i) => (
                <tr key={i} style={{ borderBottom: i < SPECS.length - 1 ? '1px solid var(--rule)' : 'none' }}>
                  <td style={{ padding: '12px 0', color: 'var(--ink-500)', width: '35%', fontWeight: 500 }}>{k}</td>
                  <td style={{ padding: '12px 0', color: 'var(--ink-900)', fontFamily: i === 2 ? 'var(--font-mono)' : 'inherit', fontSize: i === 2 ? 13 : 14 }}>{v}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* 2 thẻ ngang: Frame protocol | Giao thức hỗ trợ */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
          gap: 24,
        }}>
          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>Khung dữ liệu UART</h3>
            <pre style={{
              fontFamily: 'var(--font-mono)',
              fontSize: 13,
              background: 'var(--paper)',
              padding: 14,
              borderRadius: 8,
              border: '1px solid var(--rule)',
              color: 'var(--ink-900)',
              margin: '0 0 12px',
              overflow: 'auto',
            }}>{`[0xAA] [TYPE] [LEN]
[PAYLOAD…] [XOR] [0x55]`}</pre>
            <p style={{ fontSize: 13, color: 'var(--ink-500)', lineHeight: 1.6, margin: 0 }}>
              XOR checksum xuyên suốt PAYLOAD đảm bảo phát hiện lỗi truyền. SOF/EOF cố định
              (0xAA / 0x55) cho phép đồng bộ lại nhanh khi mất frame.
            </p>
          </div>

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 24,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 12px', fontSize: 16, color: 'var(--ink-900)' }}>Giao thức hỗ trợ</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {PROTOCOLS.map((p, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 13, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--bk-blue-500)' }}>●</span>
                  <span>{p}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <style>{`
        @media (max-width: 767px) {
          .landing-container .responsive-grid { grid-template-columns: 1fr !important; }
        }
      `}</style>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/HardwarePillar.jsx
git commit -m "feat(landing): add HardwarePillar section"
```

---

### Task 1.21: Section MobilePillar

**Files:**
- Create: `web/src/pages/landing/sections/MobilePillar.jsx`

- [ ] **Step 1: Tạo MobilePillar.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const FEATURES = [
  ['⏱', 'Live Data', 'Hiển thị 50+ thông số sensor cập nhật mỗi 500 ms, biểu diễn dạng gauge và biểu đồ thời gian thực.'],
  ['⚠', 'Mã lỗi DTC', 'Quét và giải mã mã lỗi P/C/B/U từ tất cả ECU, xóa DTC và tắt đèn Check Engine.'],
  ['💻', 'Thông tin ECU', 'Đọc VIN, phiên bản phần mềm và mã hiệu chuẩn từ các module: động cơ, hộp số, ABS, túi khí, BCM.'],
  ['📈', 'Data Logger', 'Ghi log dữ liệu ra file CSV để phân tích offline sau khi chạy thử nghiệm.'],
  ['🔧', 'Actuator Test', 'Kích hoạt cơ cấu chấp hành (van EGR, bơm nhiên liệu, quạt điện…) qua UDS Mode 31.'],
  ['📡', 'Raw CAN Monitor', 'Xem byte CAN thô song song với kết quả giải mã, công cụ debug cho kỹ sư.'],
]

const LAB_STEPS = [
  { num: '1', label: 'Pre-quiz', sub: '10 câu, 5 phút' },
  { num: '2', label: 'Thực hành', sub: '15-30 phút' },
  { num: '3', label: 'Post-lab', sub: '5 câu, 3 phút' },
  { num: '4', label: 'Báo cáo PDF', sub: 'sinh tự động' },
]

export default function MobilePillar() {
  return (
    <section id="mobile" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 2 · MOBILE APP"
          title="Ứng dụng Android chẩn đoán & học tập"
          sub="Giao tiếp với mạch CAN qua USB-OTG, hiển thị live data, đọc mã lỗi và hỗ trợ chế độ Lab cho lớp học."
          align="left"
        />

        {/* Hàng trên: Tính năng + 3 screenshot */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '7fr 5fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <div style={{
            background: 'var(--paper)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{ margin: '0 0 16px', fontSize: 18, color: 'var(--ink-900)' }}>Tính năng chính</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              {FEATURES.map(([icon, title, desc], i) => (
                <div key={i} style={{ display: 'flex', gap: 12 }}>
                  <span style={{ fontSize: 22, flexShrink: 0, lineHeight: 1 }}>{icon}</span>
                  <div>
                    <strong style={{ color: 'var(--ink-900)', fontSize: 14, display: 'block', marginBottom: 2 }}>
                      {title}
                    </strong>
                    <span style={{ color: 'var(--ink-500)', fontSize: 13, lineHeight: 1.6 }}>
                      {desc}
                    </span>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <PlaceholderImage path="app/screen-live-data.png"  alt="Live Dashboard" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-dtc-list.png"   alt="DTC List" ratio="9/19.5" />
            <PlaceholderImage path="app/screen-lab-session.png" alt="Lab Session" ratio="9/19.5"
              caption="Hình 5 — Ba màn hình chính của ứng dụng." />
          </div>
        </div>

        {/* Lab Mode sub-section */}
        <div style={{
          background: 'var(--bk-blue-100)',
          borderRadius: 'var(--radius-card)',
          padding: 32,
          border: '1px solid var(--rule)',
        }}>
          <h3 style={{
            margin: '0 0 8px',
            fontSize: 20,
            color: 'var(--bk-navy-700)',
          }}>Lab Mode — Chế độ học tập</h3>
          <p style={{ fontSize: 14, color: 'var(--ink-700)', lineHeight: 1.7, marginBottom: 24, maxWidth: 720 }}>
            Khi giảng viên kích hoạt một buổi học, ứng dụng tự chuyển sang chế độ Lab. Sinh viên
            đăng nhập bằng MSSV, làm pre-quiz, thực hành theo hướng dẫn, rồi hoàn thành post-lab.
            Toàn bộ thao tác CAN được ghi nhận làm bằng chứng (evidence) gửi real-time về dashboard
            giảng viên. Báo cáo PDF sinh tự động cuối buổi.
          </p>

          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))',
            gap: 12,
          }}>
            {LAB_STEPS.map((s, i) => (
              <div key={i} style={{
                background: 'var(--paper)',
                borderRadius: 999,
                padding: '14px 18px',
                display: 'flex',
                alignItems: 'center',
                gap: 12,
              }}>
                <span style={{
                  background: 'var(--bk-navy-700)',
                  color: '#fff',
                  width: 28, height: 28, borderRadius: '50%',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontWeight: 700, fontSize: 13, flexShrink: 0,
                }}>{s.num}</span>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--ink-900)' }}>{s.label}</div>
                  <div style={{ fontSize: 11, color: 'var(--ink-500)' }}>{s.sub}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/MobilePillar.jsx
git commit -m "feat(landing): add MobilePillar section with Lab Mode"
```

---

### Task 1.22: Section WebPillar

**Files:**
- Create: `web/src/pages/landing/sections/WebPillar.jsx`

- [ ] **Step 1: Tạo WebPillar.jsx**

```jsx
import { useState } from 'react'
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const FOR_TEACHER = [
  'Tạo Lab và Session — Cấu hình bài học, mở phiên cho lớp',
  'Quản lý nhóm sinh viên — Tạo nhóm, gán SV vào nhóm',
  'Theo dõi real-time — Xem evidence của SV trong khi học',
  'Review submission — Chấm điểm pre/post-quiz',
  'Tải báo cáo PDF — Export báo cáo cuối buổi',
]

const FOR_ADMIN = [
  'Quản lý người dùng — Tạo/khóa tài khoản, gán role',
  'Activity Logs live — Stream log đăng nhập, thao tác CAN',
  'CSV Exports archive — Tải file CAN log do app upload',
  'Wiring diagram — Sơ đồ đấu nối tham khảo',
]

const TABS = [
  { key: 'labs',     label: 'Labs',     img: 'web/dashboard-labs.png' },
  { key: 'groups',   label: 'Groups',   img: 'web/dashboard-groups.png' },
  { key: 'sessions', label: 'Sessions', img: 'web/dashboard-sessions.png' },
  { key: 'exports',  label: 'Exports',  img: 'web/dashboard-exports.png' },
  { key: 'logs',     label: 'Logs',     img: 'web/dashboard-logs.png' },
]

export default function WebPillar() {
  const [activeTab, setActiveTab] = useState('sessions')
  const tab = TABS.find(t => t.key === activeTab) ?? TABS[2]

  return (
    <section id="web" className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TRỤ CỘT 3 · WEB PLATFORM"
          title="Cổng quản trị & học liệu trực tuyến"
          sub="Web app dành cho giảng viên tổ chức buổi học, theo dõi tiến trình và xuất báo cáo; dành cho admin quản lý người dùng, log hoạt động và file CSV xuất từ thiết bị."
          align="left"
        />

        {/* Hàng trên: Screenshot + 2 group bullet */}
        <div style={{
          display: 'grid',
          gridTemplateColumns: '5fr 7fr',
          gap: 32,
          alignItems: 'start',
          marginBottom: 48,
        }} className="responsive-grid">
          <PlaceholderImage
            path="web/dashboard-sessions.png"
            alt="Trang Admin Dashboard, tab quản lý phiên học"
            ratio="16/10"
            caption="Hình 6 — Trang Admin Dashboard, tab quản lý phiên học."
          />

          <div style={{
            background: 'var(--paper-soft)',
            borderRadius: 'var(--radius-card)',
            padding: 28,
            border: '1px solid var(--rule)',
          }}>
            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>CHO GIẢNG VIÊN</h3>
            <ul style={{ margin: '0 0 24px', padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {FOR_TEACHER.map((t, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>

            <div style={{ height: 1, background: 'var(--rule)', margin: '12px 0 24px' }} />

            <h3 style={{
              margin: '0 0 12px',
              fontSize: 13,
              color: 'var(--bk-blue-500)',
              letterSpacing: 2,
              fontWeight: 800,
            }}>CHO ADMIN</h3>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 10 }}>
              {FOR_ADMIN.map((t, i) => (
                <li key={i} style={{ display: 'flex', gap: 10, fontSize: 14, color: 'var(--ink-700)' }}>
                  <span style={{ color: 'var(--green-600)', fontWeight: 700 }}>✓</span>
                  <span>{t}</span>
                </li>
              ))}
            </ul>
          </div>
        </div>

        {/* Tab showcase */}
        <div style={{
          background: 'var(--paper-soft)',
          borderRadius: 'var(--radius-card)',
          padding: 24,
          border: '1px solid var(--rule)',
        }}>
          <div style={{ display: 'flex', gap: 4, borderBottom: '1px solid var(--rule)', marginBottom: 24, overflowX: 'auto' }}>
            {TABS.map(t => (
              <button
                key={t.key}
                onClick={() => setActiveTab(t.key)}
                style={{
                  padding: '12px 20px',
                  background: 'none',
                  border: 'none',
                  cursor: 'pointer',
                  fontSize: 14,
                  fontWeight: activeTab === t.key ? 700 : 500,
                  color: activeTab === t.key ? 'var(--bk-navy-700)' : 'var(--ink-500)',
                  borderBottom: activeTab === t.key ? '2px solid var(--bk-navy-700)' : '2px solid transparent',
                  transition: 'all 200ms ease-out',
                  whiteSpace: 'nowrap',
                }}
              >{t.label}</button>
            ))}
          </div>

          <PlaceholderImage path={tab.img} alt={`Tab ${tab.label}`} ratio="16/9" />
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/WebPillar.jsx
git commit -m "feat(landing): add WebPillar section with tab showcase"
```

---

### Task 1.23: Section UseCase

**Files:**
- Create: `web/src/pages/landing/sections/UseCase.jsx`

- [ ] **Step 1: Tạo UseCase.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'

const STEPS = [
  { num: 1, icon: '👨‍🏫', title: 'GV TẠO SESSION',  desc: 'Giảng viên chọn Lab, mở phiên mới, hệ thống sinh mã 6 ký tự.' },
  { num: 2, icon: '🎓', title: 'SV THAM GIA',     desc: 'Sinh viên nhập mã session trên app/web bằng tài khoản MSSV.' },
  { num: 3, icon: '📝', title: 'PRE-QUIZ',        desc: '10 câu trắc nghiệm kiểm tra kiến thức nền (5 phút).' },
  { num: 4, icon: '🔧', title: 'THỰC HÀNH',       desc: 'Cắm cáp USB → mạch → xe. Làm theo hướng dẫn từng bước, evidence gửi real-time về dashboard.' },
  { num: 5, icon: '✅', title: 'POST-LAB',        desc: '5 câu rút kinh nghiệm sau buổi học (3 phút).' },
  { num: 6, icon: '📄', title: 'BÁO CÁO PDF',     desc: 'Hệ thống sinh báo cáo gồm pre/post score, evidence, chữ ký số. Lưu vào "Báo cáo của tôi" và DB của GV.' },
]

export default function UseCase() {
  return (
    <section id="lab" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="USE CASE"
          title="Một buổi học diễn ra thế nào"
          sub="Quy trình 6 bước chuẩn hóa, từ lúc giảng viên mở phiên đến khi báo cáo PDF được lưu vào hệ thống."
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
          gap: 16,
        }}>
          {STEPS.map((s, i) => (
            <div key={s.num} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 20,
              border: '1px solid var(--rule)',
              position: 'relative',
              textAlign: 'center',
            }}>
              <div style={{
                width: 36, height: 36,
                borderRadius: '50%',
                background: 'var(--bk-navy-700)',
                color: '#fff',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontWeight: 800, fontSize: 14,
                margin: '0 auto 12px',
              }}>{s.num}</div>
              <div style={{ fontSize: 28, marginBottom: 8 }}>{s.icon}</div>
              <h4 style={{
                margin: '0 0 8px',
                fontSize: 12,
                color: 'var(--bk-navy-700)',
                fontWeight: 800,
                letterSpacing: 1,
              }}>{s.title}</h4>
              <p style={{
                fontSize: 12,
                color: 'var(--ink-500)',
                lineHeight: 1.5,
                margin: 0,
              }}>{s.desc}</p>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/UseCase.jsx
git commit -m "feat(landing): add UseCase timeline section"
```

---

### Task 1.24: Section TechStack

**Files:**
- Create: `web/src/pages/landing/sections/TechStack.jsx`

- [ ] **Step 1: Tạo TechStack.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'

const CLUSTERS = [
  {
    icon: '⚡',
    title: 'PHẦN CỨNG',
    items: ['STM32 HAL · ngôn ngữ C', 'MCP2515 · TJA1050', 'CAN ISO 11898-1', 'STM32CubeIDE'],
  },
  {
    icon: '📱',
    title: 'MOBILE',
    items: ['Kotlin 1.9 · Coroutines', 'Jetpack Compose · Material 3', 'usb-serial-for-android', 'Supabase Auth + Realtime'],
  },
  {
    icon: '🌐',
    title: 'WEB',
    items: ['React 18 + Vite', 'Ant Design 5', 'Framer Motion', 'React Router'],
  },
  {
    icon: '☁️',
    title: 'BACKEND',
    items: ['Supabase Postgres', 'Row-Level Security (RLS)', 'Storage + Edge Functions', 'PostgREST + Realtime'],
  },
]

export default function TechStack() {
  return (
    <section className="landing-section" style={{ background: 'var(--paper)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TECH STACK"
          title="Công nghệ sử dụng"
          sub="Lựa chọn công nghệ ưu tiên mã nguồn mở, cộng đồng lớn và phù hợp môi trường giáo dục."
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 24,
        }}>
          {CLUSTERS.map((c, i) => (
            <div key={i} style={{
              background: 'var(--paper-soft)',
              borderRadius: 'var(--radius-card)',
              padding: 24,
              border: '1px solid var(--rule)',
              transition: 'transform 200ms ease-out',
            }}
            onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-4px)' }}
            onMouseLeave={e => { e.currentTarget.style.transform = 'translateY(0)' }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 16 }}>
                <span style={{ fontSize: 24 }}>{c.icon}</span>
                <h3 style={{
                  margin: 0,
                  fontSize: 13,
                  color: 'var(--bk-navy-700)',
                  letterSpacing: 2,
                  fontWeight: 800,
                }}>{c.title}</h3>
              </div>
              <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
                {c.items.map((item, j) => (
                  <li key={j} style={{
                    fontSize: 13,
                    color: 'var(--ink-700)',
                    paddingLeft: 12,
                    borderLeft: '2px solid var(--bk-blue-100)',
                  }}>{item}</li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/TechStack.jsx
git commit -m "feat(landing): add TechStack section grouped by layer"
```

---

### Task 1.25: Section Team

**Files:**
- Create: `web/src/pages/landing/sections/Team.jsx`

- [ ] **Step 1: Tạo Team.jsx**

```jsx
import SectionHeader from '../shared/SectionHeader'
import PlaceholderImage from '../shared/PlaceholderImage'

const MEMBERS = [
  {
    name: 'Đoàn Anh Kiệt',
    mssv: '2211716',
    role: 'Trưởng nhóm',
    work: 'Mobile App · Giao thức UART',
    avatar: 'team/kiet.jpg',
  },
  {
    name: 'Trần Phan Duy',
    mssv: '22xxxxxx',
    role: 'Thành viên',
    work: 'Phần cứng · Firmware STM32',
    avatar: 'team/duy.jpg',
  },
  {
    name: 'Trương Việt Hoàng',
    mssv: '22xxxxxx',
    role: 'Thành viên',
    work: 'Giao thức OBD2 · Kiểm thử hệ thống',
    avatar: 'team/hoang.jpg',
  },
]

export default function Team() {
  return (
    <section id="team" className="landing-section" style={{ background: 'var(--paper-soft)' }}>
      <div className="landing-container">
        <SectionHeader
          eyebrow="TEAM"
          title="Nhóm thực hiện đồ án"
          sub="Đồ án tốt nghiệp năm học 2025–2026, Bộ môn Kỹ thuật Ô tô, Khoa Kỹ thuật Giao thông, ĐH Bách khoa TP.HCM."
        />

        <div style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
          gap: 24,
          marginBottom: 32,
        }}>
          {MEMBERS.map((m, i) => (
            <div key={i} style={{
              background: 'var(--paper)',
              borderRadius: 'var(--radius-card)',
              padding: 24,
              border: '1px solid var(--rule)',
              textAlign: 'center',
            }}>
              <div style={{ width: 80, height: 80, margin: '0 auto 12px' }}>
                <PlaceholderImage path={m.avatar} alt={m.name} ratio="1/1" />
              </div>
              <h3 style={{ margin: '8px 0 4px', fontSize: 16, color: 'var(--ink-900)' }}>{m.name}</h3>
              <div style={{
                color: 'var(--bk-blue-500)',
                fontSize: 13,
                fontWeight: 700,
                marginBottom: 8,
              }}>MSSV: {m.mssv}</div>
              <div style={{ fontSize: 12, color: 'var(--ink-500)', lineHeight: 1.6 }}>
                {m.role}<br />{m.work}
              </div>
            </div>
          ))}
        </div>

        <div style={{
          background: 'var(--bk-blue-100)',
          borderLeft: '4px solid var(--bk-navy-700)',
          borderRadius: 'var(--radius-card)',
          padding: '20px 24px',
        }}>
          <div style={{ fontSize: 13, color: 'var(--bk-navy-700)', fontWeight: 700, marginBottom: 4 }}>
            🎓 Giảng viên hướng dẫn
          </div>
          <div style={{ fontSize: 16, fontWeight: 700, color: 'var(--ink-900)', marginBottom: 4 }}>
            ThS. Phạm Trần Đăng Quang
          </div>
          <div style={{ fontSize: 13, color: 'var(--ink-500)' }}>
            Bộ môn Kỹ thuật Ô tô · Khoa Kỹ thuật Giao thông · Trường Đại học Bách khoa, ĐHQG-HCM
          </div>
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/Team.jsx
git commit -m "feat(landing): add Team + Advisor section"
```

---

### Task 1.26: Section Footer

**Files:**
- Create: `web/src/pages/landing/sections/Footer.jsx`

- [ ] **Step 1: Tạo Footer.jsx**

```jsx
import { Link } from 'react-router-dom'
import logoSvg from '../../../assets/svg/bk-diagnostic-logo.svg'

const NAV_LINKS = [
  ['#tong-quan',  'Tổng quan'],
  ['#kien-truc',  'Kiến trúc'],
  ['#mobile',     'Mobile App'],
  ['#web',        'Web Platform'],
  ['#lab',        'Lab System'],
  ['#team',       'Team'],
]

export default function Footer() {
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
              Hệ thống chẩn đoán xe và đào tạo CAN bus.<br />
              Đồ án tốt nghiệp ngành Kỹ thuật Ô tô.
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
            }}>Điều hướng</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              {NAV_LINKS.map(([href, label]) => (
                <li key={href}>
                  <a href={href} style={{
                    color: 'rgba(255,255,255,0.75)',
                    fontSize: 14,
                    textDecoration: 'none',
                  }}>{label}</a>
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
            }}>Liên kết</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li><Link to="/login" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Đăng nhập</Link></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Tài liệu kỹ thuật</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Source code</a></li>
              <li><a href="#" style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14, textDecoration: 'none' }}>Liên hệ</a></li>
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
            }}>Trường</h4>
            <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', gap: 8 }}>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>ĐH Bách khoa TP.HCM</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>Khoa Kỹ thuật Giao thông</li>
              <li style={{ color: 'rgba(255,255,255,0.75)', fontSize: 14 }}>Bộ môn Kỹ thuật Ô tô</li>
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
          © 2026 BK Diagnostic · Đồ án tốt nghiệp · Trường ĐH Bách khoa, ĐHQG-HCM · Khoa Kỹ thuật Giao thông
        </div>
      </div>
    </footer>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/sections/Footer.jsx
git commit -m "feat(landing): add Footer with VN content"
```

---

### Task 1.27: LandingPage entry composer

**Files:**
- Create: `web/src/pages/landing/LandingPage.jsx`

- [ ] **Step 1: Tạo LandingPage.jsx (compose tất cả section)**

```jsx
import Navbar from './shared/Navbar'
import Hero from './sections/Hero'
import MetricStrip from './sections/MetricStrip'
import Context from './sections/Context'
import Architecture from './sections/Architecture'
import HardwarePillar from './sections/HardwarePillar'
import MobilePillar from './sections/MobilePillar'
import WebPillar from './sections/WebPillar'
import UseCase from './sections/UseCase'
import TechStack from './sections/TechStack'
import Team from './sections/Team'
import Footer from './sections/Footer'

export default function LandingPage() {
  return (
    <div className="landing-root">
      <Navbar />
      <Hero />
      <MetricStrip />
      <Context />
      <Architecture />
      <HardwarePillar />
      <MobilePillar />
      <WebPillar />
      <UseCase />
      <TechStack />
      <Team />
      <Footer />
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/LandingPage.jsx
git commit -m "feat(landing): add LandingPage entry composing all sections"
```

---

### Task 1.28: Backup file cũ + wire route mới

**Files:**
- Rename: `web/src/pages/LandingPage.jsx` → `web/src/pages/LandingPage.legacy.jsx`
- Modify: `web/src/App.jsx` (dòng 5 + dòng 34)

- [ ] **Step 1: Rename file cũ**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git mv web/src/pages/LandingPage.jsx web/src/pages/LandingPage.legacy.jsx
```

- [ ] **Step 2: Update App.jsx import**

Mở `web/src/App.jsx`, sửa dòng 5 từ:

```jsx
import LandingPage from './pages/LandingPage'
```

thành:

```jsx
import LandingPage from './pages/landing/LandingPage'
```

- [ ] **Step 3: Verify dev server**

```bash
cd web && npm run dev
```

Mở `http://localhost:5173/`. Expected: thấy trang landing mới với 12 section, tất cả placeholder hiển thị "Chờ ảnh: ..." rõ ràng, không lỗi console.

- [ ] **Step 4: Verify route khác không vỡ**

Test các route: `/login`, `/register`, `/dashboard` (sau khi login). Tất cả phải hoạt động bình thường.

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/LandingPage.legacy.jsx web/src/App.jsx
git commit -m "feat(landing): wire new LandingPage to / route, backup old as legacy"
```

---

### Task 1.29: Visual responsive smoke test

**Files:** Không (chỉ test thủ công)

- [ ] **Step 1: Test breakpoint xs (375px)**

Mở DevTools → Device Toolbar → iPhone SE (375×667). Scroll qua toàn bộ trang. Verify:
- Navbar có hamburger button hiện
- Hero title không bị tràn
- Tất cả grid 2 cột chuyển thành 1 cột
- Footer các cột stack thành dọc

- [ ] **Step 2: Test breakpoint md (768px)**

Đổi sang iPad Mini (768×1024). Verify:
- Navbar menu hiện horizontal
- Architecture 3 thẻ vẫn hiển thị 3 cột
- Hardware/Mobile/Web pillar 2 cột bắt đầu hiện đúng

- [ ] **Step 3: Test breakpoint lg (1280px)**

Đổi sang Responsive 1280×800. Verify:
- Container max-width hoạt động (1200px), có khoảng trắng 2 bên
- Spacing thoáng, không quá chật
- Các section padding 112px hiển thị đúng

- [ ] **Step 4: Build production**

```bash
cd web && npm run build
```

Expected: build thành công, không error.

- [ ] **Step 5: Commit nếu cần fix bug**

Nếu phát hiện bug, fix inline rồi commit. Nếu không có bug, không cần commit (skip step này).

---

## PHASE 2 — Animations

Mục tiêu: Thêm animation từ tinh tế (fade-in) đến chi tiết (count-up, stroke-draw, hover) trên skeleton đã ổn định.

### Task 2.1: Helper hook useInViewAnimation

**Files:**
- Create: `web/src/pages/landing/shared/useInViewAnimation.js`

- [ ] **Step 1: Tạo helper hook**

```jsx
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
```

- [ ] **Step 2: Commit**

```bash
git add web/src/pages/landing/shared/useInViewAnimation.js
git commit -m "feat(landing): add useInViewAnimation hook + variants"
```

---

### Task 2.2: AnimatedNumber count-up

**Files:**
- Modify: `web/src/pages/landing/shared/AnimatedNumber.jsx`

- [ ] **Step 1: Thêm count-up logic**

Mở `web/src/pages/landing/shared/AnimatedNumber.jsx` và thay toàn bộ nội dung bằng:

```jsx
import { useEffect, useState } from 'react'
import { useInView } from 'react-intersection-observer'

/**
 * Số count-up khi vào viewport. Nếu value chứa ký tự không phải số (vd "8+"),
 * tách phần số để animate, giữ phần text.
 */
export default function AnimatedNumber({ value, label }) {
  const { ref, inView } = useInView({ triggerOnce: true, threshold: 0.5 })
  const [display, setDisplay] = useState(0)

  // Tách: "8+" → numeric=8, suffix="+"
  const match = String(value).match(/^(\d+)(.*)$/)
  const numeric = match ? parseInt(match[1], 10) : null
  const suffix  = match ? match[2] : value

  useEffect(() => {
    if (!inView || numeric === null) return
    const duration = 900
    const start = performance.now()
    let raf
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3) // easeOutCubic
      setDisplay(Math.floor(eased * numeric))
      if (t < 1) raf = requestAnimationFrame(tick)
      else setDisplay(numeric)
    }
    raf = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(raf)
  }, [inView, numeric])

  return (
    <div ref={ref} style={{ textAlign: 'center', padding: '0 8px' }}>
      <div style={{
        fontSize: 'clamp(32px, 5vw, 48px)',
        fontWeight: 900,
        color: '#FFFFFF',
        lineHeight: 1,
        marginBottom: 6,
      }}>
        {numeric === null ? value : `${display}${suffix}`}
      </div>
      <div style={{
        fontSize: 11,
        color: 'rgba(255,255,255,0.7)',
        textTransform: 'uppercase',
        letterSpacing: 2,
        fontWeight: 600,
        lineHeight: 1.4,
      }}>
        {label}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Verify visual**

Refresh trang, scroll xuống Metric Strip. Expected: số chạy từ 0 lên giá trị đích trong ~0.9s.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/landing/shared/AnimatedNumber.jsx
git commit -m "feat(landing): add count-up animation to AnimatedNumber"
```

---

### Task 2.3: Hero fade-in animation

**Files:**
- Modify: `web/src/pages/landing/sections/Hero.jsx`

- [ ] **Step 1: Wrap content với motion.div**

Mở `web/src/pages/landing/sections/Hero.jsx`. Thêm import ở đầu file:

```jsx
import { motion } from 'framer-motion'
import { fadeUp, fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'
```

Trong JSX, thay `<div className="landing-container" style={{ textAlign: 'center' }}>` bằng:

```jsx
<motion.div
  className="landing-container"
  style={{ textAlign: 'center' }}
  initial="hidden"
  animate="visible"
  variants={fadeUpStagger}
>
```

(và đóng `</motion.div>` thay `</div>` ở cuối)

Wrap mỗi child trong `motion.div variants={fadeUpItem}`. Cụ thể, sửa các element con thành:

```jsx
<motion.div variants={fadeUpItem} style={{ ...tag inline style ở dòng tag... }}>
  ĐỒ ÁN TỐT NGHIỆP · HCMUT · 2026
</motion.div>

<motion.h1 variants={fadeUpItem} style={{ ...h1 style... }}>
  Hệ thống chẩn đoán xe<br />và đào tạo CAN bus
</motion.h1>

<motion.p variants={fadeUpItem} style={{ ...p style... }}>
  Nền tảng tích hợp ba thành phần...
</motion.p>

<motion.div variants={fadeUpItem} style={{ display: 'flex', gap: 12, justifyContent: 'center', flexWrap: 'wrap', marginBottom: 56 }}>
  <Button>...</Button>
  <Button>...</Button>
</motion.div>

<motion.div variants={fadeUpItem}>
  <PlaceholderImage ... />
</motion.div>
```

- [ ] **Step 2: Verify hero animate khi load**

Refresh trang. Expected: tag → title → paragraph → buttons → image xuất hiện stagger từ trên xuống dưới trong ~600ms.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/landing/sections/Hero.jsx
git commit -m "feat(landing): add fade-up stagger animation to Hero"
```

---

### Task 2.4: Section fade-in cho 8 section còn lại

**Files:** (modify 8 file)
- `web/src/pages/landing/sections/Context.jsx`
- `web/src/pages/landing/sections/Architecture.jsx`
- `web/src/pages/landing/sections/HardwarePillar.jsx`
- `web/src/pages/landing/sections/MobilePillar.jsx`
- `web/src/pages/landing/sections/WebPillar.jsx`
- `web/src/pages/landing/sections/UseCase.jsx`
- `web/src/pages/landing/sections/TechStack.jsx`
- `web/src/pages/landing/sections/Team.jsx`

- [ ] **Step 1: Wrap mỗi `<section>` trong motion.section với scroll trigger**

Cho mỗi file ở trên, thêm imports đầu file:

```jsx
import { motion } from 'framer-motion'
import { useInViewAnimation, fadeUp } from '../shared/useInViewAnimation'
```

Trong component, thêm hook:

```jsx
export default function Context() {  // ví dụ với Context
  const { ref, inView } = useInViewAnimation()

  return (
    <motion.section
      ref={ref}
      initial="hidden"
      animate={inView ? 'visible' : 'hidden'}
      variants={fadeUp}
      className="landing-section"
      style={{ background: 'var(--paper)' }}
    >
      {/* ...content... */}
    </motion.section>
  )
}
```

Áp dụng cùng pattern cho 8 section. Giữ nguyên id, className, style, content bên trong.

- [ ] **Step 2: Verify scroll**

Scroll qua trang. Expected: mỗi section fade-in + slide up 16px khi vào viewport.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/landing/sections/
git commit -m "feat(landing): add scroll-triggered fade-up to all sections"
```

---

### Task 2.5: Stagger cho lists trong các section

**Files:** Modify
- `web/src/pages/landing/sections/Architecture.jsx` (3 pillars)
- `web/src/pages/landing/sections/HardwarePillar.jsx` (5 components, 6 specs, 5 protocols)
- `web/src/pages/landing/sections/MobilePillar.jsx` (6 features, 4 lab steps)
- `web/src/pages/landing/sections/UseCase.jsx` (6 steps)
- `web/src/pages/landing/sections/TechStack.jsx` (4 clusters)

- [ ] **Step 1: Architecture — stagger 3 pillars**

Trong Architecture.jsx, thay:

```jsx
<div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 24 }}>
  {PILLARS.map((p, i) => (
    <div key={i} style={{...}}>...</div>
  ))}
</div>
```

bằng:

```jsx
<motion.div
  variants={fadeUpStagger}
  initial="hidden"
  whileInView="visible"
  viewport={{ once: true, amount: 0.3 }}
  style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 24 }}
>
  {PILLARS.map((p, i) => (
    <motion.div key={i} variants={fadeUpItem} style={{...}}>...</motion.div>
  ))}
</motion.div>
```

Thêm import: `import { fadeUpStagger, fadeUpItem } from '../shared/useInViewAnimation'`

- [ ] **Step 2: Apply same pattern cho 4 file còn lại**

Cho HardwarePillar, MobilePillar, UseCase, TechStack: tìm các `<div>` chứa `.map()` lists/grids, wrap container bằng `motion.div` với `variants={fadeUpStagger}` và mỗi item bằng `motion.div variants={fadeUpItem}`.

- [ ] **Step 3: Verify stagger**

Scroll qua từng section. Expected: items xuất hiện tuần tự trái → phải hoặc trên → dưới với delay 80ms giữa mỗi item.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/landing/sections/
git commit -m "feat(landing): stagger animations for lists in sections"
```

---

### Task 2.6: Pipeline diagram stroke-draw

**Files:**
- Modify: `web/src/pages/landing/shared/PipelineDiagram.jsx`

- [ ] **Step 1: Convert SVG sang inline JSX với motion path**

Thay toàn bộ `web/src/pages/landing/shared/PipelineDiagram.jsx` bằng:

```jsx
import { motion } from 'framer-motion'
import { useInView } from 'react-intersection-observer'

const NODES = [
  { x: 80,  fill: '#EFF6FF', stroke: '#1565C0', icon: '🚗', label: 'Xe' },
  { x: 260, fill: '#FFF7ED', stroke: '#EA580C', icon: '🔌', label: 'MCP2515' },
  { x: 440, fill: '#F0FDF4', stroke: '#16A34A', icon: '⚙️', label: 'STM32' },
  { x: 620, fill: '#FAF5FF', stroke: '#7C3AED', icon: '🔗', label: 'CP2102' },
  { x: 800, fill: '#EFF6FF', stroke: '#1565C0', icon: '📱', label: 'App' },
  { x: 980, fill: '#DBEAFE', stroke: '#0A1E6E', icon: '☁️', label: 'Web' },
]

const EDGES = [
  { x1: 124, x2: 216, label: 'CAN' },
  { x1: 304, x2: 396, label: 'SPI' },
  { x1: 484, x2: 576, label: 'UART' },
  { x1: 664, x2: 756, label: 'USB' },
  { x1: 844, x2: 936, label: 'HTTPS' },
]

export default function PipelineDiagram() {
  const { ref, inView } = useInView({ triggerOnce: true, threshold: 0.3 })

  return (
    <figure ref={ref} style={{ margin: '24px 0 0', textAlign: 'center' }}>
      <svg viewBox="0 0 1080 220" style={{ maxWidth: '100%', height: 'auto' }}>
        <style>{`
          .pl-label { font: 700 13px 'Be Vietnam Pro', sans-serif; fill: #003291; }
          .pl-icon  { font: 600 24px 'Apple Color Emoji', 'Segoe UI Emoji', sans-serif; }
          .pl-edge  { font: 500 11px 'JetBrains Mono', monospace; fill: #6B7280; }
        `}</style>

        {NODES.map((n, i) => (
          <motion.g
            key={i}
            initial={{ opacity: 0, scale: 0.7 }}
            animate={inView ? { opacity: 1, scale: 1 } : {}}
            transition={{ delay: i * 0.15, duration: 0.4, ease: 'easeOut' }}
            style={{ transformOrigin: `${n.x}px 100px` }}
          >
            <circle cx={n.x} cy="100" r="44" fill={n.fill} stroke={n.stroke} strokeWidth="2" />
            <text x={n.x} y="92" textAnchor="middle" className="pl-icon">{n.icon}</text>
            <text x={n.x} y="170" textAnchor="middle" className="pl-label">{n.label}</text>
          </motion.g>
        ))}

        {EDGES.map((e, i) => (
          <g key={i}>
            <motion.path
              d={`M${e.x1} 100 L${e.x2} 100`}
              stroke="#9CA3AF" strokeWidth="2" strokeDasharray="4 4"
              initial={{ pathLength: 0 }}
              animate={inView ? { pathLength: 1 } : {}}
              transition={{ delay: 0.15 * (i + 1) + 0.1, duration: 0.3 }}
            />
            <motion.text
              x={(e.x1 + e.x2) / 2} y="92" textAnchor="middle" className="pl-edge"
              initial={{ opacity: 0 }}
              animate={inView ? { opacity: 1 } : {}}
              transition={{ delay: 0.15 * (i + 1) + 0.4, duration: 0.3 }}
            >{e.label}</motion.text>
          </g>
        ))}
      </svg>
      <figcaption style={{
        marginTop: 12,
        fontSize: 12,
        fontStyle: 'italic',
        color: 'var(--gold-500)',
        fontWeight: 500,
      }}>
        Hình 3 — Pipeline dữ liệu từ xe đến cloud.
      </figcaption>
    </figure>
  )
}
```

- [ ] **Step 2: Verify animate**

Scroll xuống Architecture section. Expected: 6 node pop-in tuần tự, sau đó 5 connector vẽ đường + label fade-in.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/landing/shared/PipelineDiagram.jsx
git commit -m "feat(landing): animate pipeline diagram with stroke-draw + node pop-in"
```

---

### Task 2.7: Hover interactions toàn cục

**Files:** Modify (đã có hover ở 1 số chỗ — chuẩn hóa qua framer-motion)
- `web/src/pages/landing/sections/Architecture.jsx` (3 pillars)
- `web/src/pages/landing/sections/TechStack.jsx` (4 clusters)
- `web/src/pages/landing/shared/PlaceholderImage.jsx` (image zoom)

- [ ] **Step 1: Architecture — chuyển hover handler sang framer-motion**

Trong Architecture.jsx, thay onMouseEnter/onMouseLeave handler ở pillar card bằng `whileHover`:

```jsx
<motion.div
  key={i}
  variants={fadeUpItem}
  whileHover={{ y: -4, boxShadow: '0 12px 24px rgba(0,0,0,0.08)' }}
  transition={{ duration: 0.2 }}
  style={{
    background: 'var(--paper)',
    borderRadius: 'var(--radius-card)',
    border: '1px solid var(--rule)',
    padding: 28,
    textAlign: 'center',
  }}
>
```

(xóa onMouseEnter/onMouseLeave handlers)

- [ ] **Step 2: TechStack — same pattern**

Trong TechStack.jsx, làm tương tự cho cluster card. Xóa onMouseEnter/onMouseLeave, thêm `whileHover={{ y: -4 }}` vào motion.div.

- [ ] **Step 3: PlaceholderImage — zoom on hover**

Trong PlaceholderImage.jsx, wrap `<img>` trong `motion.img`:

```jsx
<motion.img
  src={src}
  alt={alt}
  style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
  loading="lazy"
  whileHover={{ scale: 1.05 }}
  transition={{ duration: 0.4 }}
/>
```

Thêm `import { motion } from 'framer-motion'` ở đầu file.

Sửa container `<div>` quanh image: thêm `overflow: 'hidden'` (nếu chưa có) để zoom không tràn.

- [ ] **Step 4: Verify**

Hover vào card pillar → lift 4px. Hover vào image → zoom 1.05x mượt.

- [ ] **Step 5: Commit**

```bash
git add web/src/pages/landing/
git commit -m "feat(landing): hover interactions for cards + image zoom"
```

---

### Task 2.8: Scroll progress bar trong Navbar

**Files:**
- Modify: `web/src/pages/landing/shared/Navbar.jsx`

- [ ] **Step 1: Thêm scroll progress bar**

Mở Navbar.jsx. Thêm import:

```jsx
import { motion, useScroll } from 'framer-motion'
```

Trong component, ngay sau dòng `const [drawerOpen, setDrawerOpen] = useState(false)`:

```jsx
const { scrollYProgress } = useScroll()
```

Thêm trước `</nav>` đóng:

```jsx
<motion.div
  style={{
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    height: 2,
    background: 'var(--bk-blue-500)',
    transformOrigin: '0 0',
    scaleX: scrollYProgress,
  }}
/>
```

- [ ] **Step 2: Verify scroll**

Scroll xuống. Expected: thanh xanh 2px ở dưới navbar mở rộng từ trái sang phải theo % scroll.

- [ ] **Step 3: Commit**

```bash
git add web/src/pages/landing/shared/Navbar.jsx
git commit -m "feat(landing): add scroll progress bar in navbar"
```

---

### Task 2.9: Verify prefers-reduced-motion

**Files:** Không (test thủ công)

- [ ] **Step 1: Bật reduced motion ở OS**

Windows: Settings → Accessibility → Visual effects → "Animation effects" OFF.
(macOS: System Settings → Accessibility → Display → "Reduce motion")

- [ ] **Step 2: Refresh trang landing**

Expected: tất cả animation bị tắt (fade-in/stagger/count-up/scroll bar không animate). Trang vẫn hiển thị đúng nội dung, chỉ không có chuyển động.

- [ ] **Step 3: Tắt reduced motion lại**

Verify animations chạy lại bình thường.

- [ ] **Step 4: Nếu pass**, không cần commit. Nếu fail (animation vẫn chạy), kiểm tra lại CSS rule trong `design-tokens.css` đã có `@media (prefers-reduced-motion: reduce)` chưa. Framer Motion tự respect rule này.

---

## PHASE 3 — Real Assets

Mục tiêu: Thay placeholder bằng ảnh thật khi user chuẩn bị xong.

### Task 3.1: Drop ảnh batch 1 — App + Web screenshots

**Files:**
- Add: `web/public/landing/app/screen-live-data.png`
- Add: `web/public/landing/app/screen-dtc-list.png`
- Add: `web/public/landing/app/screen-lab-session.png`
- Add: `web/public/landing/web/dashboard-sessions.png`
- Add: `web/public/landing/web/dashboard-exports.png`
- Add: `web/public/landing/web/dashboard-labs.png`
- Add: `web/public/landing/web/dashboard-groups.png`
- Add: `web/public/landing/web/dashboard-logs.png`

- [ ] **Step 1: Tạo thư mục**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic\web
mkdir -p public\landing\app public\landing\web
```

- [ ] **Step 2: Copy ảnh user đã chuẩn bị vào đúng path**

User cung cấp 8 file PNG. Copy vào path tương ứng (đặt tên đúng):
- 3 screenshot app (1080×2340) → `web/public/landing/app/`
- 5 screenshot web (1920×1080) → `web/public/landing/web/`

- [ ] **Step 3: Verify hiển thị**

Refresh `http://localhost:5173/`. PlaceholderImage tự detect ảnh tồn tại → hiển thị ảnh thật thay placeholder. Verify ảnh không méo, caption hiển thị đúng.

- [ ] **Step 4: Commit**

```bash
git add web/public/landing/app/ web/public/landing/web/
git commit -m "feat(landing): add real screenshots for app and web platform"
```

---

### Task 3.2: Drop ảnh batch 2 — Hardware + Hero

**Files:**
- Add: `web/public/landing/hero/sa-ban-overview.jpg`
- Add: `web/public/landing/hardware/pcb-top.jpg`
- Add: `web/public/landing/hardware/pcb-perspective.jpg` (optional)
- Add: `web/public/landing/hardware/enclosure.jpg` (optional)

- [ ] **Step 1: Tạo thư mục**

```bash
mkdir -p web\public\landing\hero web\public\landing\hardware
```

- [ ] **Step 2: Copy ảnh thật vào**

- [ ] **Step 3: Verify**

Refresh trang, scroll qua Hero (sa bàn) và Hardware Pillar (PCB). Ảnh thay placeholder.

- [ ] **Step 4: Commit**

```bash
git add web/public/landing/hero/ web/public/landing/hardware/
git commit -m "feat(landing): add real photos for hero saba and hardware PCB"
```

---

### Task 3.3: Drop ảnh batch 3 — Team avatars

**Files:**
- Add: `web/public/landing/team/kiet.jpg`
- Add: `web/public/landing/team/duy.jpg`
- Add: `web/public/landing/team/hoang.jpg`
- Add: `web/public/landing/team/advisor.jpg` (optional)

- [ ] **Step 1: Tạo thư mục + copy ảnh 400×400**

```bash
mkdir web\public\landing\team
```

Copy 4 ảnh chân dung crop vuông 400×400.

- [ ] **Step 2: Verify**

Scroll xuống Team section. Avatar hiển thị thay placeholder.

- [ ] **Step 3: Commit**

```bash
git add web/public/landing/team/
git commit -m "feat(landing): add team avatars"
```

---

### Task 3.4: Open Graph image cho social sharing

**Files:**
- Add: `web/public/landing/og/og-image.jpg`
- Modify: `web/index.html`

- [ ] **Step 1: Copy OG image 1200×630**

```bash
mkdir web\public\landing\og
# Copy file vào: web/public/landing/og/og-image.jpg
```

- [ ] **Step 2: Thêm meta tags vào index.html**

Mở `web/index.html`. Thêm sau `<meta name="description">`:

```html
<meta property="og:type" content="website" />
<meta property="og:title" content="BK Diagnostic — Hệ thống chẩn đoán xe và đào tạo CAN bus" />
<meta property="og:description" content="Đồ án tốt nghiệp HCMUT 2026: Phần cứng nhúng, Android app và Web platform tích hợp." />
<meta property="og:image" content="/landing/og/og-image.jpg" />
<meta name="twitter:card" content="summary_large_image" />
```

- [ ] **Step 3: Verify**

Mở `https://www.opengraph.xyz/url/<your-deploy-url>` để preview. Hoặc test local: view source ở `http://localhost:5173/` xem meta tags có không.

- [ ] **Step 4: Commit**

```bash
git add web/public/landing/og/ web/index.html
git commit -m "feat(landing): add OG image and social sharing meta tags"
```

---

## PHASE 4 — Polish

### Task 4.1: Image lazy load + preload hero

**Files:**
- Modify: `web/index.html`

- [ ] **Step 1: Preload hero background SVG + sa bàn JPG**

Mở `web/index.html`. Thêm sau dòng `<link href="https://fonts.googleapis...">`:

```html
<link rel="preload" as="image" href="/landing/hero/sa-ban-overview.jpg" />
```

- [ ] **Step 2: Verify trong Network tab**

DevTools → Network → reload. Ảnh `sa-ban-overview.jpg` xuất hiện ở Initiator "Other" với High priority.

- [ ] **Step 3: Verify ảnh khác là lazy-load**

Scroll xuống. Các ảnh app/web/team chỉ load khi vào viewport (do `loading="lazy"` đã set trong PlaceholderImage component).

- [ ] **Step 4: Commit**

```bash
git add web/index.html
git commit -m "perf(landing): preload hero image, keep others lazy"
```

---

### Task 4.2: Cross-browser test

**Files:** Không (test thủ công)

- [ ] **Step 1: Test Chrome desktop**

Mở `http://localhost:5173/` trên Chrome. Verify:
- Tất cả 12 section hiển thị đúng
- Animation mượt
- Không lỗi console
- Hover effects hoạt động

- [ ] **Step 2: Test Edge**

Same checks.

- [ ] **Step 3: Test Firefox**

Same checks. Đặc biệt verify `backdrop-filter` trong Navbar (Firefox cần `-moz-` prefix nếu version cũ — code đã dùng `WebkitBackdropFilter` + `backdropFilter` tiêu chuẩn, đủ cover Firefox 103+).

- [ ] **Step 4: Test mobile real (Chrome Android hoặc Safari iOS)**

Deploy preview lên Vercel (sẽ làm sau), test trên thiết bị thật. Verify:
- Drawer menu hamburger hoạt động
- Touch scroll mượt
- Font Be Vietnam Pro render đúng dấu

- [ ] **Step 5: Nếu phát hiện bug, fix + commit**

```bash
git add <file>
git commit -m "fix(landing): <mô tả bug>"
```

---

### Task 4.3: Lighthouse audit

**Files:** Không

- [ ] **Step 1: Build production**

```bash
cd web && npm run build && npm run preview
```

Mở `http://localhost:4173`.

- [ ] **Step 2: Chạy Lighthouse**

DevTools → Lighthouse → Categories: Performance + Accessibility + Best Practices + SEO. Mode: Navigation. Device: Desktop. Click "Analyze".

- [ ] **Step 3: Verify thresholds**

Expected:
- Performance ≥ 85
- Accessibility ≥ 95
- Best Practices ≥ 90
- SEO ≥ 90

- [ ] **Step 4: Nếu performance < 85, fix common issues**

Các fix khả dĩ:
- Convert hero JPG sang WebP (giảm 30-40% size)
- Thêm `width` + `height` cho mọi `<img>` để tránh CLS
- Defer JS không critical

- [ ] **Step 5: Mobile audit**

Chạy lại Lighthouse với device "Mobile". Performance threshold mobile ≥ 75 (mobile thường thấp hơn).

- [ ] **Step 6: Commit nếu có fix**

```bash
git add <files>
git commit -m "perf(landing): Lighthouse fixes (image dimensions, WebP)"
```

---

### Task 4.4: Xóa LandingPage.legacy.jsx

**Files:**
- Delete: `web/src/pages/LandingPage.legacy.jsx`

- [ ] **Step 1: Verify không có nơi nào còn import legacy**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
grep -r "LandingPage.legacy" web/src/
```

Expected: không có kết quả.

- [ ] **Step 2: Xóa file**

```bash
git rm web/src/pages/LandingPage.legacy.jsx
```

- [ ] **Step 3: Build verify**

```bash
cd web && npm run build
```

Expected: build thành công.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(landing): remove legacy LandingPage backup"
```

---

### Task 4.5: Tạo Pull Request

**Files:** Không (chỉ thao tác git)

- [ ] **Step 1: Push branch**

```bash
cd C:\Users\KIET\AndroidStudioProjects\BKDiagnostic
git push -u origin feature/landing-redesign
```

- [ ] **Step 2: Tạo PR**

```bash
gh pr create --title "Redesign trang chủ — Lab Whitepaper style + tiếng Việt" --body "$(cat <<'EOF'
## Tóm tắt

Triển khai lại trang chủ `/` theo phong cách Lab Whitepaper, mở rộng nội dung sang mô hình 3 trụ cột (Hardware / Mobile App / Web Platform) và bản địa hóa toàn bộ tiếng Việt.

Spec: `docs/superpowers/specs/2026-04-26-landing-redesign-design.md`
Plan: `docs/superpowers/plans/2026-04-26-landing-redesign.md`

## Thay đổi chính

- 12 section mới: Hero / MetricStrip / Context / Architecture / 3 Pillars / UseCase / TechStack / Team / Footer
- Font Be Vietnam Pro thay Inter
- Design tokens CSS variables (`--bk-navy-700`, `--paper-soft`, ...)
- Animations với framer-motion (fade-in, stagger, count-up, stroke-draw, hover)
- 8 SVG asset tự generate (logo, pipeline, mesh, 3 pillar icons, context diagram)
- Toàn bộ nội dung tiếng Việt

## Test plan

- [x] Visual smoke test 3 breakpoint (375 / 768 / 1280)
- [x] Cross-browser: Chrome / Edge / Firefox / Safari iOS
- [x] Lighthouse Performance ≥ 85, Accessibility ≥ 95
- [x] prefers-reduced-motion respected
- [x] Tất cả 17 ảnh thật đã được chèn (hoặc placeholder rõ ràng nếu chưa có)
- [x] Auth state đồng bộ (avatar/login button hoạt động)
- [x] Không phá route khác (/login, /dashboard, /admin)
EOF
)"
```

- [ ] **Step 3: Verify PR URL**

`gh` sẽ in URL PR. Mở URL, verify Vercel preview deployment chạy thành công (icon ✓).

- [ ] **Step 4: Self-review trên Vercel preview**

Mở Vercel preview link, scroll qua trang full. Confirm mọi thứ ok.

---

## Tổng kết

**Số task:** 36 (1 setup + 29 Phase 1 + 9 Phase 2 + 4 Phase 3 + 5 Phase 4)

**Số commit dự kiến:** ~36-40 commits

**Thời gian ước tính:**
- Phase 1 (Foundation): 4-6 giờ
- Phase 2 (Animations): 2-3 giờ
- Phase 3 (Real assets): 30 phút (chỉ copy ảnh + commit)
- Phase 4 (Polish): 1-2 giờ
- **Tổng: 8-12 giờ làm việc**

**Phân chia phase theo session:**
- Session 1: S0 + Phase 1 (foundation đầy đủ, chạy được skeleton)
- Session 2: Phase 2 (animations)
- Session 3: Phase 3 + Phase 4 (khi có ảnh thật, polish, deploy PR)
