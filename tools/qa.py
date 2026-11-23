"""Контроль качества готового видео перед публикацией."""
from pathlib import Path

from tools.media import measure_loudness, probe


def qa_check(path: str, min_seconds: float = 15, max_seconds: float = 60,
             require_subtitles: bool = False, subtitles_path: str = "") -> dict:
    """Проверить готовое видео: длительность, вертикальный формат, звук, громкость, субтитры."""
    info = probe(path)
    issues = []

    if not (min_seconds <= info["duration_s"] <= max_seconds):
        issues.append(f"длительность {info['duration_s']} c вне диапазона {min_seconds}–{max_seconds} c")
    if info["width"] and info["height"] and info["height"] <= info["width"]:
        issues.append("кадр не вертикальный (нужен 9:16)")
    if not info["has_audio"]:
        issues.append("нет аудиодорожки")
    else:
        try:
            lufs = measure_loudness(path)["integrated_lufs"]
            if not (-16 <= lufs <= -12):
                issues.append(f"громкость {lufs} LUFS, целевой диапазон -16…-12")
        except RuntimeError as e:
            issues.append(f"громкость не измерена: {e}")
    if require_subtitles and not (subtitles_path and Path(subtitles_path).is_file()):
        issues.append("требуются субтитры, но файл SRT не найден")

    return {"verdict": "PASS" if not issues else "FAIL", "issues": issues, "info": info}
