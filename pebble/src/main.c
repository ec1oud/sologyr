#include <pebble.h>

enum DictKey {
	KEY_NONE = 0,
	KEY_HELLO = 1,
	KEY_LAT = 10,
	KEY_LON = 11,
	KEY_SUNRISE_HOUR = 12,
	KEY_SUNRISE_MINUTE = 13,
	KEY_SUNSET_HOUR = 14,
	KEY_SUNSET_MINUTE = 15,
	KEY_TEMPERATURE = 20
};

static Window *window;
static TextLayer *text_time_layer;
static TextLayer *dateLayer;
static TextLayer *temperatureLayer;
static BitmapLayer *bluetooth_layer;
static BitmapLayer *charging_layer;
static Layer *batteryGraphLayer;
static int batteryPct = 0;
static int fontSize = 0;
struct tm *currentTime;
static uint8_t sunriseHour = 100;
static uint8_t sunriseMinute = 0;
static uint8_t sunsetHour = 0;
static uint8_t sunsetMinute = 0;
static char currentTemperature[12];
static bool bluetoothConnected = 0;
static Layer *circleLayer;
static GBitmap *bluetooth_bitmap = NULL;
static GBitmap *charging_bitmap = NULL;

static const int minutesPerDay = 24 * 60;

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
	strftime(date_text, sizeof(date_text), date_format, currentTime);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tick %s", time_text);
	text_layer_set_text(dateLayer, date_text);
}

void timeLayerUpdate()
{
	static char time_text[] = "00:00";
	static const char *time_format = "%H:%M";  //:%S";
	strftime(time_text, sizeof(time_text), time_format, currentTime);
	//~ APP_LOG(APP_LOG_LEVEL_DEBUG, "tick %s", time_text);
	text_layer_set_text(text_time_layer, time_text);
}

static void handle_second_tick(struct tm *tick_time, TimeUnits units_changed)
{
	*currentTime = *tick_time;
	if (units_changed & MINUTE_UNIT) {
		timeLayerUpdate();
		layer_mark_dirty(circleLayer);
	}
	if (units_changed & DAY_UNIT) {
		dateLayerUpdate();
		layer_mark_dirty(window_get_root_layer(window));
	}
}

static void updateCircleLayer(Layer *layer, GContext* ctx)
{
	GRect layerBounds = layer_get_bounds(layer);
	GRect fillCircle = grect_crop(layerBounds, 2);
	graphics_context_set_stroke_color(ctx, GColorWhite);
	graphics_draw_circle(ctx, grect_center_point(&layerBounds), layerBounds.size.w / 2);
	if (sunriseHour > 24) {
		send_hello();
	} else {
		int sunriseAngle = (sunriseHour * 60 + sunriseMinute) * 360 / minutesPerDay - 180;
		int sunsetAngle = (sunsetHour * 60 + sunsetMinute) * 360 / minutesPerDay - 180;
		graphics_context_set_fill_color(ctx, GColorYellow);
		graphics_fill_radial(ctx, fillCircle, GCornerNone, fillCircle.size.w / 3,  DEG_TO_TRIGANGLE(sunriseAngle),  DEG_TO_TRIGANGLE(sunsetAngle));
	}

	GRect innerCircle = grect_crop(fillCircle, layerBounds.size.w / 4);
	int32_t currentTimeAngle = (currentTime->tm_hour * 60 + currentTime->tm_min) * 360 / minutesPerDay - 180;
	//~ void graphics_fill_radial(GContext * ctx, GRect rect, GOvalScaleMode scale_mode, uint16_t inset_thickness, int32_t angle_start, int32_t angle_end)
	//~ GPoint gpoint_from_polar(GRect rect, GOvalScaleMode scale_mode, int32_t angle)
	//~ void graphics_draw_line(GContext * ctx, GPoint p0, GPoint p1)

	//~ graphics_context_set_fill_color(ctx, GColorBlack);
	//~ graphics_fill_radial(ctx, layerBounds, GCornerNone, layerBounds.size.w / 4,  DEG_TO_TRIGANGLE(currentTimeAngle - 2),  DEG_TO_TRIGANGLE(currentTimeAngle + 2));
	GPoint pointerInner = gpoint_from_polar(innerCircle, GCornerNone, DEG_TO_TRIGANGLE(currentTimeAngle));
	GPoint pointerOuter = gpoint_from_polar(layerBounds, GCornerNone, DEG_TO_TRIGANGLE(currentTimeAngle));
	graphics_context_set_stroke_color(ctx, GColorBlack);
	graphics_context_set_stroke_width(ctx, 7);
	graphics_draw_line(ctx, pointerInner, pointerOuter);
	graphics_context_set_stroke_color(ctx, GColorOrange);
	graphics_context_set_stroke_width(ctx, 3);
	graphics_draw_line(ctx, pointerInner, pointerOuter);
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
	//~ send_hello();
}

