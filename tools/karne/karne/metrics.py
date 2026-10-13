"""Ölçütler: ortalama mutlak hata, sapma ve yağış var/yok tablosu (isabet, yanlış alarm, ETS)."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np


@dataclass
class Error:
    """Sürekli bir değişkenin hata toplamı."""
    n: float = 0.0
    abs_sum: float = 0.0
    sum: float = 0.0

    @property
    def mae(self) -> float | None:
        return self.abs_sum / self.n if self.n else None

    @property
    def bias(self) -> float | None:
        return self.sum / self.n if self.n else None


@dataclass
class Table:
    """Yağış var/yok tablosu: isabet, yanlış alarm, kaçırılan, doğru "yok"."""
    hits: float = 0.0
    false_alarms: float = 0.0
    misses: float = 0.0
    correct_negatives: float = 0.0

    @property
    def n(self) -> float:
        return self.hits + self.false_alarms + self.misses + self.correct_negatives

    @property
    def pod(self) -> float | None:
        """İsabet oranı: yağan saatlerin kaçı tahmin edildi."""
        events = self.hits + self.misses
        return self.hits / events if events else None

    @property
    def far(self) -> float | None:
        """Yanlış alarm oranı: "yağacak" denen saatlerin kaçı kuru geçti."""
        alarms = self.hits + self.false_alarms
        return self.false_alarms / alarms if alarms else None

    @property
    def frequency_bias(self) -> float | None:
        """1'den büyükse model gereğinden sık yağış diyor."""
        events = self.hits + self.misses
        return (self.hits + self.false_alarms) / events if events else None

    @property
    def ets(self) -> float | None:
        """Equitable Threat Score: şansla tutan isabetler düşülür; 0 beceri yok, 1 kusursuz."""
        total = self.n
        if not total:
            return None
        random_hits = (self.hits + self.misses) * (self.hits + self.false_alarms) / total
        denominator = self.hits + self.misses + self.false_alarms - random_hits
        return (self.hits - random_hits) / denominator if denominator else None


def contingency(forecast_wet: np.ndarray, observed_wet: np.ndarray) -> np.ndarray:
    """[isabet, yanlış alarm, kaçırılan, doğru yok]; iki dizi de mantıksal ve aynı maskeden geçmiş olmalı."""
    return np.array([
        np.count_nonzero(forecast_wet & observed_wet),
        np.count_nonzero(forecast_wet & ~observed_wet),
        np.count_nonzero(~forecast_wet & observed_wet),
        np.count_nonzero(~forecast_wet & ~observed_wet),
    ], dtype=np.float64)


def sun_is_up(axis: np.ndarray, lat: float, lon: float) -> np.ndarray:
    """Her UTC saat başında güneş ufkun üstünde mi? (gündüz/gece ayrımı için yeterli doğrulukta)."""
    day_of_year = (axis.astype("datetime64[D]") - axis.astype("datetime64[Y]")).astype(np.int64) + 1
    hour = (axis - axis.astype("datetime64[D]")).astype(np.int64).astype(np.float64)
    gamma = 2 * np.pi / 365 * (day_of_year - 1 + (hour - 12) / 24)
    declination = (0.006918 - 0.399912 * np.cos(gamma) + 0.070257 * np.sin(gamma) - 0.006758 * np.cos(2 * gamma)
                   + 0.000907 * np.sin(2 * gamma) - 0.002697 * np.cos(3 * gamma) + 0.00148 * np.sin(3 * gamma))
    equation = 229.18 * (0.000075 + 0.001868 * np.cos(gamma) - 0.032077 * np.sin(gamma)
                         - 0.014615 * np.cos(2 * gamma) - 0.040849 * np.sin(2 * gamma))
    solar_minutes = hour * 60 + equation + 4 * lon
    hour_angle = np.radians(solar_minutes / 4 - 180)
    phi = np.radians(lat)
    elevation = np.sin(phi) * np.sin(declination) + np.cos(phi) * np.cos(declination) * np.cos(hour_angle)
    return elevation > 0
