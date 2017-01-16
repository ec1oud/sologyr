#include <pebble.h>
#include "common.h"
#include "health.h"

// from http://api.met.no/weatherapi/weathericon/1.1/documentation
enum WeatherIcon {
	WeatherUnknown = 0,
    Sun = 1,
    LightCloud,
    PartlyCloud,
    Cloud,
    LightRainSun,
    LightRainThunderSun,
    SleetSun,
    SnowSun,
    LightRain,
    Rain,
    RainThunder,
    Sleet,
    Snow,
    SnowThunder,
    Fog,
    SleetSunThunder = 20,
    SnowSunThunder,
    LightRainThunder,
    SleetThunder,
    DrizzleThunderSun,
    RainThunderSun,
    LightSleetThunderSun,
    HeavySleetThunderSun,
    LightSnowThunderSun,
    HeavySnowThunderSun,
    DrizzleThunder,
    LightSleetThunder,
    HeavySleetThunder,
    LightSnowThunder,
    HeavySnowThunder,
    DrizzleSun = 40,
    RainSun,
    LightSleetSun,
    HeavySleetSun,
    LightSnowSun,
    HeavysnowSun,
    Drizzle,
    LightSleet,
    HeavySleet,
    LightSnow,
    HeavySnow,
	// Some symbol ids are used for indicating polar night. These ids are over the ordinary id + 100,
	// the name are prefixed with Dark_. For instance, Dark_Sun is symbol id 101 (1+100).
	// Some examples are listed underneath. This is available for the following ids:
	// 101, 102, 103, 105, 106, 107, 108, 120, 121, 124, 125,
	// 126, 127, 128, 129, 140, 141, 142, 143, 144 and 145.
    Dark_Sun = 101,
    //~ 102 Dark_LightCloud
    Dark_PartlyCloud = 103,
    //~ 129 Dark_HeavySnowThunderSun
	Wind = 200
};

static Window *window;
static Layer *main_layer;
static Layer *tap_layer;
static TextLayer *text_time_layer;
static TextLayer *tapTimeLayer;
static TextLayer *tapLocationLayer;
static TextLayer *stepsLayer;
static TextLayer *dateLayer;
static TextLayer *temperatureLayer;
static BitmapLayer *bluetooth_layer;
static BitmapLayer *charging_layer;
static BitmapLayer *weather_icon_layer;
static Layer *batteryGraphLayer;
static Layer *circleLayer;
static Layer *weatherPlotLayer;
static int batteryPct = 0;
struct tm currentTime;
static int curLat = 59913; // degrees * 1000
static int curLon = 10739; // degrees * 1000
static char curLocationName[32];
static uint8_t sunriseHour = 100;
static uint8_t sunriseMinute = 0;
static uint8_t sunsetHour = 0;
static uint8_t sunsetMinute = 0;
static uint8_t cloudCover = 0;
static char currentTemperature[12];
static bool bluetoothConnected = 0;
static GBitmap *bluetooth_bitmap = NULL;
static GBitmap *charging_bitmap = NULL;
static GBitmap *weather_bitmap = NULL;
static time_t nowcastReceivedTime;
static int8_t nowcastTimes[NOWCAST_MAX_INTERVALS]; // minutes in the future from nowcastReceivedTime (in the past if negative)
static uint8_t nowcastPrecipitation[NOWCAST_MAX_INTERVALS]; // tenths of mm
static int8_t nowcastLength;
static int16_t forecastPrecipitationTimes[FORECAST_MAX_INTERVALS]; // minutes from the beginning of today
static uint8_t forecastPrecipitation[FORECAST_MAX_INTERVALS]; // tenths of mm
static uint8_t forecastPrecipitationMin[FORECAST_MAX_INTERVALS]; // tenths of mm
static uint8_t forecastPrecipitationMax[FORECAST_MAX_INTERVALS]; // tenths of mm
static uint8_t forecastPrecipitationLength;
static int16_t forecastTimes[FORECAST_MAX_INTERVALS]; // minutes from the beginning of today
static int16_t forecastTemperature[FORECAST_MAX_INTERVALS]; // tenths of degrees
static uint8_t forecastLength;
static int16_t minForecastTemperature;
static int16_t maxForecastTemperature;
static int16_t weatherUpdateFrequency; // how often we can request it, in minutes

static void send_hello()
{
	Tuplet value = TupletInteger(KEY_HELLO, 0);
	DictionaryIterator *iter;
	app_message_outbox_begin(&iter);
	if (iter == NULL)
		return;
	dict_write_tuplet(iter, &value);
	dict_write_end(iter);
	app_message_outbox_send();
}

static int min(int one, int other)
{
	return (one < other ? one : other);
}

void dateLayerUpdate()
{
	static char date_text[24];
	static const char *date_format = "%a %e %b";
	strftime(date_text, sizeof(date_text), date_format, &currentTime);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tick %s", time_text);
	text_layer_set_text(dateLayer, date_text);
}

void timeLayerUpdate()
{
	static char time_text[] = "00:00";
	static const char *time_format = "%H:%M";  //:%S";
	strftime(time_text, sizeof(time_text), time_format, &currentTime);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tick %s", time_text);
	text_layer_set_text(text_time_layer, time_text);
	text_layer_set_text(tapTimeLayer, time_text);
}

#ifdef PBL_HEALTH
void stepsLayerUpdate()
{
	static char buf[] = "000000";
	snprintf(buf, sizeof(buf), "%d", health_get_current_steps());
	text_layer_set_text(stepsLayer, buf);
}
#endif

void circleLayerUpdate()
{
	layer_mark_dirty(circleLayer);
}

