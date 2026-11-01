# Preset App cho máy Sony hỗ trợ OpenMemories — Kế hoạch (brainstorm)

App chạy trực tiếp trên máy ảnh Sony: người dùng chọn **tình huống chụp** → app tự đặt thông số phù hợp với **thân máy + ống kính đang gắn**, kèm gợi ý bố cục/tạo dáng trên live view.

- Máy khởi đầu: **Sony A5100**. Mục tiêu: mọi máy hỗ trợ PlayMemories Camera Apps (PMCA).
- Dự án mã nguồn mở, **cộng đồng đóng góp** profile máy, profile lens, preset và bản dịch.

## 1. Nền tảng kỹ thuật
- Máy hỗ trợ PMCA cài được app Android tuỳ biến qua **Sony-PMCA-RE**; app phải tương thích **Android 2.3.7** (Java kiểu cũ, không thư viện hiện đại). Máy chấp nhận cả chứng chỉ debug.
- **OpenMemories Framework** (ma1co) để dùng API camera riêng của Sony.
- Tham khảo: **obs1dium/BetterManual** (live view, khẩu/tốc/ISO/EV, M/A trong app), **PMCADemo**, **OpenMemories-Tweak** (adb/telnet để debug).
- Danh sách máy tương thích: trang devices của tài liệu OpenMemories (openmemories.readthedocs.io). Máy đời mới không có PMCA thì không cài được app → ngoài phạm vi. Theo Recipe Lab, máy cuối cùng chạy được app là A6500 và A99 II (cuối 2016); kho PlayMemories đã đóng năm 2021 → mọi thứ đi qua Sony-PMCA-RE.

### Dự án tham khảo gần nhất: voxivoid/recipe-lab-sony-pmca
- Làm phần **màu/"look"** (Creative Style + slider, WB, bù sáng, Picture Effect, DRO, Quality) — **bổ sung** cho app này (phơi sáng/AF/tình huống), không trùng.
- Ghi recipe vào **settings store** của máy → giữ nguyên khi thoát app, áp cho mọi chế độ, cả video, sau khi tắt máy.
- Kỹ thuật đáng học: CameraEx qua reflection, bản đồ settings-store (`docs/DEVELOPMENT.md`), kiểm tra slot read-only, build không Gradle (`build.sh`: ndk-build, aapt, javac, d8, zipalign, apksigner), JUnit cho phần logic.
- Quy trình đáng học: mẫu issue compatibility report (`.yml`), bảng Compatibility trong README, mỗi push build APK `-dev.N` vào prerelease `dev`, commit theo scope (`feat(browser): …`), squash merge.
- Đã có **custom recipe**: tải từ thẻ SD và đồng bộ với điện thoại → nghiên cứu định dạng file và cơ chế sync để làm tương tự (hoặc tương thích) cho preset của app này.
- Điểm khác biệt của app này: preset theo **tình huống chụp** (phơi sáng, AF, drive, gợi ý bố cục) và **tính theo thân máy + lens**, thay vì preset màu.
- Cân nhắc liên hệ tác giả: dùng chung bản đồ settings store, hoặc preset tham chiếu tới recipe màu của Recipe Lab.

## 2. Ba loại profile (dữ liệu, cộng đồng đóng góp)

### 2.1 Camera profile
Mô tả khả năng thân máy; engine dùng để tính và để ẩn/hiện tính năng.
```json
{
  "id": "ilce-5100",
  "name": "Sony A5100",
  "sensor": { "format": "APS-C", "width_mm": 23.5, "height_mm": 15.6, "coc_mm": 0.02 },
  "ibis": false,
  "fixed_lens": null,              // máy compact (RX100…) trỏ tới lens profile tích hợp
  "iso": { "min": 100, "max": 25600 },
  "burst_fps": { "lo": 3, "hi": 6 },
  "controls": { "touchscreen": true, "front_dial": false, "rear_wheel": true, "custom_keys": 1 },
  "features": {                    // xác nhận bằng test thực tế, không đoán
    "face_detection": "yes", "eye_af": "unknown", "soft_skin": "unknown",
    "creative_style": "unknown", "silent_shutter": "no", "read_lens_info": "unknown"
  },
  "tested_firmware": ["3.10"],
  "tested_by": ["@hieu"]
}
```

### 2.2 Lens profile
```json
{
  "id": "sigma-30-f14-dc-dn",
  "name": "Sigma 30mm F1.4 DC DN",
  "mount": "E", "coverage": "APS-C",
  "type": "prime",                 // prime | zoom
  "focal_min": 30, "focal_max": 30,
  "aperture_max": 1.4, "aperture_min": 16,
  "sweet_spot": [2.8, 5.6],
  "ois": false,
  "electronic": true,              // false = lens manual/legacy: chỉ gợi ý khẩu
  "af": true,
  "min_focus_m": 0.3
}
```
- Lens điện tử: thử đọc focal/khẩu qua API để **tự nhận lens**.
- Lens manual/adapter: chọn profile thủ công.
- Lens full-frame gắn trên APS-C và ngược lại (crop mode) → engine xử lý theo `coverage` + `sensor`.

