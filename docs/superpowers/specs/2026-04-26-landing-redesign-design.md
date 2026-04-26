# Landing Page Redesign — Design Spec

**Ngày tạo:** 2026-04-26
**Tác giả:** Đoàn Anh Kiệt
**Trạng thái:** Đang chờ phê duyệt

---

## 1. Tổng quan

Thiết kế lại trang chủ (`web/src/pages/LandingPage.jsx`) theo phong cách **Academic / Showcase** ("Lab Whitepaper"), cấu trúc tuyến tính kể câu chuyện, mở rộng nội dung sang mô hình **3 trụ cột** (Phần cứng — Mobile App — Web Platform), toàn bộ nội dung dịch sang tiếng Việt.

**Mục tiêu chính:**
- Nâng cấp thẩm mỹ phù hợp đồ án tốt nghiệp cho hội đồng đánh giá
- Mở rộng giới thiệu hệ sinh thái đầy đủ (không chỉ app + hardware)
- Bản địa hóa hoàn toàn tiếng Việt với typography phù hợp dấu

**Khán giả mục tiêu:** Giảng viên, hội đồng đánh giá đồ án, sinh viên ngành Kỹ thuật Ô tô, kỹ sư quan tâm hệ thống mở.

---

## 2. Cấu trúc trang (Information Architecture)

Trang gồm **12 section** theo thứ tự scroll:

