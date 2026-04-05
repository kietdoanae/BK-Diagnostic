# Active Test Cluster WebView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thay thế Canvas Compose + lưới nút trong `ActiveTestScreen` bằng WebView hiển thị đồng hồ Ford Ranger 2019 SVG tương tác, cho phép người dùng nhấn trực tiếp lên cluster để gửi lệnh CAN.

**Architecture:** `ClusterJsBridge.kt` đăng ký JavascriptInterface tên `"Android"` vào WebView; `cluster.html` chứa toàn bộ SVG + JS; `ActiveTestScreen.kt` giữ nguyên `AppTopBar` và logic CAN, thay phần còn lại bằng WebView. Khi user nhấn icon trên SVG, JS gọi `Android.sendExternalCommand(key, state)` → Android toggle CAN → gọi `webView.evaluateJavascript("window.cluster.setIndicator(...)")` để sync lại UI.

**Tech Stack:** Jetpack Compose + `AndroidView`/`WebView`, `@JavascriptInterface`, HTML5/SVG/JavaScript (Vanilla, không dùng framework)

---

## File Map

| File | Thao tác | Trách nhiệm |
|---|---|---|
| `app/src/main/assets/cluster.html` | CREATE | Toàn bộ SVG cluster + CSS + JS API |
| `app/src/main/java/com/example/bkdiagnostic/ui/webbridge/ClusterJsBridge.kt` | CREATE | `@JavascriptInterface` nhận lệnh từ JS |
| `app/src/main/java/com/example/bkdiagnostic/ui/screens/ActiveTestScreen.kt` | MODIFY | Thay Canvas/ControlPanel bằng WebView |

---

## Task 1: ClusterJsBridge.kt

**Files:**
- Create: `app/src/main/java/com/example/bkdiagnostic/ui/webbridge/ClusterJsBridge.kt`

- [ ] **Step 1: Tạo package folder `webbridge`**

```bash
mkdir -p "app/src/main/java/com/example/bkdiagnostic/ui/webbridge"
```

- [ ] **Step 2: Tạo `ClusterJsBridge.kt`**

```kotlin
package com.example.bkdiagnostic.ui.webbridge

import android.webkit.JavascriptInterface

/**
 * JavascriptInterface đăng ký dưới tên "Android" vào WebView.
 * JS gọi: Android.sendExternalCommand("left_turn", true)
 * onCommand chạy trên thread JS — caller phải dispatch sang main thread nếu cần.
 */
class ClusterJsBridge(
    private val onCommand: (key: String, isOn: Boolean) -> Unit
) {
    @JavascriptInterface
    fun sendExternalCommand(key: String, isOn: Boolean) {
        onCommand(key, isOn)
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/webbridge/ClusterJsBridge.kt
git commit -m "feat(webbridge): add ClusterJsBridge JavascriptInterface"
```

---

## Task 2: cluster.html — Skeleton + CSS

**Files:**
- Create: `app/src/main/assets/cluster.html`

- [ ] **Step 1: Tạo file với HTML shell, CSS variables, và SVG viewport**

Tạo file `app/src/main/assets/cluster.html` với nội dung sau:

```html
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<title>Ford Ranger Cluster</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }

  :root {
    --bg:        #06060A;
    --gauge-bg:  #0E0E18;
    --track:     #1A1A2E;
    --cyan:      rgba(180,240,255,0.80);
    --blue:      #2563EB;
    --red:       #EF4444;
    --amber:     #F59E0B;
    --green:     #22C55E;
    --mfd-bg:    #0D1117;
    --mfd-border:#1F2937;
    --text-dim:  #6B7280;
  }

  html, body {
    width: 100%; height: 100%;
    background: var(--bg);
    overflow: hidden;
  }

  /* SVG chiếm toàn bộ vùng còn lại dưới AppTopBar */
  #cluster-svg {
    display: block;
    width: 100%;
    height: 100%;
  }

  /* Quick Actions bar — glassmorphism overlay */
  #quick-actions {
    position: fixed;
    bottom: 8px;
    left: 50%;
    transform: translateX(-50%);
    display: flex;
    gap: 16px;
    padding: 8px 20px;
    background: rgba(255,255,255,0.04);
    border: 1px solid rgba(255,255,255,0.10);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    border-radius: 12px;
  }

  .qa-btn {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3px;
    padding: 6px 14px;
    border-radius: 8px;
    border: 1px solid rgba(255,255,255,0.08);
    background: transparent;
    cursor: pointer;
    transition: background 0.15s, box-shadow 0.15s, opacity 0.4s;
    -webkit-tap-highlight-color: transparent;
    user-select: none;
  }

  .qa-btn .qa-icon { font-size: 18px; line-height: 1; }
  .qa-btn .qa-label {
    font-family: sans-serif;
    font-size: 10px;
    letter-spacing: 0.5px;
    color: rgba(255,255,255,0.45);
    text-transform: uppercase;
  }

  /* Active pulse state */
  .qa-btn.active-amber {
    background: rgba(251,191,36,0.18);
    box-shadow: 0 0 10px rgba(251,191,36,0.45);
    border-color: rgba(251,191,36,0.4);
  }
  .qa-btn.active-blue {
    background: rgba(96,165,250,0.18);
    box-shadow: 0 0 10px rgba(96,165,250,0.45);
    border-color: rgba(96,165,250,0.4);
  }

  /* Disabled (debounce) */
  .qa-btn.debouncing { pointer-events: none; opacity: 0.5; }
</style>
</head>
<body>

<!-- Main SVG cluster -->
<svg id="cluster-svg" viewBox="0 0 800 460"
     preserveAspectRatio="xMidYMid meet"
     xmlns="http://www.w3.org/2000/svg">

  <!-- Background -->
  <defs>
    <radialGradient id="bg-grad" cx="50%" cy="50%" r="60%">
      <stop offset="0%"   stop-color="#14141E"/>
      <stop offset="100%" stop-color="#06060A"/>
    </radialGradient>
    <!-- Gauge face gradient -->
    <radialGradient id="face-l" cx="185" cy="265" r="130" gradientUnits="userSpaceOnUse">
      <stop offset="0%"   stop-color="#16162A"/>
      <stop offset="80%"  stop-color="#0E0E18"/>
      <stop offset="100%" stop-color="#0A0A12"/>
    </radialGradient>
    <radialGradient id="face-r" cx="615" cy="265" r="130" gradientUnits="userSpaceOnUse">
      <stop offset="0%"   stop-color="#16162A"/>
      <stop offset="80%"  stop-color="#0E0E18"/>
      <stop offset="100%" stop-color="#0A0A12"/>
    </radialGradient>
  </defs>

  <rect width="800" height="460" fill="url(#bg-grad)"/>

  <!-- RANGER watermark — dưới kim đồng hồ -->
  <text x="400" y="305"
        font-family="sans-serif" font-weight="bold" font-size="68"
        letter-spacing="18" fill="rgba(255,255,255,0.05)"
        text-anchor="middle" dominant-baseline="middle">RANGER</text>

  <!-- Gauge layers sẽ được JS vẽ vào đây -->
  <g id="tach-layer"></g>
  <g id="speed-layer"></g>
  <g id="sub-gauge-layer"></g>
  <g id="warning-layer"></g>
  <g id="turn-layer"></g>
  <g id="mfd-layer"></g>

</svg>

<!-- Quick Actions bar -->
<div id="quick-actions">
  <button class="qa-btn" id="qa-horn"   data-key="horn"         data-dur="2000" data-color="amber">
    <span class="qa-icon">📢</span>
    <span class="qa-label">Còi</span>
  </button>
  <button class="qa-btn" id="qa-wiper-f" data-key="front_wiper" data-dur="3000" data-color="blue">
    <span class="qa-icon">🌧</span>
    <span class="qa-label">Gạt trước</span>
  </button>
  <button class="qa-btn" id="qa-wiper-r" data-key="rear_wiper"  data-dur="3000" data-color="blue">
    <span class="qa-icon">🌧</span>
    <span class="qa-label">Gạt sau</span>
  </button>
</div>

<script>
// ── Constants ────────────────────────────────────────────────────────────────
const TACH  = { cx: 185, cy: 265, r: 130, max: 6000, redlineAt: 4750,  start: 150, sweep: 240, labels: ['0','1','2','3','4','5','6'],  unit: '×1000 r/min' };
const SPEED = { cx: 615, cy: 265, r: 130, max: 200,  dangerAt: 180,    start: 150, sweep: 240, labels: ['0','40','80','120','160','200'], unit: 'km/h' };

// Placeholder — JS functions sẽ được thêm ở các Task tiếp theo
</script>

</body>
</html>
```

