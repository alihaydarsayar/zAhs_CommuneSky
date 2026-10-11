# Changelog

What's new in each version of Commune Sky.

## 1.5 — in testing

- Fixed: on some home screens a clock showed a small, different layout in a corner instead of the style you saved. Widgets now keep their style and fill their space at every size.
- Fixed: picking a color theme did not always change the widget. A theme now replaces the colors you chose one by one (you can undo it), and the color list shows only the colors that widget really uses.
- Fixed: widgets used the phone's language instead of the language you chose in the app.
- Fixed: temperatures could be shortened to "…" in a narrow widget. Numbers are never cut; the text gets smaller instead.
- Fixed: hour labels could be cut in the hourly widgets when your phone uses the 12-hour clock.
- Fixed: the Home & location widget had an empty middle when no home was set.
- Fixed: when you are away, tapping the home side of the widget now opens home and tapping the other side opens where you are. In My places and in the clock with your places, each place opens its own page.
- Fixed: tapping the place name on a clock widget did nothing; it now opens Commune Sky.
- The widget setup screen uses less memory while you swipe through styles.
- **Sharper widgets.** Analog dials, clock hands and the soft veil behind text are now drawn as shapes that stay crisp at any size. The temperature curve, the radial clock lines and the sun path are drawn at your screen's full resolution.
- The accent color ("Today", the "now" dot) is lightened or darkened automatically when it would be hard to read on the widget's background, for example red on a blue sky.
- Widgets now refresh on the hour, so "Now" and the hourly strip show the right hour. No extra wake-ups: the hourly update is simply timed to the full hour.
- The radial clock with a sky background and the seconds effect sends a much smaller picture to your home screen and looks the same. If a widget's pictures would ever be too large for the home screen, it shows a simple layout instead of staying empty.
- **New: Settings › Clock format.** Choose "Same as phone", 24-hour or 12-hour for the app and all widgets. In Turkish the default is 24-hour; in other languages it follows your phone. The widget preview and the home screen always match.
- Smoother style swiping on the widget setup screen: while you swipe, the styles beside the current one are shown as ready pictures; the style you stop on switches to the live preview without any visible change.
- Housekeeping: build warnings fixed.
- **In Türkiye, forecasts now come from the Turkish State Meteorological Service (MGM).** Hourly and daily temperatures and the weather icon come from MGM; rain chance, UV and pressure still come from Open-Meteo. If MGM cannot be reached, the app quietly uses the ECMWF model and returns to MGM as soon as it answers.

## 1.4 — October 11, 2026

- **Widgets keep your settings.** Choosing another style no longer resets your background, colors, corners or anything else you picked; only the style changes. Switching the background keeps the transparency you set.
- **What you see is what you get.** The preview on the setup screen is now drawn at the real size of the widget on your home screen, so the saved widget looks the same as the preview.
- **Remove and re-add without starting over.** A new widget opens with the settings you last saved for that kind of widget.
- **New: Settings › My widgets.** See every Commune Sky widget on your home screen and tap one to change it, even if your home screen has no "edit widget" option.
- **One look for all widgets.** A calm dark design with the same spacing, type and colors everywhere: narrow, bold digits for temperatures, cool-to-warm temperature bars, and one accent color for "Today", "Now" and alerts.
- **New clock: Radial digital.** Sixty fine lines around a bold clock with heavy hours and light minutes. The lines light up with the seconds, as a single line or with a fading tail. Your phone draws this itself, so it costs no extra battery.
- **Three new analog clocks.** Plain, with a light dial and a date window; Field, with bold numerals where you choose which ones to highlight; and Weather ring, with the next twelve hours around the dial, cool to warm, and blue when rain is expected. Each has an optional second hand with a star or a circle.
- **Your colors.** Next to the ready-made color themes there is now "Custom": choose the background, the main text, the secondary text and the accent from twelve colors or type a color code. Clocks let you color hours and minutes separately; analog clocks let you color the dial, numerals and hands.
- **Your type.** Pick thin, normal or bold text, and choose standard or narrow digits for the clock.
- **Easier to read on any wallpaper.** On glass and clear backgrounds the text turns dark on a light wallpaper and light on a dark one, with a soft shadow or an optional soft veil behind it. The setup screen warns you when the text may be hard to read. Glass never fades away completely.
- **Tap where it matters.** Tap the clock to open your phone's clock app; tap the weather to open Commune Sky on that widget's place.
- **A new analog clock with its own seal, date window and weather.** The "SS" clock comes in three ready-made looks (classic, dark and night blue), and you can choose every color and what it shows.
- **Clearer, calmer widget settings.** The preview stays on top; swipe it to change the style and see it on your wallpaper, a light or a dark background. Below it are five short rows: background, colors, text, what's shown and location. Each opens a small page, colors are named in plain words, and one tap fixes text that is hard to read.
- **More polished widgets.** The same spacing, corners and type in every widget; corners match your phone's other widgets; a fine edge sets dark widgets apart from the wallpaper; highs and lows are written the same way everywhere; nothing is cut off at small sizes or with large text.
- **Crisper text shadows.** On glass and clear backgrounds the shadow behind text is thinner and sharper, with a stronger option if you need it.
- **Lighter widgets.** Widgets are redrawn only when what they show has changed, and the radial clock sends a much smaller picture to your home screen.
- Fixed: on the widget setup screen, some styles showed an empty preview after swiping through a few of them.
- Choose cathedral, slim cathedral or Breguet hands for the SS clock.
- On the SS clock the date, the seal and the weather sit farther from the center, so the hands cover them less.
- Example places in previews are now Besiktas, Liverpool and Limerick.

