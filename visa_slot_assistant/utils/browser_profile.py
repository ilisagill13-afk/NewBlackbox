import re
from pathlib import Path
from playwright.async_api import async_playwright, Browser, BrowserContext, Page
from loguru import logger

PROFILE_DIR = Path(__file__).parent.parent / "data" / "browser_profile"


class BrowserProfileManager:
    def __init__(self):
        PROFILE_DIR.mkdir(parents=True, exist_ok=True)
        self._playwright = None
        self._context: BrowserContext | None = None
        self._page: Page | None = None

    async def __aenter__(self):
        self._playwright = await async_playwright().start()
        return self

    async def __aexit__(self, *_):
        if self._context:
            await self._context.close()
        if self._playwright:
            await self._playwright.stop()

    async def login_and_export(self, login_url: str, email: str, password: str) -> tuple[dict, str]:
        """
        Opens a headed browser window. Fills email/password. User solves CAPTCHA if shown.
        Returns (cookies_dict, schedule_id) after successful login.
        """
        self._context = await self._playwright.chromium.launch_persistent_context(
            user_data_dir=str(PROFILE_DIR),
            headless=False,
            viewport={"width": 1280, "height": 800},
            args=["--no-sandbox"],
        )
        self._page = await self._context.new_page()

        logger.info("Opening AIS login page in browser...")
        await self._page.goto(login_url, wait_until="networkidle")

        email_field = self._page.locator("input[name='user[email]'], input[type='email']")
        password_field = self._page.locator("input[name='user[password]'], input[type='password']")

        if await email_field.count() > 0:
            await email_field.first.fill(email)
        if await password_field.count() > 0:
            await password_field.first.fill(password)

        logger.info("Credentials filled. If CAPTCHA appears, please solve it in the browser window.")
        logger.info("Waiting for login to complete (up to 5 minutes)...")

        try:
            await self._page.wait_for_url(
                re.compile(r"/en-ca/niv/schedule/\d+/"),
                timeout=300_000,
            )
        except Exception:
            submit = self._page.locator("input[type='submit'], button[type='submit']")
            if await submit.count() > 0:
                await submit.first.click()
            await self._page.wait_for_url(
                re.compile(r"/en-ca/niv/schedule/\d+/"),
                timeout=300_000,
            )

        current_url = self._page.url
        match = re.search(r"/schedule/(\d+)/", current_url)
        if not match:
            raise RuntimeError(f"Could not extract schedule_id from URL: {current_url}")
        schedule_id = match.group(1)
        logger.success("Login successful! schedule_id={}", schedule_id)

        raw_cookies = await self._context.cookies()
        cookies = {c["name"]: c["value"] for c in raw_cookies}

        await self._context.close()
        self._context = None
        return cookies, schedule_id

    async def intercept_xhrs(self, appointment_url: str, cookies: dict) -> list[dict]:
        """Debug helper: navigate to appointment page and capture XHR calls."""
        captured = []

        self._context = await self._playwright.chromium.launch_persistent_context(
            user_data_dir=str(PROFILE_DIR),
            headless=True,
        )
        self._page = await self._context.new_page()

        async def handle_request(request):
            if "application/json" in request.headers.get("accept", "") or ".json" in request.url:
                captured.append({"url": request.url, "headers": dict(request.headers)})

        self._page.on("request", handle_request)
        await self._page.goto(appointment_url, wait_until="networkidle")

        await self._context.close()
        self._context = None
        return captured
