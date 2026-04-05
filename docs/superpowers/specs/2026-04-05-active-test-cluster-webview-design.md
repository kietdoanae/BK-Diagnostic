# Active Test — Ford Ranger Instrument Cluster (WebView) Design

**Date:** 2026-04-05
**Branch:** claude/crazy-hertz
**Scope:** Thay thế Canvas Compose + ControlPanel bằng WebView hiển thị cluster.html tương tác

---

## 1. Tổng quan

Màn hình `ActiveTestScreen` hiện tại chia 2 phần: cluster Canvas (45%) + lưới nút điều khiển (55%). Thiết kế mới thay thế toàn bộ bằng:

- **AppTopBar** (Compose, giữ nguyên) — back navigation, title, subtitle
- **WebView** (fill phần còn lại) — load `assets/cluster.html`

Người dùng thao tác trực tiếp bằng cách **nhấn vào các icon đèn/chỉ báo trên cluster SVG** thay vì lưới nút riêng.

---

## 2. Files thay đổi

| File | Loại | Mô tả |
|---|---|---|
| `app/src/main/assets/cluster.html` | NEW | Toàn bộ cluster SVG + JS |
| `app/src/main/java/.../ui/screens/ActiveTestScreen.kt` | MODIFY | Bỏ Canvas + ControlPanel, thêm WebView |
| `app/src/main/java/.../ui/webbridge/ClusterJsBridge.kt` | NEW | `@JavascriptInterface` Android ↔ JS |

---

## 3. cluster.html — Bố cục SVG

### 3.1 Viewport & Responsive

```
viewBox="0 0 800 480"
width="100%" height="100%"
preserveAspectRatio="xMidYMid meet"
```

Không vẽ top bar — để khoảng trống `padding-top: 0` khớp với `AppTopBar` Android.
Nền: `#06060A` (gần đen).

### 3.2 Khu vực

```
┌─────────────────────────────────────────────────────────┐
│  ◀◀◀ (xi-nhan trái)        [Warning lights 2 hàng]   ▶▶▶ │
│                                                           │
│   ┌──────────┐    ┌─── MFD 4.2" ───┐    ┌──────────┐    │
│   │  TACH    │    │ 23°C  14:35    │    │  SPEED   │    │
│   │  0–6k    │    │  P R N D S     │    │  0–200   │    │
│   │  RPM     │    │ [Trip A][Trip B]│    │  km/h    │    │
│   │          │    │ [Settings]     │    │          │    │
│   │ [Fuel ▓▓]│    │   ODO: 95,432  │    │ [Temp ▓▓]│    │
│   └──────────┘    └────────────────┘    └──────────┘    │
│            "RANGER" watermark (mờ, dưới kim)             │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Thông số kỹ thuật đồng hồ

### 4.1 Tachometer (trái)

| Thuộc tính | Giá trị |
|---|---|
| Dải | 0 – 6 (×1000 r/min) |
| Góc bắt đầu | 150° (7 giờ) |
| Góc quét | 240° |
| Vùng bình thường | 0 – 4750 RPM (arc xanh dương / cyan) |
| **Redline** | 4750 – 6000 RPM (arc đỏ `#EF4444`, nền mờ) |
| Label | 0 1 2 3 4 5 6 |
| Đơn vị | ×1000 r/min |

### 4.2 Speedometer (phải)

| Thuộc tính | Giá trị |
|---|---|
| Dải | 0 – 200 km/h |
| Góc bắt đầu | 150° |
| Góc quét | 240° |
| Vùng nguy hiểm | 180 – 200 km/h (arc đỏ mờ) |
| Label | 0 40 80 120 160 200 |
| Đơn vị | km/h |

### 4.3 Màu sắc vạch chia & số

- Màu: `rgba(180, 240, 255, 0.80)` — Light Cyan, opacity 0.8 (hiệu ứng màn hình phát sáng)
- Vạch chính: `strokeWidth=2.5`, vạch phụ: `strokeWidth=1.2`
- Kim: trắng + đầu đỏ `#FF4444` (35% cuối kim)
- Hub kim: vòng tròn `#374151` viền, tâm `#D1D5DB`

### 4.4 Sub-gauges — Segmented (8 đoạn)

Thay thế arc liên tục bằng **8 segment hình chữ nhật tròn góc** xếp theo cung.

