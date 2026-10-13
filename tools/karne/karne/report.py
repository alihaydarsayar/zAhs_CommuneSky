"""RAPOR.md ve turkey_skill.json üretir."""

from __future__ import annotations

import json
from datetime import datetime, timezone

from . import config, ensemble, mgm, openmeteo, phone, regions, score

SEASON_ORDER = ["kis", "ilkbahar", "yaz", "sonbahar"]


def num(value: float | None, digits: int = 2) -> str:
    return "–" if value is None else f"{value:.{digits}f}".replace(".", ",")


def signed(value: float | None) -> str:
    return "–" if value is None else f"{value:+.2f}".replace(".", ",")


def pct(value: float | None) -> str:
    return "–" if value is None else f"%{value * 100:.0f}"


def gain(fraction: float) -> str:
    """Hata ne kadar azaldı: "+%12" iyileşme, "−%3" kötüleşme."""
    return ("+" if fraction >= 0 else "−") + f"%{abs(fraction) * 100:.0f}"


def count(value: float) -> str:
    return f"{int(value):,}".replace(",", ".")


def label(model: str) -> str:
    return config.MODEL_LABELS.get(model, model)


def table(header: list[str], rows: list[list[str]]) -> str:
    lines = ["| " + " | ".join(header) + " |", "|" + "|".join(["---"] + ["---:"] * (len(header) - 1)) + "|"]
    lines += ["| " + " | ".join(row) + " |" for row in rows]
    return "\n".join(lines)


def bold_best(values: list[float | None], texts: list[str], highest: bool = False) -> list[str]:
    """En iyi değeri kalın yazar."""
    known = [v for v in values if v is not None]
    if not known:
        return texts
    best = max(known) if highest else min(known)
    return [f"**{t}**" if v is not None and abs(v - best) < 1e-9 else t for v, t in zip(values, texts)]


def best_of(pairs: dict[str, float | None], highest: bool = False) -> str | None:
    known = {k: v for k, v in pairs.items() if v is not None}
    if not known:
        return None
    return (max if highest else min)(known, key=known.get)


def build(stations: list[dict], log=print) -> None:
    log("Karne hesaplanıyor…")
    scores = score.compute(stations, log=lambda *_: None)
    if not scores.stations_used:
        log("Henüz tahmin verisi yok: önce `python -m karne forecasts`.")
        return
    text = render(scores, stations)
    config.REPORT_FILE.write_text(text, encoding="utf-8")
    config.SKILL_FILE.write_text(json.dumps(skill_json(scores), ensure_ascii=False, indent=1), encoding="utf-8")
    log(f"{config.REPORT_FILE}\n{config.SKILL_FILE}")


def _models(scores: score.Scores, variable: str, lead: int = 1) -> list[str]:
    present = scores.models.get((variable, lead), [])
    return [m for m in config.MODELS if m in present]


def _leads(scores: score.Scores, variable: str) -> list[int]:
    return [lead for lead in config.LEADS if (variable, lead) in scores.models]


