import sys
from loguru import logger


def setup_logger(level: str = "INFO", log_file: str = "logs/visa_assistant.log"):
    logger.remove()
    logger.add(
        sys.stdout,
        level=level,
        format="<green>{time:HH:mm:ss}</green> | <level>{level: <8}</level> | <cyan>{name}</cyan> - <level>{message}</level>",
        colorize=True,
    )
    logger.add(
        log_file,
        level=level,
        format="{time:YYYY-MM-DD HH:mm:ss} | {level: <8} | {name} - {message}",
        rotation="10 MB",
        retention="7 days",
        encoding="utf-8",
    )
    return logger
