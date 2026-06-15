#!/usr/bin/env python3
"""
US Visa AIS Slot Booking Assistant
Usage:
  python main.py               # Full run: login + monitor + notify
  python main.py --login       # Only login (open browser window)
  python main.py --check-once  # Single slot check
  python main.py --status      # Show current status
  python main.py --setup       # Interactive first-time setup
"""

import asyncio
import argparse
import os
import sys
from pathlib import Path

import yaml
from dotenv import load_dotenv
from rich.console import Console
from rich.table import Table

# Add project root to path
sys.path.insert(0, str(Path(__file__).parent))

from utils.logger import setup_logger
from db.state import StateManager
from core.auth import AISAuthenticator
from api.ais_client import AISClient
from core.session_keeper import SessionKeeper
from core.slot_monitor import SlotMonitor
from core.auto_booker import AutoBooker
from notifications.telegram_bot import TelegramNotifier
from api.endpoints import FACILITIES, FACILITY_NAMES

console = Console()
load_dotenv()


def load_config() -> dict:
    config_path = Path(__file__).parent / "config.yaml"
    with open(config_path) as f:
        cfg = yaml.safe_load(f)
    cfg["notifications"]["telegram"]["bot_token"] = (
        cfg["notifications"]["telegram"].get("bot_token") or os.getenv("TELEGRAM_BOT_TOKEN", "")
    )
    cfg["notifications"]["telegram"]["chat_id"] = (
        cfg["notifications"]["telegram"].get("chat_id") or os.getenv("TELEGRAM_CHAT_ID", "")
    )
    fid = cfg["ais"]["facility_id"]
    cfg["_facility_name"] = FACILITY_NAMES.get(fid, str(fid))
    return cfg


async def run_login(config: dict, state: StateManager):
    email = os.getenv("AIS_EMAIL") or input("AIS Email: ").strip()
    password = os.getenv("AIS_PASSWORD") or input("AIS Password: ").strip()
    auth = AISAuthenticator(config, state)
    cookies, schedule_id = await auth.login(email, password)
    console.print(f"[green]Login OK! schedule_id={schedule_id}[/green]")
    return cookies, schedule_id


async def run_check_once(config: dict, state: StateManager):
    session = await state.load_session()
    if not session:
        console.print("[red]No active session. Run --login first.[/red]")
        return

    schedule_id, facility_id, cookies, _ = session
    config["ais"]["schedule_id"] = schedule_id
    config["ais"]["facility_id"] = facility_id

    async with AISClient(cookies, schedule_id, facility_id) as client:
        monitor = SlotMonitor(client, state, config)
        results = await monitor.check_once()

    if results:
        console.print(f"[green]Slots found: {[r['date'] for r in results]}[/green]")
    else:
        console.print("[yellow]No slots available right now.[/yellow]")


async def run_status(state: StateManager):
    session = await state.load_session()
    last_check = await state.get_last_check_time()
    last_booking = await state.get_last_booking()

    table = Table(title="Visa Assistant Status")
    table.add_column("Item", style="cyan")
    table.add_column("Value", style="white")

    table.add_row("Session", "Active ✓" if session else "None ✗")
    if session:
        schedule_id, facility_id, _, _ = session
        table.add_row("Schedule ID", schedule_id)
        table.add_row("Facility", FACILITY_NAMES.get(facility_id, str(facility_id)))
    table.add_row("Last Slot Check", last_check or "Never")
    if last_booking:
        table.add_row("Last Booking", f"{last_booking['date']} {last_booking['time']} ({last_booking['status']})")

    console.print(table)


