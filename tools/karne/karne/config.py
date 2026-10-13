"""Ortak ayarlar: klasörler, modeller, değişkenler ve tarih aralığı."""

from __future__ import annotations

import os
from datetime import date
from pathlib import Path

# Depo kökü: tools/karne/karne/config.py -> üç üst klasör.
REPO_ROOT = Path(__file__).resolve().parents[3]

# Ham veri, önbellek ve çıktılar design/karne/ altında durur; design/ commit edilmez.
DATA_DIR = Path(os.environ.get("KARNE_DATA", REPO_ROOT / "design" / "karne"))
CACHE_DIR = DATA_DIR / "cache"
IEM_DIR = CACHE_DIR / "iem"
OPENMETEO_DIR = CACHE_DIR / "openmeteo"
WORK_DIR = DATA_DIR / "work"
STATIONS_FILE = DATA_DIR / "stations.json"
REPORT_FILE = DATA_DIR / "RAPOR.md"
SKILL_FILE = DATA_DIR / "turkey_skill.json"
# Telefonun debug sürümünden dışa aktarılan CSV'ler buraya konur.
PHONE_DIR = DATA_DIR / "telefon"
# MGM tahmin arşivinin (özel depo) yerel kopyası.
MGM_ARCHIVE_DIR = Path(os.environ.get("KARNE_MGM_ARCHIVE", DATA_DIR / "mgm-arsiv"))

USER_AGENT = "CommuneSky-karne/1.0 (forecast verification; github.com/alihaydarsayar/zAhs_CommuneSky)"

IEM_NETWORK = "TR__ASOS"
START_DATE = date(2024, 1, 1)

# Open-Meteo model adları (uygulamadakilerle aynı yazım).
MODELS = [
    "ecmwf_ifs",
    "icon_eu",
    "ukmo_global_deterministic_10km",
    "meteofrance_arpege_europe",
    "ecmwf_aifs025_single",
    "gfs_seamless",
    "best_match",
]

# Raporda kullanılan kısa adlar.
MODEL_LABELS = {
    "ecmwf_ifs": "ECMWF IFS",
    "icon_eu": "ICON-EU",
    "ukmo_global_deterministic_10km": "Met Office 10 km",
    "meteofrance_arpege_europe": "ARPEGE Avrupa",
    "ecmwf_aifs025_single": "ECMWF AIFS",
    "gfs_seamless": "GFS",
    "best_match": "best_match",
    "mgm": "MGM",
}

VARIABLES = ["temperature_2m", "dew_point_2m", "wind_speed_10m", "precipitation", "weather_code"]
LEADS = [1, 2, 3, 4, 5]

# Türkiye yıl boyu UTC+3; yerel gün ve gündüz/gece ayrımı için.
TURKEY_UTC_OFFSET_HOURS = 3

# Yağış var/yok eşiği (mm/saat) ve denenecek diğer eşikler.
RAIN_THRESHOLD = 0.1
RAIN_THRESHOLDS = [0.1, 0.2, 0.3, 0.5, 1.0]

SEASONS = {12: "kis", 1: "kis", 2: "kis", 3: "ilkbahar", 4: "ilkbahar", 5: "ilkbahar",
           6: "yaz", 7: "yaz", 8: "yaz", 9: "sonbahar", 10: "sonbahar", 11: "sonbahar"}
SEASON_LABELS = {"kis": "Kış", "ilkbahar": "İlkbahar", "yaz": "Yaz", "sonbahar": "Sonbahar"}


def ensure_dirs() -> None:
    for folder in (DATA_DIR, CACHE_DIR, IEM_DIR, OPENMETEO_DIR, WORK_DIR, PHONE_DIR):
        folder.mkdir(parents=True, exist_ok=True)
