# Ticket Hub — Thiết kế giao diện (prototype)

> Prototype tương tác (xem & bấm thử): **https://claude.ai/artifact/13t7cm5BAqJ63AZa18fDgU** (bản sao của `prototype/`; sửa file nào thì publish lại file đó. Canvas chặn ảnh từ domain ngoài: khi publish, thay URL `images.unsplash.com` bằng ảnh đã upload vào asset của canvas `/_blob/…`, cùng mã ảnh Unsplash)
> Mã nguồn từng màn: [prototype/](prototype/) — mỗi file `*.dc.html` là 1 màn hình.
> Thiết kế hệ thống: [overview.md](../../overview.md) · Kế hoạch: [plans/](../plans/2026-10-19-roadmap.md)

## 1. Cách dùng tài liệu này

- Đây là **bản tham chiếu giao diện** khi code FE (React + shadcn). Không copy nguyên file `.dc.html` vào code — chúng chạy trên runtime riêng của canvas, không phải React.
- Khi code một màn: mở prototype → bấm **Play** để xem hành vi → đối chiếu bảng màn hình (mục 4) để biết route, component shadcn cần dùng và sprint thực hiện. Quy ước chung ở mục 3 làm một lần thành component dùng chung (Sprint 1–2), không làm lại theo từng màn.
- Muốn sửa thiết kế: sửa trên canvas trước (hoặc nhờ Claude), rồi chép lại file vào `prototype/` — giữ prototype là nguồn sự thật về giao diện.
- Mọi màn có prop **Tweaks → theme** (`system` · `light` · `dark`) để xem chế độ sáng/tối. Màn Check-in luôn tối (giao diện camera).

## 2. Theme (shadcn — Emerald, sáng + tối)

Font **Manrope** (400/500/600/700, có tiếng Việt). Độ đậm tối đa 600 cho tiêu đề và tên sự kiện; 700 chỉ dùng cho logo. Màu nhấn phụ **amber** chỉ cho khoảnh khắc khẩn / hot (đếm ngược mở bán, "Sắp hết vé"), không dùng cho nút.

Dán vào `src/index.css` (Tailwind v4 + shadcn). Chế độ tối dùng class `.dark` (mặc định theo hệ điều hành, người dùng đổi được ở header / menu tài khoản).

```css
:root {
  --radius: 0.5rem;
  --font-sans: 'Manrope', system-ui, sans-serif;

  --background: #FFFFFF;
  --foreground: #0A0F0D;
  --card: #FFFFFF;
  --card-foreground: #0A0F0D;
  --popover: #FFFFFF;
  --popover-foreground: #0A0F0D;
  --primary: #047857;              /* emerald-700 — chữ trắng 5.5:1 */
  --primary-foreground: #FFFFFF;
  --secondary: #F1F3F2;
  --secondary-foreground: #0A0F0D;
  --muted: #F1F3F2;
  --muted-foreground: #5A6461;     /* 6.1:1 trên nền trắng */
  --accent: #F1F3F2;
  --accent-foreground: #0A0F0D;
  --destructive: #DC2626;
  --border: #E3E7E5;
  --input: #E3E7E5;
  --ring: #047857;

  --chart-1: #059669;
  --chart-2: #1D4ED8;
  --chart-3: #F59E0B;
  --chart-4: #A21CAF;
  --chart-5: #C2410C;

  --sidebar: #F6F7F6;
  --sidebar-foreground: #0A0F0D;
  --sidebar-primary: #047857;
  --sidebar-primary-foreground: #FFFFFF;
  --sidebar-accent: #E7F5EF;       /* mục đang active */
  --sidebar-accent-foreground: #065F46;
  --sidebar-border: #E3E7E5;
  --sidebar-ring: #047857;
}

.dark {
  --background: #0A0F0D;
  --foreground: #E8EFEC;
  --card: #121A17;
  --card-foreground: #E8EFEC;
  --popover: #121A17;
  --popover-foreground: #E8EFEC;
  --primary: #34D399;              /* chữ #022C22 7.9:1 */
  --primary-foreground: #022C22;
  --secondary: #18221E;
  --secondary-foreground: #E8EFEC;
  --muted: #18221E;
  --muted-foreground: #9AA7A2;     /* 7.7:1 */
  --accent: #18221E;
  --accent-foreground: #E8EFEC;
  --destructive: #DC2626;
  --border: #222C28;
  --input: #222C28;
  --ring: #6EE7B7;

  --chart-1: #10B981;
  --chart-2: #60A5FA;
  --chart-3: #FBBF24;
  --chart-4: #E879F9;
  --chart-5: #FB923C;

  --sidebar: #0E1412;
  --sidebar-foreground: #E8EFEC;
  --sidebar-primary: #34D399;
  --sidebar-primary-foreground: #022C22;
  --sidebar-accent: #0C2D23;
  --sidebar-accent-foreground: #6EE7B7;
  --sidebar-border: #222C28;
  --sidebar-ring: #6EE7B7;
}
```

