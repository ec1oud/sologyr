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
	AppKeyLastIntervalLoad
} AppKey;
