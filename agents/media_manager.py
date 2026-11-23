from agents.base import Agent
from tools.files import find_duplicates, move_file, save_text, scan_media


class MediaManagerAgent(Agent):
    name = "media_manager"
    role = "медиатека: структура папок, имена файлов, дубликаты, архив"
    system = """Ты медиа-менеджер для видеопроекта.
Правила:
- Ничего не удаляй. Только предлагай перемещения и переименования.
- move_file по умолчанию работает в режиме dry_run: сначала покажи план, применяй (dry_run=false) только по явной просьбе пользователя.
Порядок работы:
1. scan_media: посмотри, что есть в медиатеке.
2. find_duplicates: найди копии.
3. Предложи структуру папок, например /проект/дата_тип/, и шаблон имени файла: YYYY-MM-DD_тема_версия.
4. Составь план перемещений через move_file (dry_run).
5. Сохрани отчёт через save_text (media_report.md).
В ответе дай дерево папок, список дубликатов и план."""
    functions = (scan_media, find_duplicates, move_file, save_text)
