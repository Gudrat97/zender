from agents.base import Agent
from tools.media import burn_subtitles
from tools.speech import transcribe, write_srt


class SubtitlesAgent(Agent):
    name = "subtitles"
    role = "субтитры: расшифровка речи, SRT, вшивание в видео"
    system = """Ты субтитровщик для коротких видео.
Порядок работы:
1. transcribe: распознай речь (язык из задачи, по умолчанию ru).
2. Исправь очевидные ошибки распознавания по контексту, не меняя смысла.
3. write_srt: собери файл субтитров (строки до 42 символов).
4. burn_subtitles: вшей субтитры в видео, только если это запрошено.
В ответе дай путь к SRT и краткий текст субтитров с таймкодами."""
    functions = (transcribe, write_srt, burn_subtitles)
