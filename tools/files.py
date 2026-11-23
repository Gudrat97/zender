"""Медиа-менеджер: сканирование медиатеки, поиск дубликатов, перемещение, текстовые результаты."""
import hashlib
import shutil
from pathlib import Path

from config import MEDIA_ROOT
from tools.common import out_path

MEDIA_EXT = {".mp4", ".mov", ".mkv", ".webm", ".jpg", ".jpeg", ".png",
             ".wav", ".mp3", ".m4a", ".srt", ".json", ".csv"}


def _in_media(folder: str) -> Path:
    """Путь внутри медиатеки. Выход за её пределы запрещён."""
    MEDIA_ROOT.mkdir(parents=True, exist_ok=True)
    base = MEDIA_ROOT.resolve()
    p = Path(folder).resolve() if Path(folder).is_absolute() else (base / folder).resolve()
    if p != base and base not in p.parents:
        raise PermissionError("путь вне медиатеки запрещён")
    return p


def scan_media(folder: str = "") -> list[dict]:
    """Список медиафайлов в папке медиатеки (путь относительно медиатеки и размер)."""
    base = MEDIA_ROOT.resolve()
    items = []
    for p in sorted(_in_media(folder).rglob("*")):
        if p.is_file() and p.suffix.lower() in MEDIA_EXT:
            items.append({"path": str(p.relative_to(base)),
                          "size_mb": round(p.stat().st_size / 1e6, 2)})
            if len(items) >= 500:
                break
    return items


def find_duplicates(folder: str = "") -> list[list[str]]:
    """Найти одинаковые файлы в медиатеке по SHA-256. Возвращает группы путей."""
    base = MEDIA_ROOT.resolve()
    groups: dict[str, list[str]] = {}
    for p in _in_media(folder).rglob("*"):
        if not (p.is_file() and p.suffix.lower() in MEDIA_EXT):
            continue
        h = hashlib.sha256()
        with open(p, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 20), b""):
                h.update(chunk)
        groups.setdefault(h.hexdigest(), []).append(str(p.relative_to(base)))
    return [g for g in groups.values() if len(g) > 1]


def move_file(src: str, dst: str, dry_run: bool = True) -> str:
    """Переместить или переименовать файл внутри медиатеки. По умолчанию только показывает план."""
    s, d = _in_media(src), _in_media(dst)
    if not s.is_file():
        raise FileNotFoundError(f"нет файла: {src}")
    if d.exists():
        raise FileExistsError(f"уже существует: {dst}")
    if dry_run:
        return f"ПЛАН (dry_run=true, файл не тронут): {src} -> {dst}"
    d.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(s), str(d))
    return f"перемещено: {src} -> {dst}"


def save_text(name: str, content: str) -> str:
    """Сохранить текстовый результат (сценарий, описание, отчёт) в папку outputs."""
    dst = out_path(name)
    dst.write_text(content, encoding="utf-8")
    return str(dst)