| Token phụ | Sáng / Tối | Dùng cho |
|---|---|---|
| `night` | `#022C22` / `#03251D` (chữ `#ECFDF5`, phụ `#A7C9BC`) | Vùng "xanh đêm" — chữ ký brand: dải *Sắp mở bán*, hero chi tiết sự kiện, footer, ô đếm ngược |
| `hot` / `on-hot` | `#FBBF24` / `#3B2504` (chữ amber trên nền sáng `#B45309`, trên nền tối `#FCD34D`) | Đếm ngược mở bán, "Sắp hết vé" — không dùng cho nút |
| Trạng thái (chấm badge) | xanh `#16A34A`/`#4ADE80` · vàng `#F59E0B`/`#FBBF24` · xanh dương `#2563EB`/`#60A5FA` · xám `#8A938F`/`#7D8A85` · đỏ `#DC2626` | Chấm màu trong badge trạng thái, luôn kèm chữ |
| Hạng vé | `tier-1` `#C2410C`/`#FB923C` · `tier-2` `#1D4ED8`/`#60A5FA` · `tier-3` `#A21CAF`/`#E879F9`; thêm `#0E7490`, `#4D7C0F`, `#475569` | Màu ghế theo hạng (organizer chọn từ bảng cố định). Không trùng màu brand và màu trạng thái; ghế tô đặc, đã bán là ô xám có dấu ×, đang chọn = màu primary + viền |
| Ghế đã bán | `#DCE2DF` / `#26302B` | Sơ đồ ghế |

Mã QR trên vé **luôn đen trên nền trắng** (kể cả chế độ tối) để máy quét đọc được. Bộ token đầy đủ cho prototype nằm ở đầu mỗi file `*.dc.html` (khối *Ticket Hub tokens*); khi đổi màu, đổi ở đây trước rồi cập nhật khối đó.

## 3. Quy ước giao diện

