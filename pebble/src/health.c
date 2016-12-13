#include "health.h"
#include "common.h"

typedef enum
{
	AverageTypeCurrent = 0,
	AverageTypeDaily
} AverageType;

static int s_current_steps, s_daily_average, s_current_average;
static char s_current_steps_buffer[8];
static uint16_t s_steps_per_interval[HEALTH_INTERVAL_COUNT]; // first interval is midnight, next is 00:15 and so on
static uint8_t s_current_interval_idx;
static time_t s_last_interval_load_time;

extern void circleLayerUpdate();

static void update_average(AverageType type)
{
	// Start time is midnight
	const time_t start = time_start_of_today();

	time_t end = start;
	int steps = 0;
	switch(type) {
		case AverageTypeDaily:
			// One whole day
			end = start + SECONDS_PER_DAY;
			break;
		case AverageTypeCurrent:
			// Time from midnight to now
			end = start + (time(NULL) - time_start_of_today());
			break;
		default:
			if (DEBUG) APP_LOG(APP_LOG_LEVEL_ERROR, "Unknown average type!");
			break;
	}

	// Check the average data is available
	HealthServiceAccessibilityMask mask = health_service_metric_averaged_accessible(
		HealthMetricStepCount, start, end, HealthServiceTimeScopeDaily);
	if (mask & HealthServiceAccessibilityMaskAvailable) {
		// Data is available, read it
		steps = (int)health_service_sum_averaged(HealthMetricStepCount, start, end,
			HealthServiceTimeScopeDaily);
	} else {
		if (DEBUG) APP_LOG(APP_LOG_LEVEL_DEBUG, "No data available for daily average");
	}

	// Store the calculated value
	switch(type) {
		case AverageTypeDaily:
			s_daily_average = steps;
			persist_write_int(AppKeyDailyAverage, s_daily_average);

			if (DEBUG) APP_LOG(APP_LOG_LEVEL_DEBUG, "Daily average: %d", s_daily_average);
			break;
		case AverageTypeCurrent:
			s_current_average = steps;
			persist_write_int(AppKeyCurrentAverage, s_current_average);

			if (DEBUG) APP_LOG(APP_LOG_LEVEL_DEBUG, "Current average: %d", s_current_average);
			break;
		default: break;							  // Handled by previous switch
	}
}

void health_update_steps_buffer()
{
	int thousands = s_current_steps / 1000;
	int hundreds = s_current_steps % 1000;
	if (thousands > 0) {
		snprintf(s_current_steps_buffer, sizeof(s_current_steps_buffer), "%d,%03d", thousands, hundreds);
	} else {
		snprintf(s_current_steps_buffer, sizeof(s_current_steps_buffer), "%d", hundreds);
	}

	circleLayerUpdate();
}

int health_get_current_steps()
{
	return s_current_steps;
}

int health_get_current_average()
{
	return s_current_average;
}

int health_get_daily_average()
{
	return s_daily_average;
}

void health_set_current_steps(int value)
{
	s_current_steps = value;
}

void health_set_current_average(int value)
{
	s_current_average = value;
}

void health_set_daily_average(int value)
{
	s_daily_average = value;
}

char* health_get_current_steps_buffer()
{
	return s_current_steps_buffer;
}

void health_update_steps_interval()
{
	HealthMinuteData intervalData[MINUTES_PER_HEALTH_INTERVAL];
	time_t secondsPerInterval = MINUTES_PER_HEALTH_INTERVAL * SECONDS_PER_MINUTE;
	time_t end = time(NULL) + secondsPerInterval;
	end -= end % secondsPerInterval;
	time_t start = end - secondsPerInterval;
	// TODO check whether this happens too often... health_service_get_minute_history does take a bit of time
	uint32_t records = health_service_get_minute_history(intervalData, MINUTES_PER_HEALTH_INTERVAL, &start, &end);
	uint16_t stepAcc = 0;
	for (uint32_t m = 0; m < records; m++)
		if (!intervalData[m].is_invalid)
			stepAcc += intervalData[m].steps;
	uint8_t intervalIdx = (uint8_t)((start - time_start_of_today()) / secondsPerInterval);
	s_steps_per_interval[intervalIdx] = stepAcc;
	if (intervalIdx != s_current_interval_idx) {
		persist_write_data(AppKeyStepIntervals, s_steps_per_interval, sizeof(s_steps_per_interval));
		s_current_interval_idx = intervalIdx;
	}
	if (s_last_interval_load_time > 0) {
		s_last_interval_load_time = time(NULL);
		persist_write_int(AppKeyLastIntervalLoad, s_last_interval_load_time);
	}
}