static void handle_second_tick(struct tm *tick_time, TimeUnits units_changed)
{
	memcpy(&currentTime, tick_time, sizeof(struct tm));
	if (units_changed & MINUTE_UNIT) {
		timeLayerUpdate();
		circleLayerUpdate();
#ifdef PBL_HEALTH
		stepsLayerUpdate();
		health_update_steps_interval();
#endif
	}
	if (units_changed & DAY_UNIT) {
		dateLayerUpdate();
		layer_mark_dirty(window_get_root_layer(window));
#ifdef PBL_HEALTH
		health_update_weekday();
#endif
	}
}

#ifdef PBL_HEALTH
void handleCurrentIntervalChanged(uint8_t interval, uint16_t expectedVmc)
{
	if (!expectedVmc)
		return; // if total inactivity is expected, don't bother the phone with it
	Tuplet value = TupletInteger(KEY_ACTIVE_INTERVAL, expectedVmc);
	DictionaryIterator *iter;
	app_message_outbox_begin(&iter);
	if (iter == NULL)
		return;
	dict_write_tuplet(iter, &value);
	dict_write_end(iter);
	app_message_outbox_send();
}
#endif

static void paintWeatherPlot(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	time_t now = time(NULL);
	time_t startOfToday = time_start_of_today();

	graphics_context_set_stroke_color(ctx, COLOR_CHART_DAY_DIV);
	time_t nextDay = startOfToday + SECONDS_PER_DAY;
	int x = 0;
	struct tm time;
	memcpy(&time, &currentTime, sizeof(struct tm));
	while (x < layerBounds.size.w) {
		++time.tm_wday;
		if (time.tm_wday > 6)
			time.tm_wday = 0;
		time_t minutesFromNow = (nextDay - now) / 60;
		x = (int)(minutesFromNow / FORECAST_CHART_MINUTES_PER_PIXEL);
		GFont font = fonts_get_system_font(FONT_KEY_GOTHIC_14);
		if (x < layerBounds.size.w) {
			GPoint p1 = GPoint(x, 0);
			GPoint p2 = GPoint(x, layerBounds.size.h);
			GRect textBounds = GRect(x + 2, 0, 60, 14);
			static char dayText[4];
			static const char *dateFormat = "%a";
			strftime(dayText, sizeof(dayText), dateFormat, &time);
			graphics_draw_line(ctx, p1, p2);
			graphics_draw_text(ctx, dayText, font, textBounds, GTextOverflowModeTrailingEllipsis, GTextAlignmentLeft, NULL);
		}
		nextDay += SECONDS_PER_DAY;
	}

	GRect precipitationBar = GRect(0, 0, 60 / FORECAST_CHART_MINUTES_PER_PIXEL, 0);
	graphics_context_set_fill_color(ctx, COLOR_CHART_PRECIPITATION_MAX);
	for (uint8_t i = 0; i < forecastPrecipitationLength; ++i) {
		time_t minutesFromNow = ((forecastPrecipitationTimes[i] * 60) + startOfToday - now) / 60;
		precipitationBar.origin.x = (int)(minutesFromNow / FORECAST_CHART_MINUTES_PER_PIXEL);
		precipitationBar.size.h = (forecastPrecipitationMax[i] * FORECAST_CHART_PIXELS_PER_MM_PRECIPITATION) / 10;
		precipitationBar.origin.y = layerBounds.size.h - precipitationBar.size.h;
		//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip t %d x %d amount %d bar %d x %d",
			//~ (int)minutesFromNow, precipitationBar.origin.x, forecastPrecipitation[i], precipitationBar.size.w, precipitationBar.size.h);
		graphics_fill_rect(ctx, precipitationBar, 0, GCornerNone);
	}
	graphics_context_set_fill_color(ctx, COLOR_CHART_PRECIPITATION);
	for (uint8_t i = 0; i < forecastPrecipitationLength; ++i) {
		time_t minutesFromNow = ((forecastPrecipitationTimes[i] * 60) + startOfToday - now) / 60;
		precipitationBar.origin.x = (int)(minutesFromNow / FORECAST_CHART_MINUTES_PER_PIXEL);
		precipitationBar.size.h = (forecastPrecipitation[i] * FORECAST_CHART_PIXELS_PER_MM_PRECIPITATION) / 10;
		precipitationBar.origin.y = layerBounds.size.h - precipitationBar.size.h;
		//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip t %d x %d amount %d bar %d x %d",
			//~ (int)minutesFromNow, precipitationBar.origin.x, forecastPrecipitation[i], precipitationBar.size.w, precipitationBar.size.h);
		graphics_fill_rect(ctx, precipitationBar, 0, GCornerNone);
	}
	graphics_context_set_fill_color(ctx, COLOR_CHART_PRECIPITATION_MIN);
	for (uint8_t i = 0; i < forecastPrecipitationLength; ++i) {
		time_t minutesFromNow = ((forecastPrecipitationTimes[i] * 60) + startOfToday - now) / 60;
		precipitationBar.origin.x = (int)(minutesFromNow / FORECAST_CHART_MINUTES_PER_PIXEL);
		precipitationBar.size.h = (forecastPrecipitationMin[i] * FORECAST_CHART_PIXELS_PER_MM_PRECIPITATION) / 10;
		precipitationBar.origin.y = layerBounds.size.h - precipitationBar.size.h;
		//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip t %d x %d amount %d bar %d x %d",
			//~ (int)minutesFromNow, precipitationBar.origin.x, forecastPrecipitation[i], precipitationBar.size.w, precipitationBar.size.h);
		graphics_fill_rect(ctx, precipitationBar, 0, GCornerNone);
	}

	if (maxForecastTemperature == minForecastTemperature) // unlikely but would be a divide-by-zero below
		return;
	graphics_context_set_stroke_width(ctx, 3);
	GPoint lastPoint;
	int16_t lastTime = 0;
	float scale = (float)layerBounds.size.h / (minForecastTemperature - maxForecastTemperature);
	int16_t zeroToPx = maxForecastTemperature * -scale;
	for (uint8_t i = 0; i < forecastLength; ++i) {
		if (forecastTimes[i] < lastTime)
			break;
		time_t minutesFromNow = ((forecastTimes[i] * 60) + startOfToday - now) / 60;
		GPoint point = GPoint( (int16_t)(minutesFromNow / FORECAST_CHART_MINUTES_PER_PIXEL),
				zeroToPx + (int16_t)(forecastTemperature[i] * scale) );
		//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast temp t %d x %d v %d y %d",
			//~ (int)minutesFromNow, point.x, forecastTemperature[i], point.y);
		if (i > 0) {
			if (forecastTemperature[i - 1] >= 0 && forecastTemperature[i] >= 0) {
				graphics_context_set_stroke_color(ctx, COLOR_CHART_TEMPERATURE_POSITIVE);
				graphics_draw_line(ctx, lastPoint, point);
			} else if (forecastTemperature[i - 1] <= 0 && forecastTemperature[i] <= 0) {
				graphics_context_set_stroke_color(ctx, COLOR_CHART_TEMPERATURE_NEGATIVE);
				graphics_draw_line(ctx, lastPoint, point);
			} else {
				// draw separate positive and negative portions
				int16_t dt = abs(forecastTemperature[i - 1] * (point.x - lastPoint.x) / (forecastTemperature[i - 1] - forecastTemperature[i]));
				GPoint xAxisCrossing = GPoint(lastPoint.x + dt, zeroToPx);
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast temp t %d x %d v %d y %d x-axis crossing at %d, %d",
					//~ (int)minutesFromNow, point.x, forecastTemperature[i], point.y, xAxisCrossing.x, xAxisCrossing.y);
				graphics_context_set_stroke_color(ctx,
					forecastTemperature[i - 1] < 0 ? COLOR_CHART_TEMPERATURE_NEGATIVE : COLOR_CHART_TEMPERATURE_POSITIVE);
				graphics_draw_line(ctx, lastPoint, xAxisCrossing);
				graphics_context_set_stroke_color(ctx,
					forecastTemperature[i] < 0 ? COLOR_CHART_TEMPERATURE_NEGATIVE : COLOR_CHART_TEMPERATURE_POSITIVE);
				graphics_draw_line(ctx, xAxisCrossing, point);
			}
		}
		lastPoint = point;
		lastTime = forecastTimes[i];
	}
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast plot height %d scale %c%d.%d axis %d",
		//~ layerBounds.size.h, scale < 0 ? '-' : ' ', (int)scale, abs((int)(scale * 100)) % 100, zeroToPx);
	graphics_context_set_stroke_width(ctx, 1);
	graphics_context_set_stroke_color(ctx, COLOR_CHART_AXIS);
	if (zeroToPx == layerBounds.size.h)
		--zeroToPx;
	graphics_draw_line(ctx, GPoint(0, zeroToPx), GPoint(layerBounds.size.w, zeroToPx));
}