| Quy ước | Chi tiết | Component shadcn |
|---|---|---|
| **Layout khách hàng** | Header 64px (logo · ô tìm kiếm · Bán vé cùng Ticket Hub · Vé của tôi · sáng/tối · chuông · avatar) + tab danh mục gạch chân; footer xanh đêm. Mobile: ẩn ô tìm kiếm và link chữ, tab danh mục cuộn ngang | — |
| **Ảnh sự kiện** | Ảnh làm chủ đạo (card 16:10, hero, poster 16:9). Chip ngày nằm ngang trên ảnh (`T7, 15/11`). Không có ảnh: poster màu tự động (nền màu + chữ Manrope) | — |
| **Layout Organizer / Admin** | Sidebar nhóm rõ (Admin: Vận hành · Danh mục · Hệ thống; Organizer: Tổng quan · Bán vé · Tài khoản), **không số đếm**, user ở cuối (bấm → menu Hồ sơ · Thông báo · Về trang mua vé · Đăng xuất). Topbar sticky: breadcrumb · Tìm nhanh (Ctrl K → popup tìm trang và thao tác) · sáng/tối · chuông (popover thông báo). **Breadcrumb luôn bắt đầu bằng `Trang chủ ›`** (trang Tổng quan của vai trò). Click ra ngoài đóng mọi popover / menu | `Sidebar`, `Breadcrumb`, `Command`, `DropdownMenu`, `Popover` |
| **Mọi màn danh sách** | Tiêu đề trang chỉ có tên. **Toolbar** (một component dùng chung, **ghim dưới topbar khi cuộn**) gồm 2 nhóm: trái = tìm + bộ lọc + "Đặt lại", được xuống dòng khi nhiều bộ lọc; phải = Cột (icon, tooltip) · Xuất · menu **Excel ▾** (Import Excel, Tải file mẫu, Xuất Excel) · **nút Tạo**, luôn nằm hàng đầu. Chọn dòng → không thêm hàng mới: nút thao tác hàng loạt hiện ở nhóm phải kèm số lượng (vd. "Nhân bản thành nháp (2)"), nút Xuất đổi thành "Xuất (2)" = xuất các dòng đã chọn; bộ lọc giữ nguyên. Bảng + phân trang; đủ trạng thái đang tải (skeleton), rỗng, không có kết quả, lỗi + thử lại. Tiền và giờ không xuống dòng | Data Table (`@tanstack/react-table`), `Popover` + `Command`, `Pagination`, `Skeleton` |
| **Bộ lọc** | Nút viền đứt rộng cố định; chọn xong hiện vạch ngăn + giá trị trong nút (quá 2 giá trị → "3 đã chọn", dài thì cắt "…"). Popover rộng cố định: **chọn nhiều** = checkbox, dòng đầu "Chọn nhiều" có checkbox chọn/bỏ tất cả (3 trạng thái); **chọn một** = radio, có "Tất cả" và "Tuỳ chọn khoảng ngày" (hiện ô Từ / Đến). Danh sách dài có ô tìm. Không đếm số lượng theo option. Bộ lọc ghi lên URL | `Popover`, `Command`, `Checkbox`, `RadioGroup` |
| **Header bảng** | Nền `secondary` (xám nhạt), chữ đậm 600 màu `fg-2`, bo góc trên theo khung bảng | `Table` |
| **Dropdown** | Không dùng `<select>` của trình duyệt. Một component chung: nút giống ô nhập + mũi tên; danh sách tự vẽ, option đang chọn đậm + dấu ✓, giá trị dài cắt "…", ở cuối trang mở lên trên. Danh sách thực thể (địa điểm, tỉnh/xã, organizer, sự kiện) hoặc trên 7 option có ô tìm ghim ở đầu danh sách + "Không có kết quả". Click ra ngoài đóng | `Select`; danh sách dài: `Combobox` (`Popover` + `Command`) |
| **Toast** | Góc dưới phải, nền `card`, viền, icon trạng thái (✓ xanh / ! đỏ), nút Đóng; tự ẩn sau 5 giây, đọc qua `aria-live` | `Sonner` |
| **Thao tác** | Tạo / sửa / xoá / duyệt / hoàn tiền mở **modal**; xem chi tiết bản ghi mở **side sheet** có URL (`?order=…`). Modal, sheet, popup: **click ra ngoài**, nút ✕ hoặc Esc đều đóng và trả focus. Thao tác phá huỷ dùng nút đỏ + câu xác nhận rõ hậu quả | `Dialog`, `AlertDialog`, `Sheet`; mobile dùng `Drawer` |
| **Menu dòng** | Nút `⋯` cuối mỗi dòng → menu; mục nguy hiểm màu đỏ, ngăn cách bằng separator | `DropdownMenu` |
| **Import Excel** | Nút **Tải file mẫu** và **Import Excel** nằm bên phải toolbar (cùng hàng lọc). Modal 3 bước: Chọn file (.xlsx/.csv, ≤ 5 MB, ≤ 5.000 dòng, chọn khi trùng mã: cập nhật / bỏ qua, bảng cột của file mẫu) → Kiểm tra (dry-run: tổng / hợp lệ / lỗi theo dòng–cột, tải file lỗi) → Kết quả (thêm mới / cập nhật / bỏ qua, ghi audit log). Dòng lỗi không chặn dòng hợp lệ | `Dialog`, `Table`, `Alert` |
| **Badge trạng thái** | Viền mảnh + chấm màu + chữ (không dùng nền pastel) | `Badge variant="outline"` |
| **Trường bắt buộc** | Dấu `*` đỏ nằm **cùng dòng** với nhãn (`Tên sự kiện *`); gợi ý ngắn đặt dưới ô nhập | `Label` + `FormDescription` |
| **Focus & chuyển động** | Mọi control có viền focus 2px màu `ring`; tôn trọng `prefers-reduced-motion`; carousel không dùng | — |
| **Giọng văn** | Nhãn và trạng thái ngắn, đọc là hiểu: "Chưa kết nối", "Khoá sau khi publish", "Hiệu lực 24 giờ". Không viết giọng hướng dẫn hay quảng cáo ("Kết nối Stripe để nhận tiền…", "Bạn muốn…?", "Hãy…", "Vui lòng…"). Mô tả dưới tiêu đề trang chỉ dùng khi có thông tin thật (ngày giờ, nguồn dữ liệu) | — |

