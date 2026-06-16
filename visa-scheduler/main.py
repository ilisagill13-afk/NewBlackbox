#!/usr/bin/env python3
"""
US Visa Slot Scheduler — Canada
Entry point. Configure via config.py or environment variables, then run:

    python main.py

Or with environment variables:
    AIS_EMAIL=you@example.com AIS_PASSWORD=secret CONSULATE=toronto \\
    EARLIEST_DATE=2026-07-01 CURRENT_APPOINTMENT_DATE=2026-12-01 \\
    NOTIFY_METHOD=telegram TELEGRAM_BOT_TOKEN=<tok> TELEGRAM_CHAT_ID=<id> \\
    python main.py
"""

import logging
import sys

from scheduler import VisaScheduler


def setup_logging() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s  %(levelname)-8s  %(name)s — %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        handlers=[
            logging.StreamHandler(sys.stdout),
            logging.FileHandler("visa_scheduler.log", encoding="utf-8"),
        ],
    )


def main() -> None:
    setup_logging()
    logger = logging.getLogger(__name__)
    logger.info("US Visa Slot Scheduler starting …")

    try:
        VisaScheduler().run()
    except KeyboardInterrupt:
        logger.info("Scheduler stopped by user (Ctrl+C).")
    except Exception as exc:  # noqa: BLE001
        logger.critical("Unhandled exception: %s", exc, exc_info=True)
        sys.exit(1)


if __name__ == "__main__":
    main()
