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
	KEY_CLOUD_COVER = 22
};
