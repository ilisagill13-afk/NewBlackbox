CREATE TABLE IF NOT EXISTS sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id TEXT NOT NULL,
    facility_id INTEGER NOT NULL,
    cookies_encrypted BLOB NOT NULL,
    csrf_token TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_verified_at TIMESTAMP,
    is_active INTEGER DEFAULT 1
);

CREATE TABLE IF NOT EXISTS slot_checks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    checked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    facility_id INTEGER,
    dates_found TEXT,
    http_status INTEGER,
    response_time_ms INTEGER
);

CREATE TABLE IF NOT EXISTS bookings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    booked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    facility_id INTEGER,
    appointment_date TEXT,
    appointment_time TEXT,
    confirmation_html TEXT,
    status TEXT DEFAULT 'pending'
);

CREATE TABLE IF NOT EXISTS config_overrides (
    key TEXT PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
