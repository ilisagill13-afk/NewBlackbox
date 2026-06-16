"""
AIS (Appointment Information System) HTTP client.

Uses a requests.Session to maintain cookies/CSRF tokens across calls,
exactly as the real browser does when navigating the AIS website.
"""

from __future__ import annotations

import logging
import re
import time
from datetime import date, datetime
from typing import Optional

import requests
from bs4 import BeautifulSoup

import config

logger = logging.getLogger(__name__)

_DATE_FMT = "%Y-%m-%d"
_TIME_FMT = "%I:%M %p"


class AISClient:
    """Thin wrapper around the AIS REST + HTML endpoints."""

    def __init__(self) -> None:
        self.session = requests.Session()
        self.session.headers.update(
            {
                "User-Agent": config.USER_AGENT,
                "Accept-Language": "en-US,en;q=0.9",
                "Referer": config.BASE_URL,
            }
        )
        self.schedule_id: Optional[str] = None
        self._csrf: Optional[str] = None

    # ──────────────────────────────────────────────
    # Auth
    # ──────────────────────────────────────────────

    def _get_csrf(self, url: str) -> str:
        """Fetch a page and extract the Rails CSRF token."""
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        soup = BeautifulSoup(resp.text, "html.parser")
        tag = soup.find("meta", {"name": "csrf-token"})
        if tag is None:
            raise RuntimeError("CSRF token not found on page.")
        return tag["content"]

    def login(self) -> None:
        """Authenticate with AIS and populate self.schedule_id."""
        logger.info("Logging in as %s …", config.AIS_EMAIL)
        csrf = self._get_csrf(config.SIGN_IN_URL)

        payload = {
            "utf8": "✓",
            "authenticity_token": csrf,
            "user[email]": config.AIS_EMAIL,
            "user[password]": config.AIS_PASSWORD,
            "policy_confirmed": "1",
            "commit": "Sign In",
        }
        resp = self.session.post(
            config.SIGN_IN_URL,
            data=payload,
            headers={"Referer": config.SIGN_IN_URL},
            timeout=30,
            allow_redirects=True,
        )
        resp.raise_for_status()

        if "sign_in" in resp.url:
            raise RuntimeError(
                "Login failed — check AIS_EMAIL / AIS_PASSWORD. "
                f"Landed on: {resp.url}"
            )

        self.schedule_id = self._extract_schedule_id(resp.url, resp.text)
        logger.info("Logged in. Schedule ID: %s", self.schedule_id)

    def _extract_schedule_id(self, url: str, html: str) -> str:
        """Pull the numeric schedule id from the redirect URL or page links."""
        # The URL usually looks like /en-ca/niv/schedule/12345678/continue
        match = re.search(r"/schedule/(\d+)", url)
        if match:
            return match.group(1)

        # Fall back to parsing page links
        soup = BeautifulSoup(html, "html.parser")
        for a in soup.find_all("a", href=True):
            m = re.search(r"/schedule/(\d+)", a["href"])
            if m:
                return m.group(1)

        raise RuntimeError("Could not determine schedule ID after login.")

    # ──────────────────────────────────────────────
    # Slot Queries
    # ──────────────────────────────────────────────

    def get_available_dates(self) -> list[str]:
        """Return a list of available appointment dates (YYYY-MM-DD strings)."""
        url = config.AVAILABLE_DATES_URL.format(schedule_id=self.schedule_id)
        resp = self.session.get(
            url,
            headers={
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": config.APPOINTMENTS_URL.format(
                    schedule_id=self.schedule_id
                ),
            },
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        # Each element is {"date": "2026-07-15", "business_day": true}
        dates = [entry["date"] for entry in data if isinstance(entry, dict)]
        return dates

    def get_available_times(self, appt_date: str) -> list[str]:
        """Return available time slots for a given date."""
        url = config.AVAILABLE_TIMES_URL.format(
            schedule_id=self.schedule_id, date=appt_date
        )
        resp = self.session.get(
            url,
            headers={
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": config.APPOINTMENTS_URL.format(
                    schedule_id=self.schedule_id
                ),
            },
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        # Response: {"available_times": ["08:30", ...], "business_times": [...]}
        return data.get("available_times", [])

    # ──────────────────────────────────────────────
    # Booking
    # ──────────────────────────────────────────────

    def book_appointment(self, appt_date: str, appt_time: str) -> bool:
        """
        Submit the appointment booking form.
        Returns True on success, False on failure.
        """
        appt_url = config.APPOINTMENTS_URL.format(schedule_id=self.schedule_id)

        # Refresh CSRF token from the appointment page
        try:
            csrf = self._get_csrf(appt_url)
        except Exception as exc:
            logger.error("Could not fetch CSRF before booking: %s", exc)
            return False

        payload = {
            "utf8": "✓",
            "authenticity_token": csrf,
            "confirmed_limit_message": "1",
            "use_consulate_appointment_capacity": "true",
            "appointments[consulate_appointment][facility_id]": str(config.FACILITY_ID),
            "appointments[consulate_appointment][date]": appt_date,
            "appointments[consulate_appointment][time]": appt_time,
        }

        resp = self.session.post(
            appt_url,
            data=payload,
            headers={"Referer": appt_url},
            timeout=30,
            allow_redirects=True,
        )

        if resp.status_code == 200 and "Appointment Confirmed" in resp.text:
            logger.info("Appointment booked: %s at %s", appt_date, appt_time)
            return True

        # Check for a success redirect pattern
        if "appointment" in resp.url and "new" not in resp.url:
            logger.info(
                "Booking likely succeeded (redirected to %s).", resp.url
            )
            return True

        logger.warning(
            "Booking response status %s. URL: %s", resp.status_code, resp.url
        )
        return False
