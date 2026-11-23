"""Базовый агент: цикл «модель → инструмент → результат → модель» поверх Claude API."""
import json

import anthropic

from config import MAX_STEPS, MAX_TOKENS, MODEL
from tools.common import Tool, make_tool

_client = None


def _api() -> anthropic.Anthropic:
    global _client
    if _client is None:
        _client = anthropic.Anthropic()  # берёт ANTHROPIC_API_KEY из окружения
    return _client


def _as_text(value) -> str:
    return value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, default=str)


class Agent:
    name: str = "agent"        # идентификатор (для вызова и как имя инструмента)
    role: str = ""            # одна строка: за что отвечает агент
    system: str = ""          # системный промпт
    model: str = MODEL
    max_tokens: int = MAX_TOKENS
    functions: tuple = ()     # python-функции, которые агент может вызывать

    def __init__(self, extra_tools: tuple[Tool, ...] = ()):
        self.tools = [make_tool(f) for f in self.functions] + list(extra_tools)
        self._handlers = {t.name: t.fn for t in self.tools}
        self._schemas = [t.api_schema() for t in self.tools]

    def run(self, task: str, max_steps: int = MAX_STEPS) -> str:
        messages = [{"role": "user", "content": task}]
        for _ in range(max_steps):
            kwargs = {"model": self.model, "max_tokens": self.max_tokens,
                      "system": self.system, "messages": messages}
            if self._schemas:
                kwargs["tools"] = self._schemas
            resp = _api().messages.create(**kwargs)
            messages.append({"role": "assistant", "content": resp.content})

            if resp.stop_reason != "tool_use":
                return "\n".join(b.text for b in resp.content if b.type == "text").strip()

            results = []
            for block in resp.content:
                if block.type != "tool_use":
                    continue
                try:
                    output = self._handlers[block.name](**block.input)
                except Exception as e:  # ошибку видит модель и может исправиться
                    output = f"ОШИБКА: {type(e).__name__}: {e}"
                results.append({"type": "tool_result", "tool_use_id": block.id,
                                "content": _as_text(output)})
            messages.append({"role": "user", "content": results})
        return f"[{self.name}] остановлен: достигнут лимит шагов ({max_steps})"

    def as_tool(self) -> Tool:
        """Оборачивает агента как инструмент, чтобы оркестратор мог его вызвать."""
        def delegate(task: str) -> str:
            return self.run(task)

        return Tool(
            name=self.name,
            description=f"Субагент «{self.name}»: {self.role}. Передай полную задачу и все нужные данные.",
            schema={"type": "object", "properties": {"task": {"type": "string"}}, "required": ["task"]},
            fn=delegate,
        )
