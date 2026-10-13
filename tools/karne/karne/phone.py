"""Telefonun debug sürümünden dışa aktarılan doğruluk kayıtlarını (CSV) okur.

Dosyalar design/karne/telefon/ altına konur. Sütunlar:
    type,place_id,lat,lon,source,kind,lead_hours,target_hour_utc,value,station_km
type F: bir kaynağın tahmini (kind H saatlik sıcaklık, X günün en yükseği, N en düşüğü).
type O: o saatte en yakın istasyonda ölçülen sıcaklık.
"""

from __future__ import annotations

import csv
from collections import defaultdict
from pathlib import Path

from . import config

COLUMNS = ["type", "place_id", "lat", "lon", "source", "kind", "lead_hours", "target_hour_utc", "value", "station_km"]


def read(folder: Path | None = None) -> tuple[list[dict], list[dict]]:
    """(tahminler, ölçümler). Aynı kayıt birden çok dosyada varsa bir kez sayılır."""
    folder = folder or config.PHONE_DIR
    forecasts: dict[tuple, dict] = {}
    observed: dict[tuple, dict] = {}
    if not folder.exists():
        return [], []
    for path in sorted(folder.glob("*.csv")):
        with open(path, encoding="utf-8-sig", newline="") as handle:
            for row in csv.DictReader(handle):
                try:
                    value = float(row["value"])
                except (KeyError, ValueError):
                    continue
                if row.get("type") == "F":
                    key = (row["place_id"], row["source"], row["kind"], row["target_hour_utc"], row["lead_hours"])
                    forecasts[key] = {"place": row["place_id"], "source": row["source"], "kind": row["kind"],
                                      "lead": int(row["lead_hours"]), "hour": row["target_hour_utc"], "value": value}
                elif row.get("type") == "O":
                    km = float(row["station_km"]) if row.get("station_km") else None
                    observed[(row["place_id"], row["target_hour_utc"])] = {
                        "place": row["place_id"], "hour": row["target_hour_utc"], "value": value, "km": km}
    return list(forecasts.values()), list(observed.values())


def summarize(forecasts: list[dict], observed: list[dict]) -> dict[tuple[str, str], list[float]]:
    """(kaynak, vade dilimi) -> [n, |hata| toplamı, hata toplamı]; vade dilimi "1-6 sa" ya da "24 sa"."""
    measured = {(o["place"], o["hour"]): o["value"] for o in observed}
    totals: dict[tuple[str, str], list[float]] = defaultdict(lambda: [0.0, 0.0, 0.0])
    for forecast in forecasts:
        if forecast["kind"] != "H":
            continue
        truth = measured.get((forecast["place"], forecast["hour"]))
        if truth is None:
            continue
        bucket = "24 sa" if forecast["lead"] == 24 else "1-6 sa" if 1 <= forecast["lead"] <= 6 else None
        if bucket is None:
            continue
        error = forecast["value"] - truth
        total = totals[(forecast["source"], bucket)]
        total[0] += 1
        total[1] += abs(error)
        total[2] += error
    return totals


def section() -> str:
    forecasts, observed = read()
    if not forecasts:
        return ("**Telefon kayıtları:** henüz yok. Debug sürümünde Ayarlar'ın en altındaki "
                "\"Doğruluk kayıtlarını dışa aktar\" ile alınan CSV'ler `design/karne/telefon/` altına konunca "
                "burada özetlenir.")
    totals = summarize(forecasts, observed)
    places = len({f["place"] for f in forecasts})
    lines = [f"**Telefon kayıtları:** {len(forecasts)} tahmin, {len(observed)} ölçüm, {places} yer.\n",
             "| Kaynak | Vade | Eş | Ortalama mutlak hata | Sapma |", "|---|---|---:|---:|---:|"]
    for (source, bucket), (n, abs_sum, total) in sorted(totals.items()):
        lines.append(f"| {source} | {bucket} | {int(n)} | {abs_sum / n:.2f} | {total / n:+.2f} |".replace(".", ","))
    if not totals:
        lines.append("| – | – | 0 | – | – |")
    return "\n".join(lines)