## 4. Danh sách màn hình

| # | Màn hình | File | Route (FE) | Sprint |
|---|---|---|---|---|
| 1 | Trang chủ | [Main](prototype/Main.dc.html) | `/` | 2 |
| 2 | Danh sách sự kiện (lọc + phân trang) | [EventList](prototype/EventList.dc.html) | `/events` | 2 |
| 3 | Chi tiết sự kiện | [EventDetail](prototype/EventDetail.dc.html) | `/events/:id` | 2 (phòng chờ, realtime: 7) |
| 4 | Hàng chờ trực tuyến | [Queue](prototype/Queue.dc.html) | `/events/:id/queue` | 7 |
| 5 | Chọn ghế | [SeatSelect](prototype/SeatSelect.dc.html) | `/events/:id/seats` | 2 (realtime: 7) |
| 6 | Thanh toán | [Checkout](prototype/Checkout.dc.html) | `/checkout/:bookingId` | 2 (giữ ghế) → 3 (thanh toán) |
| 7 | Thanh toán thành công | [Success](prototype/Success.dc.html) | `/checkout/:bookingId/success` | 3 |
| 8 | Vé của tôi (mobile) | [MyTickets](prototype/MyTickets.dc.html) | `/me/tickets` | 3 |
| 9 | Chi tiết vé QR + hoàn vé | [TicketQR](prototype/TicketQR.dc.html) | `/me/tickets/:id` | 3 (hoàn vé: 4) |
| 10 | Check-in tại cổng | [Checkin](prototype/Checkin.dc.html) | `/checkin/:eventId` | 4 |
| 11 | Organizer — Tổng quan | [OrgDashboard](prototype/OrgDashboard.dc.html) | `/organizer` | 6 |
| 12 | Organizer — Sự kiện | [OrgEvents](prototype/OrgEvents.dc.html) | `/organizer/events` | 2 |
| 13 | Organizer — Sửa sự kiện: hạng vé | [OrgCreateEvent](prototype/OrgCreateEvent.dc.html) | `/organizer/events/:id/tiers` | 2 |
| 14 | Organizer — Đơn hàng | [OrgOrders](prototype/OrgOrders.dc.html) | `/organizer/orders` | 6 |
| 15 | Admin — Hồ sơ organizer | [AdminOrganizers](prototype/AdminOrganizers.dc.html) | `/admin/organizers` | 5 |
| 16 | Admin — Địa điểm (+ import Excel) | [AdminVenues](prototype/AdminVenues.dc.html) | `/admin/venues` | 6 |
| 17 | Admin — Sơ đồ ghế: editor kéo-thả theo khu, sân khấu / lối đi / nhãn, ẩn ghế, xem như khách (+ import Excel) — ADR 0030 | [AdminSeatMap](prototype/AdminSeatMap.dc.html) | `/admin/venues/:id/seat-maps/:mapId` | 6 |
| 18 | Đăng nhập / Đăng ký / Xác minh / Quên & đặt lại mật khẩu (theme Keycloak) | [Auth](prototype/Auth.dc.html) | Keycloak realm `tickethub` | 1 |
| 19 | Hồ sơ cá nhân · Bảo mật · Cài đặt thông báo | [Profile](prototype/Profile.dc.html) | `/me/profile` | 1 (cài đặt thông báo: 4) |
| 20 | Thông báo (+ popover chuông ở header) | [Notifications](prototype/Notifications.dc.html) | `/me/notifications` | 4 (realtime: 7) |
| 21 | Đơn hàng của tôi | [MyBookings](prototype/MyBookings.dc.html) | `/me/bookings` | 3 (hoàn vé: 4) |
| 22 | Đăng ký làm ban tổ chức (form · chờ duyệt · bị từ chối · đã duyệt) | [OrganizerApply](prototype/OrganizerApply.dc.html) | `/organizer/apply` | 5 |
| 23 | Trạng thái lỗi & ngoại lệ (404, 403, hết giờ giữ ghế, thanh toán lỗi…) | [SystemStates](prototype/SystemStates.dc.html) | — (dùng chung) | 2 → 7 (theo từng lỗi) |
| 24 | Organizer — Sửa sự kiện: thông tin · mở bán · phòng chờ | [OrgEventInfo](prototype/OrgEventInfo.dc.html) | `/organizer/events/:id/edit` | 2 (phòng chờ: 7) |
| 25 | Organizer — Bảng điều khiển sự kiện | [OrgEventSales](prototype/OrgEventSales.dc.html) | `/organizer/events/:id/dashboard` | 6 (realtime: 7) |
| 26 | Organizer — Thanh toán & Stripe | [OrgStripe](prototype/OrgStripe.dc.html) | `/organizer/payments` | 6 |
| 27 | Organizer — Cài đặt (hồ sơ, thành viên, thông báo, pháp lý) | [OrgSettings](prototype/OrgSettings.dc.html) | `/organizer/settings` | 6 (thành viên: ngoài phạm vi) |
| 28 | Admin — Người dùng | [AdminUsers](prototype/AdminUsers.dc.html) | `/admin/users` | 5 |
| 29 | Admin — Đơn hàng & hoàn tiền (đối soát) | [AdminOrders](prototype/AdminOrders.dc.html) | `/admin/orders` | 4 (hoàn thủ công) → 6 |
| 30 | Admin — Phí nền tảng | [AdminFees](prototype/AdminFees.dc.html) | `/admin/fees` | 6 |
| 31 | Admin — Audit log | [AdminAuditLogs](prototype/AdminAuditLogs.dc.html) | `/admin/audit-logs` | 6 |
| 32 | Admin — Feature flags | [AdminFlags](prototype/AdminFlags.dc.html) | `/admin/feature-flags` | 8 (mở rộng) |
| 33 | Admin — Đơn vị hành chính (Tỉnh/TP → Xã/Phường, + import Excel) | [AdminRegions](prototype/AdminRegions.dc.html) | `/admin/administrative-units` | 6 |
| 34 | Admin — Sự kiện (tạm dừng, mở bán lại, huỷ) | [AdminEvents](prototype/AdminEvents.dc.html) | `/admin/events` | 6 |
| 35 | Admin — Tổng quan (số liệu, việc cần xử lý, sắp mở bán) | [AdminDashboard](prototype/AdminDashboard.dc.html) | `/admin` | 6 |
| 36 | Admin — Cấu hình thanh toán (chỉ đọc: khoá, webhook, Connect, event lỗi) | [AdminPayments](prototype/AdminPayments.dc.html) | `/admin/payments` | 6 |

