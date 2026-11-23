from agents.base import Agent
from agents.registry import ALL_AGENTS
from config import ORCHESTRATOR_MODEL, ORCHESTRATOR_STEPS


class OrchestratorAgent(Agent):
    name = "orchestrator"
    role = "главный агент: ведёт производство видео от тренда до проверки"
    model = ORCHESTRATOR_MODEL
    system = """Ты продюсер производства TikTok-видео. Тебе доступны субагенты:
trends, scriptwriter, editor, subtitles, audio, cover, media_manager, publisher, analytics, qa.

Типовой порядок:
1. Если есть данные о трендах, вызови trends и выбери тренд для ниши.
2. scriptwriter: сценарий под выбранный тренд.
3. editor и subtitles: монтаж и субтитры по исходникам.
4. audio и cover: громкость и обложка.
5. qa: проверка. При FAIL вернись к нужному агенту с конкретными замечаниями.
6. publisher: черновик публикации.
7. Если пользователь просит, analytics по выгрузке метрик.

Правила:
- Субагенты не видят историю диалога. Передавай им полную задачу, все пути к файлам и результаты предыдущих шагов.
- Публикацию на платформе ты не выполняешь. Итог: файлы в outputs и черновик для ручной публикации.
- Если не хватает данных (нет исходника, нет выгрузки трендов), скажи пользователю, что именно нужно. Не выдумывай.
- В итоговом ответе: что сделано, где лежат файлы, что проверить вручную."""

    def __init__(self, subagents: list[Agent]):
        super().__init__(extra_tools=tuple(a.as_tool() for a in subagents))

    def produce(self, goal: str) -> str:
        return self.run(goal, max_steps=ORCHESTRATOR_STEPS)


def build_orchestrator() -> OrchestratorAgent:
    return OrchestratorAgent([cls() for cls in ALL_AGENTS])
