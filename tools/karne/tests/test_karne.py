"""Karne hesaplarının birim testleri: python -m unittest discover -s tests"""

import io
import unittest
from datetime import date

import numpy as np

from karne import ensemble, iem, metrics, mgm, openmeteo, phone


class RainGroups(unittest.TestCase):
    def test_precipitation_groups(self):
        for group in ("RA", "-SHRA", "+TSRA", "SN", "DZ", "FZRA", "TS", "GR", "PL", "SHSN"):
            self.assertTrue(iem.is_precipitation_group(group), group)

    def test_nearby_recent_and_dry_groups_do_not_count(self):
        for group in ("VCSH", "VCTS", "RERA", "RETS", "BR", "FG", "HZ", "BLSN", "DRSN", ""):
            self.assertFalse(iem.is_precipitation_group(group), group)

    def test_any_group_in_the_report(self):
        self.assertTrue(iem.is_precipitation("BR -RA"))
        self.assertFalse(iem.is_precipitation("VCSH BR"))


class HourlyReduction(unittest.TestCase):
    HEADER = "station,valid,tmpc,dwpc,sknt,gust,mslp,alti,wxcodes"

    def reduce(self, rows):
        axis = iem.hourly_axis(date(2026, 1, 1), date(2026, 1, 1))
        return iem.reduce_to_hourly(io.StringIO("\n".join([self.HEADER] + rows)), axis)

    def test_nearest_report_sets_the_hour(self):
        out = self.reduce(["X,2026-01-01 00:50,5.00,1.00,10.00,M,M,30.00,M", "X,2026-01-01 01:20,7.00,1.00,10.00,M,M,30.00,M"])
        self.assertEqual(out["temp"][1], 5.0)
        self.assertAlmostEqual(out["wind"][1], 5.14444, places=3)
        self.assertAlmostEqual(out["pressure"][1], 1015.9, places=0)

    def test_rain_covers_the_hour_before_the_stamp(self):
        out = self.reduce(["X,2026-01-01 00:20,5.00,1.00,1.00,M,M,30.00,-RA", "X,2026-01-01 00:50,5.00,1.00,1.00,M,M,30.00,M",
                           "X,2026-01-01 01:20,5.00,1.00,1.00,M,M,30.00,M"])
        self.assertEqual(out["rain"][1], 1.0)   # (00:00, 01:00] içinde yağmur bildirildi
        self.assertEqual(out["rain"][2], 0.0)   # (01:00, 02:00] kuru
        self.assertTrue(np.isnan(out["rain"][5]))  # rapor yok: bilinmiyor, kuru değil

    def test_report_on_the_hour_belongs_to_the_hour_before(self):
        out = self.reduce(["X,2026-01-01 03:00,5.00,1.00,1.00,M,M,30.00,RA"])
        self.assertEqual(out["rain"][3], 1.0)
        self.assertTrue(np.isnan(out["rain"][4]))


class Tables(unittest.TestCase):
    def test_scores(self):
        table = metrics.Table(hits=30, false_alarms=10, misses=20, correct_negatives=940)
        self.assertAlmostEqual(table.pod, 0.6)
        self.assertAlmostEqual(table.far, 0.25)
        self.assertAlmostEqual(table.frequency_bias, 0.8)
        random_hits = 50 * 40 / 1000
        self.assertAlmostEqual(table.ets, (30 - random_hits) / (60 - random_hits))

    def test_no_skill_is_zero(self):
        self.assertAlmostEqual(metrics.Table(10, 90, 90, 810).ets, 0.0)

    def test_contingency(self):
        forecast = np.array([True, True, False, False])
        observed = np.array([True, False, True, False])
        self.assertEqual(list(metrics.contingency(forecast, observed)), [1, 1, 1, 1])

    def test_sun(self):
        axis = iem.hourly_axis(date(2026, 6, 21), date(2026, 6, 21))
        up = metrics.sun_is_up(axis, 41.0, 29.0)
        self.assertTrue(up[10])    # 13:00 Türkiye
        self.assertFalse(up[22])   # 01:00 Türkiye


class Blends(unittest.TestCase):
    def setUp(self):
        rng = np.random.default_rng(7)
        hours = 24 * 90
        self.truth = 15 + 8 * np.sin(np.arange(hours) * 2 * np.pi / 24) + rng.normal(0, 1, hours)
        self.forecasts = {
            "warm": self.truth + 2.0 + rng.normal(0, 0.5, hours),
            "cold": self.truth - 1.0 + rng.normal(0, 0.5, hours),
            "noisy": self.truth + rng.normal(0, 2.5, hours),
        }

    def mae(self, values, mask):
        return float(np.abs(values - self.truth)[mask].mean())

    def test_correction_removes_a_steady_bias(self):
        blended, mask = ensemble.blends(self.forecasts, self.truth, lead=1)
        self.assertGreater(self.mae(self.forecasts["warm"], mask), 1.8)
        self.assertLess(self.mae(blended["sapma_duzeltme:warm"], mask), 0.7)
        self.assertLess(self.mae(blended["kalman:warm"], mask), 0.7)

    def test_weights_beat_the_plain_mean(self):
        blended, mask = ensemble.blends(self.forecasts, self.truth, lead=1)
        self.assertLess(self.mae(blended["agirlikli_ortalama"], mask), self.mae(blended["duz_ortalama"], mask))
        self.assertLess(self.mae(blended["duzeltilmis_agirlikli"], mask), self.mae(blended["agirlikli_ortalama"], mask))

    def test_nothing_looks_into_the_future(self):
        """Bir günden sonraki ölçümleri değiştirmek, o güne kadarki tahminleri değiştirmemeli."""
        lead = 2
        before, _ = ensemble.blends(self.forecasts, self.truth, lead)
        changed = self.truth.copy()
        cut = 24 * 60
        changed[cut:] += 25.0
        after, _ = ensemble.blends(self.forecasts, changed, lead)
        # Değişen ölçümler ancak lead gün sonra verilen tahminlere girebilir.
        safe = cut + 24 * lead
        for name in before:
            np.testing.assert_allclose(before[name][:safe], after[name][:safe], err_msg=name)
        self.assertFalse(np.allclose(before["kalman:warm"][safe + 24:], after["kalman:warm"][safe + 24:]))

    def test_warm_up_hours_are_left_out(self):
        _, mask = ensemble.blends(self.forecasts, self.truth, lead=1)
        self.assertFalse(mask[:24 * (ensemble.MIN_DAYS + 1)].any())
        self.assertTrue(mask[24 * 40:].all())


