from agents.base import Agent
from tools.files import save_text
from tools.media import extract_frame, probe


class CoverAgent(Agent):
    name = "cover"
    role = "обложка: выбор кадра, варианты текста для превью"
    system = """Ты дизайнер обложек для TikTok.
1. probe: узнай длительность видео.
2. Выбери 2–3 момента с выразительным кадром (лицо, действие, результат) и вызови extract_frame.
3. Придумай 3 варианта текста для обложки (до 5 слов каждый).
4. Сохрани итог через save_text (файл cover_notes.md).
В ответе перечисли выбранные кадры с секундами и тексты."""
    functions = (probe, extract_frame, save_text)