static void paintCircleLayer(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	GRect fillCircle = grect_crop(layerBounds, 4);
	graphics_context_set_stroke_color(ctx, COLOR_CLOCK_RING);
	graphics_draw_circle(ctx, grect_center_point(&fillCircle), layerBounds.size.w / 2 - 2);
	time_t now = time(NULL);
	time_t startOfToday = time_start_of_today();

	// render the period of the day with sunlight
	int sunriseAngle = (sunriseHour * 60 + sunriseMinute) * 360 / MINUTES_PER_DAY - 180;
	int sunsetAngle = (sunsetHour * 60 + sunsetMinute) * 360 / MINUTES_PER_DAY - 180;
	if (sunriseHour > 24) {
		send_hello();
	} else {
		graphics_context_set_fill_color(ctx, COLOR_SUN);
		graphics_fill_radial(ctx, fillCircle, GCornerNone, fillCircle.size.w / 3 + 3,
			DEG_TO_TRIGANGLE(sunriseAngle),  DEG_TO_TRIGANGLE(sunsetAngle));
	}

	// render the forecast precipitation assuming that it's pre-sorted by time (the phone's responsibility)
	// and assuming that each interval in which precipitation is predicted is one hour long.
	if (forecastPrecipitationLength > 0) {
		APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip len %d", forecastPrecipitationLength);
		graphics_context_set_fill_color(ctx, COLOR_PRECIPITATION_MAX);
		for (int i = 0; i < (int)forecastPrecipitationLength && i < FORECAST_MAX_INTERVALS; ++i) {
			if (forecastPrecipitationMax[i] > 0) {
				time_t actualPrecipitationTime = forecastPrecipitationTimes[i] * 60 + startOfToday;
				if (actualPrecipitationTime < now - SECONDS_PER_HOUR)
					continue;
				if (actualPrecipitationTime - now > SECONDS_PER_DAY - (SECONDS_PER_HOUR * 2))
					break;
				int startAngle = forecastPrecipitationTimes[i] * 360 / MINUTES_PER_DAY - 180;
				int endAngle = (forecastPrecipitationTimes[i] + MINUTES_PER_HOUR) * 360 / MINUTES_PER_DAY - 180;
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip %d angles %d %d amount %d",
				//~ 	(int)forecastPrecipitationTimes[i], startAngle, endAngle, forecastPrecipitation[i]);
				graphics_fill_radial(ctx, fillCircle, GCornerNone,
					(forecastPrecipitationMax[i] * FORECAST_PIXELS_PER_MM_PRECIPITATION) / 10,
					DEG_TO_TRIGANGLE(startAngle),  DEG_TO_TRIGANGLE(endAngle));
			}
		}
		graphics_context_set_fill_color(ctx, COLOR_PRECIPITATION);
		for (int i = 0; i < (int)forecastPrecipitationLength && i < FORECAST_MAX_INTERVALS; ++i) {
			if (forecastPrecipitation[i] > 0) {
				time_t actualPrecipitationTime = forecastPrecipitationTimes[i] * 60 + startOfToday;
				if (actualPrecipitationTime < now - SECONDS_PER_HOUR)
					continue;
				if (actualPrecipitationTime - now > SECONDS_PER_DAY - (SECONDS_PER_HOUR * 2))
					break;
				int startAngle = forecastPrecipitationTimes[i] * 360 / MINUTES_PER_DAY - 180;
				int endAngle = (forecastPrecipitationTimes[i] + MINUTES_PER_HOUR) * 360 / MINUTES_PER_DAY - 180;
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip %d angles %d %d amount %d",
				//~ 	(int)forecastPrecipitationTimes[i], startAngle, endAngle, forecastPrecipitation[i]);
				graphics_fill_radial(ctx, fillCircle, GCornerNone,
					(forecastPrecipitation[i] * FORECAST_PIXELS_PER_MM_PRECIPITATION) / 10,
					DEG_TO_TRIGANGLE(startAngle),  DEG_TO_TRIGANGLE(endAngle));
			}
		}
		graphics_context_set_fill_color(ctx, COLOR_PRECIPITATION_MIN);
		for (int i = 0; i < (int)forecastPrecipitationLength && i < FORECAST_MAX_INTERVALS; ++i) {
			if (forecastPrecipitationMin[i] > 0) {
				time_t actualPrecipitationTime = forecastPrecipitationTimes[i] * 60 + startOfToday;
				if (actualPrecipitationTime < now - SECONDS_PER_HOUR)
					continue;
				if (actualPrecipitationTime - now > SECONDS_PER_DAY - (SECONDS_PER_HOUR * 2))
					break;
				int startAngle = forecastPrecipitationTimes[i] * 360 / MINUTES_PER_DAY - 180;
				int endAngle = (forecastPrecipitationTimes[i] + MINUTES_PER_HOUR) * 360 / MINUTES_PER_DAY - 180;
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast precip %d angles %d %d amount %d",
				//~ 	(int)forecastPrecipitationTimes[i], startAngle, endAngle, forecastPrecipitation[i]);
				graphics_fill_radial(ctx, fillCircle, GCornerNone,
					(forecastPrecipitationMin[i] * FORECAST_PIXELS_PER_MM_PRECIPITATION) / 10,
					DEG_TO_TRIGANGLE(startAngle),  DEG_TO_TRIGANGLE(endAngle));
			}
		}
	}

	// render the nowcast precipitation
	graphics_context_set_fill_color(ctx, COLOR_NOWCAST);
	time_t nowCastAge = now - nowcastReceivedTime;
	//~ if (nowCastAge > 0 && nowCastAge < 120)
		//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "nowcast age %d len %d", (int)nowCastAge, nowcastLength);
	if (nowCastAge < 2* SECONDS_PER_HOUR) {
		for (int i = 0; i < (int)nowcastLength && i < NOWCAST_MAX_INTERVALS; ++i) {
			if (nowcastPrecipitation[i] > 0) {
				int startAngle = nowcastTimes[i] * 360 / MINUTES_PER_DAY - 180;
				int endAngle = (nowcastTimes[i] + 7 /* minutes */) * 360 / MINUTES_PER_DAY - 180;
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "nowcast precip %d angles %d %d amount %d", (int)nowcastTimes[i], startAngle, endAngle, nowcastPrecipitation[i]);
				graphics_fill_radial(ctx, fillCircle, GCornerNone, nowcastPrecipitation[i],
					DEG_TO_TRIGANGLE(startAngle),  DEG_TO_TRIGANGLE(endAngle));
			}
		}
	}

	// render the clock pointer
	GRect innerCircle = grect_crop(fillCircle, layerBounds.size.w / 4);
	int32_t currentTimeAngle = (currentTime.tm_hour * 60 + currentTime.tm_min) * 360 / MINUTES_PER_DAY - 180;
	GPoint pointerInner = gpoint_from_polar(innerCircle, GCornerNone, DEG_TO_TRIGANGLE(currentTimeAngle));
	GPoint pointerOuter = gpoint_from_polar(layerBounds, GCornerNone, DEG_TO_TRIGANGLE(currentTimeAngle));
	graphics_context_set_stroke_color(ctx, COLOR_CLOCK_POINTER_SHADOW);
	graphics_context_set_stroke_width(ctx, 7);
	graphics_draw_line(ctx, pointerInner, pointerOuter);
	graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(COLOR_CLOCK_POINTER, GColorWhite));
	graphics_context_set_stroke_width(ctx, 3);
	graphics_draw_line(ctx, pointerInner, pointerOuter);

