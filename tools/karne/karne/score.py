"""Karne hesabı: her istasyonda tahminleri ölçümle eşler ve hataları gruplar hâlinde toplar."""

from __future__ import annotations

from collections import defaultdict
from datetime import date, timedelta

import numpy as np

from . import config, ensemble, iem, metrics, openmeteo

CONTINUOUS = {"temperature_2m": "temp", "dew_point_2m": "dew", "wind_speed_10m": "wind"}
# Günlük en yüksek/en düşük için bir günde en az bu kadar saat eşleşmeli.
MIN_HOURS_PER_DAY = 20
SEASON_NAMES = ["kis", "ilkbahar", "yaz", "sonbahar"]


class Scores:
    """Toplamlar. Anahtarlar düz demetlerdir; `pick` istenen alanlara göre toplar."""

    def __init__(self) -> None:
        # (değişken, model, gün, bölge, kıyı, mevsim, gündüz) -> [n, |hata| toplamı, hata toplamı]
        self.errors: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(3))
        # (tmax|tmin, model, gün, bölge, kıyı, mevsim) -> [n, |hata|, hata]
        self.daily: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(3))
        # (pencere saat, eşik, model, gün, bölge, kıyı, mevsim, gündüz) -> [isabet, yanlış alarm, kaçırılan, doğru yok]
        self.rain: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(4))
        # (yöntem ya da model, gün, bölge, kıyı) -> [n, |hata|, hata]; hepsi aynı saatlerde
        self.blend: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(3))
        # (en az kaç model yağış diyor, gün) -> yağış tablosu
        self.vote: dict[tuple, np.ndarray] = defaultdict(lambda: np.zeros(4))
        self.models: dict[tuple, list[str]] = {}
        self.stations_used = 0
        self.first_hour = None
        self.last_hour = None

    @staticmethod
    def pick(table: dict[tuple, np.ndarray], *wanted) -> np.ndarray:
        """Anahtarı `wanted` ile eşleşen girdilerin toplamı; None "hepsi" demektir."""
        total = None
        for key, value in table.items():
            if all(w is None or w == k for w, k in zip(wanted, key)):
                total = value.copy() if total is None else total + value
        if total is None:
            total = np.zeros(next(iter(table.values())).shape if table else 3)
        return total

    def error(self, *wanted) -> metrics.Error:
        return metrics.Error(*self.pick(self.errors, *wanted))

    def day(self, *wanted) -> metrics.Error:
        return metrics.Error(*self.pick(self.daily, *wanted))

    def table(self, *wanted) -> metrics.Table:
        return metrics.Table(*self.pick(self.rain, *wanted))

    def blended(self, *wanted) -> metrics.Error:
        return metrics.Error(*self.pick(self.blend, *wanted))

    def voted(self, *wanted) -> metrics.Table:
        return metrics.Table(*self.pick(self.vote, *wanted))


def forecast_range() -> tuple[date, date] | None:
    index = openmeteo.load_index()
    if not index:
        return None
    start = min(date.fromisoformat(entry["start"]) for entry in index.values())
    end = max(date.fromisoformat(entry["end"]) for entry in index.values())
    return start, end


def _grouped(codes: np.ndarray, mask: np.ndarray, error: np.ndarray, groups: int) -> np.ndarray:
    """Her grup için [n, |hata|, hata]."""
    c = codes[mask]
    e = error[mask].astype(np.float64)
    return np.stack([np.bincount(c, minlength=groups), np.bincount(c, np.abs(e), groups),
                     np.bincount(c, e, groups)], axis=1)


