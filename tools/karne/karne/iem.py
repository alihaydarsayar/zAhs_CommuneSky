"""Gözlem: Iowa Environmental Mesonet'in (IEM) Türkiye METAR arşivi.

IEM ücretsiz bir üniversite servisi: istasyon başına tek istek atılır ve istekler arasında beklenir.
"""

from __future__ import annotations

import csv
import gzip
import io
import json
import time
from datetime import date, datetime, timedelta, timezone

import numpy as np
import requests

from . import config, regions

NETWORK_URL = "https://mesonet.agron.iastate.edu/geojson/network/{network}.geojson"
DATA_URL = "https://mesonet.agron.iastate.edu/cgi-bin/request/asos.py"
FIELDS = ["tmpc", "dwpc", "sknt", "gust", "mslp", "alti", "wxcodes"]

# İki istasyon isteği arasındaki bekleme (saniye).
PAUSE_SECONDS = 6.0

KNOT_TO_MS = 0.514444
INHG_TO_HPA = 33.8639

# Yağış sayılan hava grupları; VC (çevrede) ve RE (az önce bitti) ile başlayanlar sayılmaz.
RAIN_CODES = ("RA", "SN", "DZ", "SH", "TS", "GR", "PL")
# Savrulan/sürüklenen kar (BLSN, DRSN) yağış değildir.
NOT_FALLING = ("BL", "DR")


def is_precipitation_group(group: str) -> bool:
    """Tek bir METAR hava grubu (ör. "-SHRA", "VCSH", "RERA") o an yağış bildiriyor mu?"""
    code = group.strip().upper().lstrip("+-")
    if not code or code.startswith(("VC", "RE")) or code.startswith(NOT_FALLING):
        return False
    return any(token in code for token in RAIN_CODES)


def is_precipitation(wxcodes: str) -> bool:
    return any(is_precipitation_group(group) for group in wxcodes.split())


def fetch_stations() -> list[dict]:
    """Arşivi hâlâ süren istasyonlar; il, bölge ve kıyı/iç bilgisiyle."""
    response = requests.get(NETWORK_URL.format(network=config.IEM_NETWORK),
                            headers={"User-Agent": config.USER_AGENT}, timeout=60)
    response.raise_for_status()
    mgm = regions.mgm_stations()
    coast = regions.coast_segments()
    stations = []
    for feature in response.json()["features"]:
        props = feature["properties"]
        if props.get("archive_end") is not None or not props.get("online", True):
            continue
        lon, lat = feature["geometry"]["coordinates"]
        province, province_km = regions.nearest_province(lat, lon, mgm)
        coast_km = regions.coast_distance_km(lat, lon, coast)
        stations.append({
            "id": props["sid"],
            "name": props["sname"],
            "lat": round(lat, 4),
            "lon": round(lon, 4),
            "elevation": round(float(props["elevation"])),
            "archive_begin": props.get("archive_begin"),
            "province": regions.province_name(province),
            "region": regions.region_of(province),
            "coast_km": round(coast_km, 1),
            "coastal": coast_km <= regions.COAST_KM,
        })
    stations.sort(key=lambda s: s["id"])
    config.STATIONS_FILE.write_text(json.dumps(stations, ensure_ascii=False, indent=1), encoding="utf-8")
    return stations


def load_stations() -> list[dict]:
    if not config.STATIONS_FILE.exists():
        return fetch_stations()
    return json.loads(config.STATIONS_FILE.read_text(encoding="utf-8"))


def _raw_path(station_id: str):
    return config.IEM_DIR / f"{station_id}.csv.gz"


def _meta_path(station_id: str):
    return config.IEM_DIR / f"{station_id}.meta.json"


def _request_csv(station_id: str, start: date, end: date) -> str:
    params = [("station", station_id)] + [("data", field) for field in FIELDS] + [
        ("year1", start.year), ("month1", start.month), ("day1", start.day),
        ("year2", end.year), ("month2", end.month), ("day2", end.day),
        ("tz", "Etc/UTC"), ("format", "onlycomma"), ("latlon", "no"), ("elev", "no"),
        ("missing", "M"), ("trace", "T"), ("direct", "no"), ("report_type", "3"), ("report_type", "4"),
    ]
    for attempt in range(3):
        response = requests.get(DATA_URL, params=params, headers={"User-Agent": config.USER_AGENT}, timeout=300)
        if response.status_code == 200 and response.text.startswith("station,"):
            return response.text
        # IEM yoğunken 429/503 döner: uzun bekle, sonra bir kez daha dene.
        time.sleep(30 * (attempt + 1))
    raise RuntimeError(f"IEM {station_id}: {response.status_code} {response.text[:200]}")