#ifdef PBL_HEALTH
	// render the activity record; TODO move it to another layer?(
	innerCircle = grect_crop(fillCircle, layerBounds.size.w / 3);
	graphics_context_set_stroke_width(ctx, 3);
	int currentInterval = (now - startOfToday) / (MINUTES_PER_HEALTH_INTERVAL * SECONDS_PER_MINUTE);
	for (int i = 0; i < HEALTH_INTERVAL_COUNT; ++i) {
		uint16_t steps = health_get_steps_for_interval(i);
		if (steps) {
			int angle = (i * MINUTES_PER_HEALTH_INTERVAL + MINUTES_PER_HEALTH_INTERVAL / 2) * 360 / MINUTES_PER_DAY - 180;
			GRect polarBounds;
			polarBounds.size.w = innerCircle.size.w + steps / 32;
			polarBounds.size.h = polarBounds.size.w;
			grect_align(&polarBounds, &fillCircle, GAlignCenter, false);
			GPoint pointerInner = gpoint_from_polar(innerCircle, GCornerNone, DEG_TO_TRIGANGLE(angle));
			GPoint pointerOuter = gpoint_from_polar(polarBounds, GCornerNone, DEG_TO_TRIGANGLE(angle));
			if (i > currentInterval) {
				// yesterday's data: use faded colors
				if (angle > sunriseAngle && angle < sunsetAngle)
					graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(COLOR_STEPS_YESTERDAY, GColorBlack));
				else
					graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(COLOR_STEPS_YESTERDAY_NIGHT, GColorWhite));
			} else {
				// today
				if (angle > sunriseAngle && angle < sunsetAngle)
					graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(COLOR_STEPS_DAY, GColorBlack));
				else
					graphics_context_set_stroke_color(ctx, PBL_IF_COLOR_ELSE(COLOR_STEPS_NIGHT, GColorWhite));
			}
			graphics_draw_line(ctx, pointerInner, pointerOuter);
		}
	}
