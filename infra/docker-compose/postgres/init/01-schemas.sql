-- Chạy một lần khi volume Postgres còn trống. Kết nối vào database POSTGRES_DB.
-- Mỗi module nghiệp vụ một schema (ADR 0015); `queue` thêm ở Sprint 7.
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS booking;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS ticket;
CREATE SCHEMA IF NOT EXISTS notification;
CREATE SCHEMA IF NOT EXISTS shared;

-- Keycloak dùng database riêng, tách khỏi dữ liệu nghiệp vụ.
CREATE DATABASE keycloak;
