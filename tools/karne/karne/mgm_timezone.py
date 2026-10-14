"""MGM saatlik tahmininin saat dilimi: damgalar UTC mi, Türkiye saati mi?

Arşivdeki her il merkezinin MGM saatlik sıcaklık eğrisi, aynı nokta ve aynı gün için Open-Meteo'nun ICON-EU,
ECMWF IFS ve best_match saatlik eğrileriyle karşılaştırılır. Kaydırma k: MGM'nin "T" damgası gerçekte
UTC T + k saatini anlatıyorsa k. k = 0 damgaların UTC olduğunu, k = −3 Türkiye saati olduğunu söyler
(uygulama bugüne kadar böyle okudu), k = +3 karşılaştırma için.

Günlük tahminin tarihleri de ayrıca sınanır: MGM'nin "N. gün" en yüksek sıcaklığı Open-Meteo'nun aynı günün,
bir önceki ya da bir sonraki günün en yüksek sıcaklığına mı uyuyor?
"""

from __future__ import annotations

import json
from collections import defaultdict
from datetime import date, datetime, timedelta

import numpy as np
import requests

from . import config, mgm

URL = "https://api.open-meteo.com/v1/forecast"
MODELS = ["icon_eu", "ecmwf_ifs", "best_match"]
SHIFTS = [-3, 0, 3]
BATCH = 40


def _stations(data) -> dict[int, dict]:
    return {s["id"]: s for s in json.loads((data / "stations.json").read_text(encoding="utf-8"))}


def _fetch(points: list[dict], start: date, end: date, daily: bool = False) -> list[dict]:
    out = []
    for i in range(0, len(points), BATCH):
        part = points[i:i + BATCH]
        params = {
            "latitude": ",".join(str(p["lat"]) for p in part),
            "longitude": ",".join(str(p["lon"]) for p in part),
            "elevation": ",".join(str(p.get("h") or 0) for p in part),
            "models": ",".join(MODELS),
            "start_date": start.isoformat(),
            "end_date": end.isoformat(),
            "timezone": "GMT" if not daily else "Europe/Istanbul",
        }
        params["daily" if daily else "hourly"] = "temperature_2m_max" if daily else "temperature_2m"
        response = requests.get(URL, params=params, headers={"User-Agent": config.USER_AGENT}, timeout=120)
        response.raise_for_status()
        payload = response.json()
        out += payload if isinstance(payload, list) else [payload]
    return out


def latest_runs(lines: list[dict], kind: str) -> dict[int, dict]:
    """Her istasyonun en son turdaki tahmini."""
    latest: dict[int, dict] = {}
    for line in lines:
        if line.get("k") == kind and (line["id"] not in latest or line["t"] > latest[line["id"]]["t"]):
            latest[line["id"]] = line
    return latest


def merged_steps(lines: list[dict]) -> dict[int, dict]:
    """Saatlik tahmin: her istasyonun bütün turlardaki adımları, aynı damgada en yeni turunki kalır."""
    steps: dict[int, dict] = defaultdict(dict)
    for line in sorted((l for l in lines if l.get("k") == "s"), key=lambda l: l["t"]):
        for step in line["f"]:
            steps[line["id"]][step[0]] = step
    return {sid: {"f": sorted(values.values())} for sid, values in steps.items()}


def stamp(text: str) -> np.datetime64:
    """MGM damgası, sondaki Z atılarak (saat dilimi henüz bilinmiyor)."""
    return np.datetime64(text[:16])


