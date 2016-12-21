a "circadian" Pebble watch face with weather from yr.no and darksky.net

<img src="doc/screenshot-pebble.png">

Intended for use with [GadgetBridge](https://github.com/Freeyourgadget/Gadgetbridge),
thus it has its own Android companion app rather than relying on Javascript.

yr.no provides nice forecasts, but not real-time sensor readings AFAICT.
So, using darksky.net for now, but perhaps not for the long term
(using it too much will cost money).

What's working:
- [x] real-time temperature and cloudy/sunny icons from darksky.net
- [x] time, date, battery and bluetooth status
- [x] sunrise/sunset times calculated in the Android app
- [x] 24-hour dial with highlighted region for daylight hours
- [x] today's step counts as a radial bar graph, in green
- [x] today's total step count
- [x] yesterday's step counts in dimmer green

Not well tested but but perhaps working:
- [ ] NowCast, the yr.no highly-localized prediction of upcoming precipitation for the next 2 hours.  Intend to have thin sectors around the outside of the dial to indicate precipitation.
- [ ] learning the user's active periods of day, so as to ask the phone to fetch weather updates during that time only.  That plus a frequency limit should keep API calls to a minimum.

Not started yet:
- [ ] calculate sunrise/sunset in the Pebble app, based on current location; persist current location on Pebble.  (The problem now is that every time you leave the watch screen, it loses those times.)
- [ ] precipitation forecast beyond the next 2 hours
- [ ] send the rest of the forecast across to the Pebble
- [ ] second screen (accessed via wrist flick/tap) showing longer forecast (full-width horizontal chart), and we'll see what else
- [ ] preferences on Android.  The ones you see are just mockups provided by Android Studio when it created the skeleton project.
  - [ ] maximum time between weather updates
  - [ ] colors?
  - [ ] what to show and where?
  - [ ] keep them simple to keep the package size small.  The APK is 1.3MB now, could probably be smaller.
- [ ] get real-time weather from some other source, preferably an open source where users' weather station data is contributed

