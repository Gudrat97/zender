# Zender: ИИ-агенты для TikTok-производства

Команда из 10 субагентов и оркестратора. Оркестратор получает цель, делит её на шаги
и вызывает агентов: тренды, сценарий, монтаж, субтитры, звук, обложка, медиатека,
публикация, аналитика, контроль качества.

## Агенты

| Агент | Что делает | Инструменты |
|---|---|---|
| `trends` | Тренды из вашей CSV-выгрузки | load_trends |
| `scriptwriter` | Сценарий, хук, текст | save_text |
| `editor` | Нарезка, склейка, 9:16 | probe, scene_detect, cut, concat, to_vertical |
| `subtitles` | Распознавание речи, SRT, вшивание | transcribe, write_srt, burn_subtitles |
| `audio` | Громкость под TikTok (-14 LUFS) | measure_loudness, normalize_audio |
| `cover` | Кадр и текст обложки | probe, extract_frame, save_text |
| `media_manager` | Папки, дубликаты, перемещение (dry-run) | scan_media, find_duplicates, move_file, save_text |
| `publisher` | Черновик поста, хэштеги, время. Не публикует сам | save_text |
| `analytics` | Разбор метрик из CSV и гипотезы | load_metrics, save_text |
| `qa` | Проверка перед публикацией: PASS / FAIL | qa_check |
| `orchestrator` | Ведёт весь цикл, вызывает субагентов | субагенты |

## Установка

Нужны Python 3.10+ и ffmpeg (`apt install ffmpeg` или `brew install ffmpeg`).

```bash
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
export ANTHROPIC_API_KEY=ваш_ключ
```

Первый запуск `subtitles` скачает модель Whisper (`small`, около 500 МБ).

## Использование

```bash
python main.py list
python main.py agent qa "Проверь /путь/к/video.mp4, 15–60 секунд, субтитры нужны, файл субтитров outputs/transcripts/x.srt"
python main.py agent trends "Выгрузка: examples/trends.csv, ниша: кофейня. Выбери 3 тренда."
python main.py run "Сделай 30-секундное видео про кофе. Исходник: /путь/input.mp4. Тренды: examples/trends.csv"
```

Результаты лежат в `workspace/outputs/`. Медиатека по умолчанию в `workspace/media/`.

## Важно про данные и безопасность

- **Тренды TikTok.** Публичного API с трендами нет. Агент `trends` работает с выгрузкой,
  которую вы сделали сами, например со страницы TikTok Creative Center. Парсинг ленты
  нарушает условия сервиса, поэтому в проекте его нет.
- **Музыка.** Агент `audio` не подтверждает права на трек. Используйте коммерческую
  библиотеку TikTok или лицензированную музыку.
- **Файлы.** Агенты пишут только в `workspace/outputs/`. Медиа-менеджер работает только
  внутри `ZENDER_MEDIA_ROOT`, ничего не удаляет и по умолчанию делает `move_file` в режиме dry-run.
- **Публикация.** Проект ничего не публикует на платформах. Готовый черновик и проверку
  делаете вы.

## Настройки

Все параметры в `config.py` и переменных окружения (см. `.env.example`):
модели, рабочая папка, медиатека, модель Whisper.

## Тесты

```bash
pytest -q
```

Тесты с ffmpeg пропускаются, если ffmpeg не установлен.

## Добавить агента

1. Создайте `agents/имя.py` с классом-наследником `Agent`: `name`, `role`, `system`, `functions`.
2. Напишите функции в `tools/`. Аннотации типов и первая строка docstring попадают в схему инструмента.
3. Добавьте класс в `agents/registry.py`.