- [ ] **Step 2: Verify file tồn tại**

```bash
ls app/src/main/assets/cluster.html
```

Expected: file hiển thị trong listing.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add cluster.html skeleton with CSS + SVG shell"
```

---

## Task 3: cluster.html — Vẽ vành đồng hồ + vạch chia

**Files:**
- Modify: `app/src/main/assets/cluster.html` — thêm JS function `drawGaugeRing()` vào `<script>`

- [ ] **Step 1: Thêm hàm `drawGaugeRing` vào `<script>` (thay dòng `// Placeholder`)**

Xóa `// Placeholder — JS functions sẽ được thêm ở các Task tiếp theo` và thay bằng:

```javascript
// ── Math helpers ─────────────────────────────────────────────────────────────
const RAD = Math.PI / 180;
function px(angle) { return angle * RAD; }
function ptOnCircle(cx, cy, r, angleDeg) {
  return { x: cx + r * Math.cos(angleDeg * RAD), y: cy + r * Math.sin(angleDeg * RAD) };
}
function arcPath(cx, cy, r, startDeg, endDeg) {
  const s = ptOnCircle(cx, cy, r, startDeg);
  const e = ptOnCircle(cx, cy, r, endDeg);
  const large = (endDeg - startDeg) > 180 ? 1 : 0;
  return `M ${s.x} ${s.y} A ${r} ${r} 0 ${large} 1 ${e.x} ${e.y}`;
}

// ── Draw a full gauge ring (face, track, ticks, labels, unit) ───────────────
function drawGaugeRing(layerId, cfg, faceGradId) {
  const g = document.getElementById(layerId);
  const { cx, cy, r, max, redlineAt, dangerAt, start, sweep, labels, unit } = cfg;

  const ns = 'http://www.w3.org/2000/svg';
  function el(tag, attrs) {
    const e = document.createElementNS(ns, tag);
    Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
    return e;
  }

  // Chrome outer ring
  g.appendChild(el('circle', {
    cx, cy, r: r + 8, fill: 'none',
    stroke: '#3D3D50', 'stroke-width': 5
  }));

  // Gauge face
  g.appendChild(el('circle', { cx, cy, r, fill: `url(#${faceGradId})` }));

  // Track arc (full sweep, dark)
  g.appendChild(el('path', {
    d: arcPath(cx, cy, r - 7, start, start + sweep),
    fill: 'none', stroke: '#1A1A2E', 'stroke-width': 14, 'stroke-linecap': 'round'
  }));

  // Normal zone arc (blue/cyan)
  const normalEnd = redlineAt != null
    ? start + (redlineAt / max) * sweep          // tach
    : start + (dangerAt / max) * sweep;           // speed
  g.appendChild(el('path', {
    d: arcPath(cx, cy, r - 7, start, normalEnd),
    fill: 'none', stroke: '#2563EB', 'stroke-width': 14,
    'stroke-linecap': 'round', opacity: '0.55'
  }));

  // Redline / danger arc (red)
  g.appendChild(el('path', {
    d: arcPath(cx, cy, r - 7, normalEnd, start + sweep),
    fill: 'none', stroke: '#EF4444', 'stroke-width': 14,
    'stroke-linecap': 'round', opacity: '0.35'
  }));

  // Tick marks
  const intervals = labels.length - 1;
  const minorCount = 4;
  const totalSteps = intervals * (minorCount + 1);
  for (let i = 0; i <= totalSteps; i++) {
    const frac   = i / totalSteps;
    const ang    = start + frac * sweep;
    const isMajor = (i % (minorCount + 1) === 0);
    const innerR = isMajor ? r * 0.78 : r * 0.85;
    const outerR = r * 0.93;
    const s = ptOnCircle(cx, cy, innerR, ang);
    const e = ptOnCircle(cx, cy, outerR, ang);
    g.appendChild(el('line', {
      x1: s.x, y1: s.y, x2: e.x, y2: e.y,
      stroke: `rgba(180,240,255,${isMajor ? 0.80 : 0.35})`,
      'stroke-width': isMajor ? 2.5 : 1.2,
      'stroke-linecap': 'round'
    }));
  }

  // Labels
  const labelR = r + 17;
  labels.forEach((lbl, i) => {
    if (!lbl) return;
    const frac = i / (labels.length - 1);
    const ang  = start + frac * sweep;
    const pt   = ptOnCircle(cx, cy, labelR, ang);
    const t = el('text', {
      x: pt.x, y: pt.y,
      fill: 'rgba(180,240,255,0.80)',
      'font-family': 'sans-serif', 'font-size': '13',
      'font-weight': 'bold', 'text-anchor': 'middle',
      'dominant-baseline': 'middle'
    });
    t.textContent = lbl;
    g.appendChild(t);
  });

  // Unit label (below center)
  const unitEl = el('text', {
    x: cx, y: cy + r * 0.58,
    fill: '#6B7280', 'font-family': 'sans-serif',
    'font-size': '10', 'text-anchor': 'middle',
    'dominant-baseline': 'middle', 'letter-spacing': '0.5'
  });
  unitEl.textContent = unit;
  g.appendChild(unitEl);
}
```

- [ ] **Step 2: Thêm lời gọi init sau phần functions — thêm vào cuối `<script>` (trước thẻ đóng `</script>`)**

```javascript
// Init gauges
drawGaugeRing('tach-layer',  TACH,  'face-l');
drawGaugeRing('speed-layer', SPEED, 'face-r');
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): draw gauge rings with ticks, labels, redline zones"
```

---

## Task 4: cluster.html — Kim đồng hồ (Needle System)

**Files:**
- Modify: `app/src/main/assets/cluster.html`

Kim được vẽ là SVG `<g>` group có `transform="rotate(angle, cx, cy)"`. Mặc định góc 0 → kim chỉ hướng `150°` (vị trí 0 km/h / 0 RPM).

- [ ] **Step 1: Thêm hàm `drawNeedle` vào `<script>` (sau `drawGaugeRing`)**

```javascript
// ── Needle ───────────────────────────────────────────────────────────────────
function drawNeedle(layerId, cfg, needleId) {
  const g  = document.getElementById(layerId);
  const ns = 'http://www.w3.org/2000/svg';
  const { cx, cy, r, start } = cfg;
  const nLen = r * 0.72;

  // The needle group — rotate(angle, cx, cy) controls position
  const grp = document.createElementNS(ns, 'g');
  grp.setAttribute('id', needleId);
  grp.setAttribute('transform', `rotate(0, ${cx}, ${cy})`);

  // Needle tip position at start angle (0-value position)
  const tip = ptOnCircle(cx, cy, nLen, start);
  const tipInner = ptOnCircle(cx, cy, nLen * 0.65, start);

  // Shadow
  const shadow = document.createElementNS(ns, 'line');
  shadow.setAttribute('x1', cx + 2); shadow.setAttribute('y1', cy + 2);
  shadow.setAttribute('x2', tip.x + 2); shadow.setAttribute('y2', tip.y + 2);
  shadow.setAttribute('stroke', 'rgba(0,0,0,0.5)');
  shadow.setAttribute('stroke-width', '5');
  shadow.setAttribute('stroke-linecap', 'round');
  grp.appendChild(shadow);

  // Body (white)
  const body = document.createElementNS(ns, 'line');
  body.setAttribute('x1', cx); body.setAttribute('y1', cy);
  body.setAttribute('x2', tip.x); body.setAttribute('y2', tip.y);
  body.setAttribute('stroke', '#E5E7EB');
  body.setAttribute('stroke-width', '3.5');
  body.setAttribute('stroke-linecap', 'round');
  grp.appendChild(body);

  // Red tip accent (last 35%)
  const tip2 = document.createElementNS(ns, 'line');
  tip2.setAttribute('x1', tipInner.x); tip2.setAttribute('y1', tipInner.y);
  tip2.setAttribute('x2', tip.x); tip2.setAttribute('y2', tip.y);
  tip2.setAttribute('stroke', '#FF4444');
  tip2.setAttribute('stroke-width', '3.5');
  tip2.setAttribute('stroke-linecap', 'round');
  grp.appendChild(tip2);

  // Center hub
  const hub = document.createElementNS(ns, 'circle');
  hub.setAttribute('cx', cx); hub.setAttribute('cy', cy);
  hub.setAttribute('r', r * 0.07);
  hub.setAttribute('fill', '#0C0C18');
  hub.setAttribute('stroke', '#374151');
  hub.setAttribute('stroke-width', '2');
  grp.appendChild(hub);

  const hubDot = document.createElementNS(ns, 'circle');
  hubDot.setAttribute('cx', cx); hubDot.setAttribute('cy', cy);
  hubDot.setAttribute('r', r * 0.028);
  hubDot.setAttribute('fill', '#D1D5DB');
  grp.appendChild(hubDot);

  g.appendChild(grp);
}
```

- [ ] **Step 2: Thêm lời gọi trong init section (sau `drawGaugeRing` calls)**

```javascript
drawNeedle('tach-layer',  TACH,  'tach-needle');
drawNeedle('speed-layer', SPEED, 'speed-needle');
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add rotatable needle groups for tach and speed"
```

---

## Task 5: cluster.html — Segmented Sub-Gauges (Fuel + Temp)

**Files:**
- Modify: `app/src/main/assets/cluster.html`

8 segment hình cung nằm trong mỗi đồng hồ chính (bán kính nhỏ hơn).

- [ ] **Step 1: Thêm hàm `drawSegmentedGauge` vào `<script>` (sau `drawNeedle`)**

```javascript
// ── Segmented sub-gauge (8 segments) ─────────────────────────────────────────
// Returns array of segment path elements for later update
function drawSegmentedGauge(layerId, cfg, subCfg) {
  const { cx, cy } = cfg;
  const { r, startDeg, sweep, activeColor, labelL, labelR, groupId } = subCfg;
  const ns  = 'http://www.w3.org/2000/svg';
  const g   = document.getElementById(layerId);
  const grp = document.createElementNS(ns, 'g');
  grp.setAttribute('id', groupId);

  const segCount  = 8;
  const gapDeg    = 3;
  const segDeg    = (sweep / segCount) - gapDeg;
  const rInner    = r - 10;
  const segments  = [];

  for (let i = 0; i < segCount; i++) {
    const s   = startDeg + i * (sweep / segCount);
    const e   = s + segDeg;
    const s1  = ptOnCircle(cx, cy, r, s);
    const e1  = ptOnCircle(cx, cy, r, e);
    const s2  = ptOnCircle(cx, cy, rInner, e);
    const e2  = ptOnCircle(cx, cy, rInner, s);

    const d = `M ${s1.x} ${s1.y}
               A ${r} ${r} 0 0 1 ${e1.x} ${e1.y}
               L ${s2.x} ${s2.y}
               A ${rInner} ${rInner} 0 0 0 ${e2.x} ${e2.y} Z`;

    const path = document.createElementNS(ns, 'path');
    path.setAttribute('d', d);
    path.setAttribute('fill', '#1A1A2E');
    path.setAttribute('rx', '2');
    grp.appendChild(path);
    segments.push(path);
  }

  // Labels E/F or C/H
  const lPt = ptOnCircle(cx, cy, r + 14, startDeg);
  const rPt = ptOnCircle(cx, cy, r + 14, startDeg + sweep);

  [lPt, rPt].forEach((pt, idx) => {
    const t = document.createElementNS(ns, 'text');
    t.setAttribute('x', pt.x);
    t.setAttribute('y', pt.y);
    t.setAttribute('fill', '#6B7280');
    t.setAttribute('font-family', 'sans-serif');
    t.setAttribute('font-size', '11');
    t.setAttribute('font-weight', 'bold');
    t.setAttribute('text-anchor', 'middle');
    t.setAttribute('dominant-baseline', 'middle');
    t.textContent = idx === 0 ? labelL : labelR;
    grp.appendChild(t);
  });

  g.appendChild(grp);

  // Return updater closure
  return function updateSegments(level) {
    // level: 0-8 integer
    // Temp special: segment 7 (last) turns red if temp > 6
    segments.forEach((seg, i) => {
      const lit = i < level;
      let color = '#1A1A2E';
      if (lit) {
        color = (activeColor === 'temp' && i >= 6)
          ? '#EF4444'
          : (activeColor === 'temp' ? '#22C55E' : '#F59E0B');
      }
      seg.setAttribute('fill', color);
      seg.setAttribute('opacity', lit ? '0.90' : '0.35');
    });
  };
}
```

- [ ] **Step 2: Thêm lời gọi trong init section (sau needle calls)**

```javascript
const updateFuel = drawSegmentedGauge('sub-gauge-layer', TACH,  {
  r: 65, startDeg: 195, sweep: 150,
  activeColor: 'fuel', labelL: 'E', labelR: 'F', groupId: 'fuel-segs'
});
const updateTemp = drawSegmentedGauge('sub-gauge-layer', SPEED, {
  r: 65, startDeg: 195, sweep: 150,
  activeColor: 'temp', labelL: 'C', labelR: 'H', groupId: 'temp-segs'
});

