"""Ortak tahmin denemeleri. Hepsi nedenseldir: bir saatin tahmini kurulurken yalnız o tahminin
verildiği andan önce ölçülmüş hatalar kullanılır (geleceği görmez).

N gün önce verilen tahmin için "bilinen geçmiş", hedef saatten N gün öncesine kadar olan saatlerdir.
"""

from __future__ import annotations

import numpy as np

WINDOW_DAYS = 30
# Bir pencerede bu kadar günden az veri varsa düzeltme ya da ağırlık uygulanmaz.
MIN_DAYS = 7
# Kalman süzgecinde süreç gürültüsünün ölçüm gürültüsüne oranı: küçükse sapma yavaş, büyükse hızlı izlenir.
KALMAN_RATIO = 0.06

METHODS = ["duz_ortalama", "agirlikli_ortalama", "sapma_duzeltme", "kalman", "duzeltilmis_agirlikli"]
METHOD_LABELS = {
    "duz_ortalama": "Düz ortalama",
    "agirlikli_ortalama": "Son 30 günün hatasına göre ağırlıklı ortalama",
    "sapma_duzeltme": "30 günlük sapma düzeltmesi (en iyi tek model)",
    "kalman": "Kalman sapma süzgeci (en iyi tek model)",
    "duzeltilmis_agirlikli": "Düzeltilmiş modellerin ağırlıklı ortalaması",
}


def _by_day(series: np.ndarray) -> np.ndarray:
    """Saatlik diziyi (gün, 24) biçimine getirir; eksen UTC gece yarısında başlamalı."""
    return series.reshape(-1, 24)


def rolling_mean_by_hour(errors: np.ndarray, lead: int, absolute: bool = False) -> tuple[np.ndarray, np.ndarray]:
    """Her saat için, aynı günün saatinde son 30 günün ortalama hatası (ve kaç günden hesaplandığı).

    G günündeki tahmin için pencere G−lead−30 … G−lead−1 günleridir.
    """
    days = _by_day(np.abs(errors) if absolute else errors)
    valid = ~np.isnan(days)
    sums = np.cumsum(np.where(valid, days, 0.0), axis=0)
    counts = np.cumsum(valid, axis=0)
    sums = np.vstack([np.zeros((1, 24)), sums])
    counts = np.vstack([np.zeros((1, 24)), counts])
    n_days = days.shape[0]
    index = np.arange(n_days)
    hi = np.clip(index - lead, 0, n_days)              # G−lead−1 dahil
    lo = np.clip(index - lead - WINDOW_DAYS, 0, n_days)
    total = sums[hi] - sums[lo]
    count = counts[hi] - counts[lo]
    with np.errstate(invalid="ignore", divide="ignore"):
        mean = np.where(count > 0, total / count, np.nan)
    return mean.reshape(-1), count.reshape(-1)


def rolling_mae(errors: np.ndarray, lead: int) -> tuple[np.ndarray, np.ndarray]:
    """Son 30 günün bütün saatlerindeki ortalama mutlak hata (günün saatine bakmadan)."""
    days = _by_day(np.abs(errors))
    valid = ~np.isnan(days)
    day_sum = np.where(valid, days, 0.0).sum(axis=1)
    day_count = valid.sum(axis=1)
    sums = np.concatenate([[0.0], np.cumsum(day_sum)])
    counts = np.concatenate([[0.0], np.cumsum(day_count)])
    n_days = days.shape[0]
    index = np.arange(n_days)
    hi = np.clip(index - lead, 0, n_days)
    lo = np.clip(index - lead - WINDOW_DAYS, 0, n_days)
    total = sums[hi] - sums[lo]
    count = counts[hi] - counts[lo]
    with np.errstate(invalid="ignore", divide="ignore"):
        mae = np.where(count > 0, total / count, np.nan)
    return np.repeat(mae, 24), np.repeat(count, 24)


def kalman_bias(errors: np.ndarray, lead: int) -> np.ndarray:
    """Günün her saati için ayrı, tek boyutlu Kalman süzgeciyle izlenen sapma."""
    days = _by_day(errors)
    n_days = days.shape[0]
    estimate = np.zeros(24)
    variance = np.ones(24)
    states = np.zeros((n_days + 1, 24))
    for day in range(n_days):
        variance = variance + KALMAN_RATIO
        valid = ~np.isnan(days[day])
        gain = np.where(valid, variance / (variance + 1.0), 0.0)
        estimate = estimate + gain * (np.where(valid, days[day], 0.0) - estimate)
        variance = (1 - gain) * variance
        states[day + 1] = estimate
    # G günü için G−lead−1 gününe kadarki hatalarla güncellenmiş durum.
    index = np.clip(np.arange(n_days) - lead, 0, n_days)
    return states[index].reshape(-1)


def blends(forecasts: dict[str, np.ndarray], observed: np.ndarray, lead: int) -> tuple[dict[str, np.ndarray], np.ndarray]:
    """Bütün denemelerin tahmin dizileri ve hepsinin geçmişi olan saatlerin maskesi.

    Dönen sözlükte her yöntem için bir dizi, ayrıca her model için "düzeltilmiş" ve "kalman" hâli vardır.
    """
    models = list(forecasts)
    stack = np.stack([forecasts[m] for m in models])
    errors = stack - observed
    all_present = ~np.isnan(errors).any(axis=0)

    out: dict[str, np.ndarray] = {"duz_ortalama": stack.mean(axis=0)}

    # Ağırlık: son 30 günün ortalama mutlak hatasının karesiyle ters orantılı.
    mae = np.stack([rolling_mae(errors[i], lead)[0] for i in range(len(models))])
    mae_days = np.stack([rolling_mae(errors[i], lead)[1] for i in range(len(models))]) / 24
    ready = (mae_days >= MIN_DAYS).all(axis=0) & ~np.isnan(mae).any(axis=0)
    out["agirlikli_ortalama"] = _weighted(stack, mae, ready)

    # Sapma düzeltmesi: günün saatine göre son 30 günün ortalama hatası çıkarılır.
    corrected = np.empty_like(stack)
    filtered = np.empty_like(stack)
    history_ok = np.ones(stack.shape[1], dtype=bool)
    for i, model in enumerate(models):
        bias, count = rolling_mean_by_hour(errors[i], lead)
        usable = count >= MIN_DAYS
        history_ok &= usable
        corrected[i] = stack[i] - np.where(usable, np.nan_to_num(bias), 0.0)
        filtered[i] = stack[i] - kalman_bias(errors[i], lead)
        out[f"sapma_duzeltme:{model}"] = corrected[i]
        out[f"kalman:{model}"] = filtered[i]

    corrected_errors = corrected - observed
    corrected_mae = np.stack([rolling_mae(corrected_errors[i], lead)[0] for i in range(len(models))])
    corrected_ready = ready & ~np.isnan(corrected_mae).any(axis=0)
    out["duzeltilmis_agirlikli"] = _weighted(corrected, corrected_mae, corrected_ready)

    # Karşılaştırma yalnız her yöntemin geçmişi olduğu saatlerde yapılır.
    mask = all_present & ready & history_ok & corrected_ready
    return out, mask


def _weighted(stack: np.ndarray, mae: np.ndarray, ready: np.ndarray) -> np.ndarray:
    with np.errstate(invalid="ignore", divide="ignore"):
        weights = 1.0 / np.maximum(mae, 0.05) ** 2
        weights = np.where(ready, weights, 1.0)
        weights = weights / weights.sum(axis=0)
    return (stack * weights).sum(axis=0)