#endif
}

static void updateBatteryGraphLayer(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "updateBatteryGraphLayer bounds %d %d %d %d", layerBounds.origin.x, layerBounds.origin.y, layerBounds.size.w, layerBounds.size.h);
	graphics_context_set_fill_color(ctx, COLOR_BATTERY_BACKGROUND);
	graphics_fill_rect(ctx, layerBounds, 0, GCornerNone);
	graphics_context_set_fill_color(ctx, COLOR_BATTERY_FILL);
	graphics_context_set_stroke_color(ctx, COLOR_BATTERY_STROKE);
	{
		GRect knob;
		int knobWidth = 2;
		int knobHeight = layerBounds.size.h / 2;
		knobHeight -= knobHeight % 2;
		knob.origin.x = layerBounds.size.w - knobWidth;
		knob.origin.y = layerBounds.size.h / 2 - knobHeight / 2;
		knob.size.w = knobWidth;
		knob.size.h = knobHeight;
		graphics_fill_rect(ctx, knob, 1, GCornerTopRight | GCornerBottomRight);
		layerBounds.size.w -= knobWidth;
	}
	{
		GRect fill;
		fill.origin.x = 2;
		fill.origin.y = 2;
		fill.size.w = batteryPct * (layerBounds.size.w - 4) / 100;
		fill.size.h = layerBounds.size.h - 4;
		graphics_fill_rect(ctx, fill, 0, GCornerNone);
	}
	graphics_draw_rect(ctx, layerBounds);
}

static void revert_page(void *data);

static void show_page(int page) {
	//	APP_LOG(APP_LOG_LEVEL_DEBUG, "show page %d", page);
	switch(page){
		case 0:
			layer_set_hidden(main_layer, false);
			layer_set_hidden(tap_layer, true);
			break;
		case 1:
			layer_set_hidden(tap_layer, false);
			layer_set_hidden(main_layer, true);
			app_timer_register(TAP_LAYER_TIMEOUT, &revert_page, (void*)0);
			break;
	}
}

static void revert_page(void *data){
	show_page((int)data);
}

static void handle_tap(AccelAxisType axis, int32_t direction)
{
	int8_t combined = (((int8_t)direction << 4) | (int8_t)axis);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tap %d %d | %d", (int)axis, (int)direction, (int)combined);
	DictionaryIterator *iter;
	app_message_outbox_begin(&iter);
	if (iter == NULL) {
		APP_LOG(APP_LOG_LEVEL_WARNING, "tap %d %d: null iterator", (int)axis, (int)direction);
		return;
	}
	DictionaryResult ret;
	if ((ret = dict_write_int8(iter, KEY_TAP, combined))) {
		APP_LOG(APP_LOG_LEVEL_WARNING, "tap %d %d: can't write axis/direction", (int)axis, (int)direction);
		return;
	}
	dict_write_end(iter);
	app_message_outbox_send();
	show_page(1);
}

static void handle_bluetooth(bool connected)
{
	bluetoothConnected = connected;
	bitmap_layer_set_bitmap(bluetooth_layer, bluetoothConnected ? bluetooth_bitmap : 0);
	if (bluetoothConnected)
		send_hello();
}

static void handle_weather_icon(uint8_t icon)
{
	uint32_t resource = 0;
	switch (icon) {
		case Sun:
			resource = RESOURCE_ID_IMAGE_WEATHER_CLEAR;
			break;
		case Dark_Sun:
			resource = RESOURCE_ID_IMAGE_WEATHER_CLEAR_NIGHT;
			break;
		case Dark_PartlyCloud:
			resource = RESOURCE_ID_IMAGE_WEATHER_FEW_CLOUDS_NIGHT;
			break;
		case PartlyCloud:
			resource = RESOURCE_ID_IMAGE_WEATHER_FEW_CLOUDS;
			break;
		case Cloud:
			resource = RESOURCE_ID_IMAGE_WEATHER_OVERCAST;
			break;
		case Fog:
			resource = RESOURCE_ID_IMAGE_WEATHER_FOG;
			break;
		//~ case ?:
			//~ resource = RESOURCE_ID_IMAGE_WEATHER_SEVERE_ALERT;
			//~ break;
		case Rain:
			resource = RESOURCE_ID_IMAGE_WEATHER_SHOWERS;
			break;
		//~ case ?:
			//~ resource = RESOURCE_ID_IMAGE_WEATHER_SHOWERS_SCATTERED;
			//~ break;
		case Sleet:
			resource = RESOURCE_ID_IMAGE_WEATHER_SLEET;
			break;
		case Snow:
			resource = RESOURCE_ID_IMAGE_WEATHER_SNOW;
			break;
		//~ case ?:
			//~ resource = RESOURCE_ID_IMAGE_WEATHER_STORM;
			//~ break;
		case Wind:
			resource = RESOURCE_ID_IMAGE_WEATHER_WIND;
			break;
	}
	if (weather_bitmap) {
		gbitmap_destroy(weather_bitmap);
		weather_bitmap = NULL;
	}
	if (resource) {
		weather_bitmap = gbitmap_create_with_resource(resource);
	}
	bitmap_layer_set_bitmap(weather_icon_layer, weather_bitmap);
}