def render(scores: score.Scores, stations: list[dict]) -> str:
    out: list[str] = []
    add = out.append
    leads = _leads(scores, "temperature_2m")
    models = _models(scores, "temperature_2m", leads[0])
    first, last = str(scores.first_hour)[:10], str(scores.last_hour)[:10]

    add("# Türkiye karnesi: hangi model ne kadar isabetli?\n")
    add(f"Üretildi: {datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M')} UTC · `python -m karne report`\n")

    # ---- Kapsam
    add("## 1. Kapsam\n")
    add(f"- **Ölçüm:** IEM METAR arşivi, {scores.stations_used} havalimanı (arşivi süren {len(stations)} istasyondan).")
    add(f"- **Tahmin:** Open-Meteo Previous Runs API; istasyon yüksekliği `elevation` ile verildi.")
    add(f"- **Dönem:** {first} – {last}.")
    add(f"- **Vadeler:** {', '.join(str(l) for l in leads)} gün önce verilen tahminler.")
    add("- **Adil karşılaştırma:** bir saat, ancak bütün modellerin ve ölçümün o saatte değeri varsa sayılır; "
        "her model aynı saatlerde sınanır.")
    add("- **METAR sınırı:** sıcaklık tam dereceye yuvarlanmış gelir; bu, her modelin hatasına yaklaşık 0,25° ekler. "
        "Yağış miktarı yoktur, yalnız \"yağış var/yok\" (hava grubu) vardır.\n")
    cover = openmeteo.coverage()
    add(table(["İndirilen aşama", "Parça", "Başlangıç", "Bitiş", "İstasyon"],
              [[name, str(c["chunks"]), c["start"], c["end"], str(c["stations"])] for name, c in sorted(cover.items())]))
    plan = openmeteo.plan(stations, datetime.now(timezone.utc).date())
    index = openmeteo.load_index()
    missing = sum(c.weight for c in plan if c.key not in index)
    if missing > 0:
        add(f"\n**Eksik:** planın ~{count(missing)} çağrılık kısmı henüz inmedi "
            f"(günde {openmeteo.DAILY_BUDGET} çağrıyla ~{missing / openmeteo.DAILY_BUDGET:.0f} gün). "
            "`python -m karne forecasts` her gün kaldığı yerden sürer; rapor eldeki veriyle üretilir.")
    region_counts = {r: sum(1 for s in stations if s["region"] == r) for r in regions.REGION_ORDER}
    add("\nİstasyon dağılımı: " + ", ".join(f"{r} {n}" for r, n in region_counts.items())
        + f"; kıyı {sum(1 for s in stations if s['coastal'])}, iç {sum(1 for s in stations if not s['coastal'])} "
          f"(kıyı: denize {regions.COAST_KM:.0f} km'den yakın).\n")

    # ---- Sıcaklık
    add("## 2. Sıcaklık\n")
    add("Ortalama mutlak hata (°C); parantez içinde sapma (+ model sıcak, − model soğuk). En iyi değer kalın.\n")
    rows = []
    for model in models:
        values = [scores.error("temperature_2m", model, lead).mae for lead in leads]
        rows.append((model, values))
    columns = list(zip(*[v for _, v in rows]))
    bolded = [bold_best(list(col), [num(v) for v in col]) for col in columns]
    add(table(["Model"] + [f"{l} gün önce" for l in leads],
              [[label(m)] + [f"{bolded[i][j]} ({signed(scores.error('temperature_2m', m, leads[i]).bias)})"
                             for i in range(len(leads))] for j, (m, _) in enumerate(rows)]))
    add(f"\nEşleşen saat: {count(scores.error('temperature_2m', models[0], leads[0]).n)} ({leads[0]} gün önce).\n")

    add("### Günün en yüksek ve en düşük sıcaklığı\n")
    add("Türkiye takvim gününde, eşleşen saatlerin en yükseği ve en düşüğü (°C ortalama mutlak hata, parantezde sapma).\n")
    for which, title in (("tmax", "En yüksek"), ("tmin", "En düşük")):
        columns = [[scores.day(which, m, lead).mae for m in models] for lead in leads]
        bolded = [bold_best(col, [num(v) for v in col]) for col in columns]
        add(f"**{title}**\n")
        add(table(["Model"] + [f"{l} gün önce" for l in leads],
                  [[label(m)] + [f"{bolded[i][j]} ({signed(scores.day(which, m, leads[i]).bias)})" for i in range(len(leads))]
                   for j, m in enumerate(models)]))
        add("")

    lead = leads[0]
    add(f"### Bölgeye göre ({lead} gün önce, °C ortalama mutlak hata)\n")
    region_best: dict[str, str | None] = {}
    rows = []
    for region in regions.REGION_ORDER:
        values = [scores.error("temperature_2m", m, lead, region).mae for m in models]
        region_best[region] = best_of(dict(zip(models, values)))
        rows.append([region] + bold_best(values, [num(v) for v in values]))
    for name, flag in (("Kıyı", True), ("İç", False)):
        values = [scores.error("temperature_2m", m, lead, None, flag).mae for m in models]
        rows.append([name] + bold_best(values, [num(v) for v in values]))
    values = [scores.error("temperature_2m", m, lead).mae for m in models]
    rows.append(["**Türkiye**"] + bold_best(values, [num(v) for v in values]))
    add(table(["Bölge"] + [label(m) for m in models], rows))
    add("")

    add(f"### Bölgeye göre sapma ({lead} gün önce, °C)\n")
    rows = [[region] + [signed(scores.error("temperature_2m", m, lead, region).bias) for m in models]
            for region in regions.REGION_ORDER]
    add(table(["Bölge"] + [label(m) for m in models], rows))
    add("")

    add(f"### Mevsime ve gündüz/geceye göre ({lead} gün önce, °C ortalama mutlak hata)\n")
    rows = []
    for season in SEASON_ORDER:
        values = [scores.error("temperature_2m", m, lead, None, None, season).mae for m in models]
        if any(v is not None for v in values):
            rows.append([config.SEASON_LABELS[season]] + bold_best(values, [num(v) for v in values]))
    for name, flag in (("Gündüz", True), ("Gece", False)):
        values = [scores.error("temperature_2m", m, lead, None, None, None, flag).mae for m in models]
        rows.append([name] + bold_best(values, [num(v) for v in values]))
    add(table(["Dilim"] + [label(m) for m in models], rows))
    add("")
    rows = []
    for name, flag in (("Gündüz", True), ("Gece", False)):
        rows.append([name + " sapması"] + [signed(scores.error("temperature_2m", m, lead, None, None, None, flag).bias) for m in models])
    add(table(["Dilim"] + [label(m) for m in models], rows))
    add("")

    # ---- Çiy noktası ve rüzgâr
    add("## 3. Çiy noktası ve rüzgâr\n")
    any_extra = False
    for variable, title, unit in (("dew_point_2m", "Çiy noktası", "°C"), ("wind_speed_10m", "Rüzgâr hızı", "m/sn")):
        v_leads = _leads(scores, variable)
        if not v_leads:
            continue
        any_extra = True
        v_models = _models(scores, variable, v_leads[0])
        columns = [[scores.error(variable, m, l).mae for m in v_models] for l in v_leads]
        bolded = [bold_best(col, [num(v) for v in col]) for col in columns]
        add(f"**{title}** ({unit}, ortalama mutlak hata; parantezde sapma)\n")
        add(table(["Model"] + [f"{l} gün önce" for l in v_leads],
                  [[label(m)] + [f"{bolded[i][j]} ({signed(scores.error(variable, m, v_leads[i]).bias)})"
                                 for i in range(len(v_leads))] for j, m in enumerate(v_models)]))
        add("")
    if not any_extra:
        add("Bu değişkenlerin eski tahminleri henüz indirilmedi (plandaki 3. aşama).\n")

    # ---- Yağış
    add("## 4. Yağış var/yok\n")
    r_leads = _leads(scores, "precipitation")
    if r_leads:
        r_models = _models(scores, "precipitation", r_leads[0])
        base = scores.table(1, config.RAIN_THRESHOLD, r_models[0], r_leads[0])
        add("Ölçüm: o saatin METAR'larında RA, SN, DZ, SH, TS, GR ya da PL var mı (VC ve RE ile başlayanlar hariç). "
            f"Tahmin: saatlik yağış ≥ {num(config.RAIN_THRESHOLD, 1)} mm. "
            f"Yağışlı saat oranı %{num(100 * (base.hits + base.misses) / base.n, 1)} ({count(base.n)} saat).\n")
        add("İsabet: yağan saatlerin kaçı tahmin edildi. Yanlış alarm: \"yağacak\" denen saatlerin kaçı kuru geçti. "
            "ETS: şansla tutanlar düşüldükten sonraki beceri (0 yok, 1 kusursuz).\n")
        for window, title in ((1, "Saatlik"), (3, "3 saatlik pencere")):
            add(f"**{title}** (isabet / yanlış alarm / ETS)\n")
            columns = [[scores.table(window, config.RAIN_THRESHOLD, m, l).ets for m in r_models] for l in r_leads]
            bolded = [bold_best(col, [num(v, 3) for v in col], highest=True) for col in columns]
            rows = []
            for j, m in enumerate(r_models):
                cells = []
                for i, l in enumerate(r_leads):
                    t = scores.table(window, config.RAIN_THRESHOLD, m, l)
                    cells.append(f"{pct(t.pod)} / {pct(t.far)} / {bolded[i][j]}")
                rows.append([label(m)] + cells)
            add(table(["Model"] + [f"{l} gün önce" for l in r_leads], rows))
            add("")

        lead = r_leads[0]
        add(f"### Eşik denemesi ({lead} gün önce, saatlik)\n")
        add("Her modelde \"yağış var\" demek için hangi eşik en iyi beceriyi veriyor? Hücre: ETS (sıklık oranı; "
            "1'den büyükse model gereğinden sık yağış diyor).\n")
        rows = []
        best_threshold: dict[str, float] = {}
        for m in r_models:
            tables = [scores.table(1, th, m, lead) for th in config.RAIN_THRESHOLDS]
            values = [t.ets for t in tables]
            texts = bold_best(values, [num(v, 3) for v in values], highest=True)
            known = [(v, th) for v, th in zip(values, config.RAIN_THRESHOLDS) if v is not None]
            if known:
                best_threshold[m] = max(known)[1]
            rows.append([label(m)] + [f"{t} ({num(tb.frequency_bias, 1)})" for t, tb in zip(texts, tables)])
        add(table(["Model"] + [f"≥ {num(th, 1)} mm" for th in config.RAIN_THRESHOLDS], rows))
        add("")

        add(f"### Bölgeye göre ETS ({lead} gün önce, saatlik, ≥ {num(config.RAIN_THRESHOLD, 1)} mm)\n")
        rows = []
        rain_region_best: dict[str, str | None] = {}
        for region in regions.REGION_ORDER:
            values = [scores.table(1, config.RAIN_THRESHOLD, m, lead, region).ets for m in r_models]
            rain_region_best[region] = best_of(dict(zip(r_models, values)), highest=True)
            rows.append([region] + bold_best(values, [num(v, 3) for v in values], highest=True))
        for name, flag in (("Kıyı", True), ("İç", False)):
            values = [scores.table(1, config.RAIN_THRESHOLD, m, lead, None, flag).ets for m in r_models]
            rows.append([name] + bold_best(values, [num(v, 3) for v in values], highest=True))
        add(table(["Bölge"] + [label(m) for m in r_models], rows))
        add("")

        add(f"### Mevsime göre ETS ({lead} gün önce, saatlik)\n")
        rows = []
        for season in SEASON_ORDER:
            values = [scores.table(1, config.RAIN_THRESHOLD, m, lead, None, None, season).ets for m in r_models]
            if any(v is not None for v in values):
                rows.append([config.SEASON_LABELS[season]] + bold_best(values, [num(v, 3) for v in values], highest=True))
        add(table(["Mevsim"] + [label(m) for m in r_models], rows))
        add("")

        add(f"### Modellerin oyu ({lead} gün önce, saatlik)\n")
        add("\"En az kaç model yağış diyorsa yağış var sayalım?\" sorusunun cevabı.\n")
        size = max((k[2] for k in scores.vote if k[1] == lead), default=0)
        rows = []
        vote_values = {}
        for needed in range(1, size + 1):
            t = scores.voted(needed, lead, size)
            vote_values[needed] = t.ets
            rows.append([f"en az {needed}/{size}", pct(t.pod), pct(t.far), num(t.ets, 3), num(t.frequency_bias, 1)])
        best_vote = best_of(vote_values, highest=True)
        add(table(["Kural", "İsabet", "Yanlış alarm", "ETS", "Sıklık oranı"], rows))
        add("")
    else:
        best_threshold, rain_region_best, best_vote, vote_values, r_models = {}, {}, None, {}, []
        add("Yağış tahminleri henüz indirilmedi.\n")

    # ---- Ortak tahmin
    add("## 5. Ortak tahmin ve düzeltme (sıcaklık)\n")
    add("Hepsi geleceği görmeden kuruldu: bir saatin tahmini için yalnız o tahmin verilmeden önce ölçülmüş hatalar "
        f"kullanıldı. Pencere {ensemble.WINDOW_DAYS} gün; düzeltme günün saatine göre ayrı. "
        "Karşılaştırma, her yöntemin geçmişi olan ortak saatlerde.\n")
    blend_rows = []
    gains: dict[int, dict[str, float]] = {}
    for lead in leads:
        singles = {m: scores.blended(m, lead).mae for m in models}
        best_single = best_of(singles)
        if best_single is None:
            continue
        corrected = {m: scores.blended(f"sapma_duzeltme:{m}", lead).mae for m in models}
        filtered = {m: scores.blended(f"kalman:{m}", lead).mae for m in models}
        methods = {
            "duz_ortalama": scores.blended("duz_ortalama", lead).mae,
            "agirlikli_ortalama": scores.blended("agirlikli_ortalama", lead).mae,
            "sapma_duzeltme": corrected[best_of(corrected)],
            "kalman": filtered[best_of(filtered)],
            "duzeltilmis_agirlikli": scores.blended("duzeltilmis_agirlikli", lead).mae,
        }
        gains[lead] = {name: 100 * (1 - value / singles[best_single]) for name, value in methods.items()}
        gains[lead]["_best_single"] = singles[best_single]
        blend_rows.append((lead, best_single, singles[best_single], methods, best_of(corrected), best_of(filtered),
                           scores.blended(best_single, lead).n))
    if blend_rows:
        header = ["Yöntem"] + [f"{lead} gün önce" for lead, *_ in blend_rows]
        rows = [["En iyi tek model"] + [f"{num(mae)} ({label(m)})" for _, m, mae, *_ in blend_rows]]
        for name in ensemble.METHODS:
            cells = []
            for lead, _, single, methods, best_corrected, best_filtered, _ in blend_rows:
                extra = ""
                if name == "sapma_duzeltme":
                    extra = f", {label(best_corrected)}"
                if name == "kalman":
                    extra = f", {label(best_filtered)}"
                cells.append(f"{num(methods[name])} ({gain(1 - methods[name] / single)}{extra})")
            rows.append([ensemble.METHOD_LABELS[name]] + cells)
        add(table(header, rows))
        add(f"\nParantez: en iyi tek modele göre hata ne kadar azaldı (+ iyileşme). Ortak saat: "
            f"{count(blend_rows[0][6])} ({blend_rows[0][0]} gün önce).\n")

        lead = blend_rows[0][0]
        add(f"### Her modele düzeltme ne kazandırıyor ({lead} gün önce, °C ortalama mutlak hata)\n")
        rows = []
        for m in models:
            raw = scores.blended(m, lead).mae
            fixed = scores.blended(f"sapma_duzeltme:{m}", lead).mae
            kal = scores.blended(f"kalman:{m}", lead).mae
            rows.append([label(m), num(raw), num(fixed), num(kal)])
        add(table(["Model", "Ham", "30 günlük düzeltme", "Kalman"], rows))
        add("")

        add(f"### Bölgeye göre ({lead} gün önce, °C ortalama mutlak hata)\n")
        rows = []
        for region in regions.REGION_ORDER:
            singles = {m: scores.blended(m, lead, region).mae for m in models}
            best_single = best_of(singles)
            if best_single is None:
                continue
            mix = scores.blended("duzeltilmis_agirlikli", lead, region).mae
            rows.append([region, f"{num(singles[best_single])} ({label(best_single)})", num(mix),
                         gain(1 - mix / singles[best_single])])
        add(table(["Bölge", "En iyi tek model", "Düzeltilmiş ağırlıklı ortalama", "Kazanç"], rows))
        add("")

    # ---- MGM ve telefon
    add("## 6. MGM ve telefon kayıtları\n")
    add(mgm.section(stations))
    add("")
    add(phone.section())
    add("")

    # ---- Yorum
    add("## 7. Kısa yorum\n")
    lead = leads[0]
    temp = {m: scores.error("temperature_2m", m, lead).mae for m in models}
    best_temp = best_of(temp)
    ranked = sorted((v, m) for m, v in temp.items() if v is not None)
    add(f"- **Sıcaklıkta en iyi tek model ({lead} gün önce):** {label(best_temp)} ({num(temp[best_temp])}°). "
        f"Sıra: " + ", ".join(f"{label(m)} {num(v)}" for v, m in ranked) + ".")
    winners = sorted(set(v for v in region_best.values() if v))
    if len(winners) == 1:
        add(f"- **Bölgeye göre:** sıcaklıkta her bölgede aynı model önde ({label(winners[0])}).")
    else:
        add("- **Bölgeye göre:** sıcaklıkta en iyi model bölgeden bölgeye değişiyor: "
            + ", ".join(f"{r}: {label(m)}" for r, m in region_best.items() if m) + ".")
    if r_models:
        rain = {m: scores.table(1, config.RAIN_THRESHOLD, m, r_leads[0]).ets for m in r_models}
        best_rain = best_of(rain, highest=True)
        add(f"- **Yağışta en iyi tek model ({r_leads[0]} gün önce, saatlik ETS):** {label(best_rain)} "
            f"({num(rain[best_rain], 3)}). Sıra: "
            + ", ".join(f"{label(m)} {num(v, 3)}" for v, m in sorted(((v, m) for m, v in rain.items() if v is not None), reverse=True)) + ".")
        rain_winners = sorted(set(v for v in rain_region_best.values() if v))
        if len(rain_winners) > 1:
            add("- **Yağış, bölgeye göre:** " + ", ".join(f"{r}: {label(m)}" for r, m in rain_region_best.items() if m) + ".")
        if best_threshold:
            add("- **Yağış eşiği:** en iyi beceriyi veren eşik: "
                + ", ".join(f"{label(m)} ≥ {num(th, 1)} mm" for m, th in best_threshold.items()) + ".")
        if best_vote:
            add(f"- **Modellerin oyu:** en iyi kural \"en az {best_vote} model yağış diyorsa yağış var\" "
                f"(ETS {num(vote_values[best_vote], 3)}; en iyi tek model {num(rain[best_rain], 3)}).")
    if gains:
        g = gains[leads[0]]
        add(f"- **Ortak tahmin ve düzeltme ({leads[0]} gün önce):** en iyi tek modele ({num(g['_best_single'])}°) göre "
            f"düz ortalama %{g['duz_ortalama']:.0f}, ağırlıklı ortalama %{g['agirlikli_ortalama']:.0f}, "
            f"tek modele sapma düzeltmesi %{g['sapma_duzeltme']:.0f}, Kalman %{g['kalman']:.0f}, "
            f"düzeltilmiş modellerin ağırlıklı ortalaması %{g['duzeltilmis_agirlikli']:.0f} daha az hata veriyor.")
    add("")
    # Elle tutulan notlar (servis denemeleri, tahminler): design/karne/NOTLAR.md varsa rapora eklenir.
    notes = config.DATA_DIR / "NOTLAR.md"
    if notes.exists():
        add("## 8. Notlar\n")
        add(notes.read_text(encoding="utf-8").strip())
        add("")
    return "\n".join(out)


