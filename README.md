a "circadian" Pebble watch face, [Android app and widgets](https://github.com/ec1oud/sologyr/raw/master/android/app/sologyr-release.apk)
with current weather from darksky.net and forecast from yr.no

<img src="doc/screenshot-android.png"> <img src="doc/screenshot-pebble.png">
<img src="doc/screenshot-pebble-tap-screen.png">
<img src="doc/screenshot-pebble-time-round-tap-screen.png">
<img src="doc/screenshot-pebble-time-round.png">

To install:

- Download and install [the APK](https://github.com/ec1oud/sologyr/raw/master/android/app/sologyr-release.apk)
- Select "Install Pebble watch face" from the menu

Intended for use with [GadgetBridge](https://github.com/Freeyourgadget/Gadgetbridge),
thus it has its own Android companion app rather than relying on Javascript.

yr.no provides nice forecasts, but not real-time sensor readings, so using darksky.net for now.

What's working:
- [x] real-time temperature and cloudy/sunny icons from darksky.net
- [x] time, date, battery and bluetooth status
- [x] name (locality) of current location
- [x] sunrise/sunset times calculated in the Android app
- [x] 24-hour dial with highlighted region for daylight hours
- [x] today's step counts as a radial bar graph, in green
- [x] today's total step count
- [x] yesterday's step counts in dimmer colors
- [x] NowCast, the yr.no highly-localized prediction of upcoming precipitation for the next 2 hours.
- [x] precipitation forecast beyond the next 2 hours (min/likely/max)
- [x] temperature forecast
- [x] second screen (accessed via wrist flick/tap) showing longer forecast (full-width horizontal chart), and the time
- [x] Android app includes and installs the Pebble watch face
- [x] weather radar in the Android app (main activity)
- [x] show name of the current location
- [x] Android widget with forecast temperature and precipitation (3 days)
- [x] looks good on Pebble Time, Pebble Time Round and classic Pebble

In progress:
- [ ] learning the user's active periods of day, so as to ask the phone to fetch weather updates during that time only.  That plus a frequency limit should keep API calls to a minimum.

Not started yet:
- [ ] add a widget which resembles the watch face
- [ ] auto-scale and label the temperature range on the forecast chart (Pebble and Android)
- [ ] send the rest of the forecast across to the Pebble (wind? cloud level?)
- [ ] show moonrise/set - second screen
- [ ] preferences on Android
  - [ ] colors? maybe use https://github.com/gabrielemariotti/colorpickercollection
  - [ ] what to show and where?
- [ ] Android: show time since weather update, expected active periods today (or even all week)
- [ ] get real-time weather from some other source, preferably an open source where users' weather station data is contributed
- [ ] help screen on Android

