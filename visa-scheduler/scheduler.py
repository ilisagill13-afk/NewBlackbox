"""
Core scheduling loop.

Polls the AIS system for available visa appointment slots and books
the earliest one that falls within the user's acceptable date range.
"""

from __future__ import annotations

import logging
import time
from datetime import date, datetime
from typing import Optional

import config
import notifier
from ais_client import AISClient

logger = logging.getLogger(__name__)

_DATE_FMT = "%Y-%m-%d"


def _parse_date(s: str) -> date:
    return datetime.strptime(s, _DATE_FMT).date()


class VisaScheduler:
    def __init__(self) -> None:
        self.client = AISClient()
        self.current_date = _parse_date(config.CURRENT_APPOINTMENT_DATE)
        self.earliest_date = _parse_date(config.EARLIEST_DATE)
        self._consecutive_errors = 0

    # ──────────────────────────────────────────────
    # Main entry point
    # ──────────────────────────────────────────────

    def run(self) -> None:
        """Start the polling loop. Runs until a slot is booked or max retries hit."""
        logger.info(
            "Starting US Visa Slot Scheduler for Canada — consulate: %s (ID %s)",
            config.CONSULATE,
            config.FACILITY_ID,
        )
        logger.info(
            "Target: slot earlier than %s, no sooner than %s",
            self.current_date,
            self.earliest_date,
        )

        self._login_with_retry()

        while True:
            try:
                booked = self._check_and_book()
                self._consecutive_errors = 0
                if booked:
                    break
            except Exception as exc:  # noqa: BLE001
                self._consecutive_errors += 1
                logger.error(
                    "Error during poll (attempt %d/%d): %s",
                    self._consecutive_errors,
                    config.MAX_RETRIES,
                    exc,
                )
                if self._consecutive_errors >= config.MAX_RETRIES:
                    msg = (
                        f"Scheduler gave up after {config.MAX_RETRIES} consecutive errors. "
                        f"Last error: {exc}"
                    )
                    logger.critical(msg)
                    notifier.notify("Visa Scheduler — STOPPED", msg)
                    break

                # Re-login on auth errors
                if "401" in str(exc) or "403" in str(exc) or "sign_in" in str(exc).lower():
                    logger.info("Auth error detected — re-logging in …")
                    self._login_with_retry()

            sleep_secs = config.POLL_INTERVAL_SECONDS
            logger.info("Sleeping %ds before next poll …", sleep_secs)
            time.sleep(sleep_secs)

    # ──────────────────────────────────────────────
    # Internal helpers
    # ──────────────────────────────────────────────

    def _login_with_retry(self, max_attempts: int = 3) -> None:
        for attempt in range(1, max_attempts + 1):
            try:
                self.client.login()
                return
            except Exception as exc:  # noqa: BLE001
                logger.error("Login attempt %d failed: %s", attempt, exc)
                if attempt < max_attempts:
                    time.sleep(5 * attempt)
        raise RuntimeError("Could not log in after multiple attempts.")

    def _check_and_book(self) -> bool:
        """
        Fetch available dates, filter, and book the best one.
        Returns True if a slot was booked.
        """
        logger.info("Checking available dates …")
        dates = self.client.get_available_dates()

        if not dates:
            logger.info("No slots available right now.")
            return False

        logger.info("Available dates found: %s", dates[:10])

        best = self._pick_best_date(dates)
        if best is None:
            logger.info(
                "No dates meet the criteria (earlier than %s, at or after %s).",
                self.current_date,
                self.earliest_date,
            )
            return False

        logger.info("Best available date: %s — fetching time slots …", best)
        times = self.client.get_available_times(best)

        if not times:
            logger.info("No time slots available for %s.", best)
            return False

        chosen_time = times[0]
        logger.info("Attempting to book %s at %s …", best, chosen_time)

        notifier.notify(
            "Visa Slot Found!",
            f"Attempting to book {best} at {chosen_time} at {config.CONSULATE.title()}.",
        )

        success = self.client.book_appointment(best, chosen_time)

        if success:
            msg = (
                f"Successfully booked US visa appointment!\n"
                f"Date: {best}\n"
                f"Time: {chosen_time}\n"
                f"Consulate: {config.CONSULATE.title()}, Canada"
            )
            logger.info(msg)
            notifier.notify("Visa Appointment Booked!", msg)
            return True

        logger.warning("Booking failed for %s %s — will retry next poll.", best, chosen_time)
        notifier.notify(
            "Booking Attempt Failed",
            f"Could not book {best} at {chosen_time}. Will try again shortly.",
        )
        return False

    def _pick_best_date(self, available: list[str]) -> Optional[str]:
        """Return the earliest date that is better than the current appointment."""
        candidates = []
        for d_str in available:
            try:
                d = _parse_date(d_str)
            except ValueError:
                continue
            if self.earliest_date <= d < self.current_date:
                candidates.append(d)
        if not candidates:
            return None
        return min(candidates).strftime(_DATE_FMT)
