# karne — the Türkiye scorecard

Which weather model is how accurate in Türkiye? This tool compares what each model predicted one to five days
ahead with what Turkish airports then measured, so that model choice, blending weights and rain thresholds in the
app can be set from measurements instead of guesses.

It is a developer tool. Nothing here ships with the app, and the app does not read its output yet.

## What it does

1. **Measurements** — hourly airport reports (METAR) from the [Iowa Environmental Mesonet](https://mesonet.agron.iastate.edu/)
   archive, network `TR__ASOS`, from 1 January 2024: temperature, dew point, wind, gusts, pressure and the weather
   group. An hour counts as wet when one of its reports carries RA, SN, DZ, SH, TS, GR or PL (groups starting with
   VC or RE do not count).
2. **Forecasts** — the [Open-Meteo Previous Runs API](https://open-meteo.com/en/docs/previous-runs-api): what
   `ecmwf_ifs`, `icon_eu`, `ukmo_global_deterministic_10km`, `meteofrance_arpege_europe`, `ecmwf_aifs025_single`,
   `gfs_seamless` and `best_match` said 1–5 days before, at each station's own elevation.
3. **Scores** — by model, days ahead, season, region (MGM's seven regions, and coast/inland) and day/night:
   mean absolute error and bias for temperature, the daily high and low, dew point and wind; hit rate, false alarm
   ratio and ETS for rain yes/no (hourly and in 3-hour windows), with a threshold trial.
4. **Blends** — a plain mean of the models, a mean weighted by the last 30 days' error, a 30-day rolling bias
   correction, a simple Kalman bias filter, and a weighted mean of the corrected models. Each uses only what was
   known when the forecast was issued.
5. **MGM and the phone** — MGM's own forecasts from the private archive repository, and the accuracy records
   exported from a debug build of the app.

## Running it

```bash
cd tools/karne
python -m pip install -r requirements.txt

python -m karne obs         # METAR from IEM: one request per station, with pauses
python -m karne forecasts   # earlier model runs from Open-Meteo, as far as today's budget goes
python -m karne report      # writes RAPOR.md and turkey_skill.json
python -m karne all         # the three steps above

python -m unittest discover -s tests
```

Raw data, caches and results go to `design/karne/` in the repository root, which is not committed. Set
`KARNE_DATA` to use another folder.

### The Open-Meteo budget

The free API allows 10,000 calls a day, and a large request counts as several calls (more than 10 variables, more
than 14 days and every extra location multiply). The full plan is about 90,000 calls, so it takes several days.
`python -m karne forecasts` stops at 8,000 calls a day (`--budget` changes that) or as soon as the service reports
a limit, keeps every answer on disk, and continues where it stopped the next time you run it. The plan is ordered so
that the most useful data comes first:

1. the last 12 months, temperature and rain, 1–3 days ahead;
2. the same, 4–5 days ahead;
3. dew point, wind and weather code for the last 12 months;
4. and 5. the same for the time before that, back to 1 January 2024.

The report is always built from what has been downloaded so far and says what is still missing.

### MGM

MGM's forecasts are not archived anywhere, so a separate private repository collects them with GitHub Actions.
Clone it next to this repository as `zAhs_ForWeather` (or point `KARNE_MGM_ARCHIVE` at it) and the report will
include MGM once 30 days have been collected: centres whose forecast station is within 15 km of an airport are
checked against METAR, the others against MGM's own daily measurements.

### Records from the phone

In a debug build, Settings has "Export accuracy records" at the very bottom. Put the exported CSV files in
`design/karne/telefon/`; the report then summarises them. The files hold place numbers and coordinates rounded to
about 1 km, no place names.

## Output

- `design/karne/RAPOR.md` — tables and a short commentary (in Turkish).
- `design/karne/turkey_skill.json` — region × model × days ahead: temperature error and bias, daily high and low,
  rain hit rate, false alarm ratio and ETS.

## Notes on the method

- A model is only compared on hours where every model and the measurement have a value, so all models are tested
  on the same hours.
- METAR temperatures are whole degrees. That adds roughly 0.25° to every model's error and does not change the
  ranking.
- METAR has no rain amounts outside the United States, only the weather group; rain is scored as yes/no.
- Open-Meteo's hourly rain is the sum of the hour before the time stamp, so a report at 13:20 belongs to the 14:00
  stamp on both sides.