def analyse() -> str:
    data = mgm.archive_dir()
    if data is None:
        return "MGM arşivi bulunamadı."
    forecasts, _ = mgm.load(data)
    stations = _stations(data)
    centers = json.loads((data / "centers.json").read_text(encoding="utf-8"))["centers"]
    hourly = merged_steps(forecasts)
    hourly = {sid: line for sid, line in hourly.items() if sid in stations and stations[sid].get("lat") is not None}
    ids = sorted(hourly)
    times = [stamp(step[0]) for line in hourly.values() for step in line["f"]]
    start = date.fromisoformat(str(min(times))[:10]) - timedelta(days=1)
    end = date.fromisoformat(str(max(times))[:10]) + timedelta(days=1)
    answers = _fetch([stations[i] for i in ids], start, end)

    rows = []
    totals = {(m, k): [] for m in MODELS for k in SHIFTS}
    votes = defaultdict(int)
    votes_corr = defaultdict(int)
    points = []
    for sid, answer in zip(ids, answers):
        series = {m: dict(zip(np.array(answer["hourly"]["time"], dtype="datetime64[m]"), answer["hourly"][f"temperature_2m_{m}"])) for m in MODELS}
        steps = [(stamp(s[0]), s[2]) for s in hourly[sid]["f"] if s[2] is not None]
        best_by_model = {}
        best_by_corr = {}
        cells = {}
        for model in MODELS:
            scores = {}
            for shift in SHIFTS:
                pairs = [(value, series[model].get(t + np.timedelta64(shift, "h"))) for t, value in steps]
                pairs = [(a, b) for a, b in pairs if b is not None]
                if len(pairs) < 4:
                    continue
                a, b = np.array(pairs, dtype=float).T
                mae = float(np.mean(np.abs(a - b)))
                corr = float(np.corrcoef(a, b)[0, 1]) if a.std() > 0 and b.std() > 0 else float("nan")
                scores[shift] = (mae, corr)
                totals[(model, shift)] += list(a - b)
            if scores:
                best_by_model[model] = min(scores, key=lambda k: scores[k][0])
                best_by_corr[model] = max(scores, key=lambda k: -2 if np.isnan(scores[k][1]) else scores[k][1])
                cells[model] = scores
        # Merkezin kararı: üç modelden en az ikisinin en iyi bulduğu kaydırma.
        counted = defaultdict(int)
        for shift in best_by_model.values():
            counted[shift] += 1
        verdict = max(counted, key=counted.get) if counted and max(counted.values()) >= 2 else None
        votes[verdict] += 1
        counted_corr = defaultdict(int)
        for shift in best_by_corr.values():
            counted_corr[shift] += 1
        corr_verdict = max(counted_corr, key=counted_corr.get) if counted_corr and max(counted_corr.values()) >= 2 else None
        votes_corr[corr_verdict] += 1
        points.append(len(steps))
        name = next((c["il"] for c in centers if c.get("saatlik") == sid), str(sid))
        rows.append((name, sid, cells, verdict))

    out = ["# MGM saatlik tahmininin saat dilimi\n",
           f"Üretildi: {datetime.now().strftime('%Y-%m-%d %H:%M')} · `python -m karne mgm-saat`\n",
           f"Karşılaştırılan: {len(rows)} saatlik tahmin istasyonu (81 il merkezinin), her birinin arşivdeki en son "
           "turu; MGM 3 saatte bir değer veriyor. Open-Meteo eğrileri aynı nokta için, istasyon yüksekliğiyle.\n",
           "Kaydırma k: MGM'nin \"T\" damgası gerçekte UTC T + k saatini anlatıyorsa. **k = 0: damgalar UTC.** "
           "**k = −3: damgalar Türkiye saati** (uygulamanın bugüne kadarki okuması).\n",
           "## Toplam\n",
           "| Model | k = −3 (Türkiye saati) | k = 0 (UTC) | k = +3 |", "|---|---:|---:|---:|"]
    for model in MODELS:
        cells = []
        for shift in SHIFTS:
            errors = np.array(totals[(model, shift)])
            cells.append(f"{np.mean(np.abs(errors)):.2f}°".replace(".", ",") if len(errors) else "–")
        out.append(f"| {model} | " + " | ".join(cells) + " |")
    corr_cells = []
    out += ["", "Ortalama mutlak fark (MGM − model).", "",
            "**Merkezlerin kararı** (üç modelden en az ikisinde en küçük farkı veren kaydırma):", "",
            "| Kaydırma | Merkez (en küçük fark) | Merkez (en yüksek korelasyon) |", "|---|---:|---:|"]
    labels = {-3: "k = −3 (Türkiye saati)", 0: "k = 0 (UTC)", 3: "k = +3", None: "belirsiz"}
    for verdict in (-3, 0, 3, None):
        out.append(f"| {labels[verdict]} | {votes.get(verdict, 0)} | {votes_corr.get(verdict, 0)} |")
    out += ["", f"Merkez başına MGM değeri: {min(points)}–{max(points)} (bütün turların adımları birleştirildi)."]
    out += ["", "## Merkez merkez", "",
            "Hücre: ortalama mutlak fark / korelasyon, k = −3 · 0 · +3 sırasıyla. Kalın: o modelde en iyi uyan.", "",
            "| İl | İstasyon | ICON-EU | ECMWF IFS | best_match | Karar |", "|---|---:|---|---|---|---|"]
    for name, sid, cells, verdict in sorted(rows):
        parts = []
        for model in MODELS:
            scores = cells.get(model, {})
            if not scores:
                parts.append("–")
                continue
            best = min(scores, key=lambda k: scores[k][0])
            texts = []
            for shift in SHIFTS:
                if shift not in scores:
                    texts.append("–")
                    continue
                mae, corr = scores[shift]
                text = f"{mae:.1f}/{corr:.2f}".replace(".", ",")
                texts.append(f"**{text}**" if shift == best else text)
            parts.append(" · ".join(texts))
        out.append(f"| {name} | {sid} | " + " | ".join(parts) + f" | {labels[verdict]} |")

    out += ["", daily_dates(forecasts, stations, centers)]
    return "\n".join(out)