> **Hành vi từng nút**: trên canvas, dưới mỗi màn có một ghi chú vàng “HÀNH ĐỘNG — …” liệt kê mọi nút/link, điều hướng đi đâu, mở modal gì, và trạng thái nào bị khoá.
>
> Prototype vẽ theo **bản hoàn chỉnh**. Làm ở sprint sớm hơn thì phần chưa tới sprint để trống hoặc ẩn (vd. Sprint 2 chưa có SSE → sơ đồ ghế và chuông thông báo dùng polling; trước Sprint 6 chưa có Stripe Connect → ẩn mục "Thanh toán & Stripe").

## 5. Modal có trong prototype

Xem trên canvas: mở **Tweaks → dialog** của màn tương ứng, hoặc bấm **Play** rồi thao tác.

| Màn | Modal |
|---|---|
| Organizer — Sự kiện | Tạo sự kiện mới |
| Organizer — Sửa sự kiện | Thêm / sửa hạng vé (chọn màu, khu áp dụng) · Xoá hạng vé · Chưa thể publish |
| Admin — Sự kiện | Chi tiết (side sheet) · Tạm dừng bán (lý do bắt buộc, có bản hàng loạt) · Mở bán lại · Huỷ sự kiện (lý do + "HUY", hoàn 100%) |
| Admin — Hồ sơ organizer | Xem hồ sơ (side sheet) · Duyệt · Từ chối (lý do bắt buộc) · Tạm khoá |
| Admin — Địa điểm | Thêm / sửa địa điểm (Tỉnh/TP → Xã/Phường lấy từ danh mục) · Xoá địa điểm · Import Excel |
| Admin — Sơ đồ ghế | Xoá khu / phần tử (xác nhận) · Import Excel. Thêm, sửa, xoay, nhân bản làm trực tiếp trên sơ đồ + panel thuộc tính, không qua modal |
| Vé QR (mobile) | Bottom sheet hoàn cả đơn (trừ phí nền tảng) · Chia sẻ |
| Chi tiết sự kiện | Chia sẻ (link + Facebook / Zalo / Messenger) · Liên hệ ban tổ chức (chủ đề, nội dung, email nhận phản hồi) |
| Khung Admin / Organizer | Tìm nhanh (Ctrl K) · Popover thông báo · Menu tài khoản · Popover bộ lọc · Popover ẩn/hiện cột |
| Hồ sơ cá nhân | Đổi email · Đổi mật khẩu · Đăng xuất thiết bị khác · Xoá tài khoản (gõ email xác nhận) |
| Đơn hàng của tôi | Chi tiết đơn (vé, thanh toán, lịch sử) · Yêu cầu hoàn vé · Đã quá hạn hoàn vé (< 48h) · Huỷ giữ chỗ |
| Đăng ký ban tổ chức | Xác nhận gửi hồ sơ · Rút hồ sơ |
| Organizer — Sửa sự kiện: thông tin | Publish (xác nhận) · Chưa thể publish → Kết nối Stripe · Đổi sơ đồ ghế (cảnh báo mất gán hạng vé) |
| Organizer — Thanh toán & Stripe | Mở trang Stripe / Tiếp tục hồ sơ trên Stripe · Không thể ngắt kết nối |
| Organizer — Cài đặt | Mời thành viên / Đổi vai trò · Xoá thành viên · Yêu cầu cập nhật pháp lý |
| Admin — Người dùng | Chi tiết người dùng (side sheet) · Đổi vai trò · Khoá tài khoản |
| Admin — Đơn hàng & hoàn tiền | Chi tiết + dòng thời gian Stripe (side sheet) · Hoàn tiền thủ công (tuỳ chọn hoàn cả phí) · Xử lý đối soát |
| Admin — Phí nền tảng | Đổi mức mặc định · Thêm / sửa mức riêng · Xoá mức riêng |
| Admin — Audit log | Chi tiết (so sánh trước / sau, side sheet) |
| Admin — Feature flags | Bật/tắt flag quan trọng (nhập lý do) · Tạo / sửa flag · Lưu trữ flag |
| Admin — Đơn vị hành chính | Thêm / sửa đơn vị (chọn cấp 1/2, thuộc tỉnh/thành) · Ngừng sử dụng · Không xoá được (còn đơn vị con hoặc địa điểm) · Xoá · Import Excel |

