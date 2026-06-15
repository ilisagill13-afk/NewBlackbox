import re
import time
import aiohttp
from loguru import logger
from api.endpoints import TIMEOUT_URL, appointment_url

BROWSER_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/125.0.0.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
    "Accept-Encoding": "gzip, deflate, br",
    "Connection": "keep-alive",
    "Sec-Fetch-Site": "same-origin",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Dest": "empty",
}


class AISClient:
    def __init__(self, cookies: dict, schedule_id: str, facility_id: int):
        self._cookies = cookies
        self._schedule_id = schedule_id
        self._facility_id = facility_id
        self._session: aiohttp.ClientSession | None = None
        self._csrf_token: str | None = None

    async def __aenter__(self):
        jar = aiohttp.CookieJar(unsafe=True)
        self._session = aiohttp.ClientSession(
            headers=BROWSER_HEADERS,
            cookie_jar=jar,
        )
        for name, value in self._cookies.items():
            self._session.cookie_jar.update_cookies(
                {name: value}, response_url=aiohttp.typedefs.StrOrURL("https://ais.usvisa-info.com")
            )
        return self

    async def __aexit__(self, *_):
        if self._session:
            await self._session.close()

    async def get_json(self, url: str, params: dict = None) -> tuple[dict | list, int, int]:
        start = time.monotonic()
        headers = {
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": appointment_url(self._schedule_id),
        }
        async with self._session.get(url, params=params, headers=headers, allow_redirects=False) as resp:
            elapsed = int((time.monotonic() - start) * 1000)
            if resp.status in (301, 302, 303):
                logger.warning("Redirect on GET {} → session may have expired", url)
                return None, resp.status, elapsed
            data = await resp.json(content_type=None)
            return data, resp.status, elapsed

    async def get_html(self, url: str) -> tuple[str, int]:
        headers = {
            "Accept": "text/html,application/xhtml+xml,*/*",
            "Referer": appointment_url(self._schedule_id),
        }
        async with self._session.get(url, headers=headers, allow_redirects=True) as resp:
            html = await resp.text()
            if resp.status == 200:
                token = self._extract_csrf(html)
                if token:
                    self._csrf_token = token
            return html, resp.status

    async def post_form(self, url: str, data: dict) -> tuple[str, int]:
        if not self._csrf_token:
            html, _ = await self.get_html(appointment_url(self._schedule_id))
        headers = {
            "Accept": "text/html,application/xhtml+xml,*/*",
            "Content-Type": "application/x-www-form-urlencoded",
            "Referer": appointment_url(self._schedule_id),
            "X-CSRF-Token": self._csrf_token or "",
        }
        async with self._session.post(url, data=data, headers=headers, allow_redirects=True) as resp:
            body = await resp.text()
            return body, resp.status

    async def ping_session(self) -> dict | None:
        headers = {
            "Accept": "application/json, text/javascript, */*; q=0.01",
            "X-Requested-With": "XMLHttpRequest",
            "Referer": appointment_url(self._schedule_id),
        }
        async with self._session.get(TIMEOUT_URL, headers=headers, allow_redirects=False) as resp:
            if resp.status in (301, 302, 401):
                return None
            return await resp.json(content_type=None)

    def get_csrf_token(self) -> str | None:
        return self._csrf_token

    @staticmethod
    def _extract_csrf(html: str) -> str | None:
        match = re.search(r'<meta[^>]+name=["\']csrf-token["\'][^>]+content=["\']([^"\']+)["\']', html)
        if match:
            return match.group(1)
        match = re.search(r'content=["\']([^"\']+)["\'][^>]+name=["\']csrf-token["\']', html)
        return match.group(1) if match else None
