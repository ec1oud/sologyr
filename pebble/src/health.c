#include "health.h"
#include "config.h"

typedef enum
{
	AppKeyCurrentAverage = 0,
	AppKeyDailyAverage,
	AppKeyCurrentSteps
} AppKey;

typedef enum
{
	AverageTypeCurrent = 0,
	AverageTypeDaily
} AverageType;

static int s_current_steps, s_daily_average, s_current_average;
static char s_current_steps_buffer[8];

extern void circleLayerUpdate();
struct tm *currentTime;

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

static void load_health_health_handler(void *context)
{
	s_current_steps = health_service_sum_today(HealthMetricStepCount);
	persist_write_int(AppKeyCurrentSteps, s_current_steps);

	update_average(AverageTypeDaily);
	update_average(AverageTypeCurrent);

	health_update_steps_buffer();
}

void health_reload_averages()
{
	app_timer_register(LOAD_DATA_DELAY, load_health_health_handler, NULL);
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

static void health_handler(HealthEventType event, void *context)
{
	health_set_current_steps((int)health_service_sum_today(HealthMetricStepCount));
	health_update_steps_buffer();
}

void health_init()
{
	health_service_events_subscribe(health_handler, NULL);

	// First time persist
	if (!persist_exists(AppKeyCurrentSteps)) {
		s_current_steps = 0;
		s_current_average = 0;
		s_daily_average = 0;
	} else {
		s_current_average = persist_read_int(AppKeyCurrentAverage);
		s_daily_average = persist_read_int(AppKeyDailyAverage);
		s_current_steps = persist_read_int(AppKeyCurrentSteps);
	}
	health_update_steps_buffer();

	// Avoid half-second delay loading the app by delaying API read
	health_reload_averages();
}