def daily_dates(forecasts: list[dict], stations: dict, centers: list[dict]) -> str:
    """Günlük tahminin tarihi: N. günün en yüksek sıcaklığı Open-Meteo'nun hangi gününe uyuyor?"""
    daily = latest_runs(forecasts, "g")
    points = []
    for c in centers:
        line = daily.get(c.get("gunluk"))
        if line and c.get("lat") is not None:
            points.append((c, line))
    if not points:
        return ""
    dates = [date.fromisoformat(d[0][:10]) for _, line in points for d in line["d"]]
    answers = _fetch([{"lat": c["lat"], "lon": c["lon"], "h": c.get("h")} for c, _ in points], min(dates) - timedelta(days=1),
                     min(max(dates) + timedelta(days=1), date.today() + timedelta(days=14)), daily=True)
    errors = {o: [] for o in (-1, 0, 1)}
    for (c, line), answer in zip(points, answers):
        model = dict(zip(answer["daily"]["time"], answer["daily"]["temperature_2m_max_best_match"]))
        for day in line["d"]:
            if day[2] is None:
                continue
            for offset in errors:
                value = model.get((date.fromisoformat(day[0][:10]) + timedelta(days=offset)).isoformat())
                if value is not None:
                    errors[offset].append(day[2] - value)
    lines = ["## Günlük tahminin tarihleri", "",
             "MGM günlük tahmininde tarihler \"2026-10-14T00:00:00.000Z\" gibi gece yarısıdır; bu, saatlik tahminin saat "
             "dilimi için bir şey kanıtlamaz. Burada yalnız gün eşleşmesi sınanır: MGM'nin bir günün en yüksek sıcaklığı, "
             "Open-Meteo'nun (best_match, Türkiye saatiyle) aynı gününe mi, bir gün öncesine mi, sonrasına mı uyuyor?", "",
             "| Eşleşme | Ortalama mutlak fark | Eş |", "|---|---:|---:|"]
    for offset, label in ((-1, "bir gün önce"), (0, "aynı gün"), (1, "bir gün sonra")):
        values = np.array(errors[offset])
        lines.append(f"| {label} | {np.mean(np.abs(values)):.2f}° | {len(values)} |".replace(".", ","))
    return "\n".join(lines)
