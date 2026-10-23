ALTER TABLE booking.bookings ADD COLUMN needs_reconcile BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_bookings_needs_reconcile ON booking.bookings (needs_reconcile) WHERE needs_reconcile;
