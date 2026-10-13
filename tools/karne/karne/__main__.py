"""Komut satırı: python -m karne <komut>."""

from __future__ import annotations

import argparse
import sys

from . import config, iem, openmeteo


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="karne", description="Türkiye'de hangi model ne kadar isabetli?")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("stations", help="IEM istasyon listesini yenile")
    sub.add_parser("obs", help="METAR gözlemlerini indir")
    forecasts = sub.add_parser("forecasts", help="Open-Meteo eski tahminlerini indir (kaldığı yerden sürer)")
    forecasts.add_argument("--budget", type=float, default=openmeteo.DAILY_BUDGET, help="bugün için çağrı tavanı")
    sub.add_parser("report", help="RAPOR.md ve turkey_skill.json üret")
    sub.add_parser("all", help="obs + forecasts + report")
    args = parser.parse_args(argv)

    config.ensure_dirs()
    if args.command == "stations":
        stations = iem.fetch_stations()
        print(f"{len(stations)} istasyon -> {config.STATIONS_FILE}")
        return 0
    stations = iem.load_stations()
    if args.command in ("obs", "all"):
        iem.download(stations)
    if args.command in ("forecasts", "all"):
        openmeteo.fetch(stations, budget=getattr(args, "budget", openmeteo.DAILY_BUDGET))
    if args.command in ("report", "all"):
        from . import report
        report.build(stations)
    return 0


if __name__ == "__main__":
    sys.exit(main())
