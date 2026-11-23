"""Распознавание речи (faster-whisper) и выпуск субтитров в формате SRT."""
import json
from functools import lru_cache

from config import WHISPER_MODEL
from tools.common import in_path, out_path


@lru_cache(maxsize=1)
def _whisper():
    from faster_whisper import WhisperModel  # тяжёлый импорт, грузим только при необходимости
    return WhisperModel(WHISPER_MODEL, device="cpu", compute_type="int8")


def transcribe(path: str, language: str = "ru") -> dict:
    """Распознать речь в видео или аудио. Возвращает путь к JSON и сегменты с таймкодами."""
    src = in_path(path)
    segments, _ = _whisper().transcribe(str(src), language=language, vad_filter=True)
    data = [{"start": round(s.start, 2), "end": round(s.end, 2), "text": s.text.strip()}
            for s in segments]
    dst = out_path(f"transcripts/{src.stem}.json")
    dst.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    return {"path": str(dst), "segments": data}


def _ts(sec: float) -> str:
    ms = int(round(sec * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02}:{m:02}:{s:02},{ms:03}"


def _chunks(text: str, max_chars: int) -> list[str]:
    lines, cur = [], ""
    for word in text.split():
        if cur and len(cur) + len(word) + 1 > max_chars:
            lines.append(cur)
            cur = word
        else:
            cur = f"{cur} {word}".strip()
    if cur:
        lines.append(cur)
    return lines


def write_srt(transcript_path: str, out_name: str, max_chars: int = 42) -> str:
    """Собрать файл субтитров SRT из JSON-транскрипта. Строки не длиннее max_chars символов."""
    data = json.loads(in_path(transcript_path).read_text(encoding="utf-8"))
    cues = []
    for seg in data:
        chunks = _chunks(seg["text"], max_chars)
        if not chunks:
            continue
        total = sum(len(c) for c in chunks)
        t, dur = seg["start"], seg["end"] - seg["start"]
        for c in chunks:
            d = dur * len(c) / total
            cues.append((t, t + d, c))
            t += d
    lines = []
    for i, (a, b, text) in enumerate(cues, 1):
        lines += [str(i), f"{_ts(a)} --> {_ts(b)}", text, ""]
    dst = out_path(out_name)
    dst.write_text("\n".join(lines), encoding="utf-8")
    return str(dst)