async def run_setup():
    console.print("[bold cyan]US Visa AIS Slot Booking Assistant — Setup[/bold cyan]\n")
    config_path = Path(__file__).parent / "config.yaml"
    env_path = Path(__file__).parent / ".env"

    with open(config_path) as f:
        cfg = yaml.safe_load(f)

    console.print("Available facilities:")
    for name, fid in FACILITIES.items():
        console.print(f"  {fid}: {name}")

    fid = input(f"\nFacility ID [{cfg['ais']['facility_id']}]: ").strip()
    if fid.isdigit():
        cfg["ais"]["facility_id"] = int(fid)

    earliest = input(f"Earliest date YYYY-MM-DD [{cfg['preferences']['earliest_date']}]: ").strip()
    if earliest:
        cfg["preferences"]["earliest_date"] = earliest

    latest = input(f"Latest date YYYY-MM-DD [{cfg['preferences']['latest_date']}]: ").strip()
    if latest:
        cfg["preferences"]["latest_date"] = latest

    auto_book = input(f"Auto-book when slot found? (y/n) [{'y' if cfg['preferences']['auto_book'] else 'n'}]: ").strip()
    if auto_book.lower() == "y":
        cfg["preferences"]["auto_book"] = True
    elif auto_book.lower() == "n":
        cfg["preferences"]["auto_book"] = False

    with open(config_path, "w") as f:
        yaml.dump(cfg, f, default_flow_style=False)
    console.print("[green]config.yaml updated.[/green]")

    email = input("\nAIS Email: ").strip()
    password = input("AIS Password: ").strip()
    bot_token = input("Telegram Bot Token (leave blank to skip): ").strip()
    chat_id = input("Telegram Chat ID (leave blank to skip): ").strip()

    env_content = f"AIS_EMAIL={email}\nAIS_PASSWORD={password}\n"
    if bot_token:
        env_content += f"TELEGRAM_BOT_TOKEN={bot_token}\n"
    if chat_id:
        env_content += f"TELEGRAM_CHAT_ID={chat_id}\n"

    env_path.write_text(env_content)
    console.print("[green].env file created.[/green]")
    console.print("\n[bold]Setup complete! Run: python main.py --login[/bold]")


async def run_main(config: dict, state: StateManager):
    session = await state.load_session()
    if not session:
        console.print("[yellow]No session found. Starting login...[/yellow]")
        await run_login(config, state)
        session = await state.load_session()
        if not session:
            console.print("[red]Login failed.[/red]")
            return

    schedule_id, facility_id, cookies, _ = session
    config["ais"]["schedule_id"] = schedule_id
    config["ais"]["facility_id"] = facility_id

    async with AISClient(cookies, schedule_id, facility_id) as client:
        keeper = SessionKeeper(client, state, config["preferences"]["heartbeat_interval_seconds"])
        monitor = SlotMonitor(client, state, config)
        booker = AutoBooker(client, state, config)
        telegram = TelegramNotifier(config, monitor=monitor, booker=booker)

        async def on_session_expired():
            console.print("[red]Session expired! Attempting re-login...[/red]")
            await telegram.send_session_expired()
            try:
                new_cookies, new_sid = await run_login(config, state)
                console.print("[green]Re-login successful![/green]")
            except Exception as e:
                console.print(f"[red]Re-login failed: {e}[/red]")

        async def on_slot_found(date: str, times: list[str]):
            console.print(f"\n[bold green]SLOT FOUND: {date} — Times: {', '.join(times)}[/bold green]")
            auto_book = config["preferences"]["auto_book"]
            await telegram.send_slot_found(date, times, auto_book)
            if auto_book and times:
                await asyncio.sleep(3)
                success = await booker.book(date, times[0])
                if success:
                    console.print(f"[bold green]BOOKED: {date} at {times[0]}[/bold green]")
                    await telegram.send_booking_confirmed(date, times[0])
                    monitor.pause()
                else:
                    console.print("[red]Booking failed — continuing monitor[/red]")
                    await telegram.send_booking_failed(date, times[0])

        keeper.set_expired_callback(on_session_expired)
        monitor.set_slot_found_callback(on_slot_found)

        facility_name = FACILITY_NAMES.get(facility_id, str(facility_id))
        console.print(f"\n[bold cyan]Monitoring {facility_name} — {config['preferences']['earliest_date']} to {config['preferences']['latest_date']}[/bold cyan]")
        console.print(f"Poll every {config['preferences']['poll_interval_seconds']}s | Heartbeat every {config['preferences']['heartbeat_interval_seconds']}s")
        console.print("Press Ctrl+C to stop.\n")

        await telegram.start()
        await keeper.start()
        await monitor.start()

        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            pass
        except KeyboardInterrupt:
            pass
        finally:
            await monitor.stop()
            await keeper.stop()
            await telegram.stop()
            console.print("\n[yellow]Monitoring stopped.[/yellow]")


async def async_main():
    parser = argparse.ArgumentParser(description="US Visa AIS Slot Booking Assistant")
    parser.add_argument("--login", action="store_true", help="Open browser and login")
    parser.add_argument("--check-once", action="store_true", help="Check slots once and exit")
    parser.add_argument("--status", action="store_true", help="Show current status")
    parser.add_argument("--setup", action="store_true", help="Interactive first-time setup")
    args = parser.parse_args()

    if args.setup:
        await run_setup()
        return

    config = load_config()
    setup_logger(config["logging"]["level"], config["logging"]["file"])
    state = StateManager()
    await state.init()

    if args.login:
        await run_login(config, state)
    elif args.check_once:
        await run_check_once(config, state)
    elif args.status:
        await run_status(state)
    else:
        await run_main(config, state)


if __name__ == "__main__":
    asyncio.run(async_main())