**Fuel gauge** (dưới Tachometer):
- Vị trí: góc 195° → 345° (sweep 150°)
- Màu active: `#F59E0B` (amber)
- Label: E (trái) — F (phải)
- Mức mặc định: 2/8

**Temp gauge** (dưới Speedometer):
- Vị trí: góc 195° → 345° (sweep 150°)
- Màu active: `#22C55E` (xanh lá) — đoạn cuối `#EF4444` (đỏ) nếu > 6/8
- Label: C (trái) — H (phải)
- Mức mặc định: 4/8

---

## 5. MFD — Màn hình trung tâm 4.2"

Hình chữ nhật bo góc, nền `#0D1117`, viền `#1F2937`.

### 5.1 Dòng thông tin trên (header MFD)

```
┌──────────────────────────────────┐
│ 23°C              [clock] 14:35  │  ← tĩnh (fake data)
├──────────────────────────────────┤
```

- Nhiệt độ ngoài trời: góc trên trái, font mono, màu `#9CA3AF`
- Đồng hồ giờ: góc trên phải, cập nhật real-time từ JS `Date`

### 5.2 Gear Indicator

```
  P   R   N  [D]  S
```

- Hiển thị `P R N D S` cách đều
- Ký tự được chọn: font lớn hơn + màu trắng + khung highlight `#2563EB`
- Mặc định: `D` được chọn
- API: `cluster.setGear(gear)` — `'P'|'R'|'N'|'D'|'S'`

### 5.3 Trip tabs

3 nút: `Trip A` | `Trip B` | `Settings`

**Trip A (mặc định):**
```
  Trip A
  ──────────────────
  Trip:   127.3 km
  Avg:    18.2 L/100
  Time:   6h 58m
  Range:  412 km
```

**Trip B:**
```
  Trip B
  ──────────────────
  Trip:   43.1 km
  Avg:    17.6 L/100
  Time:   2h 12m
  Range:  412 km
```

**Settings:**
```
  Settings
  ──────────────────
  Brightness:  ████░  80%
  Units:       km/h
  Language:    VI
```

### 5.4 Odometer

Cố định mép dưới MFD:
```
ODO  95,432 km
```
Font monospace, màu `#6B7280`.

---

## 6. Warning Lights — Bảng đèn cảnh báo

2 hàng × 5 icons, nằm trên cùng giữa cluster.
Mỗi icon: hình tròn nhỏ, mờ khi tắt (opacity 0.15), sáng khi bật.

| Key | Icon | Màu active | Hàng |
|---|---|---|---|
| `left_turn` | ← mũi tên | `#22C55E` nhấp nháy | — (nằm ngoài, góc trái) |
| `right_turn` | → mũi tên | `#22C55E` nhấp nháy | — (nằm ngoài, góc phải) |
| `hazard` | ⚠ | `#FBBF24` nhấp nháy cả 2 xi-nhan | 1 |
| `check_engine` | E (engine) | `#FBBF24` | 1 |
| `battery` | 🔋 | `#EF4444` | 1 |
| `oil_pressure` | oil drop | `#EF4444` | 1 |
| `high_beam` | 💡H | `#3B82F6` | 1 |
| `airbag` | airbag | `#FBBF24` | 2 |
| `door_lock` | door | `#EF4444` | 2 |
| `abs` | ABS | `#FBBF24` | 2 |
| `low_beam` | 💡L | `#22C55E` | 2 |
| `brake_light` | BR | `#EF4444` | 2 |
| `reverse_light` | R (reverse) | `#FFFFFF` | 2 |
| `horn` | 📢 | `#FBBF24` pulse | không hiển thị trong cluster |
| `front_wiper` | wiper | `#60A5FA` pulse | không hiển thị trong cluster |
| `rear_wiper` | wiper-r | `#60A5FA` pulse | không hiển thị trong cluster |

**Clickable icons** (nhấn để gửi lệnh):
`hazard`, `low_beam`, `high_beam`, `brake_light`, `reverse_light`, `door_lock`, `left_turn`, `right_turn`
→ Khi nhấn: JS gọi `Android.sendExternalCommand(key, newState)`

**Display-only** (không nhấn được, chờ dữ liệu từ Android):
`check_engine`, `battery`, `oil_pressure`, `airbag`, `abs`

