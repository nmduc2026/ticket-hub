// Mọi chuỗi hiển thị nằm ở đây (vi là ngôn ngữ mặc định); component dùng useTranslation(), không viết cứng chuỗi.
export const vi = {
  app: { name: 'Ticket Hub' },
  nav: {
    home: 'Trang chủ',
    organizer: 'Ban tổ chức',
    admin: 'Quản trị',
  },
  auth: {
    login: 'Đăng nhập',
    logout: 'Đăng xuất',
    loggingOut: 'Đang đăng xuất…',
  },
  home: {
    title: 'Ticket Hub',
    greeting: 'Xin chào, {{name}}',
    guest: 'Bạn chưa đăng nhập',
  },
  admin: { title: 'Tổng quan quản trị' },
  organizer: { title: 'Tổng quan ban tổ chức' },
  forbidden: {
    title: 'Không có quyền truy cập',
    description: 'Tài khoản của bạn không được xem trang này.',
    back: 'Về trang chủ',
  },
  common: { loading: 'Đang tải…' },
}
