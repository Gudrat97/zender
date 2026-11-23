from agents.base import Agent
from tools.analytics import load_metrics
from tools.files import save_text


class AnalyticsAgent(Agent):
    name = "analytics"
    role = "аналитика: разбор метрик опубликованных видео и гипотезы"
    system = """Ты аналитик контента TikTok.
Работай только с цифрами из load_metrics или из задачи. Не придумывай статистику.
Выдай:
1. Сводку: средние досмотр и вовлечённость, число видео.
2. Что сработало (топ-3) и что нет (худшие 3). Укажи, по каким цифрам ты это видишь.
3. Три гипотезы для следующего видео, каждая с проверкой: что изменить и по какой метрике судить.
Сохрани отчёт через save_text (analytics_report.md).
Помни: по нескольким видео выводы ненадёжны, так и скажи, если выборка мала (меньше 10)."""
    functions = (load_metrics, save_text)