// Default values
updateFuel(2);   // 2/8
updateTemp(4);   // 4/8
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add 8-segment fuel and temp sub-gauges"
```

---

## Task 6: cluster.html — Warning Lights + Turn Signals

**Files:**
- Modify: `app/src/main/assets/cluster.html`

- [ ] **Step 1: Thêm định nghĩa warning lights và hàm `buildWarningPanel` vào `<script>` (sau segment gauge functions)**

```javascript
// ── Warning light definitions ────────────────────────────────────────────────
const LIGHTS = [
  // Row 1 (y=50)
  { id: 'hazard',        label: '⚠',   color: '#FBBF24', clickable: true,  row: 0, col: 0 },
  { id: 'check_engine',  label: 'CE',  color: '#FBBF24', clickable: false, row: 0, col: 1 },
  { id: 'battery',       label: '🔋',  color: '#EF4444', clickable: false, row: 0, col: 2 },
  { id: 'oil_pressure',  label: '🛢',  color: '#EF4444', clickable: false, row: 0, col: 3 },
  { id: 'high_beam',     label: 'H',   color: '#3B82F6', clickable: true,  row: 0, col: 4 },
  // Row 2 (y=82)
  { id: 'airbag',        label: '🛡',  color: '#FBBF24', clickable: false, row: 1, col: 0 },
  { id: 'door_lock',     label: '🚪',  color: '#EF4444', clickable: true,  row: 1, col: 1 },
  { id: 'abs',           label: 'ABS', color: '#FBBF24', clickable: false, row: 1, col: 2 },
  { id: 'low_beam',      label: 'L',   color: '#22C55E', clickable: true,  row: 1, col: 3 },
  { id: 'brake_light',   label: 'BR',  color: '#EF4444', clickable: true,  row: 1, col: 4 },
  { id: 'reverse_light', label: 'REV', color: '#FFFFFF', clickable: true,  row: 1, col: 5 },
];

