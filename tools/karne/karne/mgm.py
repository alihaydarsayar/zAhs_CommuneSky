"""MGM tahmin arşivini (özel depo) okur ve MGM'yi karneye katar.

- Saatlik tahmin istasyonu bir havalimanına 15 km'den yakın olan merkezler: METAR sıcaklığıyla.
- Diğer merkezler: MGM'nin kendi günlük ölçümüyle (en yüksek, en düşük, yağış var/yok).

MGM saatlik tahminindeki saatler UTC'dir; günlük tahmindeki tarih Türkiye takvim günüdür.
"""

from __future__ import annotations

import gzip
import json
from collections import defaultdict
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

import numpy as np

from . import config, iem, metrics, regions

AIRPORT_KM = 15.0
# MGM'yi rapora katmak için gereken en az arşiv günü.
MIN_DAYS = 30
# Günlük tahminde yağış anlatan hadise kodları (uygulamadaki MgmWeatherCodes ile aynı liste).
WET_CODES = {"HHY", "HY", "Y", "KY", "KKY", "HKY", "K", "YKY", "KYK", "HSY", "MSY", "SY", "KSY", "GSY", "KGY", "DY"}
WET_MM = 0.1


def archive_dir() -> Path | None:
    """Arşivin yerel kopyası: KARNE_MGM_ARCHIVE, design/karne/mgm-arsiv ya da deponun yanındaki zAhs_ForWeather."""
    for candidate in (config.MGM_ARCHIVE_DIR, config.REPO_ROOT.parent / "zAhs_ForWeather"):
        if (candidate / "data").exists():
            return candidate / "data"
    return None


def read_lines(path: Path) -> list[dict]:
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def _time(text: str) -> datetime:
    return datetime.fromisoformat(text[:19]).replace(tzinfo=timezone.utc)