static void handle_battery(BatteryChargeState charge_state)
{
	batteryPct = charge_state.charge_percent;
	bitmap_layer_set_bitmap(charging_layer, charge_state.is_charging ? charging_bitmap : 0);
	layer_mark_dirty(batteryGraphLayer);
}

static void in_received_handler(DictionaryIterator *iter, void *context)
{
	Tuple *tuple = dict_read_first(iter);
	int16_t currentForecastPrecipitationTime = 0;
	uint8_t currentForecastPrecipitation = 0; // tenths of mm
	uint8_t currentForecastPrecipitationMin = 0; // tenths of mm
	uint8_t currentForecastPrecipitationMax = 0; // tenths of mm
	int16_t currentForecastTime = 0;
	int16_t currentForecastTemperature = 0; // tenths of degrees
	while (tuple) {
#ifdef DEBUG_MSG
		switch (tuple->type) {
		case TUPLE_CSTRING:
			APP_LOG(APP_LOG_LEVEL_DEBUG, "key %d value %s" , (int)tuple->key, tuple->value->cstring);
			break;
		case TUPLE_INT:
			APP_LOG(APP_LOG_LEVEL_DEBUG, "key %d value %d" , (int)tuple->key, tuple->value->int8);
			break;
		default:
			APP_LOG(APP_LOG_LEVEL_WARNING, "key %d unexpected type %d len %d" , (int)tuple->key, tuple->type, tuple->length);
			break;
		}
#endif

		switch (tuple->key) {
		case KEY_LAT:
			curLat = tuple->value->int32;
			break;
		case KEY_LON:
			curLon = tuple->value->int32;
			break;
		case KEY_LOCATION_NAME:
			strncpy(curLocationName, tuple->value->cstring, sizeof(curLocationName));
			curLocationName[sizeof(curLocationName) - 1] = 0;
			text_layer_set_text(tapLocationLayer, curLocationName);
			break;
		case KEY_CLOUD_COVER:
			cloudCover = tuple->value->uint8;
			break;
		case KEY_WEATHER_ICON:
			handle_weather_icon(tuple->value->uint8);
			break;
		case KEY_TEMPERATURE:
			strncpy(currentTemperature, tuple->value->cstring, sizeof(currentTemperature));
			currentTemperature[sizeof(currentTemperature) - 1] = 0;
			{
				// remove any fractional degrees - we haven't got space here
				char *firstDecimalPoint = strchr(currentTemperature, '.');
				char *degreeSymbol = strchr(currentTemperature, 0xc2);
				if (firstDecimalPoint) {
					if (degreeSymbol) {
						memmove(firstDecimalPoint, degreeSymbol, 3);
						*(firstDecimalPoint + 2) = 0;
					}
					else
						*firstDecimalPoint = 0;
				}
			}
			text_layer_set_text(temperatureLayer, currentTemperature);
			break;
		case KEY_SUNRISE_HOUR:
			sunriseHour = tuple->value->int8;
			break;
		case KEY_SUNRISE_MINUTE:
			sunriseMinute = tuple->value->int8;
			break;
		case KEY_SUNSET_HOUR:
			sunsetHour = tuple->value->int8;
			break;
		case KEY_SUNSET_MINUTE:
			sunsetMinute = tuple->value->int8;
			break;
		case KEY_NOWCAST_MINUTES:
			nowcastReceivedTime = time(NULL);
			nowcastLength = tuple->length;
			if (nowcastLength > NOWCAST_MAX_INTERVALS)
				nowcastLength = NOWCAST_MAX_INTERVALS;
			//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "nowcast len %d", nowcastLength);
			memset(nowcastTimes, 0, NOWCAST_MAX_INTERVALS);
			memcpy(nowcastTimes, tuple->value->data, nowcastLength);
			break;
		case KEY_NOWCAST_PRECIPITATION:
			memset(nowcastPrecipitation, 0, NOWCAST_MAX_INTERVALS);
			if (nowcastLength > NOWCAST_MAX_INTERVALS)
				nowcastLength = NOWCAST_MAX_INTERVALS;
			memcpy(nowcastPrecipitation, tuple->value->data, nowcastLength);
			//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "nowcast prcp len %d", (int)tuple->length);
			//~ for (int i = 0; i < tuple->length; ++i)
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "nowcast %d %d", (int)nowcastTimes[i], nowcastPrecipitation[i]);
			break;
		case KEY_FORECAST_BEGIN:
			forecastPrecipitationLength = 0;
			memset(forecastPrecipitationTimes, 0, sizeof(forecastPrecipitationTimes));
			memset(forecastPrecipitation, 0, sizeof(forecastPrecipitation));
			forecastLength = 0;
			minForecastTemperature = 8000;
			maxForecastTemperature = -8000;
			memset(forecastTimes, 0, sizeof(forecastTimes));
			memset(forecastTemperature, 0, sizeof(forecastTemperature));
			break;
		case KEY_PRECIPITATION_MINUTES:
			currentForecastPrecipitationTime = tuple->value->int16;
			break;
		case KEY_FORECAST_PRECIPITATION:
			currentForecastPrecipitation = tuple->value->uint8;
			break;
		case KEY_FORECAST_PRECIPITATION_MIN:
			currentForecastPrecipitationMin = tuple->value->uint8;
			break;
		case KEY_FORECAST_PRECIPITATION_MAX:
			currentForecastPrecipitationMax = tuple->value->uint8;
			break;
		case KEY_FORECAST_MINUTES:
			currentForecastTime = tuple->value->int16;
			break;
		case KEY_FORECAST_TEMPERATURE:
			currentForecastTemperature = tuple->value->int16;
			if (currentForecastTemperature < minForecastTemperature) {
				minForecastTemperature = currentForecastTemperature;
				if (minForecastTemperature % 100)
					minForecastTemperature -= 100 + minForecastTemperature % 100;
			}
			if (currentForecastTemperature > maxForecastTemperature) {
				maxForecastTemperature = currentForecastTemperature;
				if (maxForecastTemperature % 100)
					maxForecastTemperature += 100 - maxForecastTemperature % 100;
			}
			break;
		case KEY_PREF_UPDATE_FREQ:
			weatherUpdateFrequency = tuple->value->int16;
			break;
		default:
			APP_LOG(APP_LOG_LEVEL_WARNING, "unexpected key %d", (int)tuple->key);
		}
		tuple = dict_read_next(iter);
	}
	if (currentForecastPrecipitationTime) {
		if (forecastPrecipitationLength < FORECAST_MAX_INTERVALS) {
			//~ if (currentForecastPrecipitation > 0)
				//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast %d precip %d\n", (int)currentForecastPrecipitationTime, (int)currentForecastPrecipitation);
			forecastPrecipitationTimes[forecastPrecipitationLength] = currentForecastPrecipitationTime;
			forecastPrecipitation[forecastPrecipitationLength] = currentForecastPrecipitation;
			forecastPrecipitationMin[forecastPrecipitationLength] = currentForecastPrecipitationMin;
			forecastPrecipitationMax[forecastPrecipitationLength] = currentForecastPrecipitationMax;
			++forecastPrecipitationLength;
		}
	} else if (currentForecastTime) {
		if (forecastLength < FORECAST_MAX_INTERVALS) {
			//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "forecast %d temp %d min %d max %d\n",
				//~ currentForecastTime, currentForecastTemperature, minForecastTemperature, maxForecastTemperature);
			forecastTimes[forecastLength] = currentForecastTime;
			forecastTemperature[forecastLength] = currentForecastTemperature;
			++forecastLength;
		}
	} else {
		circleLayerUpdate();
	}
}

