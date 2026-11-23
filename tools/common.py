"""Общие утилиты: схемы инструментов для Claude, запуск ffmpeg, безопасные пути."""
import inspect
import subprocess
import typing
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

from config import OUTPUT_DIR

_JSON_TYPES = {str: "string", int: "integer", float: "number", bool: "boolean"}


@dataclass
class Tool:
    name: str
    description: str
    schema: dict
    fn: Callable

    def api_schema(self) -> dict:
        return {"name": self.name, "description": self.description, "input_schema": self.schema}


def make_tool(fn: Callable) -> Tool:
    """Превращает python-функцию в инструмент: схема строится по аннотациям и docstring."""
    hints = typing.get_type_hints(fn)
    props, required = {}, []
    for pname, param in inspect.signature(fn).parameters.items():
        t = hints.get(pname, str)
        if typing.get_origin(t) is list:
            item = typing.get_args(t)[0]
            props[pname] = {"type": "array", "items": {"type": _JSON_TYPES.get(item, "string")}}
        else:
            props[pname] = {"type": _JSON_TYPES.get(t, "string")}
        if param.default is inspect.Parameter.empty:
            required.append(pname)
    description = (inspect.getdoc(fn) or fn.__name__).splitlines()[0]
    return Tool(fn.__name__, description,
                {"type": "object", "properties": props, "required": required}, fn)


def run_cmd(cmd: list[str]) -> subprocess.CompletedProcess:
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if proc.returncode != 0:
        raise RuntimeError((proc.stderr or "команда завершилась с ошибкой").strip()[-600:])
    return proc


def ffmpeg(args: list[str], quiet: bool = True) -> subprocess.CompletedProcess:
    level = "error" if quiet else "info"
    return run_cmd(["ffmpeg", "-hide_banner", "-loglevel", level, "-y", *args])


def out_path(name: str) -> Path:
    """Путь внутри папки outputs. Выход за её пределы запрещён."""
    base = OUTPUT_DIR.resolve()
    target = (base / name).resolve()
    if target != base and base not in target.parents:
        raise ValueError("путь вне папки outputs запрещён")
    target.parent.mkdir(parents=True, exist_ok=True)
    return target


def in_path(path: str) -> Path:
    p = Path(path).expanduser().resolve()
    if not p.is_file():
        raise FileNotFoundError(f"файл не найден: {path}")
    return p