| # | Section | Background | Vai trò |
|---|---------|------------|---------|
| 0 | Navbar (sticky) | Trắng + blur | Điều hướng nội bộ + auth |
| 1 | Hero | Gradient mesh BK navy | Giới thiệu, CTA, ảnh sa bàn |
| 2 | Metric Strip | BK navy 700 | 5 con số nổi bật |
| 3 | Bối cảnh & Mục tiêu | Trắng | Vì sao có đồ án |
| 4 | Kiến trúc hệ thống | Paper soft (#F9FAFB) | 3 trụ cột + pipeline |
| 5 | Trụ cột 1 — Phần cứng | Trắng | PCB + spec + protocol |
| 6 | Trụ cột 2 — Mobile App | Paper soft | Tính năng + Lab Mode |
| 7 | Trụ cột 3 — Web Platform | Trắng | Dashboard + tabs |
| 8 | Use Case — Buổi học | Paper soft | Timeline 6 bước |
| 9 | Tech Stack | Trắng | 4 cluster theo layer |
| 10 | Team & Giảng viên | Paper soft | 3 thành viên + GV |
| 11 | Footer | BK navy 900 | Logo + links + bản quyền |

**Quy tắc xen kẽ background:** Trắng ↔ Paper soft tạo nhịp đọc. Hai section nền màu (Hero gradient, Metric BK navy, Footer navy) đóng vai trò "ngắt nhịp".

---

## 3. Visual Design System

### 3.1. Bảng màu

```
PRIMARY (BK Identity)
  --bk-navy-900    #0A1E6E   ← màu chủ lực, footer/hero/heading lớn
  --bk-navy-700    #003291   ← màu thứ 2, link/button primary
  --bk-blue-500    #1565C0   ← accent xanh, icon/badge
  --bk-blue-100    #DBEAFE   ← background nhạt cho card highlight

NEUTRAL (Whitepaper)
  --paper          #FFFFFF   ← nền chính
  --paper-soft     #F9FAFB   ← nền section xen kẽ
  --ink-900        #111827   ← heading
  --ink-700        #374151   ← body text
  --ink-500        #6B7280   ← muted/caption
  --rule           #E5E7EB   ← border/divider

ACCENT (sparingly used)
  --gold-500       #D4A017   ← caption "Hình X.Y" + key number
  --green-600      #16A34A   ← trạng thái OK
  --red-600        #DC2626   ← cảnh báo
```

**Quy tắc dùng màu:** 80% trắng/xám trung tính · 15% BK navy/blue · 5% gold accent.

### 3.2. Typography

**Font:** Be Vietnam Pro (heading + body), JetBrains Mono (code).
**Lý do chọn Be Vietnam Pro:** Hỗ trợ đầy đủ dấu tiếng Việt, render rõ nét hơn Inter, miễn phí qua Google Fonts.

```
Heading:  Be Vietnam Pro, weight 700-900, tracking -0.02em
Body:     Be Vietnam Pro, weight 400-500, line-height 1.7
Mono:     JetBrains Mono, weight 400-500
Caption:  Be Vietnam Pro, weight 500, italic, --gold-500

Type scale (mobile / desktop):
  Hero title       36 / 56 px   weight 800
  Section title    24 / 36 px   weight 700
  Subsection       18 / 24 px   weight 700
  Body lead        16 / 18 px   weight 400
  Body             14 / 16 px   weight 400
  Caption          12 / 13 px   weight 500 italic
  Mono code        13 / 14 px   weight 400
```

Load qua Google Fonts trong `web/index.html`:
```html
<link href="https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;500;600;700;800;900&family=JetBrains+Mono:wght@400;500&display=swap" rel="stylesheet">
```

### 3.3. Spacing & Layout

```
Container max-width:  1200px
Gutter:              24px (mobile) / 48px (desktop)
Section padding:     64px (mobile) / 112px (desktop) trục dọc
Card padding:        20px (mobile) / 28px (desktop)
Border-radius:       12px cho card · 8px cho button · 4px cho tag
```

### 3.4. Image conventions

- Mọi ảnh có **caption** kiểu paper khoa học: *Hình X — chú thích ngắn*
- Khung ảnh: bo góc 12px + border 1px `--rule` + shadow nhẹ
- Tỷ lệ ưu tiên: 16:9 cho ảnh ngang, 4:3 cho screenshot
- Không filter/saturation — giữ ảnh nguyên trạng

### 3.5. Animation policy

```
KHI VÀO VIEWPORT (Intersection Observer)
  • Fade-in + slide-up 16px         400ms ease-out
  • Stagger 80ms cho list items
  • Number count-up cho metric strip
  • Reveal sơ đồ pipeline từng node một

HOVER
  • Card: lift 4px + shadow tăng    200ms cubic-bezier
  • Button primary: scale 1.02 + glow nhẹ
  • Image: zoom 1.05 trong khung overflow-hidden
  • Pillar icon: rotate 5° hoặc bounce

SCROLL-TRIGGERED
  • Section heading: text-mask reveal trái → phải
  • Hero ảnh sa bàn: parallax 10%
  • Pipeline diagram: vẽ stroke connector dần khi scroll
  • Code block protocol: typing effect (1 lần khi xuất hiện)

MICRO-INTERACTIONS
  • Tab switching Web Platform: slide indicator
  • Avatar team: ring xanh xoay khi hover
  • Tag/badge: pulse nhẹ khi mới mount
  • Scroll progress bar 2px ở top navbar

GIỚI HẠN
  • Tổng số animation đồng thời ≤ 3 trên màn hình
  • Tôn trọng `prefers-reduced-motion`
  • Không scroll-jacking
  • Tất cả animation < 600ms
```

**Thư viện:**
- `framer-motion` — most animations (Intersection Observer wrapper, variants, AnimatePresence)
- `react-intersection-observer` — trigger scroll-based
- CSS-only — hover transitions cơ bản

### 3.6. Responsive breakpoints

```
xs  < 576px    1 cột, ảnh xếp trên text
md  ≥ 768px    2 cột bắt đầu, nav menu hiện
lg  ≥ 992px    spacing tăng, container giới hạn
xl  ≥ 1200px   max-width container, không scale thêm
```

---

## 4. Component Layout chi tiết

### 4.0. Navbar (sticky, h=64px)

- Background: `rgba(255,255,255,0.92)` + `backdrop-filter: blur(8px)`
- Border-bottom 1px `--rule`
- Progress bar 2px BK blue ở dưới navbar (animation scroll-triggered)
- Logo bên trái (SVG + text "BK Diagnostic"), menu giữa, CTA bên phải
- Mobile: hamburger icon → drawer slide-in từ phải

### 4.1. Hero (~720px)

- Title 2 dòng căn giữa, max-width 800px
- Background: gradient mesh BK navy → trắng (SVG self-generated)
- Tag academic phía trên title
- 2 button CTA: primary white-bg, secondary outline-white
- Ảnh sa bàn full-width bên dưới text, ratio 16:9, có caption gold
- Animation: title fade-in stagger, ảnh parallax 10% khi scroll

### 4.2. Metric Strip (h=120px, BK navy)

- 5 cột chia đều, divider mảnh giữa
- Số: 48px white weight 900, count-up từ 0 (Intersection trigger)
- Label: 11px uppercase letter-spacing 2px opacity 0.7

### 4.3. Bối cảnh & Mục tiêu

- Layout 2 cột 1:1 (md+), 1 cột mobile
- Cột trái: 3 đoạn text (Vấn đề / Giải pháp / Phạm vi)
- Cột phải: SVG context-diagram (3 ô + arrow), animate stroke-draw

### 4.4. Kiến trúc hệ thống (paper-soft)

- 3 thẻ trụ cột grid 1:1:1, hover lift + icon rotate
- Pipeline diagram bên dưới: 6 node + connector
- Animation: mỗi node fade-in tuần tự, connector stroke-draw

### 4.5. Trụ cột 1 — Phần cứng

- Hàng trên: layout 5:7 (ảnh PCB : text "Thành phần")
- Hàng dưới: 1 bảng spec full-width
- Hàng cuối: 2 thẻ ngang (Frame Protocol | Giao thức hỗ trợ)
- Code block: typing effect khi xuất hiện
- Bảng spec: row hover nhẹ

### 4.6. Trụ cột 2 — Mobile App (đảo cột)

- Layout 7:5 (text : ảnh) — đảo so với section 5 tạo nhịp
- Cột phải: 3 phone screenshot xếp lệch (stacked tilted)
- Sub-section "Lab Mode": full-width card, 4 step pill có connector
- Animation: 6 bullet tính năng stagger fade-in

### 4.7. Trụ cột 3 — Web Platform (paper-soft)

- Layout 5:7 (ảnh : text) tiếp tục đảo nhịp
- 2 group bullet (Cho giảng viên / Cho admin) có divider
- Tab interactive 5 tab: click switch ảnh, slide indicator dưới tab name
- Browser frame mockup bao quanh screenshot

### 4.8. Use Case — Buổi học

- Horizontal timeline 6 bước (md+), vertical timeline mobile
- Mỗi bước có icon + title + caption 1-2 dòng
- Connector dashed line + arrow giữa các step
- Animation: line "draw" trái sang phải, mỗi card pop-in tuần tự

### 4.9. Tech Stack

- 4 cluster grid 2x2 (Phần cứng / Mobile / Web / Backend)
- Mỗi cluster có icon header + 4-5 mục bullet
- Hover: cluster lift, mục bullet glow

### 4.10. Team & Giảng viên (paper-soft)

- 3 thẻ thành viên ngang grid 1:1:1
- Avatar 80px, ring xanh xoay khi hover
- Card giảng viên full-width, background `--bk-blue-100`, border-left 4px BK navy

### 4.11. Footer (BK navy 900)

- 4 cột: Logo+desc (xs=24, md=8) / Điều hướng / Liên kết / Trường
- Bottom bar: copyright căn giữa, divider top

---

## 5. Nội dung tiếng Việt

(Bản đầy đủ đã trình bày trong brainstorming. Tóm tắt key messages:)

- **Hero title:** "Hệ thống chẩn đoán xe và đào tạo CAN bus"
- **Hero sub:** "Nền tảng tích hợp ba thành phần — phần cứng nhúng, ứng dụng Android và web platform — phục vụ giảng dạy giao thức CAN bus và quy trình chẩn đoán xe ô tô cho sinh viên ngành Kỹ thuật Ô tô."
- **Tagline trang:** "Đồ án tốt nghiệp · HCMUT · 2026"
- **Trường/Khoa/Bộ môn:** Trường ĐH Bách khoa, ĐHQG-HCM · Khoa Kỹ thuật Giao thông · Bộ môn Kỹ thuật Ô tô
- **Giảng viên hướng dẫn:** ThS. Phạm Trần Đăng Quang
- **Thành viên:** Đoàn Anh Kiệt (2211716, trưởng nhóm), Trần Phan Duy, Trương Việt Hoàng

Toàn bộ navbar, button, label, caption đều tiếng Việt. Code/protocol/tên kỹ thuật giữ tiếng Anh (OBD-II, CAN, UDS, RPM, DTC...) — đây là chuẩn quốc tế, không dịch.

---

## 6. File structure (Code organization)

### 6.1. Tạo mới

```
web/src/pages/landing/
├── LandingPage.jsx                   ← entry point mới (thay thế file cũ)
├── sections/
│   ├── Hero.jsx
│   ├── MetricStrip.jsx
│   ├── Context.jsx
│   ├── Architecture.jsx
│   ├── HardwarePillar.jsx
│   ├── MobilePillar.jsx
│   ├── WebPillar.jsx
│   ├── UseCase.jsx
│   ├── TechStack.jsx
│   ├── Team.jsx
│   └── Footer.jsx
└── shared/
    ├── Navbar.jsx
    ├── SectionHeader.jsx             (eyebrow + title + sub)
    ├── FigureCaption.jsx             (Hình X.Y — ...)
    ├── AnimatedNumber.jsx            (count-up cho metric)
    ├── PipelineDiagram.jsx           (SVG có scroll-trigger)
    └── PlaceholderImage.jsx          (khung "ẢNH SẮP CÓ")

web/src/styles/
└── design-tokens.css                 (CSS variables tokens)

web/src/assets/svg/
├── bk-diagnostic-logo.svg
├── pipeline-diagram.svg
├── timeline-arrow.svg
├── hero-bg-mesh.svg
├── context-diagram.svg
└── three-pillars/
    ├── icon-hardware.svg
    ├── icon-mobile.svg
    └── icon-web.svg
```

**Quy tắc tách section:** Mỗi section là 1 file React riêng để dễ test và maintain. Mỗi file < 300 dòng.

### 6.2. Sửa

```
web/src/App.jsx                       ← đổi route '/' trỏ tới landing/LandingPage
web/src/main.jsx                      ← import design-tokens.css
web/index.html                        ← link Google Fonts + meta OG + lang="vi"
```

### 6.3. Backup

Đổi tên `web/src/pages/LandingPage.jsx` cũ → `LandingPage.legacy.jsx` để dễ rollback. Xóa hẳn sau 1 tuần khi confirm production OK.

---

## 7. Assets

### 7.1. SVG (tự generate trong code)

```
✅ bk-diagnostic-logo.svg            Chữ B/K cách điệu + xe, line-art 2px
✅ pipeline-diagram.svg              6 node Vehicle → MCP → STM → CP → App → Web
✅ timeline-arrow.svg                Mũi tên dashed cho timeline
✅ hero-bg-mesh.svg                  Gradient mesh BK navy → trắng + noise
✅ context-diagram.svg               3 ô "Vấn đề → Giải pháp → Mục tiêu"
✅ three-pillars/icon-hardware.svg   Icon mạch điện stylized
✅ three-pillars/icon-mobile.svg     Icon phone với gauge
✅ three-pillars/icon-web.svg        Icon dashboard browser
```

Phong cách: line-art, stroke 2px, BK navy + accent gold, transparent bg.

### 7.2. PNG/JPG (do user chuẩn bị)

```
web/public/landing/
├── hero/sa-ban-overview.jpg          1920×1080
├── hardware/pcb-top.jpg              1600×1200
├── hardware/pcb-perspective.jpg      1600×1200 (optional)
├── hardware/enclosure.jpg            1200×1200 (optional)
├── app/screen-live-data.png          1080×2340
├── app/screen-dtc-list.png           1080×2340
├── app/screen-lab-session.png        1080×2340
├── web/dashboard-sessions.png        1920×1080
├── web/dashboard-exports.png         1920×1080
├── web/dashboard-labs.png            1920×1080
├── web/dashboard-groups.png          1920×1080
├── web/dashboard-logs.png            1920×1080
├── team/kiet.jpg                     400×400
├── team/duy.jpg                      400×400
├── team/hoang.jpg                    400×400
├── team/advisor.jpg                  400×400 (optional)
└── og/og-image.jpg                   1200×630 (optional)
```

### 7.3. Placeholder strategy

Trước khi có ảnh, dùng `<PlaceholderImage path="hero/sa-ban-overview.jpg" caption="Hình 1" />` — render khung xám có icon 📷 + chữ "Chờ ảnh: <path>". Khi user drop ảnh vào đúng path, ảnh tự hiển thị.

---

## 8. Dependencies

```json
{
  "dependencies": {
    "framer-motion": "^11.0.0",
    "react-intersection-observer": "^9.5.0"
  }
}
```

Cài đặt: `cd web && npm install framer-motion react-intersection-observer`.

Font Be Vietnam Pro: load qua Google Fonts (không cần npm package).

---

## 9. Implementation phases

### Phase 1 — Foundation (không cần ảnh)
1. Cài deps, thêm Google Fonts vào `index.html`
2. Tạo `design-tokens.css` với CSS variables
3. Tạo SVG tự generate (8 file)
4. Tạo shared components: SectionHeader, FigureCaption, PlaceholderImage, AnimatedNumber, PipelineDiagram
5. Tạo Navbar mới
6. Build skeleton 11 section với placeholder + content tiếng Việt
7. Verify layout responsive ở 3 breakpoint (xs/md/lg)

### Phase 2 — Animations
8. Add Framer Motion vào từng section (fade-in, stagger)
9. AnimatedNumber count-up cho metric strip
10. Pipeline diagram scroll-triggered SVG draw
11. Hover interactions (card lift, image zoom, ring rotate)
12. Respect `prefers-reduced-motion`

### Phase 3 — Real assets (khi có)
13. Replace placeholder với ảnh thật batch 1 (screenshots)
14. Replace placeholder với ảnh thật batch 2 (sa bàn, PCB)
15. Avatar team, OG image

### Phase 4 — Polish
16. SEO meta tags (title, description, og:image)
17. Performance: lazy-load images, preload font
18. Cross-browser test (Chrome, Edge, Safari mobile)
19. Lighthouse audit (target Performance ≥85, A11y ≥95)

---

## 10. Considerations

### 10.1. Backward compatibility
- Route `/` vẫn là LandingPage — không breaking change
- Tất cả route khác (`/dashboard`, `/login`, ...) không bị ảnh hưởng
- `useAuth` vẫn dùng để hiện avatar trên navbar khi đã đăng nhập

### 10.2. Ant Design vs raw component
- Section dùng raw HTML/CSS (không cần Antd) cho freedom layout
- Chỉ dùng Antd cho `Button`, `Avatar`, `Tag` (đã có trong project) để giữ consistency với phần dashboard

### 10.3. Performance budget
- LCP < 2.5s — preload hero image, dùng `loading="eager"` cho ảnh hero
- CLS < 0.1 — set width/height cho mọi ảnh để tránh layout shift
- JS bundle landing page < 150 KB gzipped

### 10.4. SEO
- `<title>BK Diagnostic — Hệ thống chẩn đoán xe và đào tạo CAN bus</title>`
- `<meta name="description">` — đoạn sub của hero
- Open Graph + Twitter Card tags
- `<html lang="vi">`

### 10.5. Accessibility
- Alt text mọi ảnh (mô tả nội dung + caption "Hình X")
- Keyboard navigation đầy đủ (focus ring rõ)
- Contrast ratio WCAG AA cho mọi text
- Screen reader: heading hierarchy h1 → h2 → h3 đúng

### 10.6. Testing
- Test thủ công: Chrome/Edge/Safari iOS/Chrome Android
- Lighthouse: target Performance ≥85, Accessibility ≥95
- Không cần unit test (UI tĩnh)

---

## 11. Migration plan

1. Tạo branch `feature/landing-redesign`
2. Implement theo Phase 1-4 (mỗi phase có thể là 1 sub-task)
3. Mở PR, review trên Vercel preview deployment
4. Merge sau khi user approve
5. Sau 1 tuần production stable → xóa `LandingPage.legacy.jsx`

---

## 12. Success criteria

- ✅ Trang load được, không lỗi console
- ✅ Tất cả 12 section hiển thị đúng trên Chrome/Edge/Safari/Firefox
- ✅ Responsive đúng ở xs (375px), md (768px), lg (1280px)
- ✅ Toàn bộ text tiếng Việt, font Be Vietnam Pro render đúng dấu
- ✅ Animation chạy mượt 60fps, tôn trọng `prefers-reduced-motion`
- ✅ Lighthouse Performance ≥85, Accessibility ≥95
- ✅ Khi chưa có ảnh thật, placeholder hiển thị rõ ràng
- ✅ Khi user drop ảnh vào path đúng, ảnh hiển thị tự động
- ✅ Auth state đồng bộ với phần còn lại của app (avatar/login button)
- ✅ Không phá vỡ route khác (dashboard, login, lab pages)

---

## 13. Out of scope

- Multi-language switcher (chỉ 1 ngôn ngữ tiếng Việt)
- Dark mode cho landing (giữ light theme thuần)
- Blog / News section
- Tài liệu kỹ thuật chi tiết (nằm ở route khác)
- Form liên hệ (chỉ link email)
- Animation 3D / WebGL (overkill cho academic style)
