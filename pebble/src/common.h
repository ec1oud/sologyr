#pragma once

#include <pebble.h>

// Used to turn off logging
#define DEBUG false

// Number of days the averaging mechanism takes into account
#define PAST_DAYS_CONSIDERED 7

// Delay after launch before querying the Health API
#define LOAD_DATA_DELAY 500

#define MINUTES_PER_HEALTH_INTERVAL 15

#define HEALTH_INTERVAL_COUNT 24 * 60 / MINUTES_PER_HEALTH_INTERVAL

#define NOWCAST_MAX_INTERVALS 16
#define FORECAST_MAX_INTERVALS 128

#define FORECAST_CHART_MINUTES_PER_PIXEL 30
#define FORECAST_CHART_PIXELS_PER_MM_PRECIPITATION 5

typedef enum {
	AppKeyCurrentAverage = 0,
	AppKeyDailyAverage,
	AppKeyCurrentSteps,
	AppKeyStepIntervals,
	AppKeyLastIntervalLoad,
	AppKeyVmcSunday, // tm_wday = 0
	AppKeyVmcMonday,
	AppKeyVmcTuesday,
	AppKeyVmcWednesday,
	AppKeyVmcThursday,
	AppKeyVmcFriday,
	AppKeyVmcSaturday
} AppKey;

enum DictKey {
	KEY_NONE = 0,
	KEY_HELLO = 1,
	KEY_ACTIVE_INTERVAL = 2, // inform phone that the watch usually sees activity, so keep data up-to-date
	KEY_TAP = 3,
	KEY_LAT = 10,
	KEY_LON = 11,
	KEY_SUNRISE_HOUR = 12,
	KEY_SUNRISE_MINUTE = 13,
	KEY_SUNSET_HOUR = 14,
	KEY_SUNSET_MINUTE = 15,
	KEY_TEMPERATURE = 20,
	KEY_WEATHER_ICON = 21,
	KEY_CLOUD_COVER = 22,
	KEY_FORECAST_BEGIN = 39,
	KEY_NOWCAST_MINUTES = 40, // how far in the future
	KEY_NOWCAST_PRECIPITATION = 41,
	KEY_PRECIPITATION_MINUTES = 42, // minutes after last midnight (beginning of today)
	KEY_FORECAST_PRECIPITATION = 43
};

// Colors for the central 24-hour disk
#define COLOR_SUN GColorWindsorTan
#define COLOR_PRECIPITATION GColorCobaltBlue
#define COLOR_NOWCAST GColorBlue
#define COLOR_CLOCK_RING GColorWhite
#define COLOR_CLOCK_POINTER GColorOrange
#define COLOR_CLOCK_POINTER_SHADOW GColorBlack
#define COLOR_STEPS_DAY GColorInchworm
#define COLOR_STEPS_NIGHT GColorJaegerGreen
#define COLOR_STEPS_YESTERDAY GColorKellyGreen
#define COLOR_STEPS_YESTERDAY_NIGHT GColorMidnightGreen

// Colors for the battery graph
#define COLOR_BATTERY_BACKGROUND GColorBlack
#define COLOR_BATTERY_FILL GColorMintGreen
#define COLOR_BATTERY_STROKE GColorBrass

// Colors for various labels
#define COLOR_TEMPERATURE GColorWhite
#define COLOR_TIME GColorCeleste
#define COLOR_DATE GColorLimerick
#define COLOR_STEPS GColorInchworm

// Colors for the yr forecast chart
#define COLOR_CHART_PRECIPITATION_MAX GColorCobaltBlue
#define COLOR_CHART_PRECIPITATION GColorPictonBlue
#define COLOR_CHART_PRECIPITATION_MIN GColorCyan
#define COLOR_CHART_DAY_DIV GColorLightGray