// ── Build warning panel ───────────────────────────────────────────────────────
function buildWarningPanel() {
  const ns  = 'http://www.w3.org/2000/svg';
  const g   = document.getElementById('warning-layer');
  const DOT = 22;   // circle radius
  const GAP = 52;   // horizontal spacing

  // Row 0: 5 icons centered at x=400, y=50
  // Row 1: 6 icons centered at x=400, y=82
  const rowY   = [50, 82];
  const rowLen = [5, 6];

  LIGHTS.forEach(light => {
    const totalW = (rowLen[light.row] - 1) * GAP;
    const startX = 400 - totalW / 2;
    const cx = startX + light.col * GAP;
    const cy = rowY[light.row];

    const grp = document.createElementNS(ns, 'g');
    grp.setAttribute('id', `light-${light.id}`);
    grp.setAttribute('opacity', '0.15');
    if (light.clickable) {
      grp.setAttribute('cursor', 'pointer');
      grp.style.cursor = 'pointer';
    }

    // Background circle
    const circle = document.createElementNS(ns, 'circle');
    circle.setAttribute('cx', cx); circle.setAttribute('cy', cy);
    circle.setAttribute('r', DOT * 0.9);
    circle.setAttribute('fill', light.color);
    circle.setAttribute('fill-opacity', '0.15');
    circle.setAttribute('stroke', light.color);
    circle.setAttribute('stroke-opacity', '0.6');
    circle.setAttribute('stroke-width', '1.5');
    grp.appendChild(circle);

    // Label text
    const txt = document.createElementNS(ns, 'text');
    txt.setAttribute('x', cx); txt.setAttribute('y', cy);
    txt.setAttribute('fill', light.color);
    txt.setAttribute('font-family', 'sans-serif');
    txt.setAttribute('font-size', light.label.length > 2 ? '8' : '11');
    txt.setAttribute('font-weight', 'bold');
    txt.setAttribute('text-anchor', 'middle');
    txt.setAttribute('dominant-baseline', 'middle');
    txt.textContent = light.label;
    grp.appendChild(txt);

    g.appendChild(grp);

    // Click handler for clickable lights
    if (light.clickable) {
      grp.addEventListener('click', () => handleLightClick(light.id));
    }
  });
}

// ── Turn signals (outside the gauge circles) ─────────────────────────────────
function buildTurnSignals() {
  const ns = 'http://www.w3.org/2000/svg';
  const g  = document.getElementById('turn-layer');

  function makeArrow(id, x, y, direction) {
    const grp = document.createElementNS(ns, 'g');
    grp.setAttribute('id', id);
    grp.setAttribute('opacity', '0.12');
    grp.setAttribute('cursor', 'pointer');
    grp.style.cursor = 'pointer';

    for (let i = 0; i < 3; i++) {
      const txt = document.createElementNS(ns, 'text');
      txt.setAttribute('x', direction === 'left' ? x - i * 14 : x + i * 14);
      txt.setAttribute('y', y);
      txt.setAttribute('fill', '#22C55E');
      txt.setAttribute('font-size', '20');
      txt.setAttribute('font-weight', 'bold');
      txt.setAttribute('text-anchor', 'middle');
      txt.setAttribute('dominant-baseline', 'middle');
      txt.textContent = direction === 'left' ? '◀' : '▶';
      grp.appendChild(txt);
    }
    g.appendChild(grp);

    const key = direction === 'left' ? 'left_turn' : 'right_turn';
    grp.addEventListener('click', () => handleLightClick(key));
  }

  makeArrow('turn-left',  65,  25, 'left');
  makeArrow('turn-right', 735, 25, 'right');
}
```

- [ ] **Step 2: Thêm lời gọi trong init section (sau segment gauge calls)**

```javascript
buildWarningPanel();
buildTurnSignals();
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add warning lights panel and turn signal arrows"
```

---

## Task 7: cluster.html — MFD Center Display

**Files:**
- Modify: `app/src/main/assets/cluster.html`

- [ ] **Step 1: Thêm hàm `buildMFD` vào `<script>` (sau `buildTurnSignals`)**

```javascript
// ── MFD (Multi-Function Display) ─────────────────────────────────────────────
let mfdActiveTab  = 'tripA';
let mfdActiveGear = 'D';