def download(stations: list[dict], log=print) -> None:
    """Her istasyonun ham METAR'larını indirir; daha önce inen kısım yeniden istenmez."""
    # IEM'de bitiş günü dahil değildir: yarın verilirse bugün de gelir.
    end = datetime.now(timezone.utc).date() + timedelta(days=1)
    first = True
    for station in stations:
        sid = station["id"]
        meta = json.loads(_meta_path(sid).read_text()) if _meta_path(sid).exists() else {}
        through = date.fromisoformat(meta["through"]) if meta.get("through") else None
        if through and through >= end - timedelta(days=1):
            continue
        start = through - timedelta(days=1) if through else config.START_DATE
        if not first:
            time.sleep(PAUSE_SECONDS)
        first = False
        text = _request_csv(sid, start, end)
        rows: dict[str, str] = {}
        header = "station,valid," + ",".join(FIELDS)
        if _raw_path(sid).exists():
            with gzip.open(_raw_path(sid), "rt", encoding="utf-8") as handle:
                header = handle.readline().strip()
                for line in handle:
                    rows[line.split(",", 2)[1]] = line.rstrip("\n")
        lines = text.splitlines()
        header = lines[0]
        for line in lines[1:]:
            if line:
                rows[line.split(",", 2)[1]] = line
        with gzip.open(_raw_path(sid), "wt", encoding="utf-8") as handle:
            handle.write(header + "\n")
            for key in sorted(rows):
                handle.write(rows[key] + "\n")
        _meta_path(sid).write_text(json.dumps({"through": (end - timedelta(days=1)).isoformat(), "rows": len(rows)}))
        log(f"IEM {sid}: {len(rows)} METAR")


def _number(text: str) -> float:
    if text in ("M", "", "T"):
        return np.nan
    try:
        return float(text)
    except ValueError:
        return np.nan


def hourly_axis(start: date, end: date) -> np.ndarray:
    """UTC saat başları, [start 00:00, end 23:00]."""
    return np.arange(np.datetime64(start.isoformat(), "h"), np.datetime64(end.isoformat(), "h") + np.timedelta64(24, "h"))


def reduce_to_hourly(lines, axis: np.ndarray) -> dict[str, np.ndarray]:
    """METAR'ları saat başlarına indirger.

    - Anlık değerler (sıcaklık, çiy noktası, rüzgâr, hamle, basınç): saat başına en yakın rapor, en çok ±30 dk.
    - Yağış: S saat damgası, (S−1 sa, S] aralığındaki raporların herhangi birinde yağış grubu varsa 1.
      Open-Meteo'nun saatlik yağışı da bir önceki saatin toplamıdır; ikisi aynı aralığı anlatır.
      O aralıkta hiç rapor yoksa değer bilinmiyor (NaN) sayılır, "kuru" sayılmaz.
    """
    count = len(axis)
    origin = axis[0].astype("datetime64[m]").astype(np.int64)
    out = {name: np.full(count, np.nan, dtype=np.float32) for name in ("temp", "dew", "wind", "gust", "pressure")}
    nearest = np.full(count, 31, dtype=np.int32)
    reports = np.zeros(count, dtype=np.int32)
    wet = np.zeros(count, dtype=np.int32)

    reader = csv.reader(lines)
    header = next(reader)
    col = {name: index for index, name in enumerate(header)}
    for row in reader:
        if len(row) < len(header):
            continue
        try:
            minutes = np.datetime64(row[col["valid"]].replace(" ", "T"), "m").astype(np.int64) - origin
        except ValueError:
            continue
        # Yağış aralığı: raporun düştüğü (S−1, S] diliminin S'si.
        bucket = int(-(-minutes // 60))
        if 0 <= bucket < count:
            reports[bucket] += 1
            if is_precipitation(row[col["wxcodes"]]) if row[col["wxcodes"]] != "M" else False:
                wet[bucket] += 1
        # Anlık değer: en yakın saat başı.
        slot = int((minutes + 30) // 60)
        offset = abs(int(minutes - slot * 60))
        if 0 <= slot < count and offset <= 30 and offset < nearest[slot]:
            temp = _number(row[col["tmpc"]])
            if np.isnan(temp):
                continue
            nearest[slot] = offset
            out["temp"][slot] = temp
            out["dew"][slot] = _number(row[col["dwpc"]])
            out["wind"][slot] = _number(row[col["sknt"]]) * KNOT_TO_MS
            out["gust"][slot] = _number(row[col["gust"]]) * KNOT_TO_MS
            pressure = _number(row[col["mslp"]])
            if np.isnan(pressure):
                pressure = _number(row[col["alti"]]) * INHG_TO_HPA
            out["pressure"][slot] = pressure
    rain = np.where(reports > 0, (wet > 0).astype(np.float32), np.nan).astype(np.float32)
    out["rain"] = rain
    # Akla yatmayan değerler (bozuk rapor) atılır.
    out["temp"][(out["temp"] < -50) | (out["temp"] > 55)] = np.nan
    out["dew"][(out["dew"] < -60) | (out["dew"] > 40)] = np.nan
    out["wind"][(out["wind"] < 0) | (out["wind"] > 60)] = np.nan
    return out


def load_hourly(station_id: str, axis: np.ndarray) -> dict[str, np.ndarray] | None:
    path = _raw_path(station_id)
    if not path.exists():
        return None
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        return reduce_to_hourly(io.StringIO(handle.read()), axis)
