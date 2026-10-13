"""İstasyonları ile, MGM'nin 7 coğrafi bölgesine ve kıyı/iç ayrımına yerleştirir."""

from __future__ import annotations

import json
import math
import unicodedata

import requests

from . import config

REGIONS = {
    "Marmara": ["İstanbul", "Edirne", "Kırklareli", "Tekirdağ", "Çanakkale", "Kocaeli", "Yalova", "Sakarya",
                "Bilecik", "Bursa", "Balıkesir"],
    "Ege": ["İzmir", "Manisa", "Aydın", "Denizli", "Kütahya", "Afyonkarahisar", "Uşak", "Muğla"],
    "Akdeniz": ["Adana", "Osmaniye", "Antalya", "Burdur", "Hatay", "Isparta", "Mersin", "Kahramanmaraş"],
    "İç Anadolu": ["Aksaray", "Ankara", "Çankırı", "Eskişehir", "Karaman", "Kayseri", "Kırıkkale", "Kırşehir",
                   "Konya", "Nevşehir", "Niğde", "Sivas", "Yozgat"],
    "Karadeniz": ["Rize", "Trabzon", "Artvin", "Sinop", "Tokat", "Çorum", "Amasya", "Samsun", "Zonguldak", "Bolu",
                  "Düzce", "Karabük", "Bartın", "Kastamonu", "Bayburt", "Giresun", "Gümüşhane", "Ordu"],
    "Doğu Anadolu": ["Ağrı", "Ardahan", "Bingöl", "Bitlis", "Elazığ", "Erzincan", "Erzurum", "Hakkari", "Iğdır",
                     "Kars", "Malatya", "Muş", "Tunceli", "Van"],
    "Güneydoğu Anadolu": ["Adıyaman", "Batman", "Diyarbakır", "Gaziantep", "Kilis", "Mardin", "Siirt", "Şanlıurfa",
                          "Şırnak"],
}
REGION_ORDER = list(REGIONS)

# Denize bu kadar yakın istasyon "kıyı" sayılır.
COAST_KM = 25.0

MGM_STATIONS_URL = "https://servis.mgm.gov.tr/web/istasyonlar"
COASTLINE_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/"
                 "ne_50m_coastline.geojson")


def fold(name: str) -> str:
    """Türkçe harfleri sadeleştirir: "Hakkâri" ve "HAKKARİ" aynı anahtara düşer."""
    text = name.replace("İ", "i").replace("I", "i").replace("ı", "i").lower()
    text = unicodedata.normalize("NFKD", text)
    return "".join(ch for ch in text if ch.isalnum())


_PROVINCE_REGION = {fold(province): region for region, provinces in REGIONS.items() for province in provinces}
_PROVINCE_NAME = {fold(province): province for provinces in REGIONS.values() for province in provinces}


def region_of(province: str) -> str | None:
    return _PROVINCE_REGION.get(fold(province))


def province_name(province: str) -> str:
    return _PROVINCE_NAME.get(fold(province), province)


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    radius = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(a))


def _cached_json(name: str, url: str, headers: dict | None = None):
    path = config.CACHE_DIR / name
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    response = requests.get(url, headers={"User-Agent": config.USER_AGENT, **(headers or {})}, timeout=60)
    response.raise_for_status()
    path.write_text(response.text, encoding="utf-8")
    return response.json()


def mgm_stations() -> list[dict]:
    """MGM'nin tüm istasyon listesi (il adı ve koordinatla); ili bulmak için."""
    return _cached_json("mgm_istasyonlar.json", MGM_STATIONS_URL, {"Origin": "https://www.mgm.gov.tr"})


def nearest_province(lat: float, lon: float, stations: list[dict]) -> tuple[str, float]:
    best, best_km = "", float("inf")
    for station in stations:
        slat, slon = station.get("enlem"), station.get("boylam")
        if slat is None or slon is None or not station.get("il"):
            continue
        if abs(slat - lat) > 1.0 or abs(slon - lon) > 1.5:
            continue
        km = distance_km(lat, lon, slat, slon)
        if km < best_km:
            best, best_km = station["il"], km
    return best, best_km


def coast_segments() -> list[tuple[float, float, float, float]]:
    """Türkiye çevresindeki kıyı çizgisi parçaları (Natural Earth 1:50m, kamu malı)."""
    data = _cached_json("ne_50m_coastline.geojson", COASTLINE_URL)
    segments = []
    for feature in data["features"]:
        geometry = feature["geometry"]
        lines = geometry["coordinates"] if geometry["type"] == "MultiLineString" else [geometry["coordinates"]]
        for line in lines:
            for (lon1, lat1), (lon2, lat2) in zip(line, line[1:]):
                if 23.0 <= min(lon1, lon2) and max(lon1, lon2) <= 47.0 and 33.0 <= min(lat1, lat2) and max(lat1, lat2) <= 45.0:
                    segments.append((lat1, lon1, lat2, lon2))
    return segments


def coast_distance_km(lat: float, lon: float, segments: list[tuple[float, float, float, float]]) -> float:
    """Noktanın en yakın kıyı parçasına uzaklığı; yerel düzlemde nokta–doğru parçası."""
    kx = 111.32 * math.cos(math.radians(lat))
    ky = 110.57
    best = float("inf")
    for lat1, lon1, lat2, lon2 in segments:
        ax, ay = (lon1 - lon) * kx, (lat1 - lat) * ky
        bx, by = (lon2 - lon) * kx, (lat2 - lat) * ky
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        t = 0.0 if length2 == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / length2))
        best = min(best, math.hypot(ax + t * dx, ay + t * dy))
    return best
