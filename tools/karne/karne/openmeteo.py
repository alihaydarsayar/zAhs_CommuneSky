"""Tahmin: Open-Meteo Previous Runs API (modellerin 1–5 gün önce verdiği tahminler).

Ücretsiz sınır günde 10.000 çağrıdır ve büyük istekler birden çok çağrı sayılır
(10'dan fazla değişken, 14 günden uzun aralık ve her ek konum çarpan olur).
Bu yüzden iş parçalara bölünür, ham cevaplar diske yazılır ve günlük bütçe dolunca durulur;
ertesi gün aynı komut kaldığı yerden sürer.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import time
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone

import numpy as np
import requests

from . import config

URL = "https://previous-runs-api.open-meteo.com/v1/forecast"

# Kendi koyduğumuz günlük tavan; telefonun aynı ağdan yaptığı isteklere de pay kalsın.
DAILY_BUDGET = 8000
# Saatlik sınır 5.000, dakikalık 600: istekler bu hıza göre aralanır.
HOURLY_PACE = 4500
WINDOW_DAYS = 28
BATCH_SIZE = 6
# Son günlerin eski tahminleri henüz arşive girmemiş olabilir.
LAG_DAYS = 2


@dataclass(frozen=True)
class Phase:
    name: str
    variables: tuple[str, ...]
    leads: tuple[int, ...]
    start: date
    end: date | None  # None: bugüne kadar


def phases(today: date) -> list[Phase]:
    """Öncelik sırası: önce son 12 ayın sıcaklık ve yağışı, sonra kalan vadeler, değişkenler ve eski yıllar."""
    year_ago = today - timedelta(days=365)
    older_end = year_ago - timedelta(days=1)
    core = ("temperature_2m", "precipitation")
    rest = ("dew_point_2m", "wind_speed_10m", "weather_code")
    return [
        Phase("1-son12ay-sicaklik-yagis-gun1-3", core, (1, 2, 3), year_ago, None),
        Phase("2-son12ay-sicaklik-yagis-gun4-5", core, (4, 5), year_ago, None),
        Phase("3-son12ay-ciy-ruzgar-kod", rest, (1, 2, 3, 4, 5), year_ago, None),
        Phase("4-eski-sicaklik-yagis", core, (1, 2, 3, 4, 5), config.START_DATE, older_end),
        Phase("5-eski-ciy-ruzgar-kod", rest, (1, 2, 3, 4, 5), config.START_DATE, older_end),
    ]


@dataclass(frozen=True)
class Chunk:
    phase: str
    variables: tuple[str, ...]
    leads: tuple[int, ...]
    start: date
    end: date
    stations: tuple[str, ...]

    @property
    def hourly(self) -> list[str]:
        return [f"{variable}_previous_day{lead}" for variable in self.variables for lead in self.leads]

    @property
    def weight(self) -> float:
        days = (self.end - self.start).days + 1
        fields = len(self.hourly) * len(config.MODELS)
        return len(self.stations) * max(1.0, fields / 10) * max(1.0, days / 14)

    @property
    def key(self) -> str:
        text = "|".join([",".join(self.hourly), self.start.isoformat(), ",".join(self.stations),
                         ",".join(config.MODELS)])
        return f"{self.phase}_{self.start.isoformat()}_{hashlib.sha1(text.encode()).hexdigest()[:10]}"


def windows(start: date, end: date) -> list[tuple[date, date]]:
    """Sabit bir başlangıca hizalı 28 günlük pencereler; hangi gün çalıştırılırsa çalıştırılsın aynı kalır."""
    out = []
    index = (start - config.START_DATE).days // WINDOW_DAYS
    while True:
        w_start = config.START_DATE + timedelta(days=index * WINDOW_DAYS)
        if w_start > end:
            break
        out.append((max(w_start, start), min(w_start + timedelta(days=WINDOW_DAYS - 1), end)))
        index += 1
    return out


def plan(stations: list[dict], today: date) -> list[Chunk]:
    ids = [station["id"] for station in stations]
    batches = [tuple(ids[i:i + BATCH_SIZE]) for i in range(0, len(ids), BATCH_SIZE)]
    last_day = today - timedelta(days=LAG_DAYS)
    chunks = []
    for phase in phases(today):
        end = min(phase.end or last_day, last_day)
        # Yeni pencereler önce: rapor için en değerli veri en güncel olanıdır.
        for w_start, w_end in reversed(windows(phase.start, end)):
            for batch in batches:
                chunks.append(Chunk(phase.name, phase.variables, phase.leads, w_start, w_end, batch))
    return chunks


def _index_path():
    return config.OPENMETEO_DIR / "index.json"


def load_index() -> dict:
    path = _index_path()
    if not path.exists():
        return {}
    # İndirme sürerken rapor da çalışabilir: dosya tam o an yazılıyorsa kısa bir süre sonra yeniden okunur.
    for _ in range(5):
        try:
            return json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError):
            time.sleep(0.5)
    return json.loads(path.read_text(encoding="utf-8"))


def _save_index(index: dict) -> None:
    temporary = _index_path().with_suffix(".tmp")
    temporary.write_text(json.dumps(index, indent=0), encoding="utf-8")
    temporary.replace(_index_path())


def _budget_path():
    return config.OPENMETEO_DIR / "budget.json"


def _load_budget() -> dict:
    path = _budget_path()
    return json.loads(path.read_text()) if path.exists() else {}


def _is_done(chunk: Chunk, index: dict) -> bool:
    """Pencere tam inmişse ya da yarım pencerenin eldeki hâli bir haftadan yeniyse yeniden istenmez."""
    entry = index.get(chunk.key)
    if not entry:
        return False
    return date.fromisoformat(entry["end"]) >= chunk.end - timedelta(days=7)


def fetch(stations: list[dict], budget: float = DAILY_BUDGET, log=print) -> dict:
    """Plandaki eksik parçaları sırayla indirir. Günlük bütçe dolunca ya da servis sınır bildirince durur."""
    by_id = {station["id"]: station for station in stations}
    today = datetime.now(timezone.utc).date()
    chunks = plan(stations, today)
    repair(log)
    index = load_index()
    spent_by_day = _load_budget()
    day_key = today.isoformat()
    spent = float(spent_by_day.get(day_key, 0.0))
    todo = [chunk for chunk in chunks if not _is_done(chunk, index)]
    log(f"Open-Meteo: {len(chunks) - len(todo)}/{len(chunks)} parça hazır; bugün harcanan {spent:.0f}/{budget:.0f} çağrı")
    session = requests.Session()
    session.headers["User-Agent"] = config.USER_AGENT
    stopped = "bitti"
    for chunk in todo:
        if spent + chunk.weight > budget:
            stopped = "günlük bütçe doldu"
            break
        params = {
            "latitude": ",".join(str(by_id[sid]["lat"]) for sid in chunk.stations),
            "longitude": ",".join(str(by_id[sid]["lon"]) for sid in chunk.stations),
            "elevation": ",".join(str(by_id[sid]["elevation"]) for sid in chunk.stations),
            "hourly": ",".join(chunk.hourly),
            "models": ",".join(config.MODELS),
            "start_date": chunk.start.isoformat(),
            "end_date": chunk.end.isoformat(),
            "timezone": "GMT",
            "wind_speed_unit": "ms",
        }
        started = time.time()
        text, problem = _get(session, params, log)
        if text is None:
            stopped = problem
            break
        (config.OPENMETEO_DIR / f"{chunk.key}.json.gz").write_bytes(gzip.compress(text.encode("utf-8")))
        index[chunk.key] = {"phase": chunk.phase, "start": chunk.start.isoformat(), "end": chunk.end.isoformat(),
                            "stations": list(chunk.stations), "hourly": chunk.hourly}
        _save_index(index)
        spent += chunk.weight
        spent_by_day[day_key] = round(spent, 1)
        _budget_path().write_text(json.dumps(spent_by_day))
        log(f"  {chunk.phase} {chunk.start}..{chunk.end} {chunk.stations[0]}–{chunk.stations[-1]} "
            f"(+{chunk.weight:.0f}, toplam {spent:.0f})")
        # Saatlik sınırın altında kalacak kadar bekle.
        time.sleep(max(0.0, chunk.weight / HOURLY_PACE * 3600 - (time.time() - started)))
    remaining = sum(1 for chunk in chunks if not _is_done(chunk, index))
    remaining_weight = sum(chunk.weight for chunk in chunks if not _is_done(chunk, index))
    log(f"Open-Meteo: {stopped}; kalan {remaining} parça (~{remaining_weight:.0f} çağrı, "
        f"~{remaining_weight / budget:.1f} gün)")
    return {"stopped": stopped, "remaining_chunks": remaining, "remaining_calls": round(remaining_weight)}


def _is_forecast(text: str) -> bool:
    try:
        payload = json.loads(text)
    except ValueError:
        return False
    first = payload[0] if isinstance(payload, list) and payload else payload
    return isinstance(first, dict) and "hourly" in first


def repair(log=print) -> int:
    """Bozuk inmiş parçaları (yarım cevap) dizinden ve diskten siler; sonraki indirmede yeniden istenir."""
    index = load_index()
    broken = []
    for key in index:
        path = config.OPENMETEO_DIR / f"{key}.json.gz"
        try:
            ok = path.exists() and _is_forecast(gzip.decompress(path.read_bytes()).decode("utf-8"))
        except (OSError, ValueError):
            ok = False
        if not ok:
            broken.append(key)
    for key in broken:
        index.pop(key)
        (config.OPENMETEO_DIR / f"{key}.json.gz").unlink(missing_ok=True)
    if broken:
        _save_index(index)
        log(f"Open-Meteo: {len(broken)} bozuk parça silindi, yeniden istenecek")
    return len(broken)


def _get(session: requests.Session, params: dict, log) -> tuple[str | None, str]:
    waits = 0
    for attempt in range(4):
        try:
            response = session.get(URL, params=params, timeout=180)
        except requests.RequestException as error:
            log(f"  bağlantı hatası: {error}")
            time.sleep(20 * (attempt + 1))
            continue
        if response.status_code == 200:
            if _is_forecast(response.text):
                return response.text, ""
            # Servis bazen 200 ile birlikte yarım bir cevap ya da hata metni döndürür: yeniden dene.
            log(f"  eksik cevap: {response.text[:80]!r}")
            time.sleep(20 * (attempt + 1))
            continue
        reason = response.text[:200]
        if response.status_code == 429:
            lowered = reason.lower()
            if "daily" in lowered:
                return None, "servis günlük sınırı bildirdi"
            # Dakikalık ya da saatlik sınır: bekleyip bir kez daha dene.
            pause = 3660 if "hourly" in lowered else 65
            waits += 1
            if waits > 2:
                return None, f"servis sınır bildirdi: {reason}"
            log(f"  sınır ({reason.strip()}); {pause} sn bekleniyor")
            time.sleep(pause)
            continue
        if response.status_code >= 500:
            time.sleep(30 * (attempt + 1))
            continue
        return None, f"HTTP {response.status_code}: {reason}"
    return None, "servis cevap vermedi"


def coverage() -> dict:
    """Rapor için: hangi aşamadan ne kadar veri indi."""
    out: dict[str, dict] = {}
    for entry in load_index().values():
        phase = out.setdefault(entry["phase"], {"chunks": 0, "start": entry["start"], "end": entry["end"],
                                                "stations": set()})
        phase["chunks"] += 1
        phase["start"] = min(phase["start"], entry["start"])
        phase["end"] = max(phase["end"], entry["end"])
        phase["stations"].update(entry["stations"])
    for phase in out.values():
        phase["stations"] = len(phase["stations"])
    return out


def load_station(station_id: str, axis: np.ndarray) -> dict[tuple[str, str, int], np.ndarray]:
    """Bir istasyonun inmiş bütün tahminleri: (değişken, model, kaç gün önce) -> saatlik dizi."""
    origin = axis[0].astype(np.int64)
    count = len(axis)
    out: dict[tuple[str, str, int], np.ndarray] = {}
    for key, entry in load_index().items():
        if station_id not in entry["stations"]:
            continue
        path = config.OPENMETEO_DIR / f"{key}.json.gz"
        if not path.exists():
            continue
        try:
            payload = json.loads(gzip.decompress(path.read_bytes()))
        except (OSError, ValueError):
            # Bozuk parça: `forecasts` komutu bir sonraki çalışmada yeniden indirir.
            continue
        if isinstance(payload, dict):
            payload = [payload]
        hourly = payload[entry["stations"].index(station_id)]["hourly"]
        slots = np.array(hourly["time"], dtype="datetime64[h]").astype(np.int64) - origin
        inside = (slots >= 0) & (slots < count)
        for name in entry["hourly"]:
            variable, lead = name.rsplit("_previous_day", 1)
            for model in config.MODELS:
                values = hourly.get(f"{name}_{model}")
                if values is None:
                    continue
                series = out.get((variable, model, int(lead)))
                if series is None:
                    series = out[(variable, model, int(lead))] = np.full(count, np.nan, dtype=np.float32)
                data = np.array([np.nan if v is None else v for v in values], dtype=np.float32)
                series[slots[inside]] = data[inside]
    return out
