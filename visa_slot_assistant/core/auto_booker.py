import re
import asyncio
from loguru import logger
from api.ais_client import AISClient
from api.endpoints import appointment_url, times_url
from db.state import StateManager


class AutoBooker:
    def __init__(self, client: AISClient, state: StateManager, config: dict):
        self._client = client
        self._state = state
        self._config = config

    async def book(self, date: str, time: str) -> bool:
        schedule_id = self._config["ais"]["schedule_id"]
        facility_id = self._config["ais"]["facility_id"]

        if not await self._verify_still_available(schedule_id, facility_id, date, time):
            logger.warning("Slot {} {} no longer available — skipping", date, time)
            return False

        appt_url = appointment_url(schedule_id)
        html, status = await self._client.get_html(appt_url)
        if status != 200:
            logger.error("Failed to load appointment page (status={})", status)
            return False

        csrf = self._client.get_csrf_token()
        if not csrf:
            csrf = self._extract_form_token(html)
        if not csrf:
            logger.error("Could not extract CSRF token — cannot book")
            return False

        form_data = {
            "utf8": "✓",
            "_method": "put",
            "authenticity_token": csrf,
            "appointments[consulate_id]": str(facility_id),
            "appointments[facility_id]": str(facility_id),
            "appointments[date]": date,
            "appointments[time]": time,
            "appointments[confirmed_limit_message]": "1",
            "appointments[payment_confirmation]": "1",
            "commit": "Schedule Appointment",
        }

        logger.info("Submitting booking for {} at {}...", date, time)
        body, post_status = await self._client.post_form(appt_url, form_data)

        if post_status in (200, 302) and self._is_success(body):
            await self._state.save_booking(facility_id, date, time, body, status="confirmed")
            logger.success("Appointment BOOKED: {} at {} (facility={})", date, time, facility_id)
            return True
        else:
            logger.error("Booking failed (status={}) — response snippet: {}", post_status, body[:300])
            await self._state.save_booking(facility_id, date, time, body, status="failed")
            return False

    async def _verify_still_available(self, schedule_id: str, facility_id: int, date: str, time: str) -> bool:
        url = times_url(schedule_id, facility_id)
        params = {"date": date, "appointments[expedite]": "false"}
        data, status, _ = await self._client.get_json(url, params)
        if status == 200 and isinstance(data, dict):
            available = data.get("available_times", [])
            return time in available
        return False

    @staticmethod
    def _extract_form_token(html: str) -> str | None:
        match = re.search(r'name="authenticity_token"\s+value="([^"]+)"', html)
        return match.group(1) if match else None

    @staticmethod
    def _is_success(body: str) -> bool:
        success_markers = [
            "Appointment Confirmed",
            "appointment has been scheduled",
            "continue_actions",
            "Your appointment",
        ]
        error_markers = ["error", "Error", "failed", "could not", "not available"]
        body_lower = body.lower()
        for marker in success_markers:
            if marker.lower() in body_lower:
                return True
        return False