### 2.3 Preset
Preset là **quy tắc**, không phải thông số cố định → một preset chạy được trên mọi máy/lens.
```json
{
  "id": "portrait-bokeh",
  "name": { "vi": "1 người xoá phông", "en": "Single portrait, blurred background" },
  "goal": "shallow_dof",
  "aperture": { "strategy": "widest_safe", "stop_down_from_max": 0.5 },
  "shutter": { "strategy": "handheld_min" },
  "iso": "auto",
  "af": { "mode": "single", "face": true, "eye": "if_available" },
  "style": { "creative_style": "portrait", "fallback": "standard" },
  "hints": ["distance_half_body", "background_far"]
}
```

## 3. Preset engine (camera + lens + preset → thông số)
- **Tốc an toàn cầm tay**: `1 / (2 × focal × crop)`; nới 2–3 stop nếu lens có OIS hoặc thân có IBIS.
- **Khẩu theo DOF**: tính từ focal, khoảng cách, CoC của cảm biến → khẩu đủ nét (vd. nhóm 2 hàng).
- **Khoảng cách gợi ý**: từ góc nhìn (focal + kích thước cảm biến) → "đứng cách ~X m để lấy toàn thân / nửa người".
- **Kẹp** mọi giá trị vào giới hạn lens và máy.
- **Fallback theo capability**: tính năng `no`/`unknown` → bỏ qua hoặc thay thế, báo nhẹ trên UI.

### Preset ban đầu
| Preset | Logic chính |
|---|---|
| Nhiều người | Khẩu đủ DOF theo số hàng, tốc ≥1/125, nhận diện nhiều mặt, burst, hẹn giờ tuỳ chọn |
| 1 người xoá phông | Khẩu lớn nhưng chừa an toàn (vd. Sigma 30 → f/1.8–2.0), lấy nét mắt nếu có |
| 1 người lấy cảnh | Khẩu vùng sweet spot, lấy nét mặt, gợi ý bố cục 1/3 |
| Ngược sáng / silhouette | Đo sáng điểm vào trời, EV −1.5 đến −2 |
| High-key | EV +1 đến +1.5 |
| Trẻ con chạy nhảy | Tốc ≥1/500, AF liên tục, burst, Auto ISO |
| Trong nhà thiếu sáng | Khẩu mở, tốc tối thiểu theo lens/thân, Auto ISO trần cao |
| Nghệ thuật (tuỳ biến) | Người dùng/cộng đồng tự định nghĩa |

## 4. Kiến trúc
```
core/            Java thuần, không phụ thuộc Android/Sony
                 PresetEngine, tính DOF/FOV/tốc, model profile
                 → test được trên JVM desktop, chạy CI
camera-adapter/  Lớp mỏng gọi OpenMemories; ánh xạ thông số → API;
                 phát hiện model + capability lúc chạy
app/             UI Android 2.3.7: chọn preset, live view + overlay, settings
profiles/
  cameras/*.json
  lenses/*.json
  presets/*.json
  schema/*.schema.json
i18n/            vi, en (cộng đồng thêm ngôn ngữ)
tools/           script kiểm tra schema, đóng gói profile; (sau) web tool tạo profile
```
- Profile đóng gói sẵn trong APK + cho phép ghi đè/thêm từ thẻ SD (người dùng thử profile mới không cần build lại).
- UI thích ứng theo `controls`: máy có cảm ứng (A5100) vs chỉ có bánh xe/nút (A6000…).

## 5. Đóng góp cộng đồng
- `CONTRIBUTING.md`: cách thêm camera/lens/preset, chạy kiểm tra schema.
- CI: validate JSON theo schema + unit test engine cho mọi tổ hợp camera×lens×preset (không ra thông số ngoài giới hạn).
- Mẫu issue **"Báo cáo test máy"**: model, firmware, bảng tính năng chạy/không chạy → cập nhật `features` trong camera profile.
- Trạng thái máy: `verified` / `community-tested` / `untested`.
- Bản đầu: tiếng Việt + tiếng Anh.

### Phân phối & license
- Phân phối tự do qua **GitHub Releases** (giống Recipe Lab): APK release + prerelease `dev` build tự động mỗi push (GitHub Actions).
- Cài đặt: `pmca-console.py install -d native -f <App>.apk` hoặc PMCA-GUI → Install App. README có hướng dẫn từng bước + cách gỡ.
- Lỗi cài/USB → chuyển về issue của Sony-PMCA-RE; lỗi preset/app → issue của repo này.
- License: **GPL-3.0**, giống Recipe Lab. Hệ quả: được tái dùng code GPL-3.0 của Recipe Lab (vd. bản đồ settings store, cơ chế sync) miễn là toàn bộ app phát hành mã nguồn theo GPL-3.0; code MIT (BetterManual) nhúng vào được. Vẫn cần xác nhận license của OpenMemories Framework là tương thích.
- Ghi chú miễn trừ trách nhiệm trong README (app không chính thức, dùng với rủi ro tự chịu).