const TRIP_DATA = {
  tripA: [
    ['Trip',  '127.3 km'],
    ['Avg',   '18.2 L/100km'],
    ['Time',  '6h 58m'],
    ['Range', '412 km'],
  ],
  tripB: [
    ['Trip',  '43.1 km'],
    ['Avg',   '17.6 L/100km'],
    ['Time',  '2h 12m'],
    ['Range', '412 km'],
  ],
  settings: [
    ['Bright', '80%'],
    ['Units',  'km/h'],
    ['Lang',   'VI'],
  ]
};

function buildMFD() {
  const ns  = 'http://www.w3.org/2000/svg';
  const g   = document.getElementById('mfd-layer');

  // MFD box
  const MX = 318, MY = 100, MW = 164, MH = 255;

  // Background rect
  const bg = document.createElementNS(ns, 'rect');
  bg.setAttribute('x', MX); bg.setAttribute('y', MY);
  bg.setAttribute('width', MW); bg.setAttribute('height', MH);
  bg.setAttribute('rx', '8');
  bg.setAttribute('fill', '#0D1117');
  bg.setAttribute('stroke', '#1F2937');
  bg.setAttribute('stroke-width', '1');
  g.appendChild(bg);

  // Header row: outside temp (left) + clock (right)
  const tempEl = document.createElementNS(ns, 'text');
  tempEl.setAttribute('id', 'mfd-temp');
  tempEl.setAttribute('x', MX + 10); tempEl.setAttribute('y', MY + 16);
  tempEl.setAttribute('fill', '#9CA3AF');
  tempEl.setAttribute('font-family', 'monospace');
  tempEl.setAttribute('font-size', '11');
  tempEl.setAttribute('dominant-baseline', 'middle');
  tempEl.textContent = '23°C';
  g.appendChild(tempEl);

  const clockEl = document.createElementNS(ns, 'text');
  clockEl.setAttribute('id', 'mfd-clock');
  clockEl.setAttribute('x', MX + MW - 10); clockEl.setAttribute('y', MY + 16);
  clockEl.setAttribute('fill', '#9CA3AF');
  clockEl.setAttribute('font-family', 'monospace');
  clockEl.setAttribute('font-size', '11');
  clockEl.setAttribute('text-anchor', 'end');
  clockEl.setAttribute('dominant-baseline', 'middle');
  clockEl.textContent = '00:00';
  g.appendChild(clockEl);

  // Separator line
  const sep = document.createElementNS(ns, 'line');
  sep.setAttribute('x1', MX + 6); sep.setAttribute('y1', MY + 26);
  sep.setAttribute('x2', MX + MW - 6); sep.setAttribute('y2', MY + 26);
  sep.setAttribute('stroke', '#1F2937'); sep.setAttribute('stroke-width', '1');
  g.appendChild(sep);

  // Gear indicator row: P R N D S
  const GEARS = ['P','R','N','D','S'];
  const gearY = MY + 47;
  const gearSpacing = MW / (GEARS.length + 1);
  GEARS.forEach((gear, i) => {
    const gx = MX + gearSpacing * (i + 1);

    // Highlight box for selected gear
    const box = document.createElementNS(ns, 'rect');
    box.setAttribute('id', `gear-box-${gear}`);
    box.setAttribute('x', gx - 10); box.setAttribute('y', gearY - 13);
    box.setAttribute('width', '20'); box.setAttribute('height', '20');
    box.setAttribute('rx', '4');
    box.setAttribute('fill', gear === mfdActiveGear ? '#2563EB' : 'none');
    box.setAttribute('opacity', '0.8');
    g.appendChild(box);

    const txt = document.createElementNS(ns, 'text');
    txt.setAttribute('id', `gear-txt-${gear}`);
    txt.setAttribute('x', gx); txt.setAttribute('y', gearY);
    txt.setAttribute('fill', gear === mfdActiveGear ? '#FFFFFF' : '#4B5563');
    txt.setAttribute('font-family', 'monospace');
    txt.setAttribute('font-size', gear === mfdActiveGear ? '14' : '11');
    txt.setAttribute('font-weight', gear === mfdActiveGear ? 'bold' : 'normal');
    txt.setAttribute('text-anchor', 'middle');
    txt.setAttribute('dominant-baseline', 'middle');
    txt.textContent = gear;
    g.appendChild(txt);
  });

  // Separator
  const sep2 = document.createElementNS(ns, 'line');
  sep2.setAttribute('x1', MX + 6); sep2.setAttribute('y1', MY + 60);
  sep2.setAttribute('x2', MX + MW - 6); sep2.setAttribute('y2', MY + 60);
  sep2.setAttribute('stroke', '#1F2937'); sep2.setAttribute('stroke-width', '1');
  g.appendChild(sep2);

  // Tab buttons row: Trip A | Trip B | Settings
  const TABS = [
    { key: 'tripA', label: 'Trip A' },
    { key: 'tripB', label: 'Trip B' },
    { key: 'settings', label: '⚙' },
  ];
  const tabY = MY + 78;
  const tabW = MW / TABS.length;
  TABS.forEach((tab, i) => {
    const tx = MX + i * tabW;

    const tabBg = document.createElementNS(ns, 'rect');
    tabBg.setAttribute('id', `tab-bg-${tab.key}`);
    tabBg.setAttribute('x', tx + 2); tabBg.setAttribute('y', tabY - 10);
    tabBg.setAttribute('width', tabW - 4); tabBg.setAttribute('height', '22');
    tabBg.setAttribute('rx', '4');
    tabBg.setAttribute('fill', tab.key === mfdActiveTab ? '#1F2937' : 'none');
    tabBg.setAttribute('cursor', 'pointer');
    g.appendChild(tabBg);

    const tabTxt = document.createElementNS(ns, 'text');
    tabTxt.setAttribute('id', `tab-txt-${tab.key}`);
    tabTxt.setAttribute('x', tx + tabW / 2); tabTxt.setAttribute('y', tabY);
    tabTxt.setAttribute('fill', tab.key === mfdActiveTab ? '#E5E7EB' : '#6B7280');
    tabTxt.setAttribute('font-family', 'sans-serif');
    tabTxt.setAttribute('font-size', '10');
    tabTxt.setAttribute('text-anchor', 'middle');
    tabTxt.setAttribute('dominant-baseline', 'middle');
    tabTxt.setAttribute('cursor', 'pointer');
    tabTxt.textContent = tab.label;
    g.appendChild(tabTxt);

    [tabBg, tabTxt].forEach(el => el.addEventListener('click', () => switchTab(tab.key)));
  });

  // Trip data content area
  const contentG = document.createElementNS(ns, 'g');
  contentG.setAttribute('id', 'mfd-content');
  g.appendChild(contentG);

  // ODO line (bottom of MFD)
  const odoSep = document.createElementNS(ns, 'line');
  odoSep.setAttribute('x1', MX + 6);  odoSep.setAttribute('y1', MY + MH - 25);
  odoSep.setAttribute('x2', MX + MW - 6); odoSep.setAttribute('y2', MY + MH - 25);
  odoSep.setAttribute('stroke', '#1F2937'); odoSep.setAttribute('stroke-width', '1');
  g.appendChild(odoSep);

  const odoEl = document.createElementNS(ns, 'text');
  odoEl.setAttribute('x', MX + MW / 2); odoEl.setAttribute('y', MY + MH - 13);
  odoEl.setAttribute('fill', '#6B7280');
  odoEl.setAttribute('font-family', 'monospace');
  odoEl.setAttribute('font-size', '10');
  odoEl.setAttribute('text-anchor', 'middle');
  odoEl.setAttribute('dominant-baseline', 'middle');
  odoEl.textContent = 'ODO  95,432 km';
  g.appendChild(odoEl);

  // Initial render
  renderTabContent(mfdActiveTab);
  updateClock();
  setInterval(updateClock, 1000);
}

