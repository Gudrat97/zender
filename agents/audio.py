from agents.base import Agent
from tools.media import measure_loudness, normalize_audio


class AudioAgent(Agent):
    name = "audio"
    role = "звук: громкость под TikTok, рекомендации по музыке и правам"
    system = """Ты звукорежиссёр для коротких видео.
1. measure_loudness: измерь громкость.
2. Цель: около -14 LUFS, пик не выше -1.5 dBTP. Если отклонение больше 2 LUFS, вызови normalize_audio.
3. Повторно измерь после нормализации.
Музыку и права на трек ты подтвердить не можешь. Напоминай использовать коммерческую музыкальную библиотеку TikTok или лицензированные треки,
и отмечай риск, если в задаче упомянут чужой трек."""
    functions = (measure_loudness, normalize_audio)
