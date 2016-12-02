#include <pebble.h>

static Window *window;
static TextLayer *text_time_layer;
static BitmapLayer *bluetooth_layer;
static BitmapLayer *charging_layer;
static Layer *batteryGraphLayer;
static int batteryPct = 0;
static int fontSize = 0;
static uint8_t sunriseHour = 8;
static uint8_t sunriseMinute = 50;
static uint8_t sunsetHour = 15;
static uint8_t sunsetMinute = 24;
static bool bluetoothConnected = 0;
static Layer *circleLayer;
static GBitmap *bluetooth_bitmap = NULL;
static GBitmap *charging_bitmap = NULL;

static const int minutesPerDay = 24 * 60;

static int min(int one, int other)
{
	return (one < other ? one : other);
}

static void handle_second_tick(struct tm *tick_time, TimeUnits units_changed)
{
	static char time_text[] = "00:00:00";
	static const char *time_format = "%H:%M:%S";
	strftime(time_text, sizeof(time_text), time_format, tick_time);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tick %s", time_text);
	text_layer_set_text(text_time_layer, time_text);
}

static void updateCircleLayer(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	GRect fillCircle = grect_crop(layerBounds, 2);
	graphics_context_set_stroke_color(ctx, GColorWhite);
	graphics_draw_circle(ctx, grect_center_point(&layerBounds), layerBounds.size.w / 2);
	int sunriseAngle = (sunriseHour * 60 + sunriseMinute) * 360 / minutesPerDay - 180;
	int sunsetAngle = (sunsetHour * 60 + sunsetMinute) * 360 / minutesPerDay - 180;
	//~ graphics_context_set_fill_color(ctx, GColorRed);
	//~ graphics_fill_radial(ctx, fillCircle, GCornerNone, fillCircle.size.w / 2,  DEG_TO_TRIGANGLE(sunsetAngle - 2),  DEG_TO_TRIGANGLE(sunsetAngle));
	graphics_context_set_fill_color(ctx, GColorYellow);
	graphics_fill_radial(ctx, fillCircle, GCornerNone, fillCircle.size.w / 2,  DEG_TO_TRIGANGLE(sunriseAngle),  DEG_TO_TRIGANGLE(sunsetAngle));
	//~ graphics_fill_radial(ctx, fillCircle, GCornerNone, fillCircle.size.w / 2,  DEG_TO_TRIGANGLE(sunriseAngle - 2),  DEG_TO_TRIGANGLE(sunriseAngle));
}

static void updateBatteryGraphLayer(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "updateBatteryGraphLayer bounds %d %d %d %d", layerBounds.origin.x, layerBounds.origin.y, layerBounds.size.w, layerBounds.size.h);
	graphics_context_set_fill_color(ctx, GColorBlack);
	graphics_fill_rect(ctx, layerBounds, 0, GCornerNone);
	graphics_context_set_fill_color(ctx, GColorWhite);
	graphics_context_set_stroke_color(ctx, GColorWhite);
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

static void handle_tap(AccelAxisType axis, int32_t direction)
{
	APP_LOG(APP_LOG_LEVEL_DEBUG, "tap %d %d", (int)axis, (int)direction);
}

static void handle_bluetooth(bool connected)
{
	bluetoothConnected = connected;
	bitmap_layer_set_bitmap(bluetooth_layer, bluetoothConnected ? bluetooth_bitmap : 0);
}

static void handle_battery(BatteryChargeState charge_state)
{
	batteryPct = charge_state.charge_percent;
	bitmap_layer_set_bitmap(charging_layer, charge_state.is_charging ? charging_bitmap : 0);
	layer_mark_dirty(batteryGraphLayer);
}

static void window_window_load(Window *window) {
	Layer *window_layer = window_get_root_layer(window);
	GRect bounds = layer_get_bounds(window_layer);

	text_time_layer = text_layer_create(GRect(3, -10, 72, 28));
	text_layer_set_text_color(text_time_layer, GColorWhite);
	text_layer_set_background_color(text_time_layer, GColorClear);
	text_layer_set_font(text_time_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
	//~ text_layer_set_text_alignment(text_time_layer, GTextAlignmentRight);
	layer_add_child(window_layer, text_layer_get_layer(text_time_layer));

	bluetooth_layer = bitmap_layer_create(GRect(95, 0, 14, 14));
	layer_add_child(window_layer, bitmap_layer_get_layer(bluetooth_layer));
	bluetooth_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_BLUETOOTH);
	handle_bluetooth(bluetooth_connection_service_peek());

	batteryGraphLayer = layer_create(GRect(109, 0, 32, 14));
	layer_set_update_proc(batteryGraphLayer, updateBatteryGraphLayer);
	layer_add_child(window_layer, batteryGraphLayer);

	charging_layer = bitmap_layer_create(GRect(117, 2, 14, 10));
	bitmap_layer_set_background_color(charging_layer, GColorClear);
	bitmap_layer_set_compositing_mode(charging_layer, GCompOpAssignInverted);
	layer_add_child(window_layer, bitmap_layer_get_layer(charging_layer));
	charging_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_CHARGING);

	int diameter = min(bounds.size.w - 1, bounds.size.h - 16 - 1);
	circleLayer = layer_create(GRect(0, 16, diameter, diameter));
	layer_set_update_proc(circleLayer, updateCircleLayer);
	layer_add_child(window_layer, circleLayer);
	layer_mark_dirty(circleLayer);

	tick_timer_service_subscribe(SECOND_UNIT, handle_second_tick);
	bluetooth_connection_service_subscribe(&handle_bluetooth);
	handle_battery(battery_state_service_peek());
	battery_state_service_subscribe(&handle_battery);
	accel_tap_service_subscribe(handle_tap);
}

static void window_window_unload(Window *window) {
	tick_timer_service_unsubscribe();
	layer_destroy(circleLayer);
	text_layer_destroy(text_time_layer);
	bitmap_layer_destroy(bluetooth_layer);
	gbitmap_destroy(bluetooth_bitmap);
	bitmap_layer_destroy(charging_layer);
	gbitmap_destroy(charging_bitmap);
}

static void window_init(void) {
	window = window_create();
	window_set_background_color(window, GColorBlack);
	window_set_window_handlers(window, (WindowHandlers) {
		.load = window_window_load,
		.unload = window_window_unload,
	});
	const bool animated = true;
	window_stack_push(window, animated);
}

static void window_deinit(void) {
	window_destroy(window);
}

int main(void) {
	window_init();
	APP_LOG(APP_LOG_LEVEL_DEBUG, "Done initializing, pushed window: %p", window);
	app_event_loop();
	window_deinit();
}
