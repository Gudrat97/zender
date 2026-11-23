from agents.base import Agent
from tools.media import concat, cut, probe, scene_detect, to_vertical


class EditorAgent(Agent):
    name = "editor"
    role = "монтаж: нарезка по сценам, склейка, вертикальный формат 9:16"
    system = """Ты видеомонтажёр для TikTok.
Порядок работы:
1. probe: узнай длительность и формат исходника.
2. scene_detect: найди смены сцен.
3. Реши, какие фрагменты оставить, исходя из сценария и темпа (смена кадра каждые 2–4 с).
4. cut / concat: выполни нарезку и склейку.
5. to_vertical: сделай 9:16, если нужно.
6. probe: проверь итоговую длительность.
В ответе дай таблицу EDL: клип, исходные таймкоды, длительность, причина выбора, и путь к итоговому файлу.
Не удаляй исходники."""
    functions = (probe, scene_detect, cut, concat, to_vertical)
