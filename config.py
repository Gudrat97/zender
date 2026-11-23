"""Настройки проекта. Переопределяются переменными окружения."""
import os
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent

# Рабочая папка: сюда агенты пишут все результаты (outputs/)
WORKDIR = Path(os.getenv("ZENDER_WORKDIR", ROOT_DIR / "workspace"))
OUTPUT_DIR = WORKDIR / "outputs"

# Медиатека, с которой работает медиа-менеджер (только внутри этой папки)
MEDIA_ROOT = Path(os.getenv("ZENDER_MEDIA_ROOT", WORKDIR / "media"))

# Модели Claude
MODEL = os.getenv("ZENDER_MODEL", "claude-sonnet-5-5")
ORCHESTRATOR_MODEL = os.getenv("ZENDER_ORCHESTRATOR_MODEL", "claude-opus-5-5")

MAX_TOKENS = 4096
MAX_STEPS = 10          # шагов цикла инструментов на одного агента
ORCHESTRATOR_STEPS = 25

# Распознавание речи (faster-whisper): tiny, base, small, medium, large-v3
WHISPER_MODEL = os.getenv("ZENDER_WHISPER_MODEL", "small")