function renderTabContent(tabKey) {
  const ns = 'http://www.w3.org/2000/svg';
  const MX = 318, MY = 100, MW = 164;
  const contentG = document.getElementById('mfd-content');
  while (contentG.firstChild) contentG.removeChild(contentG.firstChild);

  const rows = TRIP_DATA[tabKey] || [];
  rows.forEach((row, i) => {
    const y = MY + 105 + i * 28;

    const keyEl = document.createElementNS(ns, 'text');
    keyEl.setAttribute('x', MX + 10); keyEl.setAttribute('y', y);
    keyEl.setAttribute('fill', '#6B7280');
    keyEl.setAttribute('font-family', 'sans-serif');
    keyEl.setAttribute('font-size', '10');
    keyEl.setAttribute('dominant-baseline', 'middle');
    keyEl.textContent = row[0];
    contentG.appendChild(keyEl);

    const valEl = document.createElementNS(ns, 'text');
    valEl.setAttribute('x', MX + MW - 10); valEl.setAttribute('y', y);
    valEl.setAttribute('fill', '#E5E7EB');
    valEl.setAttribute('font-family', 'monospace');
    valEl.setAttribute('font-size', '11');
    valEl.setAttribute('font-weight', 'bold');
    valEl.setAttribute('text-anchor', 'end');
    valEl.setAttribute('dominant-baseline', 'middle');
    valEl.textContent = row[1];
    contentG.appendChild(valEl);
  });
}

function switchTab(tabKey) {
  const TABS = ['tripA', 'tripB', 'settings'];
  TABS.forEach(t => {
    const bg  = document.getElementById(`tab-bg-${t}`);
    const txt = document.getElementById(`tab-txt-${t}`);
    if (!bg || !txt) return;
    const active = t === tabKey;
    bg.setAttribute('fill', active ? '#1F2937' : 'none');
    txt.setAttribute('fill', active ? '#E5E7EB' : '#6B7280');
  });
  mfdActiveTab = tabKey;
  renderTabContent(tabKey);
}

function updateClock() {
  const now = new Date();
  const hh  = String(now.getHours()).padStart(2, '0');
  const mm  = String(now.getMinutes()).padStart(2, '0');
  const el  = document.getElementById('mfd-clock');
  if (el) el.textContent = `${hh}:${mm}`;
}
```

- [ ] **Step 2: Thêm lời gọi trong init section (sau warning panel calls)**

```javascript
buildMFD();
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add MFD center display with gear, clock, trip tabs, ODO"
```

---

## Task 8: cluster.html — window.cluster JS API + Animations + Quick Actions

**Files:**
- Modify: `app/src/main/assets/cluster.html`

- [ ] **Step 1: Thêm toàn bộ `window.cluster` API, blink logic, startup sweep, và Quick Actions handler vào `<script>` (sau `buildMFD`)**

```javascript
// ── State ────────────────────────────────────────────────────────────────────
const activeIndicators = new Set();
let   doorLockState    = false;  // false=unlocked, true=locked
let   blinkTimer       = null;
let   blinkPhase       = true;

// ── Blink engine (1 Hz for turn signals + hazard) ────────────────────────────
function startBlink(keys) {
  if (blinkTimer) clearInterval(blinkTimer);
  blinkPhase = true;
  blinkTimer = setInterval(() => {
    blinkPhase = !blinkPhase;
    const alpha = blinkPhase ? '1.0' : '0.10';
    keys.forEach(k => {
      const el = document.getElementById(
        k === 'left_turn' ? 'turn-left' :
        k === 'right_turn' ? 'turn-right' :
        `light-${k}`
      );
      if (el) el.setAttribute('opacity', alpha);
    });
  }, 500);
}

function recalcBlink() {
  const blinkKeys = [];
  if (activeIndicators.has('hazard')) {
    blinkKeys.push('left_turn', 'right_turn', 'hazard');
  } else {
    if (activeIndicators.has('left_turn'))  blinkKeys.push('left_turn');
    if (activeIndicators.has('right_turn')) blinkKeys.push('right_turn');
  }
  if (blinkKeys.length > 0) {
    startBlink(blinkKeys);
  } else {
    if (blinkTimer) { clearInterval(blinkTimer); blinkTimer = null; }
  }
}

// ── Light click handler (user taps icon on cluster) ──────────────────────────
function handleLightClick(key) {
  let newState;
  if (key === 'door_lock') {
    doorLockState = !doorLockState;
    const sendKey = doorLockState ? 'door_lock' : 'door_unlock';
    newState = true;
    if (typeof Android !== 'undefined') Android.sendExternalCommand(sendKey, true);
    // Update icon color: red=locked, amber=unlocked
    updateDoorIcon(doorLockState);
    return;
  }

  newState = !activeIndicators.has(key);
  if (typeof Android !== 'undefined') Android.sendExternalCommand(key, newState);
  // Visual update handled by window.cluster.setIndicator called back from Android
  // But for immediate feedback when not connected:
  window.cluster.setIndicator(key, newState);
}

function updateDoorIcon(isLocked) {
  const el = document.getElementById('light-door_lock');
  if (!el) return;
  const color = isLocked ? '#EF4444' : '#FBBF24';
  // Update circle fill and text color
  const circle = el.querySelector('circle');
  const txt    = el.querySelector('text');
  if (circle) { circle.setAttribute('fill', color); circle.setAttribute('stroke', color); }
  if (txt)    txt.setAttribute('fill', color);
  el.setAttribute('opacity', '1.0');
}

