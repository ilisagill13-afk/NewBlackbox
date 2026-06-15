import asyncio
from loguru import logger
from api.ais_client import AISClient
from db.state import StateManager


class SessionKeeper:
    def __init__(self, client: AISClient, state: StateManager, interval_seconds: int = 120):
        self._client = client
        self._state = state
        self._interval = interval_seconds
        self._running = False
        self._task: asyncio.Task | None = None
        self.session_valid = True
        self._on_expired_callback = None

    def set_expired_callback(self, cb):
        self._on_expired_callback = cb

    async def start(self):
        self._running = True
        self._task = asyncio.create_task(self._loop())
        logger.info("Session keeper started (heartbeat every {}s)", self._interval)

    async def stop(self):
        self._running = False
        if self._task:
            self._task.cancel()

    async def _loop(self):
        while self._running:
            await asyncio.sleep(self._interval)
            await self._heartbeat()

    async def _heartbeat(self):
        result = await self._client.ping_session()
        if result is None:
            logger.warning("Heartbeat FAILED — session expired")
            self.session_valid = False
            await self._state.invalidate_session()
            if self._on_expired_callback:
                await self._on_expired_callback()
        else:
            self.session_valid = True
            await self._state.update_session_verified()
            logger.debug("Heartbeat OK | response={}", result)