def lead_days(run: datetime, valid: datetime) -> int:
    """Tahmin kaç gün önce verildi: 24–48 saat önce 1, 48–72 saat önce 2 …; 24 saatten yakınsa 0."""
    return int((valid - run).total_seconds() // 86400)


def load(data: Path) -> tuple[list[dict], dict[tuple[str, int], dict]]:
    forecasts = [line for path in sorted((data / "forecast").rglob("*.jsonl.gz")) for line in read_lines(path)]
    observed = {(line["gun"], line["id"]): line
                for path in sorted((data / "observed").rglob("*.jsonl.gz")) for line in read_lines(path)}
    return forecasts, observed


def score_daily(forecasts: list[dict], observed: dict, centers: list[dict], skip: set[int]) -> dict:
    """Günlük tahmin (en yüksek, en düşük, yağış) ile MGM'nin günlük ölçümü. Anahtar: (ölçüt, gün önce)."""
    station_of = {c["gunluk"]: c["sondurum"] for c in centers if c.get("gunluk") and c.get("sondurum")}
    errors: dict[tuple[str, int], np.ndarray] = defaultdict(lambda: np.zeros(3))
    tables: dict[int, np.ndarray] = defaultdict(lambda: np.zeros(4))
    seen = set()
    for line in forecasts:
        if line.get("k") != "g" or line["id"] in skip or line["id"] not in station_of:
            continue
        run_day = (_time(line["t"]) + timedelta(hours=config.TURKEY_UTC_OFFSET_HOURS)).date()
        for day in line["d"]:
            target = date.fromisoformat(day[0][:10])
            lead = (target - run_day).days
            truth = observed.get((target.isoformat(), station_of[line["id"]]))
            # Aynı gün içinde verilen birden çok tur aynı tahmini tekrar eder: günde bir kez say.
            key = (line["id"], target, lead)
            if truth is None or lead < 1 or lead > 5 or key in seen:
                continue
            seen.add(key)
            for name, forecast, measured in (("tmax", day[2], truth.get("max")), ("tmin", day[3], truth.get("min"))):
                if forecast is not None and measured is not None:
                    errors[(name, lead)] += np.array([1, abs(forecast - measured), forecast - measured])
            if truth.get("yagis") is not None and day[1]:
                said, wet = day[1].upper() in WET_CODES, truth["yagis"] >= WET_MM
                tables[lead] += np.array([said and wet, said and not wet, not said and wet, not said and not wet], dtype=float)
    return {"errors": errors, "tables": tables}


def score_hourly(forecasts: list[dict], pairs: dict[int, str], axis: np.ndarray) -> dict[int, np.ndarray]:
    """Saatlik sıcaklık tahmini ile METAR. [pairs]: MGM saatlik istasyonu -> havalimanı. Anahtar: gün önce."""
    origin = axis[0].astype(np.int64)
    observations = {airport: iem.load_hourly(airport, axis) for airport in set(pairs.values())}
    errors: dict[int, np.ndarray] = defaultdict(lambda: np.zeros(3))
    seen = set()
    for line in forecasts:
        if line.get("k") != "s" or line["id"] not in pairs:
            continue
        obs = observations.get(pairs[line["id"]])
        if obs is None:
            continue
        run = _time(line["t"])
        for step in line["f"]:
            if step[2] is None:
                continue
            valid = _time(step[0])
            lead = lead_days(run, valid)
            slot = int(np.datetime64(valid.strftime("%Y-%m-%dT%H"), "h").astype(np.int64) - origin)
            key = (line["id"], slot, lead)
            if lead < 0 or lead > 5 or not 0 <= slot < len(axis) or key in seen or np.isnan(obs["temp"][slot]):
                continue
            seen.add(key)
            error = step[2] - float(obs["temp"][slot])
            errors[lead] += np.array([1, abs(error), error])
    return errors


def airport_pairs(mgm_stations: list[dict], centers: list[dict], airports: list[dict]) -> dict[int, str]:
    """Saatlik tahmin istasyonu bir havalimanına 15 km'den yakın olanlar."""
    by_id = {s["id"]: s for s in mgm_stations}
    pairs = {}
    for station_id in {c["saatlik"] for c in centers if c.get("saatlik")}:
        station = by_id.get(station_id)
        if not station or station.get("lat") is None:
            continue
        best = min(airports, key=lambda a: regions.distance_km(station["lat"], station["lon"], a["lat"], a["lon"]))
        if regions.distance_km(station["lat"], station["lon"], best["lat"], best["lon"]) <= AIRPORT_KM:
            pairs[station_id] = best["id"]
    return pairs


def section(airports: list[dict]) -> str:
    data = archive_dir()
    if data is None:
        return ("**MGM:** arşivin yerel kopyası bulunamadı. Özel depoyu bu deponun yanına `zAhs_ForWeather` adıyla "
                "klonlayın ya da `KARNE_MGM_ARCHIVE` ile yerini verin.")
    forecasts, observed = load(data)
    days = sorted({line["t"][:10] for line in forecasts})
    centers = json.loads((data / "centers.json").read_text(encoding="utf-8"))["centers"] if (data / "centers.json").exists() else []
    lines = [f"**MGM arşivi:** {len(days)} gün ({days[0] if days else '–'} – {days[-1] if days else '–'}), "
             f"{len({l['id'] for l in forecasts if l.get('k') == 'g'})} merkez, "
             f"{len({k[0] for k in observed})} günün ölçümü."]
    if len(days) < MIN_DAYS:
        lines.append(f"Karşılaştırma için en az {MIN_DAYS} gün gerekiyor; {MIN_DAYS - len(days)} gün sonra "
                     "MGM bu rapora kendiliğinden girer. Aşağıdaki sayılar o zamana kadar yalnız ön bilgidir.")
    if not forecasts or not centers:
        return "\n".join(lines)

    mgm_stations = json.loads((data / "stations.json").read_text(encoding="utf-8")) if (data / "stations.json").exists() else []
    pairs = airport_pairs(mgm_stations, centers, airports)
    first = date.fromisoformat(days[0])
    last = date.fromisoformat(days[-1]) + timedelta(days=6)
    hourly = score_hourly(forecasts, pairs, iem.hourly_axis(first, last))
    near_airport = {c["gunluk"] for c in centers if c.get("saatlik") in pairs}
    daily = score_daily(forecasts, observed, centers, skip=set())

    if hourly:
        lines += ["", f"Saatlik sıcaklık, havalimanına {AIRPORT_KM:.0f} km içindeki {len(pairs)} tahmin istasyonu, METAR ile:", "",
                  "| Gün önce | Eş | Ortalama mutlak hata | Sapma |", "|---|---:|---:|---:|"]
        for lead in sorted(hourly):
            e = metrics.Error(*hourly[lead])
            lines.append(f"| {lead} | {int(e.n)} | {e.mae:.2f} | {e.bias:+.2f} |".replace(".", ","))
    if daily["errors"]:
        lines += ["", f"Günlük en yüksek/en düşük, MGM'nin günlük ölçümüyle ({len(near_airport)} merkez havalimanı yakınında):", "",
                  "| Gün önce | En yüksek: hata (sapma) | En düşük: hata (sapma) | Yağış: isabet / yanlış alarm / ETS |", "|---|---:|---:|---:|"]
        for lead in sorted({k[1] for k in daily["errors"]}):
            hi = metrics.Error(*daily["errors"].get(("tmax", lead), np.zeros(3)))
            lo = metrics.Error(*daily["errors"].get(("tmin", lead), np.zeros(3)))
            rain = metrics.Table(*daily["tables"].get(lead, np.zeros(4)))

            def cell(e):
                return "–" if not e.n else f"{e.mae:.2f} ({e.bias:+.2f})".replace(".", ",")

            def part(v):
                return "–" if v is None else f"{v:.2f}".replace(".", ",")
            lines.append(f"| {lead} | {cell(hi)} | {cell(lo)} | {part(rain.pod)} / {part(rain.far)} / {part(rain.ets)} |")
    if not hourly and not daily["errors"]:
        lines.append("Henüz hedef günü gelmiş ve ölçümü alınmış tahmin yok.")
    return "\n".join(lines)
