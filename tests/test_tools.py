import shutil
import subprocess

import pytest

from agents.registry import ALL_AGENTS
from config import OUTPUT_DIR
from tools.common import make_tool, out_path
from tools.trends import load_trends

HAS_FFMPEG = shutil.which("ffmpeg") and shutil.which("ffprobe")


def test_schema_from_signature():
    def sample(path: str, start: float, flag: bool = False, tags: list[str] | None = None) -> str:
        """Пример инструмента."""
        return ""

    t = make_tool(sample)
    assert t.name == "sample"
    assert t.description == "Пример инструмента."
    assert t.schema["required"] == ["path", "start"]
    assert t.schema["properties"]["start"] == {"type": "number"}
    assert t.schema["properties"]["flag"] == {"type": "boolean"}


def test_out_path_blocks_escape():
    with pytest.raises(ValueError):
        out_path("../evil.txt")
    assert str(out_path("ok/file.txt")).startswith(str(OUTPUT_DIR.resolve()))


def test_all_agents_build_without_api_key():
    names = [cls.name for cls in ALL_AGENTS]
    assert sorted(names) == sorted([
        "trends", "scriptwriter", "editor", "subtitles", "audio",
        "cover", "media_manager", "publisher", "analytics", "qa",
    ])
    assert len(set(names)) == len(names)
    for cls in ALL_AGENTS:
        agent = cls()
        assert agent.system and agent.role
        assert agent.tools, f"у агента {cls.name} нет инструментов"


def test_load_trends_sorts_by_growth(tmp_path):
    csv_file = tmp_path / "t.csv"
    csv_file.write_text(
        "name,type,growth_pct,views,niche\n"
        "#coffee,hashtag,35%,1000,кофейня\n"
        "sound_a,sound,120,500,кофейня\n"
        "#gym,hashtag,90,300,спорт\n",
        encoding="utf-8",
    )
    rows = load_trends(str(csv_file), niche="кофе", top_n=5)
    assert [r["name"] for r in rows] == ["sound_a", "#coffee"]  # по росту, без «спорта»
    rows_all = load_trends(str(csv_file), top_n=5)
    assert rows_all[0]["name"] == "sound_a"


@pytest.mark.skipif(not HAS_FFMPEG, reason="нужен ffmpeg")
def test_probe_and_cut(tmp_path):
    from tools.media import cut, probe

    src = tmp_path / "src.mp4"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error", "-y",
        "-f", "lavfi", "-i", "testsrc=size=720x1280:rate=25:duration=3",
        "-f", "lavfi", "-i", "sine=frequency=440:duration=3",
        "-shortest", "-c:v", "libx264", "-c:a", "aac", str(src),
    ], check=True)

    info = probe(str(src))
    assert info["has_audio"] is True
    assert abs(info["duration_s"] - 3.0) < 0.2
    assert info["height"] > info["width"]

    out = cut(str(src), 1.0, 2.0, "test_cut.mp4")
    assert abs(probe(out)["duration_s"] - 1.0) < 0.2