static void handle_bluetooth(bool connected)
{
	bluetoothConnected = connected;
	bitmap_layer_set_bitmap(bluetooth_layer, bluetoothConnected ? bluetooth_bitmap : 0);
	if (bluetoothConnected)
		send_hello();
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
		case KEY_LON:
			// TODO maybe calculate sunrise/sunset so the phone doesn't have to send it
			break;
		case KEY_TEMPERATURE:
			strncpy(currentTemperature, tuple->value->cstring, 12);
			currentTemperature[11] = 0;
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
		default:
			APP_LOG(APP_LOG_LEVEL_WARNING, "unexpected key %d", (int)tuple->key);
		}
		tuple = dict_read_next(iter);
	}
	layer_mark_dirty(circleLayer);
	//~ layer_mark_dirty(window_get_root_layer(window));
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

	bluetooth_layer = bitmap_layer_create(GRect(0, bounds.size.h - 14, 14, 14));
	layer_add_child(window_layer, bitmap_layer_get_layer(bluetooth_layer));
	bluetooth_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_BLUETOOTH);
	handle_bluetooth(bluetooth_connection_service_peek());

	batteryGraphLayer = layer_create(GRect(110, bounds.size.h - 14, 32, 14));
	layer_set_update_proc(batteryGraphLayer, updateBatteryGraphLayer);
	layer_add_child(window_layer, batteryGraphLayer);

	charging_layer = bitmap_layer_create(GRect(117, 2, 14, 10));
	bitmap_layer_set_background_color(charging_layer, GColorClear);
	bitmap_layer_set_compositing_mode(charging_layer, GCompOpAssignInverted);
	layer_add_child(window_layer, bitmap_layer_get_layer(charging_layer));
	charging_bitmap = gbitmap_create_with_resource(RESOURCE_ID_IMAGE_CHARGING);

	int diameter = min(bounds.size.w - 1, bounds.size.h - 16 - 1);
	circleLayer = layer_create(GRect(0, 0, diameter, diameter));
	layer_set_update_proc(circleLayer, updateCircleLayer);
	layer_add_child(window_layer, circleLayer);
	layer_mark_dirty(circleLayer);

	GRect text_time_rect = GRect(0, 0, bounds.size.w, 50);
	grect_align(&text_time_rect, &bounds, GAlignCenter, false);
	text_time_layer = text_layer_create(text_time_rect);
	text_layer_set_text_color(text_time_layer, GColorCeleste);
	text_layer_set_background_color(text_time_layer, GColorClear);
	text_layer_set_font(text_time_layer, fonts_get_system_font(FONT_KEY_LECO_42_NUMBERS));
	text_layer_set_text_alignment(text_time_layer, GTextAlignmentCenter);
	layer_add_child(window_layer, text_layer_get_layer(text_time_layer));

	dateLayer = text_layer_create(GRect(14, bounds.size.h - 24, bounds.size.w - 32 - 14, 24));
	text_layer_set_text_alignment(dateLayer, GTextAlignmentCenter);
	text_layer_set_text_color(dateLayer, GColorLimerick);
	text_layer_set_background_color(dateLayer, GColorClear);
	text_layer_set_font(dateLayer, fonts_get_system_font(FONT_KEY_GOTHIC_24_BOLD));
	layer_add_child(window_layer, text_layer_get_layer(dateLayer));

	temperatureLayer = text_layer_create(GRect(bounds.size.w - 32, -4, 32, 18));
	text_layer_set_text_alignment(temperatureLayer, GTextAlignmentRight);
	text_layer_set_text_color(temperatureLayer, GColorWhite);
	text_layer_set_background_color(temperatureLayer, GColorClear);
	text_layer_set_font(temperatureLayer, fonts_get_system_font(FONT_KEY_GOTHIC_18));
	layer_add_child(window_layer, text_layer_get_layer(temperatureLayer));

	dateLayerUpdate();
	timeLayerUpdate();

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
	text_layer_destroy(text_time_layer);
	bitmap_layer_destroy(bluetooth_layer);
	gbitmap_destroy(bluetooth_bitmap);
	bitmap_layer_destroy(charging_layer);
	gbitmap_destroy(charging_bitmap);
}

static void window_init(void) {
    time_t tt = time(0);
    currentTime = localtime(&tt);
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
