BASE_URL = "https://ais.usvisa-info.com/en-ca/niv"

LOGIN_URL = f"{BASE_URL}/users/sign_in"
TIMEOUT_URL = f"{BASE_URL}/users/timeout"
SIGN_OUT_URL = f"{BASE_URL}/users/sign_out"

APPOINTMENT_PAGE = f"{BASE_URL}/schedule/{{schedule_id}}/appointment"
DAYS_API = f"{BASE_URL}/schedule/{{schedule_id}}/appointment/days/{{facility_id}}.json"
TIMES_API = f"{BASE_URL}/schedule/{{schedule_id}}/appointment/times/{{facility_id}}.json"

FACILITIES = {
    "Calgary":   89,
    "Ottawa":    90,
    "Montreal":  91,
    "Halifax":   92,
    "Toronto":   94,
    "Vancouver": 99,
}

FACILITY_NAMES = {v: k for k, v in FACILITIES.items()}


def days_url(schedule_id: str, facility_id: int) -> str:
    return DAYS_API.format(schedule_id=schedule_id, facility_id=facility_id)


def times_url(schedule_id: str, facility_id: int) -> str:
    return TIMES_API.format(schedule_id=schedule_id, facility_id=facility_id)


def appointment_url(schedule_id: str) -> str:
    return APPOINTMENT_PAGE.format(schedule_id=schedule_id)
