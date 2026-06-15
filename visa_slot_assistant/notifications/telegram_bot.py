import asyncio
from loguru import logger

try:
    from telegram import Bot, Update, InlineKeyboardButton, InlineKeyboardMarkup
    from telegram.ext import Application, CommandHandler, CallbackQueryHandler, ContextTypes
    TELEGRAM_AVAILABLE = True
except ImportError:
    TELEGRAM_AVAILABLE = False


class TelegramNotifier:
    def __init__(self, config: dict, monitor=None, booker=None):
        self._cfg = config["notifications"]["telegram"]
        self._monitor = monitor
        self._booker = booker
        self._app = None
        self._bot = None

    async def start(self):
        if not TELEGRAM_AVAILABLE:
            logger.warning("python-telegram-bot not installed — Telegram disabled")
            return
        if not self._cfg.get("bot_token") or not self._cfg.get("chat_id"):
            logger.warning("Telegram bot_token or chat_id missing in config — Telegram disabled")
            return

        self._app = Application.builder().token(self._cfg["bot_token"]).build()
        self._bot = self._app.bot

        self._app.add_handler(CommandHandler("status", self._cmd_status))
        self._app.add_handler(CommandHandler("pause", self._cmd_pause))
        self._app.add_handler(CommandHandler("resume", self._cmd_resume))
        self._app.add_handler(CallbackQueryHandler(self._handle_booking_callback))

        await self._app.initialize()
        await self._app.start()
        await self._app.updater.start_polling(drop_pending_updates=True)
        logger.info("Telegram bot started")

    async def stop(self):
        if self._app:
            await self._app.updater.stop()
            await self._app.stop()
            await self._app.shutdown()

    async def send_slot_found(self, date: str, times: list[str], auto_book: bool):
        if not self._bot:
            return
        times_str = ", ".join(times)
        msg = (
            f"🟢 *SLOT MILA!*\n\n"
            f"📅 Date: `{date}`\n"
            f"🕐 Times: `{times_str}`\n\n"
            f"{'✅ Auto-booking...' if auto_book else '⚠️ Manual booking required!'}"
        )
        keyboard = None
        if not auto_book:
            buttons = [
                [InlineKeyboardButton(f"Book {t}", callback_data=f"book|{date}|{t}")]
                for t in times[:5]
            ]
            keyboard = InlineKeyboardMarkup(buttons)

        await self._bot.send_message(
            chat_id=self._cfg["chat_id"],
            text=msg,
            parse_mode="Markdown",
            reply_markup=keyboard,
        )
        logger.info("Telegram: slot found alert sent")

    async def send_booking_confirmed(self, date: str, time: str):
        if not self._bot:
            return
        msg = f"✅ *Appointment Booked!*\n\n📅 Date: `{date}`\n🕐 Time: `{time}`"
        await self._bot.send_message(
            chat_id=self._cfg["chat_id"],
            text=msg,
            parse_mode="Markdown",
        )

    async def send_booking_failed(self, date: str, time: str):
        if not self._bot:
            return
        msg = f"❌ *Booking Failed*\n\nSlot `{date} {time}` could not be booked. Monitoring continues..."
        await self._bot.send_message(
            chat_id=self._cfg["chat_id"],
            text=msg,
            parse_mode="Markdown",
        )

    async def send_session_expired(self):
        if not self._bot:
            return
        await self._bot.send_message(
            chat_id=self._cfg["chat_id"],
            text="⚠️ *Session Expired* — Please restart the assistant and log in again.",
            parse_mode="Markdown",
        )

    async def _cmd_status(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        msg = "📊 *Status*\n\nMonitoring: " + ("Running ✅" if self._monitor and not self._monitor._paused else "Paused ⏸")
        await update.message.reply_text(msg, parse_mode="Markdown")

    async def _cmd_pause(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        if self._monitor:
            self._monitor.pause()
        await update.message.reply_text("⏸ Monitoring paused.")

    async def _cmd_resume(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        if self._monitor:
            self._monitor.resume()
        await update.message.reply_text("▶️ Monitoring resumed.")

    async def _handle_booking_callback(self, update: Update, context: ContextTypes.DEFAULT_TYPE):
        query = update.callback_query
        await query.answer()
        parts = query.data.split("|")
        if parts[0] == "book" and self._booker:
            date, time = parts[1], parts[2]
            await query.edit_message_text(f"⏳ Booking {date} at {time}...")
            success = await self._booker.book(date, time)
            if success:
                await query.edit_message_text(f"✅ Booked! {date} at {time}")
            else:
                await query.edit_message_text(f"❌ Booking failed for {date} at {time}")