Màn **Đăng nhập** (Tweaks → mode), **Chi tiết sự kiện** (Tweaks → sale: sắp mở bán · phòng chờ · đang bán · hết vé · tạm dừng), **Trạng thái lỗi** (Tweaks → state), **Đăng ký ban tổ chức** (Tweaks → status), **Thanh toán & Stripe** (Tweaks → state) có nhiều trạng thái màn hình, chọn trong Tweaks hoặc thao tác khi Play. Mọi màn danh sách Admin có **Tweaks → view** (`data` · `loading` · `empty` · `error`); không có kết quả lọc thì gõ từ khoá không khớp. Mọi màn danh sách Organizer cũng có Tweaks → view. **Bảng điều khiển sự kiện** (Tweaks → paused) hiện banner khi Admin hoặc hệ thống tạm dừng bán.

> Nút và modal chỉ phản hồi khi bấm **Play** trên artboard. Ở chế độ chỉnh sửa canvas, click chỉ chọn phần tử.

### Luồng kết nối Stripe (màn 26)

| Trạng thái | `state` | Hiển thị | Hành động |
|---|---|---|---|
| Chưa kết nối | `none` | Cảnh báo BR-05 · điều khoản (phí 5%, phí thẻ, lịch rút, giấy tờ) | **Kết nối Stripe** → modal "Mở trang Stripe" → **Mở Stripe**: BE gọi `POST /organizer/stripe/onboarding-link` (tạo Express account nếu chưa có) rồi redirect sang stripe.com. Quay về: hoàn tất → `pending`, thoát giữa chừng hoặc link hết hạn → `incomplete` (bấm lại sẽ tạo link mới) |
| Hồ sơ chưa hoàn tất | `incomplete` | Danh sách mục còn thiếu | **Tiếp tục trên Stripe** → modal → trang Stripe |
| Đang xác minh | `pending` | Hồ sơ đã gửi, trạng thái từng mục | **Làm mới trạng thái** (prototype: chuyển sang `active`). Trạng thái thật cập nhật qua webhook `account.updated`: xác minh xong → `active`, yêu cầu bổ sung → `incomplete` |
| Đã kết nối | `active` | `charges_enabled` + `payouts_enabled` · số dư · doanh thu & phí theo sự kiện · lịch sử rút tiền | **Stripe Dashboard** (Express login link, tab mới) · Xuất CSV · Ngắt kết nối (bị chặn khi còn sự kiện đang bán hoặc chưa diễn) |
| Bị hạn chế | `restricted` | Stripe tắt `charges_enabled`, sự kiện đang bán chuyển sang Tạm dừng bán | **Cập nhật trên Stripe** → modal → trang Stripe |

