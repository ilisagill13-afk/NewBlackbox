import json
import aiosqlite
from datetime import datetime
from pathlib import Path
from cryptography.fernet import Fernet
from loguru import logger

DB_PATH = Path(__file__).parent.parent / "data" / "visa_assistant.db"
SCHEMA_PATH = Path(__file__).parent / "schema.sql"


class StateManager:
    def __init__(self, encryption_key: bytes = None):
        DB_PATH.parent.mkdir(parents=True, exist_ok=True)
        if encryption_key:
            self._fernet = Fernet(encryption_key)
        else:
            key = Fernet.generate_key()
            key_file = DB_PATH.parent / ".fernet.key"
            if key_file.exists():
                key = key_file.read_bytes()
            else:
                key_file.write_bytes(key)
            self._fernet = Fernet(key)

    async def init(self):
        async with aiosqlite.connect(DB_PATH) as db:
            schema = SCHEMA_PATH.read_text()
            await db.executescript(schema)
            await db.commit()
        logger.debug("Database initialized at {}", DB_PATH)

    async def save_session(self, schedule_id: str, facility_id: int, cookies: dict, csrf_token: str = None):
        cookies_json = json.dumps(cookies).encode()
        encrypted = self._fernet.encrypt(cookies_json)
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute("UPDATE sessions SET is_active = 0")
            await db.execute(
                "INSERT INTO sessions (schedule_id, facility_id, cookies_encrypted, csrf_token, last_verified_at) VALUES (?, ?, ?, ?, ?)",
                (schedule_id, facility_id, encrypted, csrf_token, datetime.utcnow().isoformat()),
            )
            await db.commit()
        logger.info("Session saved for schedule_id={}", schedule_id)

    async def load_session(self) -> tuple[str, int, dict, str] | None:
        async with aiosqlite.connect(DB_PATH) as db:
            async with db.execute(
                "SELECT schedule_id, facility_id, cookies_encrypted, csrf_token FROM sessions WHERE is_active = 1 ORDER BY id DESC LIMIT 1"
            ) as cur:
                row = await cur.fetchone()
        if not row:
            return None
        schedule_id, facility_id, encrypted, csrf_token = row
        cookies_json = self._fernet.decrypt(encrypted)
        cookies = json.loads(cookies_json)
        return schedule_id, facility_id, cookies, csrf_token

    async def update_session_verified(self):
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute(
                "UPDATE sessions SET last_verified_at = ? WHERE is_active = 1",
                (datetime.utcnow().isoformat(),),
            )
            await db.commit()

    async def update_csrf(self, csrf_token: str):
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute(
                "UPDATE sessions SET csrf_token = ? WHERE is_active = 1",
                (csrf_token,),
            )
            await db.commit()

    async def invalidate_session(self):
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute("UPDATE sessions SET is_active = 0")
            await db.commit()
        logger.warning("Session invalidated")

    async def log_slot_check(self, facility_id: int, dates_found: list, http_status: int, response_time_ms: int):
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute(
                "INSERT INTO slot_checks (facility_id, dates_found, http_status, response_time_ms) VALUES (?, ?, ?, ?)",
                (facility_id, json.dumps(dates_found), http_status, response_time_ms),
            )
            await db.commit()

    async def save_booking(self, facility_id: int, date: str, time: str, html: str = None, status: str = "confirmed"):
        async with aiosqlite.connect(DB_PATH) as db:
            await db.execute(
                "INSERT INTO bookings (facility_id, appointment_date, appointment_time, confirmation_html, status) VALUES (?, ?, ?, ?, ?)",
                (facility_id, date, time, html, status),
            )
            await db.commit()
        logger.info("Booking saved: {} {} facility={}", date, time, facility_id)

    async def get_last_booking(self) -> dict | None:
        async with aiosqlite.connect(DB_PATH) as db:
            async with db.execute(
                "SELECT facility_id, appointment_date, appointment_time, status, booked_at FROM bookings ORDER BY id DESC LIMIT 1"
            ) as cur:
                row = await cur.fetchone()
        if not row:
            return None
        return {
            "facility_id": row[0],
            "date": row[1],
            "time": row[2],
            "status": row[3],
            "booked_at": row[4],
        }

    async def get_last_check_time(self) -> str | None:
        async with aiosqlite.connect(DB_PATH) as db:
            async with db.execute(
                "SELECT checked_at FROM slot_checks ORDER BY id DESC LIMIT 1"
            ) as cur:
                row = await cur.fetchone()
        return row[0] if row else None