def compute(stations: list[dict], log=print) -> Scores:
    scores = Scores()
    span = forecast_range()
    if span is None:
        return scores
    axis = iem.hourly_axis(span[0], span[1])
    months = axis.astype("datetime64[M]").astype(np.int64) % 12 + 1
    # Mevsim yerel tarihe göre; Türkiye'de fark yalnız ay başlarındaki 3 saatte olur.
    local_axis = axis + np.timedelta64(config.TURKEY_UTC_OFFSET_HOURS, "h")
    local_months = local_axis.astype("datetime64[M]").astype(np.int64) % 12 + 1
    season_code = np.array([SEASON_NAMES.index(config.SEASONS[int(m)]) for m in local_months])
    del months

    for station in stations:
        observed = iem.load_hourly(station["id"], axis)
        if observed is None:
            continue
        forecasts = openmeteo.load_station(station["id"], axis)
        if not forecasts:
            continue
        scores.stations_used += 1
        region, coastal = station["region"], bool(station["coastal"])
        daylight = metrics.sun_is_up(axis, station["lat"], station["lon"])
        codes = season_code * 2 + daylight.astype(np.int64)

        for lead in config.LEADS:
            for variable, obs_name in CONTINUOUS.items():
                series = {m: forecasts[(variable, m, lead)] for m in config.MODELS if (variable, m, lead) in forecasts}
                series = {m: s for m, s in series.items() if np.isfinite(s).any()}
                if not series:
                    continue
                obs = observed[obs_name]
                # Adil karşılaştırma: yalnız bütün modellerin ve ölçümün bulunduğu saatler.
                mask = np.isfinite(obs)
                for s in series.values():
                    mask &= np.isfinite(s)
                if not mask.any():
                    continue
                scores.models[(variable, lead)] = sorted(set(scores.models.get((variable, lead), [])) | set(series))
                _note_span(scores, axis, mask)
                for model, s in series.items():
                    grouped = _grouped(codes, mask, s - obs, 8)
                    for code in range(8):
                        if grouped[code, 0]:
                            scores.errors[(variable, model, lead, region, coastal, SEASON_NAMES[code // 2], bool(code % 2))] += grouped[code]
                if variable == "temperature_2m":
                    _daily_extremes(scores, series, obs, mask, season_code, lead, region, coastal)
                    _blends(scores, series, obs, lead, region, coastal)
            _rain(scores, forecasts, observed["rain"], codes, lead, region, coastal)
        log(f"  {station['id']} {station['name']}")
    return scores


def _note_span(scores: Scores, axis: np.ndarray, mask: np.ndarray) -> None:
    hours = axis[mask]
    scores.first_hour = hours[0] if scores.first_hour is None else min(scores.first_hour, hours[0])
    scores.last_hour = hours[-1] if scores.last_hour is None else max(scores.last_hour, hours[-1])


def _local_days(series: np.ndarray) -> np.ndarray:
    """Saatlik diziyi Türkiye takvim günlerine böler: (gün, 24). Yerel gece yarısı 21:00 UTC'dir."""
    start = 24 - config.TURKEY_UTC_OFFSET_HOURS
    usable = (len(series) - start) // 24 * 24
    return series[start:start + usable].reshape(-1, 24)


def _daily_extremes(scores, series, obs, mask, season_code, lead, region, coastal) -> None:
    day_mask = _local_days(mask)
    enough = day_mask.sum(axis=1) >= MIN_HOURS_PER_DAY
    if not enough.any():
        return
    seasons = _local_days(season_code)[:, 12]
    obs_days = np.where(day_mask, _local_days(obs), np.nan)
    with np.errstate(all="ignore"):
        obs_max, obs_min = np.nanmax(obs_days[enough], axis=1), np.nanmin(obs_days[enough], axis=1)
        for model, s in series.items():
            days = np.where(day_mask, _local_days(s), np.nan)[enough]
            for name, error in (("tmax", np.nanmax(days, axis=1) - obs_max), ("tmin", np.nanmin(days, axis=1) - obs_min)):
                grouped = _grouped(seasons[enough], np.ones(len(error), dtype=bool), error, 4)
                for code in range(4):
                    if grouped[code, 0]:
                        scores.daily[(name, model, lead, region, coastal, SEASON_NAMES[code])] += grouped[code]


def _blends(scores, series, obs, lead, region, coastal) -> None:
    if len(series) < 2:
        return
    usable = len(obs) // 24 * 24
    cut = {m: s[:usable].astype(np.float64) for m, s in series.items()}
    target = obs[:usable].astype(np.float64)
    blended, mask = ensemble.blends(cut, target, lead)
    if not mask.any():
        return
    for name, values in {**cut, **blended}.items():
        error = (values - target)[mask]
        scores.blend[(name, lead, region, coastal)] += np.array([len(error), np.abs(error).sum(), error.sum()])


def _rain(scores, forecasts, obs_rain, codes, lead, region, coastal) -> None:
    series = {m: forecasts[("precipitation", m, lead)] for m in config.MODELS if ("precipitation", m, lead) in forecasts}
    series = {m: s for m, s in series.items() if np.isfinite(s).any()}
    if not series:
        return
    mask = np.isfinite(obs_rain)
    for s in series.values():
        mask &= np.isfinite(s)
    if not mask.any():
        return
    scores.models[("precipitation", lead)] = sorted(set(scores.models.get(("precipitation", lead), [])) | set(series))
    wet = obs_rain > 0.5

    # Saatlik: her eşik için.
    for threshold in config.RAIN_THRESHOLDS:
        for model, s in series.items():
            said = s >= threshold - 1e-6
            for code in np.unique(codes[mask]):
                group = mask & (codes == code)
                scores.rain[(1, threshold, model, lead, region, coastal, SEASON_NAMES[code // 2], bool(code % 2))] += \
                    metrics.contingency(said[group], wet[group])

    # Kaç model "yağış var" diyor? (eşik 0,1 mm)
    votes = np.sum([s >= config.RAIN_THRESHOLD - 1e-6 for s in series.values()], axis=0)
    for needed in range(1, len(series) + 1):
        scores.vote[(needed, lead, len(series))] += metrics.contingency((votes >= needed)[mask], wet[mask])

    # 3 saatlik pencere: (0,3], (3,6] … Pencerede yağış bildirilen saat varsa "var"; toplam ≥ eşikse tahmin "var".
    usable = (len(obs_rain) - 1) // 3 * 3
    window_mask = mask[1:1 + usable].reshape(-1, 3).all(axis=1)
    if not window_mask.any():
        return
    window_wet = wet[1:1 + usable].reshape(-1, 3).any(axis=1)
    window_codes = codes[1:1 + usable].reshape(-1, 3)[:, 2]
    for model, s in series.items():
        said = np.nansum(s[1:1 + usable].reshape(-1, 3), axis=1) >= config.RAIN_THRESHOLD - 1e-6
        for code in np.unique(window_codes[window_mask]):
            group = window_mask & (window_codes == code)
            scores.rain[(3, config.RAIN_THRESHOLD, model, lead, region, coastal, SEASON_NAMES[code // 2], bool(code % 2))] += \
                metrics.contingency(said[group], window_wet[group])