## 1.3 — in testing

- **New widgets, redesigned.** Nine widgets to choose from: Home & location, Clock, Hourly, Weekly, Small, Text, My places, Rain and Details. Each one comes in several styles, more than 30 designs in all, and you pick the style when you add it.
- **Home & location knows where you are.** At home it shows your home. Nearby, home stays in front with a small note such as "You're in Tuzla Merkez · 3 km · same weather". Away, the widget splits in two: where you are on the left, home on the right, with the distance, the temperature difference and when rain starts at home.
- **Ten clock styles.** Large, side by side, one line, bold, analog, clock with your places, clock with the sun's path, a date "at a glance" and a small card. The clock always keeps exact time without using extra battery.
- **Rain in the next two hours.** The Rain widget says in one sentence when rain starts or stops ("Rain starts at 20:00") and shows it in 15-minute bars.
- **Make every widget yours.** Background (sky, glass, clear or solid), transparency, colors (sky, your wallpaper's colors on Android 12 and later, sunset, ocean, forest, night, pastel), light or dark text, rounded corners, text size, and what each widget shows.
- **See it before you add it.** The setup screen shows the widget exactly as it will look, and updates as you change anything. Touch and hold a widget later to change it again.
- **Widgets fit any size.** Make a widget smaller and it shows less; make it bigger and it shows more. Text is sized to fit the space.
- **New: Settings › Home & location.** Choose your home, or make your current location home, and set how far counts as "near home" (2 to 20 km). Choose what widgets show when you're away. Whether you're home or away is worked out on your phone; your location isn't sent anywhere for this.
- Fixed: removing a widget from the home screen could close the app in the background.

## 1.2 — in testing

- **Your places.** Search for any city, district or neighbourhood, even small ones like Atakum or Kurupelit, and save it. Drag to reorder, delete with undo, and mark one place as Home.
- **Swipe between places** on the home screen. The app opens on the place you looked at last, instantly.
- **More accurate right now.** The current temperature and sky are checked against the nearest weather station: MGM stations in Türkiye, airport reports elsewhere. When a nearby station measures something different from the forecast, you see what was actually measured, with a small note such as "Measured: MGM, Samsun Bölge station, 8 min ago".
- **Storm heads-up.** If a station a little farther away reports rain or thunder, you get a note like "Thunderstorm nearby".
- **New widgets.** A clock widget with the time, date and weather, and a "Home and my location" widget that shows both places, or a single line when you're home.
- **Widget settings.** Choose each widget's place and background (sky, glass or clear) when you add it, and change them later.
- **Settings.** °C or °F, four wind units, light or dark theme, and the app language.
- All your places are updated together in the background, in a single request.

## 1.1 — October 8, 2026 (closed testing)

- Rain that the forecast's weather code missed now shows up. If the model expects rain, showers or snow right now or in a given hour, the app shows it even when the code says "partly cloudy".
- A heads-up on the home screen when rain is about to start or stop, such as "Rain expected in about 25 min".

## 1.0 — October 8, 2026

First release.

- Animated sky that follows the weather and the time of day
- Current conditions, hourly forecast and 7-day forecast
- Detail tiles: feels like, humidity, wind, UV, sun, visibility, pressure, precipitation and cloud cover
- Corrected cloud cover, so thin high clouds don't turn a sunny day "overcast"
- Weather for your approximate location, with Istanbul as the fallback
- Offline-first cache with background refresh
- Home screen widgets
- English and Turkish
