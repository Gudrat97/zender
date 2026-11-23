"""Разбор метрик опубликованных видео из CSV."""
import csv

from tools.common import in_path


def load_metrics(csv_path: str) -> dict:
    """Прочитать CSV метрик (video,views,likes,comments,shares,avg_watch_s,duration_s) и посчитать итоги."""
    rows = []
    with open(in_path(csv_path), encoding="utf-8-sig", newline="") as f:
        for r in csv.DictReader(f):
            try:
                views = float(r.get("views") or 0)
                if views <= 0:
                    continue
                interactions = sum(float(r.get(k) or 0) for k in ("likes", "comments", "shares"))
                duration = float(r.get("duration_s") or 0)
                avg_watch = float(r.get("avg_watch_s") or 0)
            except ValueError:
                continue
            completion = min(avg_watch / duration, 1.0) if duration > 0 else 0.0
            rows.append({
                "video": r.get("video", ""),
                "views": int(views),
                "engagement_pct": round(interactions / views * 100, 2),
                "completion_pct": round(completion * 100, 1),
            })
    if not rows:
        return {"count": 0, "note": "нет валидных строк в CSV"}
    rows.sort(key=lambda x: x["views"], reverse=True)
    n = len(rows)
    return {
        "count": n,
        "avg_engagement_pct": round(sum(r["engagement_pct"] for r in rows) / n, 2),
        "avg_completion_pct": round(sum(r["completion_pct"] for r in rows) / n, 1),
        "top3": rows[:3],
        "bottom3": rows[-3:],
    }
