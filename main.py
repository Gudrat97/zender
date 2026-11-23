"""Zender: команда ИИ-агентов для производства TikTok-видео.

Примеры:
  python main.py list
  python main.py run "Сделай видео про кофе: исходник input.mp4, тренды examples/trends.csv"
  python main.py agent qa "Проверь файл /путь/к/video.mp4, длительность 15–60 с, нужны субтитры"
"""
import argparse
import sys

from agents.orchestrator import build_orchestrator
from agents.registry import ALL_AGENTS


def main() -> None:
    parser = argparse.ArgumentParser(description="Zender: ИИ-агенты для TikTok")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("list", help="Показать всех агентов")

    p_run = sub.add_parser("run", help="Полный цикл через оркестратора")
    p_run.add_argument("goal", help="Что нужно сделать")

    p_agent = sub.add_parser("agent", help="Вызвать одного агента напрямую")
    p_agent.add_argument("name", help="Имя агента (см. list)")
    p_agent.add_argument("task", help="Задача для агента")

    args = parser.parse_args()

    if args.cmd == "list":
        for cls in ALL_AGENTS:
            print(f"{cls.name:15} {cls.role}")
    elif args.cmd == "run":
        print(build_orchestrator().produce(args.goal))
    elif args.cmd == "agent":
        by_name = {cls.name: cls for cls in ALL_AGENTS}
        if args.name not in by_name:
            sys.exit(f"нет агента «{args.name}». Доступны: {', '.join(by_name)}")
        print(by_name[args.name]().run(args.task))


if __name__ == "__main__":
    main()