static void load_health_data(void *context)
{
	s_current_steps = health_service_sum_today(HealthMetricStepCount);
	persist_write_int(AppKeyCurrentSteps, s_current_steps);

	update_average(AverageTypeDaily);
	update_average(AverageTypeCurrent);

	health_update_steps_buffer();

	// loading complete s_steps_per_interval takes a long time
	// so we only do it if the watchface is newly installed or hasn't been used for more than a day
	time_t now = time(NULL);
	if (now - s_last_interval_load_time > SECONDS_PER_DAY) {
		HealthMinuteData intervalData[MINUTES_PER_HEALTH_INTERVAL];
		memset(s_steps_per_interval, 0, sizeof(s_steps_per_interval));
		time_t startToday = time_start_of_today();
		uint32_t i = (time(NULL) - startToday) / (MINUTES_PER_HEALTH_INTERVAL * SECONDS_PER_MINUTE);
		for (; i > 0; --i) {
			time_t start = startToday + i * MINUTES_PER_HEALTH_INTERVAL * SECONDS_PER_MINUTE;
			time_t end = start + (MINUTES_PER_HEALTH_INTERVAL * SECONDS_PER_MINUTE);
			uint32_t records = health_service_get_minute_history(intervalData, MINUTES_PER_HEALTH_INTERVAL, &start, &end);
			uint16_t stepAcc = 0;
			for (uint32_t m = 0; m < records; m++)
				if (!intervalData[m].is_invalid)
					stepAcc += intervalData[m].steps;
			s_steps_per_interval[i] = stepAcc;
			APP_LOG(APP_LOG_LEVEL_DEBUG, "interval %d steps (from service) %d", (int)i, (int)stepAcc);
		}
		s_last_interval_load_time = time(NULL);
		persist_write_data(AppKeyStepIntervals, s_steps_per_interval, sizeof(s_steps_per_interval));
		persist_write_int(AppKeyLastIntervalLoad, s_last_interval_load_time);
	} else {
		for (int i = 0; i < HEALTH_INTERVAL_COUNT; ++i)
			if (s_steps_per_interval[i] > 0)
				APP_LOG(APP_LOG_LEVEL_DEBUG, "interval %d steps %d", i, (int)(s_steps_per_interval[i]));
	}

	circleLayerUpdate();
}

uint16_t health_get_steps_for_interval(int i)
{
	return s_steps_per_interval[i];
}

static void health_handler(HealthEventType event, void *context)
{
	health_set_current_steps((int)health_service_sum_today(HealthMetricStepCount));
	health_update_steps_buffer();
}

void health_init()
{
	health_service_events_subscribe(health_handler, NULL);

	if (!persist_exists(AppKeyLastIntervalLoad)) {
		// First time: no persistent store
		s_current_steps = 0;
		s_current_average = 0;
		s_daily_average = 0;
		s_last_interval_load_time = 0;
		memset(s_steps_per_interval, 0, sizeof(s_steps_per_interval));
	} else {
		s_current_average = persist_read_int(AppKeyCurrentAverage);
		s_daily_average = persist_read_int(AppKeyDailyAverage);
		s_current_steps = persist_read_int(AppKeyCurrentSteps);
		s_last_interval_load_time = persist_read_int(AppKeyLastIntervalLoad);
		persist_read_data(AppKeyStepIntervals, s_steps_per_interval, sizeof(s_steps_per_interval));
	}
	health_update_steps_buffer();

	// Avoid half-second delay loading the app by delaying API read
	app_timer_register(LOAD_DATA_DELAY, load_health_data, NULL);
}

void health_deinit()
{
	persist_write_data(AppKeyStepIntervals, s_steps_per_interval, sizeof(s_steps_per_interval));
}
