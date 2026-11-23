"""Инструменты монтажа на ffmpeg / ffprobe."""
import json
import re

from tools.common import ffmpeg, in_path, out_path, run_cmd

LOUDNORM = "loudnorm=I=-14:TP=-1.5:LRA=11"


def probe(path: str) -> dict:
    """Метаданные видео: длительность в секундах, размер кадра, fps, есть ли звук."""
    src = in_path(path)
    data = json.loads(run_cmd(["ffprobe", "-v", "error", "-print_format", "json",
                               "-show_format", "-show_streams", str(src)]).stdout)
    video = next((s for s in data["streams"] if s["codec_type"] == "video"), {})
    has_audio = any(s["codec_type"] == "audio" for s in data["streams"])
    num, _, den = (video.get("avg_frame_rate") or "0/1").partition("/")
    fps = round(int(num) / int(den), 2) if den and int(den) else 0.0
    return {
        "duration_s": round(float(data["format"].get("duration", 0)), 2),
        "width": video.get("width"),
        "height": video.get("height"),
        "fps": fps,
        "has_audio": has_audio,
        "size_mb": round(int(data["format"].get("size", 0)) / 1e6, 2),
    }


def cut(path: str, start: float, end: float, out_name: str) -> str:
    """Вырезать фрагмент с start по end (секунды) и сохранить в outputs."""
    if end <= start:
        raise ValueError("end должен быть больше start")
    src, dst = in_path(path), out_path(out_name)
    ffmpeg(["-i", str(src), "-ss", str(start), "-to", str(end),
            "-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac", str(dst)])
    return str(dst)


def concat(paths: list[str], out_name: str) -> str:
    """Склеить несколько видео (у каждого должна быть дорожка звука) в одно."""
    if len(paths) < 2:
        raise ValueError("нужно минимум два видео")
    srcs = [str(in_path(p)) for p in paths]
    dst = out_path(out_name)
    args = []
    for s in srcs:
        args += ["-i", s]
    flt = "".join(f"[{i}:v:0][{i}:a:0]" for i in range(len(srcs)))
    flt += f"concat=n={len(srcs)}:v=1:a=1[v][a]"
    ffmpeg([*args, "-filter_complex", flt, "-map", "[v]", "-map", "[a]",
            "-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac", str(dst)])
    return str(dst)


def to_vertical(path: str, out_name: str) -> str:
    """Привести видео к вертикальному формату 9:16 (1080x1920) с обрезкой по центру."""
    src, dst = in_path(path), out_path(out_name)
    vf = "scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1"
    ffmpeg(["-i", str(src), "-vf", vf,
            "-c:v", "libx264", "-preset", "veryfast", "-c:a", "aac", str(dst)])
    return str(dst)


def normalize_audio(path: str, out_name: str) -> str:
    """Выровнять громкость под TikTok (около -14 LUFS, пик -1.5 dB). Видео не перекодируется."""
    src, dst = in_path(path), out_path(out_name)
    ffmpeg(["-i", str(src), "-af", LOUDNORM, "-c:v", "copy", "-c:a", "aac", str(dst)])
    return str(dst)


def measure_loudness(path: str) -> dict:
    """Измерить громкость дорожки: интегральная громкость (LUFS) и истинный пик (dBTP)."""
    src = in_path(path)
    err = ffmpeg(["-i", str(src), "-af", LOUDNORM + ":print_format=json",
                  "-f", "null", "-"], quiet=False).stderr
    m = re.search(r'\{[^{}]*"input_i"[^{}]*\}', err)
    if not m:
        raise RuntimeError("не удалось измерить громкость (возможно, нет аудио)")
    d = json.loads(m.group(0))
    return {"integrated_lufs": float(d["input_i"]), "true_peak_db": float(d["input_tp"])}


def scene_detect(path: str, threshold: float = 0.4) -> list[float]:
    """Найти моменты смены сцены в секундах. threshold от 0.05 до 0.95: чем выше, тем меньше сцен."""
    threshold = min(max(threshold, 0.05), 0.95)
    src = in_path(path)
    err = ffmpeg(["-i", str(src), "-filter:v",
                  f"select='gt(scene,{threshold})',showinfo", "-f", "null", "-"],
                 quiet=False).stderr
    times = [float(t) for t in re.findall(r"pts_time:([\d.]+)", err)]
    return times[:300]


def extract_frame(path: str, time_s: float, out_name: str) -> str:
    """Сохранить кадр из видео в указанной секунде. Имя файла заканчивается на .jpg или .png."""
    if not out_name.lower().endswith((".jpg", ".jpeg", ".png")):
        raise ValueError("имя кадра должно заканчиваться на .jpg или .png")
    src, dst = in_path(path), out_path(out_name)
    ffmpeg(["-ss", str(time_s), "-i", str(src), "-frames:v", "1", "-q:v", "2", str(dst)])
    return str(dst)


def burn_subtitles(path: str, srt_path: str, out_name: str) -> str:
    """Вшить SRT-субтитры в видео и сохранить в outputs."""
    src, srt, dst = in_path(path), in_path(srt_path), out_path(out_name)
    esc = str(srt).replace("\\", "/").replace(":", r"\:").replace("'", r"\'")
    ffmpeg(["-i", str(src), "-vf", f"subtitles='{esc}'", "-c:a", "copy", str(dst)])
    return str(dst)
