import asyncio
import random
from datetime import datetime
from loguru import logger
from api.ais_client import AISClient
from api.endpoints import days_url, times_url
from db.state import StateManager


class SlotMonitor:
    def __init__(self, client: AISClient, state: StateManager, config: dict):
        self._client = client
        self._state = state
        self._config = config
        self._running = False
        self._task: asyncio.Task | None = None
        self._on_slot_found = None
        self._paused = False

    def set_slot_found_callback(self, cb):
        self._on_slot_found = cb

    def pause(self):
        self._paused = True
        logger.info("Slot monitor paused")

    def resume(self):
        self._paused = False
        logger.info("Slot monitor resumed")

    async def start(self):
        self._running = True
        self._task = asyncio.create_task(self._loop())
        facility_name = self._config.get("_facility_name", self._config["ais"]["facility_id"])
        logger.info("Slot monitor started for facility={}", facility_name)

    async def stop(self):
        self._running = False
        if self._task:
            self._task.cancel()

    async def check_once(self) -> list[dict]:
        return await self._check_dates()

    async def _loop(self):
        interval = self._config["preferences"]["poll_interval_seconds"]
        while self._running:
            jitter = random.uniform(-5, 5)
            await asyncio.sleep(max(15, interval + jitter))
            if not self._paused:
                await self._check_dates()

    async def _check_dates(self) -> list[dict]:
        schedule_id = self._config["ais"]["schedule_id"]
        facility_id = self._config["ais"]["facility_id"]
        url = days_url(schedule_id, facility_id)
        params = {"appointments[expedite]": "false"}

        data, status, elapsed_ms = await self._client.get_json(url, params)

        if status in (301, 302, 401):
            logger.warning("Session expired during slot check")
            return []

        if status == 429:
            logger.warning("Rate limited by AIS — backing off 60s")
            await asyncio.sleep(60)
            return []

        dates_found = []
        if isinstance(data, list) and data:
            earliest = self._config["preferences"]["earliest_date"]
            latest = self._config["preferences"]["latest_date"]
            for entry in data:
                date_str = entry.get("date", "")
                if earliest <= date_str <= latest:
                    dates_found.append(entry)

        await self._state.log_slot_check(facility_id, [d["date"] for d in dates_found], status, elapsed_ms)

        if dates_found:
            logger.success("SLOTS FOUND: {}", [d["date"] for d in dates_found])
            for entry in dates_found:
                times = await self._get_times(schedule_id, facility_id, entry["date"])
                if times and self._on_slot_found:
                    await self._on_slot_found(entry["date"], times)
                    return dates_found
        else:
            logger.debug("No slots available ({} ms)", elapsed_ms)

        return dates_found

    async def _get_times(self, schedule_id: str, facility_id: int, date: str) -> list[str]:
        url = times_url(schedule_id, facility_id)
        params = {"date": date, "appointments[expedite]": "false"}
        data, status, _ = await self._client.get_json(url, params)
        if status == 200 and isinstance(data, dict):
            return data.get("available_times", [])
        return []