static void in_dropped_handler(AppMessageResult reason, void *context)
{
	APP_LOG(APP_LOG_LEVEL_WARNING, "App Message Dropped!");
}

static void out_failed_handler(DictionaryIterator *failed, AppMessageResult reason, void *context)
{
	if (reason == APP_MSG_SEND_REJECTED)
		return; // nacks seem to happen all the time... not sure why
	switch (reason) {
		case APP_MSG_SEND_TIMEOUT:
			APP_LOG(APP_LOG_LEVEL_WARNING, "failed to send: APP_MSG_SEND_TIMEOUT (%d)", APP_MSG_SEND_TIMEOUT);
			break;
		case APP_MSG_SEND_REJECTED:
			APP_LOG(APP_LOG_LEVEL_WARNING, "failed to send: APP_MSG_SEND_REJECTED (%d)", APP_MSG_SEND_REJECTED);
			break;
		case APP_MSG_NOT_CONNECTED:
			APP_LOG(APP_LOG_LEVEL_WARNING, "failed to send: APP_MSG_NOT_CONNECTED (%d)", APP_MSG_NOT_CONNECTED);
			break;
		case APP_MSG_APP_NOT_RUNNING:
			APP_LOG(APP_LOG_LEVEL_WARNING, "failed to send: APP_MSG_APP_NOT_RUNNING (%d)", APP_MSG_APP_NOT_RUNNING);
			break;
		default:
			APP_LOG(APP_LOG_LEVEL_WARNING, "failed to send: %d", reason);
			break;
	}
}

static void window_load(Window *window) {
	Layer *window_layer = window_get_root_layer(window);
	GRect bounds = layer_get_bounds(window_layer);

	main_layer = layer_create(bounds);
	layer_add_child(window_layer, main_layer);
	tap_layer = layer_create(bounds);
	layer_add_child(window_layer, tap_layer);

	bluetooth_layer = bitmap_layer_create(GRect(0, bounds.size.h - 14, 14, 14));
	layer_add_child(main_layer, bitmap_layer_get_layer(bluetooth_layer));
	bluetooth_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_BLUETOOTH);
	handle_bluetooth(bluetooth_connection_service_peek());

	batteryGraphLayer = layer_create(GRect(110, bounds.size.h - 14, 32, 14));
	layer_set_update_proc(batteryGraphLayer, updateBatteryGraphLayer);
	layer_add_child(main_layer, batteryGraphLayer);

	charging_layer = bitmap_layer_create(GRect(117, 2, 14, 10));
	bitmap_layer_set_background_color(charging_layer, GColorClear);
	bitmap_layer_set_compositing_mode(charging_layer, GCompOpAssignInverted);
	layer_add_child(main_layer, bitmap_layer_get_layer(charging_layer));
	charging_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_CHARGING);

	temperatureLayer = text_layer_create(GRect(0, -6, 50, 24));
	//~ temperatureLayer = text_layer_create(GRect(bounds.size.w - 50, -4, 50, 22));
	text_layer_set_text_color(temperatureLayer, COLOR_TEMPERATURE);
	text_layer_set_background_color(temperatureLayer, GColorClear);
	text_layer_set_font(temperatureLayer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
	layer_add_child(main_layer, text_layer_get_layer(temperatureLayer));

	weather_icon_layer = bitmap_layer_create(GRect(bounds.size.w - 22, -1, 22, 22));
	//~ weather_icon_layer = bitmap_layer_create(GRect(0, -1, 22, 22));
	layer_add_child(main_layer, bitmap_layer_get_layer(weather_icon_layer));

	int diameter = min(bounds.size.w - 1, bounds.size.h - 16 - 1);
	circleLayer = layer_create(GRect(0, 0, diameter, diameter));
	layer_set_update_proc(circleLayer, paintCircleLayer);
	layer_add_child(main_layer, circleLayer);
	circleLayerUpdate();

	GRect text_time_rect = GRect(0, 0, bounds.size.w, 50);
	grect_align(&text_time_rect, &bounds, GAlignCenter, false);
	text_time_layer = text_layer_create(text_time_rect);
	text_layer_set_text_color(text_time_layer, COLOR_TIME);
	text_layer_set_background_color(text_time_layer, GColorClear);
	text_layer_set_font(text_time_layer, fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS));
	text_layer_set_text_alignment(text_time_layer, GTextAlignmentCenter);
	layer_add_child(main_layer, text_layer_get_layer(text_time_layer));