// ── window.cluster public API ─────────────────────────────────────────────────
window.cluster = {

  setSpeed(kmh) {
    const angle = (Math.min(Math.max(kmh, 0), SPEED.max) / SPEED.max) * SPEED.sweep;
    const el = document.getElementById('speed-needle');
    if (el) el.setAttribute('transform', `rotate(${angle}, ${SPEED.cx}, ${SPEED.cy})`);
  },

  setRPM(rpm) {
    const angle = (Math.min(Math.max(rpm, 0), TACH.max) / TACH.max) * TACH.sweep;
    const el = document.getElementById('tach-needle');
    if (el) el.setAttribute('transform', `rotate(${angle}, ${TACH.cx}, ${TACH.cy})`);
  },

  setIndicator(key, state) {
    if (state) activeIndicators.add(key); else activeIndicators.delete(key);

    const blinkKeys = ['left_turn', 'right_turn', 'hazard'];
    if (blinkKeys.includes(key)) {
      recalcBlink();
      if (!state) {
        const elId = key === 'left_turn' ? 'turn-left' :
                     key === 'right_turn' ? 'turn-right' :
                     `light-${key}`;
        const el = document.getElementById(elId);
        if (el) el.setAttribute('opacity', '0.12');
      }
      return;
    }

    if (key === 'door_lock') {
      updateDoorIcon(state);
      return;
    }

    const el = document.getElementById(`light-${key}`);
    if (!el) return;
    el.setAttribute('opacity', state ? '1.0' : '0.15');
  },

  setFuel(level)  { updateFuel(Math.round(Math.min(Math.max(level, 0), 8))); },
  setTemp(level)  { updateTemp(Math.round(Math.min(Math.max(level, 0), 8))); },

  setGear(gear) {
    const GEARS = ['P','R','N','D','S'];
    GEARS.forEach(g => {
      const box = document.getElementById(`gear-box-${g}`);
      const txt = document.getElementById(`gear-txt-${g}`);
      const active = g === gear;
      if (box) box.setAttribute('fill', active ? '#2563EB' : 'none');
      if (txt) {
        txt.setAttribute('fill', active ? '#FFFFFF' : '#4B5563');
        txt.setAttribute('font-size', active ? '14' : '11');
        txt.setAttribute('font-weight', active ? 'bold' : 'normal');
      }
    });
    mfdActiveGear = gear;
  },

  setDoorState(isLocked) {
    doorLockState = isLocked;
    updateDoorIcon(isLocked);
  },

  powerOn() {
    const totalMs      = 1500;
    const sweepUpMs    = 750;
    const holdMs       = 100;
    const sweepDownMs  = 650;
    let   startTime    = null;

    function easeInOutQuad(t) {
      return t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
    }

    function frame(ts) {
      if (!startTime) startTime = ts;
      const elapsed = ts - startTime;
      let frac;

      if (elapsed <= sweepUpMs) {
        frac = easeInOutQuad(elapsed / sweepUpMs);
      } else if (elapsed <= sweepUpMs + holdMs) {
        frac = 1.0;
      } else if (elapsed <= totalMs) {
        const t = (elapsed - sweepUpMs - holdMs) / sweepDownMs;
        frac = 1.0 - easeInOutQuad(Math.min(t, 1));
      } else {
        window.cluster.setSpeed(0);
        window.cluster.setRPM(0);
        return;
      }

      window.cluster.setSpeed(frac * SPEED.max);
      window.cluster.setRPM(frac * TACH.max);
      requestAnimationFrame(frame);
    }

    requestAnimationFrame(frame);
  }
};

// ── Quick Actions — debounce + pulse effect ───────────────────────────────────
document.querySelectorAll('.qa-btn').forEach(btn => {
  let debouncing = false;
  btn.addEventListener('click', () => {
    if (debouncing) return;

    const key      = btn.dataset.key;
    const durMs    = parseInt(btn.dataset.dur, 10);
    const colorCls = `active-${btn.dataset.color}`;

    // Visual: light up
    btn.classList.add(colorCls);

    // Debounce 500ms
    debouncing = true;
    btn.classList.add('debouncing');
    setTimeout(() => {
      debouncing = false;
      btn.classList.remove('debouncing');
    }, 500);

    // Send command to Android
    if (typeof Android !== 'undefined') Android.sendExternalCommand(key, true);

    // Fade out after pulse duration
    setTimeout(() => {
      btn.classList.remove(colorCls);
    }, durMs);
  });
});

// ── Boot ─────────────────────────────────────────────────────────────────────
// powerOn() will be called by Android after 300ms delay via evaluateJavascript.
// Fallback: auto-run after 500ms if Android doesn't call it (browser preview).
setTimeout(() => {
  if (typeof Android === 'undefined') {
    window.cluster.powerOn();
  }
}, 500);
```

- [ ] **Step 2: Thêm lời gọi init trong init section (sau `buildMFD()`)**

```javascript
// No additional init calls needed — window.cluster is self-contained
```

- [ ] **Step 3: Verify cấu trúc `<script>` đầy đủ theo thứ tự:**
  1. Constants (TACH, SPEED)
  2. Math helpers (RAD, px, ptOnCircle, arcPath)
  3. drawGaugeRing
  4. drawNeedle
  5. drawSegmentedGauge
  6. buildWarningPanel + buildTurnSignals
  7. buildMFD + renderTabContent + switchTab + updateClock
  8. State + blink engine + handleLightClick + updateDoorIcon
  9. window.cluster API
  10. Quick Actions event listeners
  11. Boot timeout
  12. **Init calls** (drawGaugeRing × 2, drawNeedle × 2, drawSegmentedGauge × 2, buildWarningPanel, buildTurnSignals, buildMFD)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/cluster.html
git commit -m "feat(cluster): add window.cluster JS API, startup sweep, blink engine, Quick Actions"
```

---

## Task 9: ActiveTestScreen.kt — Integrate WebView

**Files:**
- Modify: `app/src/main/java/com/example/bkdiagnostic/ui/screens/ActiveTestScreen.kt`

- [ ] **Step 1: Xóa toàn bộ các composables và functions không còn dùng**

Xóa các phần sau khỏi file (giữ nguyên package, imports cần thiết):
- Composable `FordRangerCluster2019` (dòng 190–339)
- Function `DrawScope.drawFordRangerCluster` (dòng 346–468)
- Function `DrawScope.drawFordGauge` (dòng 472–651)
- Function `DrawScope.drawSubGauge` (dòng 654–712)
- Function `DrawScope.drawWarningLightsPanel` (dòng 714–782)
- Composable `ActiveTestControlPanel` (dòng 788–đến cuối)
- Composable `CompactCommandButton`
- Composable `CategoryTab`
- Function `categoryColor`

