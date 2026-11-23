"""Список всех субагентов. Чтобы добавить агента, создайте класс и впишите его сюда."""
from agents.analytics import AnalyticsAgent
from agents.audio import AudioAgent
from agents.cover import CoverAgent
from agents.editor import EditorAgent
from agents.media_manager import MediaManagerAgent
from agents.publisher import PublisherAgent
from agents.qa import QAAgent
from agents.scriptwriter import ScriptwriterAgent
from agents.subtitles import SubtitlesAgent
from agents.trends import TrendsAgent

ALL_AGENTS = [
    TrendsAgent,
    ScriptwriterAgent,
    EditorAgent,
    SubtitlesAgent,
    AudioAgent,
    CoverAgent,
    MediaManagerAgent,
    PublisherAgent,
    AnalyticsAgent,
    QAAgent,
]