#ifdef PBL_HEALTH
	text_time_rect.origin.y += 40;
	stepsLayer = text_layer_create(text_time_rect);
	text_layer_set_text_color(stepsLayer, COLOR_STEPS);
	text_layer_set_background_color(stepsLayer, GColorClear);
	text_layer_set_font(stepsLayer, fonts_get_system_font(FONT_KEY_GOTHIC_18));
	text_layer_set_text_alignment(stepsLayer, GTextAlignmentCenter);
	layer_add_child(main_layer, text_layer_get_layer(stepsLayer));
#endif

	text_time_rect.origin.y = bounds.size.h / 2;
	tapTimeLayer = text_layer_create(text_time_rect);
	text_layer_set_text_color(tapTimeLayer, COLOR_TIME);
	text_layer_set_background_color(tapTimeLayer, GColorClear);
	text_layer_set_font(tapTimeLayer, fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS));
	text_layer_set_text_alignment(tapTimeLayer, GTextAlignmentCenter);
	layer_add_child(tap_layer, text_layer_get_layer(tapTimeLayer));

	text_time_rect.origin.y += 40;
	tapLocationLayer = text_layer_create(text_time_rect);
	text_layer_set_text_color(tapLocationLayer, COLOR_LOCATION);
	text_layer_set_background_color(tapLocationLayer, GColorClear);
	text_layer_set_font(tapLocationLayer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
	text_layer_set_text_alignment(tapLocationLayer, GTextAlignmentCenter);
	layer_add_child(tap_layer, text_layer_get_layer(tapLocationLayer));

	dateLayer = text_layer_create(GRect(14, bounds.size.h - 24, bounds.size.w - 32 - 14, 24));
	text_layer_set_text_alignment(dateLayer, GTextAlignmentCenter);
	text_layer_set_text_color(dateLayer, COLOR_DATE);
	text_layer_set_background_color(dateLayer, GColorClear);
	text_layer_set_font(dateLayer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
	layer_add_child(main_layer, text_layer_get_layer(dateLayer));

	weatherPlotLayer = layer_create(GRect(0, 0, bounds.size.w, bounds.size.h / 2));
	layer_set_update_proc(weatherPlotLayer, paintWeatherPlot);
	layer_add_child(tap_layer, weatherPlotLayer);

	show_page(0);

	dateLayerUpdate();
	timeLayerUpdate();
#ifdef PBL_HEALTH
	stepsLayerUpdate();
#endif

	tick_timer_service_subscribe(SECOND_UNIT, handle_second_tick);
	bluetooth_connection_service_subscribe(&handle_bluetooth);
	handle_battery(battery_state_service_peek());
	battery_state_service_subscribe(&handle_battery);
	accel_tap_service_subscribe(handle_tap);
}

static void window_appear(Window *window) {
	//~ send_hello();
}

static void window_disappear(Window *window) {
}

static void window_unload(Window *window) {
	tick_timer_service_unsubscribe();
	layer_destroy(circleLayer);
	layer_destroy(weatherPlotLayer);
	layer_destroy(main_layer);
	layer_destroy(tap_layer);
	text_layer_destroy(text_time_layer);
	text_layer_destroy(tapTimeLayer);
	text_layer_destroy(tapLocationLayer);
	bitmap_layer_destroy(bluetooth_layer);
	gbitmap_destroy(bluetooth_bitmap);
	bitmap_layer_destroy(charging_layer);
	gbitmap_destroy(charging_bitmap);
	bitmap_layer_destroy(weather_icon_layer);
	if (weather_bitmap)
		gbitmap_destroy(weather_bitmap);
}

static void window_init(void) {
    time_t tt = time(NULL);
	memcpy(&currentTime, localtime(&tt), sizeof(struct tm));
	window = window_create();
	window_set_background_color(window, GColorBlack);
	window_set_window_handlers(window, (WindowHandlers) {
		.load = window_load,
		.appear = window_appear,
		.disappear = window_disappear,
		.unload = window_unload,
	});
	const bool animated = true;
	window_stack_push(window, animated);

#ifdef DEBUG_MSG
	APP_LOG(APP_LOG_LEVEL_DEBUG, "max mailboxen %d %d", (int)app_message_inbox_size_maximum(), (int)app_message_outbox_size_maximum());
#endif
	app_message_register_inbox_received(in_received_handler);
	app_message_register_inbox_dropped(in_dropped_handler);
	app_message_register_outbox_failed(out_failed_handler);
	Tuplet value = TupletInteger(KEY_NONE, 0);
	app_message_open(1024, dict_calc_buffer_size_from_tuplets(&value, 1));
#ifdef PBL_HEALTH
	health_init();
#endif
}

static void window_deinit(void) {
	window_destroy(window);
#ifdef PBL_HEALTH
	health_deinit();
#endif
}

int main(void) {
	window_init();
	app_event_loop();
	window_deinit();
}
