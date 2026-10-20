# syntax=docker/dockerfile:1
# Build tĩnh bằng Node rồi phục vụ bằng nginx non-root. CSP đầy đủ cho SPA đặt ở nginx.conf (overview.md §13.1).

FROM node:22.21.1-alpine AS build
WORKDIR /app
# npm 10 lỗi khi phân giải peer dependency của Vite 8; dùng npm 11 giống máy dev.
RUN npm install -g npm@11.6.2
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.7-alpine3.24
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
# Image gốc đã chạy bằng user nginx (uid 101) và nghe cổng 8080.
EXPOSE 8080