- [ ] **Step 2: Xóa imports không còn dùng** — Xóa tất cả import liên quan đến Canvas, DrawScope, Brush, drawIntoCanvas, android.graphics.Paint, android.graphics.Typeface, kotlin.math.cos, kotlin.math.sin, GridCells, LazyVerticalGrid, SnackbarHost, SnackbarHostState

- [ ] **Step 3: Thêm imports cần thiết**

Thêm vào block import của file:

```kotlin
import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bkdiagnostic.ui.webbridge.ClusterJsBridge
```

- [ ] **Step 4: Thêm `webViewRef` state và `handleClusterCommand` vào `ActiveTestScreen`**

Thêm ngay sau khai báo `val scope = rememberCoroutineScope()`:

```kotlin
val webViewRef = remember { mutableStateOf<WebView?>(null) }

fun syncIndicator(key: String, isOn: Boolean) {
    val js = "window.cluster.setIndicator('$key', $isOn)"
    webViewRef.value?.post {
        webViewRef.value?.evaluateJavascript(js, null)
    }
}

fun handleClusterCommand(key: String, isOn: Boolean) {
    // Map key → command
    val cmd = commands.find { it.id == key || it.indicatorKey == key }
        ?: commands.find { it.id == key }
    if (cmd != null) {
        scope.launch(kotlinx.coroutines.Dispatchers.Main) {
            toggle(cmd)
        }
    }
}
```

- [ ] **Step 5: Sửa hàm `toggle` để gọi `syncIndicator` sau khi cập nhật state**

Tìm phần `activeStates[cmd.id] = true` và `activeStates[cmd.id] = false` và bổ sung `syncIndicator` call:

```kotlin
fun toggle(cmd: ActiveTestCommand) {
    if (!isConnected) {
        scope.launch { snackbarHost.showSnackbar(strErrorNotConnected) }
    }
    val isOn = activeStates[cmd.id] == true
    if (isOn) {
        if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
        activeStates[cmd.id] = false
        if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, false)
    } else {
        ActivityLogger.activeTest(viewModel.brandId, viewModel.modelId, cmd.name)
        if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOn)
        activeStates[cmd.id] = true
        if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, true)
        if (!cmd.isToggle) {
            scope.launch {
                delay(cmd.pulseDurationMs)
                if (isConnected) viewModel.sendActiveTestCommand(cmd.requestCanId, cmd.dataOff)
                activeStates[cmd.id] = false
                if (cmd.indicatorKey.isNotEmpty()) syncIndicator(cmd.indicatorKey, false)
            }
        }
    }
}
```

- [ ] **Step 6: Thay nội dung `Scaffold` — thay 2 phần Canvas + ControlPanel bằng WebView**

Thay toàn bộ nội dung bên trong `Scaffold(snackbarHost = ...) { padding -> Column(...) { ... } }` bằng:

```kotlin
Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF08080D))
            .padding(bottom = padding.calculateBottomPadding())
    ) {
        AppTopBar(
            title = strTitle,
            subtitle = viewModel.protocolConfig?.displayName ?: strActuatorControl,
            onBack = onBack
        )

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                @SuppressLint("SetJavaScriptEnabled")
                val wv = WebView(ctx).apply {
                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess   = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort   = true
                    settings.loadWithOverviewMode = true
                    addJavascriptInterface(
                        ClusterJsBridge { key, isOn ->
                            handleClusterCommand(key, isOn)
                        },
                        "Android"
                    )
                    loadUrl("file:///android_asset/cluster.html")
                }
                webViewRef.value = wv
                // Trigger startup sweep after WebView finishes loading
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        view?.postDelayed({
                            view.evaluateJavascript("window.cluster.powerOn()", null)
                        }, 300)
                    }
                }
                wv
            }
        )
    }
}
```

- [ ] **Step 7: Build project để kiểm tra compile**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/bkdiagnostic/ui/screens/ActiveTestScreen.kt
git commit -m "feat(active-test): replace Canvas cluster + control panel with interactive WebView cluster"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|---|---|
| SVG responsive viewBox | Task 2 — `preserveAspectRatio="xMidYMid meet"` |
| Speedometer 0–200 km/h | Task 2 — `SPEED.max=200` |
| Tachometer 0–6k, redline 4750 | Task 3 — `TACH.redlineAt=4750` |
| Light Cyan vạch chia opacity 0.8 | Task 3 — `rgba(180,240,255,0.80)` |
| Kim quét startup 1.5s | Task 8 — `powerOn()` easeInOutQuad |
| Warning lights clickable/display | Task 6 — `clickable` flag |
| Turn signals blink 1Hz | Task 8 — `setInterval(500)` |
| low_beam (green) / high_beam (blue) tách biệt | Task 6 — định nghĩa LIGHTS |
| Fuel segmented 8 segments | Task 5 — `drawSegmentedGauge` |
| Temp segmented 8 segments (đỏ > 6) | Task 5 — `activeColor='temp'` logic |
| MFD: clock thực, nhiệt độ, gear, trip, ODO | Task 7 — `buildMFD` |
| Quick Actions glassmorphism | Task 2 CSS — `backdrop-filter: blur(8px)` |
| Quick Actions debounce 500ms | Task 8 — `debouncing` flag |
| Quick Actions active state glow | Task 8 — `active-amber`, `active-blue` classes |
| `window.cluster.setSpeed/setRPM/setIndicator/setFuel/setTemp/setGear/setDoorState/powerOn` | Task 8 |
| `Android.sendExternalCommand` | Task 1 + Task 6/8 |
| Door back-sync `setDoorState` | Task 8 — `window.cluster.setDoorState` |
| RANGER watermark | Task 2 — SVG `<text>` mờ |
| Option B: không vẽ top bar trong HTML | Task 2 — không có top bar element |
| ClusterJsBridge.kt | Task 1 |
| ActiveTestScreen: giữ toggle logic, thêm WebView | Task 9 |

**Không có TBD / placeholder.**

**Type consistency:**
- `updateFuel` / `updateTemp`: closures được tham chiếu đúng từ Task 5 và Task 8
- `TACH.cx=185`, `SPEED.cx=615`: dùng nhất quán trong `drawNeedle` và `window.cluster.setSpeed/setRPM`
- `light-${id}`: dùng nhất quán giữa `buildWarningPanel` (Task 6) và `setIndicator` (Task 8)
- `handleClusterCommand` / `syncIndicator`: định nghĩa trong Task 9 trước khi dùng trong `toggle`

---

## Execution Handoff

Plan complete và saved tại `docs/superpowers/plans/2026-04-05-active-test-cluster-webview.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — Dispatch subagent mới cho mỗi task, review giữa các task, iteration nhanh

**2. Inline Execution** — Thực thi trong session này, có checkpoint để review

**Which approach?**