## 5b. Kiểm thử tự động (bám theo firmware thật)
Ý tưởng: tải firmware chính thức của từng máy, giải nén và **đọc** trong lúc test để kiểm tra code khớp với máy thật. **Không commit firmware hay file trích xuất** (bản quyền Sony).

### Ba tầng test
1. **Unit test JVM** (`core/`): engine, DOF/FOV/tốc, validate profile. Chạy mọi push.
2. **Contract test theo firmware** (máy × phiên bản firmware):
   - Giải nén firmware (công cụ unpack của hệ sinh thái ma1co, vd. fwtool) → lấy framework jar của Sony (`com.sony.scalar…`, CameraEx).
   - Kiểm tra mọi class/method/hằng số mà `camera-adapter` gọi (kể cả qua reflection) **tồn tại đúng chữ ký** trên từng model/firmware.
   - Nếu được: trích bảng slot/settings store (đối chiếu bản đồ của Recipe Lab) để kiểm slot ID theo model.
   - Sinh **báo cáo capability** (chỉ là dữ kiện: có/không, tên API) → được commit, dùng để điền `features` trong camera profile.
3. **Test trên máy thật**: chế độ self-test trong app (kiểu kiểm slot read-only / chụp mẫu của Recipe Lab) + mẫu issue compatibility report.

Firmware chỉ cho biết API **có tồn tại**, không chứng minh nó **chạy đúng** → tầng 3 vẫn bắt buộc.

### Cách làm cho an toàn
- `firmware/sources.json` (commit): model, phiên bản, URL tải chính thức của Sony, sha256 — chỉ là con trỏ, không chứa file.
- `tools/fetch-firmware` + `tools/extract-firmware`: tải → kiểm sha256 → giải nén vào `.firmware/` (nằm trong `.gitignore`).
- CI: job riêng, dùng Actions cache cho file tải về; **không upload firmware/file trích xuất làm artifact** (repo public thì artifact ai cũng tải được). Chỉ xuất báo cáo dữ kiện.
- Nguồn thay thế khi Sony gỡ link: contributor tự dump firmware từ máy mình (Sony-PMCA-RE updater mode có lệnh dump), chạy script local, chỉ gửi PR báo cáo capability.
- Máy chưa có firmware trong `sources.json` → contract test bỏ qua (skip), không fail.
- Rủi ro pháp lý: đọc firmware để đảm bảo tương thích, không phân phối lại; nên ghi rõ trong README/CONTRIBUTING (không phải tư vấn pháp lý).

## 6. Lộ trình
1. **Spike trên A5100**: dựng môi trường build, cài hello world + live view, xác nhận firmware.
2. **Khảo sát API trên A5100**: khẩu, tốc, ISO, Auto ISO + tốc tối thiểu, EV, đo sáng, AF, nhận diện mặt, Eye AF, drive mode, hẹn giờ, Creative Style, Soft Skin, đọc focal/khẩu lens, đọc model máy → điền camera profile đầu tiên.
3. **core + schema**: engine, 3 loại profile, test trên JVM.
3b. **Pipeline firmware**: fetch/extract firmware A5100, contract test cho các API đã dùng, sinh báo cáo capability đầu tiên.
4. **MVP**: 3 preset (nhiều người, xoá phông, lấy cảnh) + 2 lens (kit 16-50 OIS, Sigma 30 f/1.4) trên A5100.
5. Overlay gợi ý (khoảng cách, bố cục) + DOF.
6. **Mở cho cộng đồng**: tách camera-adapter, phát hiện model lúc chạy, CONTRIBUTING, mẫu báo cáo test, profile cho máy thứ hai (vd. A6000 — không cảm ứng, để kiểm UI thích ứng).
7. Preset nghệ thuật tuỳ biến, thêm ngôn ngữ, web tool tạo profile.

## 7. Câu hỏi mở
- API có trả về model máy, lens ID, focal hiện tại không?
- Eye AF, Soft Skin, Creative Style có điều khiển được qua API không, và khác nhau thế nào giữa các đời máy?
- App đặt được chế độ phơi sáng (M/A/S) khi mặt xoay ở chế độ khác không? (BetterManual làm được M/A.)
- Có nên đăng ký app vào danh sách app của Sony-PMCA-RE (để chọn cài bằng `install -i`) ngoài GitHub Releases?
- **Áp preset kiểu nào**: (a) chỉ trong lúc app mở (như BetterManual), hay (b) ghi vào settings store để giữ sau khi thoát (như Recipe Lab)? (b) tiện hơn nhưng khẩu/tốc/chế độ phơi sáng có thể không ghi được kiểu này → cần thử ở bước khảo sát API.