class Plan(unittest.TestCase):
    def test_windows_are_aligned_and_cover_the_range(self):
        spans = openmeteo.windows(date(2025, 10, 14), date(2026, 10, 12))
        self.assertEqual(spans[0][0], date(2025, 10, 14))
        self.assertEqual(spans[-1][1], date(2026, 10, 12))
        for (_, end), (start, _) in zip(spans, spans[1:]):
            self.assertEqual((start - end).days, 1)
        self.assertEqual(openmeteo.windows(date(2025, 10, 20), date(2026, 10, 18))[1], spans[1])

    def test_weight_counts_fields_days_and_places(self):
        chunk = openmeteo.Chunk("p", ("temperature_2m", "precipitation"), (1, 2, 3), date(2026, 1, 1), date(2026, 1, 28),
                                ("A", "B", "C", "D", "E", "F"))
        self.assertAlmostEqual(chunk.weight, 6 * (6 * 7 / 10) * 2)

    def test_first_day_fits_the_daily_budget(self):
        stations = [{"id": f"S{i:02d}"} for i in range(69)]
        first = [c for c in openmeteo.plan(stations, date(2026, 10, 14)) if c.phase.startswith("1-")]
        self.assertLess(sum(c.weight for c in first), openmeteo.DAILY_BUDGET)


class Phone(unittest.TestCase):
    def test_pairs_by_place_and_hour(self):
        forecasts = [
            {"place": "1", "source": "MGM", "kind": "H", "lead": 3, "hour": "2026-10-10T12:00", "value": 20.0},
            {"place": "1", "source": "MGM", "kind": "H", "lead": 24, "hour": "2026-10-10T12:00", "value": 17.0},
            {"place": "1", "source": "MGM", "kind": "X", "lead": 24, "hour": "2026-10-10T00:00", "value": 30.0},
            {"place": "2", "source": "MGM", "kind": "H", "lead": 3, "hour": "2026-10-10T12:00", "value": 99.0},
        ]
        observed = [{"place": "1", "hour": "2026-10-10T12:00", "value": 19.0, "km": 2.0}]
        totals = phone.summarize(forecasts, observed)
        self.assertEqual(totals[("MGM", "1-6 sa")], [1.0, 1.0, 1.0])
        self.assertEqual(totals[("MGM", "24 sa")], [1.0, 2.0, -2.0])


class MgmArchive(unittest.TestCase):
    def test_lead_days(self):
        run = mgm._time("2026-10-13T19:17:00+00:00")
        self.assertEqual(mgm.lead_days(run, mgm._time("2026-10-14T12:00:00.000Z")), 0)
        self.assertEqual(mgm.lead_days(run, mgm._time("2026-10-15T00:00:00.000Z")), 1)
        self.assertEqual(mgm.lead_days(run, mgm._time("2026-10-16T21:00:00.000Z")), 3)

    def test_daily_forecast_against_measurements(self):
        centers = [{"gunluk": 90601, "sondurum": 17130, "saatlik": 17130}]
        line = {"t": "2026-10-13T07:17:00+00:00", "k": "g", "id": 90601,
                "d": [["2026-10-13T00:00:00.000Z", "A", 20, 9, 40, 90, 5, 10], ["2026-10-14T00:00:00.000Z", "Y", 18, 8, 40, 90, 5, 10],
                      ["2026-10-15T00:00:00.000Z", "PB", 15, 6, 40, 90, 5, 10]]}
        observed = {("2026-10-14", 17130): {"max": 19.5, "min": 8.5, "yagis": 3.2},
                    ("2026-10-15", 17130): {"max": 15.0, "min": 7.0, "yagis": 0.4}}
        # Aynı gün ikinci tur: aynı tahmin iki kez sayılmaz.
        result = mgm.score_daily([line, dict(line, t="2026-10-13T13:17:00+00:00")], observed, centers, skip=set())
        self.assertEqual(list(result["errors"][("tmax", 1)]), [1, 1.5, -1.5])
        self.assertEqual(list(result["errors"][("tmin", 2)]), [1, 1.0, -1.0])
        self.assertEqual(list(result["tables"][1]), [1, 0, 0, 0])   # "Y" dendi, 3,2 mm yağdı
        self.assertEqual(list(result["tables"][2]), [0, 0, 1, 0])   # "PB" dendi, 0,4 mm yağdı


if __name__ == "__main__":
    unittest.main()