---

## 7. Watermark RANGER

```svg
<text x="400" y="300"
      font-family="sans-serif" font-weight="bold"
      font-size="72" letter-spacing="20"
      fill="rgba(255,255,255,0.05)"
      text-anchor="middle">RANGER</text>
```

Nằm dưới kim đồng hồ, chữ in hoa, không chân (sans-serif), rất mờ.

---

## 8. JavaScript API (window.cluster)

### 8.1 Android → JS (gọi từ WebView.evaluateJavascript)

```javascript
window.cluster.setSpeed(value)       // 0–200, quay kim tốc độ
window.cluster.setRPM(value)         // 0–6000, quay kim vòng tua
window.cluster.setIndicator(key, state)  // key: string, state: boolean
window.cluster.setFuel(level)        // 0–8, số segment sáng
window.cluster.setTemp(level)        // 0–8, số segment sáng
window.cluster.setGear(gear)         // 'P'|'R'|'N'|'D'|'S'
window.cluster.powerOn()             // trigger startup sweep animation
```

### 8.2 JS → Android (gọi JavascriptInterface)

```javascript
Android.sendExternalCommand(key, state)
// key: 'left_turn'|'right_turn'|'hazard'|'low_beam'|'high_beam'|
//      'brake_light'|'reverse_light'|'door_lock'|'door_unlock'
// state: boolean (true = ON, false = OFF)
```

### 8.3 Startup Sweep Animation

Khi DOM load xong, tự động chạy:
1. Cả 2 kim quét từ 0 → max (Speed: 200, Tach: 6000) trong 0.75s
2. Ngừng 0.1s
3. Quét về 0 trong 0.65s
4. Tổng: ~1.5s

Dùng `requestAnimationFrame` + easing `easeInOutQuad`.

---

## 9. ClusterJsBridge.kt

```kotlin
class ClusterJsBridge(
    private val onCommand: (key: String, isOn: Boolean) -> Unit
) {
    @JavascriptInterface
    fun sendExternalCommand(key: String, isOn: Boolean) {
        onCommand(key, isOn)
    }
}
```

Đăng ký trong `ActiveTestScreen`:
```kotlin
webView.addJavascriptInterface(
    ClusterJsBridge { key, isOn -> handleClusterCommand(key, isOn) },
    "Android"
)
```

---

## 10. ActiveTestScreen.kt — Thay đổi

### Xóa:
- `FordRangerCluster2019` composable
- `drawFordRangerCluster`, `drawFordGauge`, `drawSubGauge`, `drawWarningLightsPanel`
- `ActiveTestControlPanel`, `CompactCommandButton`, `CategoryTab`
- Toàn bộ import Canvas/DrawScope

### Giữ nguyên:
- `AppTopBar`
- Hàm `toggle(cmd)` — logic gửi CAN
- `DisposableEffect` cleanup
- `activeStates` map

### Thêm:
- `AndroidView { WebView }` với config:
  - `javaScriptEnabled = true`
  - `domStorageEnabled = true`
  - `addJavascriptInterface(ClusterJsBridge, "Android")`
  - `loadUrl("file:///android_asset/cluster.html")`
- `LaunchedEffect(activeStates)` → `webView.evaluateJavascript("window.cluster.setIndicator('$key', $value)")`
- `LaunchedEffect(Unit)` → sau 300ms gọi `window.cluster.powerOn()`

---

## 11. Điểm kiểm tra (Definition of Done)

- [ ] Kim Speed/Tach quét startup khi WebView load
- [ ] Nhấn icon hazard trên cluster → cả 2 xi-nhan nhấp nháy 1Hz
- [ ] `low_beam` (xanh lá) và `high_beam` (xanh dương) hoạt động độc lập
- [ ] Fuel/Temp hiển thị đúng số segment (0/8 – 8/8)
- [ ] MFD hiển thị đồng hồ giờ thực, nhiệt độ, ODO
- [ ] Gear indicator `D` sáng mặc định, chuyển được
- [ ] Trip A/B/Settings tab chuyển đổi được
- [ ] `Android.sendExternalCommand` được gọi khi nhấn icon clickable
- [ ] SVG responsive, không méo trên màn hình 6" và 10" tablet
- [ ] Watermark "RANGER" hiển thị mờ dưới kim
