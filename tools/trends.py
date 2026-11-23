"""Тренды из CSV-выгрузки. Выгрузку вы делаете сами (например, со страницы TikTok Creative Center)."""
import csv

from tools.common import in_path


def _num(value) -> float:
    try:
        return float(str(value).replace("%", "").replace(",", "").strip())
    except (TypeError, ValueError):
        return 0.0


def load_trends(csv_path: str, niche: str = "", top_n: int = 10) -> list[dict]:
    """Прочитать CSV трендов (колонки name,type,growth_pct,views,niche) и вернуть топ по росту."""
    rows = []
    with open(in_path(csv_path), encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            if niche and niche.lower() not in (r.get("niche") or "").lower():
                continue
            rows.append({
                "name": r.get("name", ""),
                "type": r.get("type", ""),          # hashtag / sound / format
                "growth_pct": _num(r.get("growth_pct")),
                "views": _num(r.get("views")),
                "niche": r.get("niche", ""),
            })
    rows.sort(key=lambda x: x["growth_pct"], reverse=True)
    return rows[: max(1, min(top_n, 50))]