Thanh 4 bước luôn hiện ở đầu màn: Tạo tài khoản Stripe → Hồ sơ trên Stripe → Stripe xác minh → Nhận thanh toán & rút tiền.

## 6. Dữ liệu trong prototype

Tên sự kiện, địa điểm, người dùng, số liệu doanh thu… là **dữ liệu mẫu** để hình dung bố cục — không phải yêu cầu nghiệp vụ. Quy tắc nghiệp vụ thật nằm trong [overview.md §3](../overview.md#3-quy-tắc-nghiệp-vụ).

## 7. File mẫu import

Hàng đầu là tên cột (đúng như bảng dưới); cột "Bắt buộc" kiểm tra ở bước dry-run. File mẫu có sẵn 2 dòng ví dụ và sheet "Hướng dẫn" liệt kê giá trị hợp lệ.

| Màn | File mẫu | Cột | Khoá khi trùng |
|---|---|---|---|
| Đơn vị hành chính | `mau-don-vi-hanh-chinh.xlsx` | `ma`*, `ten`*, `loai`* (Tỉnh · Thành phố · Phường · Xã · Đặc khu), `ma_cha` (bắt buộc với cấp xã), `hieu_luc_tu` | `ma` |
| Địa điểm | `mau-dia-diem.xlsx` | `ma`, `ten`*, `dia_chi`*, `ma_xa`* (mã cấp xã trong danh mục), `suc_chua`* | `ma` |
| Sơ đồ ghế | `mau-so-do-ghe.xlsx` | `ma_khu`*, `ten_khu`*, `hang`*, `ghe_tu`*, `ghe_den`*, `x`, `y` | `ma_khu` + `hang` + số ghế |

Lỗi thường gặp đã vẽ trong prototype: mã cha không tồn tại, loại không hợp lệ, trùng mã trong file, thiếu trường bắt buộc, `ghe_den` < `ghe_tu`, trùng ghế với dòng khác.
