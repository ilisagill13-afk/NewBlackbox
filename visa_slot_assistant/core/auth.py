from loguru import logger
from api.endpoints import LOGIN_URL
from db.state import StateManager
from utils.browser_profile import BrowserProfileManager


class AISAuthenticator:
    def __init__(self, config: dict, state: StateManager):
        self._config = config
        self._state = state

    async def login(self, email: str, password: str) -> tuple[dict, str]:
        """
        Opens headed browser for login. User solves CAPTCHA manually if needed.
        Saves session to DB and returns (cookies, schedule_id).
        """
        async with BrowserProfileManager() as browser:
            cookies, schedule_id = await browser.login_and_export(LOGIN_URL, email, password)

        facility_id = self._config["ais"]["facility_id"]
        await self._state.save_session(schedule_id, facility_id, cookies)

        if not self._config["ais"].get("schedule_id"):
            self._config["ais"]["schedule_id"] = schedule_id
            self._persist_schedule_id(schedule_id)

        return cookies, schedule_id

    def _persist_schedule_id(self, schedule_id: str):
        import yaml
        from pathlib import Path
        config_path = Path(__file__).parent.parent / "config.yaml"
        data = yaml.safe_load(config_path.read_text())
        data["ais"]["schedule_id"] = schedule_id
        config_path.write_text(yaml.dump(data, default_flow_style=False))
        logger.info("schedule_id={} saved to config.yaml", schedule_id)

    async def ensure_session(self) -> tuple[dict, str] | None:
        """Load existing session from DB if valid, else return None (caller must re-login)."""
        session = await self._state.load_session()
        if session:
            schedule_id, _, cookies, _ = session
            logger.info("Loaded existing session for schedule_id={}", schedule_id)
            return cookies, schedule_id
        return None