def skill_json(scores: score.Scores) -> dict:
    """Bölge × model × gün önce: sıcaklık hatası ve sapması, yağış isabeti. Henüz uygulamaya bağlı değil."""
    leads = _leads(scores, "temperature_2m")

    def entry(region, coastal) -> dict:
        models = {}
        for model in config.MODELS:
            per_lead = {}
            for lead in leads:
                t = scores.error("temperature_2m", model, lead, region, coastal)
                if not t.n:
                    continue
                hi = scores.day("tmax", model, lead, region, coastal)
                lo = scores.day("tmin", model, lead, region, coastal)
                rain = scores.table(1, config.RAIN_THRESHOLD, model, lead, region, coastal)
                rain3 = scores.table(3, config.RAIN_THRESHOLD, model, lead, region, coastal)
                per_lead[str(lead)] = {
                    "hours": int(t.n),
                    "temp_mae": _r(t.mae), "temp_bias": _r(t.bias),
                    "tmax_mae": _r(hi.mae), "tmax_bias": _r(hi.bias),
                    "tmin_mae": _r(lo.mae), "tmin_bias": _r(lo.bias),
                    "rain_hours": int(rain.n),
                    "rain_pod": _r(rain.pod, 3), "rain_far": _r(rain.far, 3), "rain_ets": _r(rain.ets, 3),
                    "rain3h_pod": _r(rain3.pod, 3), "rain3h_far": _r(rain3.far, 3), "rain3h_ets": _r(rain3.ets, 3),
                }
            if per_lead:
                models[model] = per_lead
        return models

    return {
        "generated": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%MZ"),
        "period": [str(scores.first_hour)[:10], str(scores.last_hour)[:10]],
        "stations": scores.stations_used,
        "observation": "IEM METAR (TR__ASOS)",
        "rain_threshold_mm": config.RAIN_THRESHOLD,
        "units": {"temp": "°C", "lead": "days before"},
        "turkey": entry(None, None),
        "regions": {region: entry(region, None) for region in regions.REGION_ORDER},
        "coastal": entry(None, True),
        "inland": entry(None, False),
    }


def _r(value: float | None, digits: int = 2) -> float | None:
    return None if value is None else round(value, digits)
