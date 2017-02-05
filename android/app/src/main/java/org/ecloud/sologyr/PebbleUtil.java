package org.ecloud.sologyr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.oleaarnseth.weathercast.Forecast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

public class PebbleUtil implements WeatherListener {
    public static final UUID WATCHAPP_UUID = UUID.fromString("7a83bfb6-3795-4452-8c26-5f23e9fedf34");
    public static final int
            KEY_HELLO = 1,
            KEY_ACTIVE_INTERVAL = 2, // inform phone that the watch usually sees activity, so keep data up-to-date
            KEY_TAP = 3,
            KEY_LAT = 10,
            KEY_LON = 11,
            KEY_SUNRISE_HOUR = 12,
            KEY_SUNRISE_MINUTE = 13,
            KEY_SUNSET_HOUR = 14,
            KEY_SUNSET_MINUTE = 15,
            KEY_LOCATION_NAME = 16,
            KEY_TEMPERATURE = 20,
            KEY_WEATHER_ICON = 21,
            KEY_CLOUD_COVER = 22,
            KEY_FORECAST_TRANSACTION = 39,
            KEY_NOWCAST_MINUTES = 40, // how far in the future
            KEY_NOWCAST_PRECIPITATION = 41,
            KEY_PRECIPITATION_MINUTES = 42, // minutes after last midnight (beginning of today)
            KEY_FORECAST_PRECIPITATION = 43, // tenths of mm
            KEY_FORECAST_PRECIPITATION_MIN = 44, // tenths of mm
            KEY_FORECAST_PRECIPITATION_MAX = 45, // tenths of mm
            KEY_FORECAST_MINUTES = 46, // minutes after last midnight (beginning of today)
            KEY_FORECAST_TEMPERATURE = 47, // tenths of degrees
            KEY_PREF_UPDATE_FREQ = 100;

    private final String TAG = this.getClass().getSimpleName();
    private final int SLEEP_BETWEEN_PACKETS = 50; // milliseconds
    List<BroadcastReceiver> m_receivers = new ArrayList<>();
    int m_sendingTrans = -1;
    int m_nackCount = 0;
    boolean m_connected = false;
    boolean m_updatedSinceConnected = false;
    boolean m_busySending = false;
    WeatherService m_weatherService;

    LinkedList<Forecast> m_forecast = new LinkedList<>();
    LinkedList<Forecast> m_precipitation = new LinkedList<>();
    LinkedList<Forecast> m_nowcast = new LinkedList<>();

    public void close() {
        for (BroadcastReceiver r : m_receivers)
            m_weatherService.unregisterReceiver(r);
    }

    private void onConnected() {
        if (m_connected)
            return;
        Log.i(TAG, "Pebble connected!");
        m_connected = true;
        m_updatedSinceConnected = false;
        m_nackCount = 0;
        m_sendingTrans = 0;
        m_weatherService.addWeatherListener(PebbleUtil.this);
    }

    public PebbleUtil(WeatherService ws) {
        m_weatherService = ws;
        BroadcastReceiver rcvr = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onConnected();
            }
        };
        PebbleKit.registerPebbleConnectedReceiver(m_weatherService, rcvr);
        m_receivers.add(rcvr);

        rcvr = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.i(TAG, "Pebble disconnected!");
                m_connected = false;
                m_weatherService.removeWeatherListener(PebbleUtil.this);
            }
        };
        PebbleKit.registerPebbleDisconnectedReceiver(m_weatherService, rcvr);
        m_receivers.add(rcvr);

        PebbleKit.PebbleAckReceiver parcvr = new PebbleKit.PebbleAckReceiver(PebbleUtil.WATCHAPP_UUID) {
            @Override
            public void receiveAck(Context context, int transactionId) {
//                Log.i(TAG, "Received ack for transaction " + transactionId);
                // if the phone app starts after the pebble app, we only receive an ack,
                // but nothing in the pebble connected receiver
                onConnected();
                m_nackCount = 0;
            }
        };
        PebbleKit.registerReceivedAckHandler(m_weatherService, parcvr);
        m_receivers.add(parcvr);

        PebbleKit.PebbleNackReceiver pnrcvr = new PebbleKit.PebbleNackReceiver(PebbleUtil.WATCHAPP_UUID) {
            @Override
            public void receiveNack(Context context, int transactionId) {
                if (transactionId != 255)
                    m_sendingTrans = transactionId;
                Log.i(TAG, "Received nack " + m_nackCount + " for transaction " + transactionId +
                        " was sending trans " + m_sendingTrans);
                // TODO retry?
            }
        };
        PebbleKit.registerReceivedNackHandler(m_weatherService, pnrcvr);
        m_receivers.add(pnrcvr);

        PebbleKit.PebbleDataReceiver pdrcvr = new PebbleKit.PebbleDataReceiver(PebbleUtil.WATCHAPP_UUID) {
            @Override
            public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
                PebbleKit.sendAckToPebble(m_weatherService.getApplicationContext(), transactionId);
                m_nackCount = 0;
//                Log.i(TAG, "Received txn " + transactionId + ": " + data.toJsonString());
                PebbleKit.sendAckToPebble(context, transactionId);
                if (data.contains(PebbleUtil.KEY_HELLO)) {
                    Log.i(TAG, "Pebble says hello");
                    m_weatherService.updateWeather(false);
                } else if (data.contains(PebbleUtil.KEY_ACTIVE_INTERVAL)) {
                    Log.i(TAG, "Pebble says predicted activity level will be " + data.getUnsignedIntegerAsLong(KEY_ACTIVE_INTERVAL));
                    m_weatherService.updateWeather(false);
                } else if (data.contains(PebbleUtil.KEY_TAP)) {
                    long axisDirn = data.getInteger(KEY_TAP);
                    boolean updatedWeather = m_weatherService.updateWeather(false);
                    Log.i(TAG, "Pebble reports tap: axis " + (axisDirn & 0x0F) + " direction " + (axisDirn >> 4) +
                            " fetching weather update? " + updatedWeather + " pebble already updated? " + m_updatedSinceConnected);
                    if (!updatedWeather && !m_updatedSinceConnected)
                        m_weatherService.resendEverything(PebbleUtil.this);
                }
            }
        };
        PebbleKit.registerReceivedDataHandler(m_weatherService, pdrcvr);
        m_receivers.add(pdrcvr);

        m_weatherService.m_prefs.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
//                        Log.d(TAG, "pref changed: " + key);
                        if (key.equals("weather_update_frequency"))
                            sendPreferences();
                    }
                });

        m_weatherService.addWeatherListener(this); // in case the Android app was restarted separately
    }

    public void sendPreferences() {
        PebbleDictionary out = new PebbleDictionary();
        out.addInt16(KEY_PREF_UPDATE_FREQ, (short)Integer.parseInt(m_weatherService.m_prefs.getString("weather_update_frequency", "180")));
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public void updateLocation(double lat, double lon, String name, int distance) {
//        Log.d(TAG, "updateLocation " + lat + " " + lon + " '" + name + "'");
        if (name.isEmpty())
            name = String.format("%.4f,%.4f", lat, lon);
        PebbleDictionary out = new PebbleDictionary();
        out.addInt32(KEY_LAT, (int)Math.round(lat * 1000));
        out.addInt32(KEY_LON, (int)Math.round(lon * 1000));
        out.addString(KEY_LOCATION_NAME, name);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public void updateCurrentWeather(double temperature, double cloudCover, WeatherIcon icon,
                                     double windSpeed, double windBearing, double humidity, double dewPoint,
                                     double pressure, double ozone, double precipIntensity) {
        m_updatedSinceConnected = true;
        // TODO send more to the pebble
        String tempStr = Math.round(temperature) + "°";
        if (icon == null)
            return;
//        Log.d(TAG, "updateCurrentWeather " + tempStr + " " + cloudCover + "% " + icon.getValue());
        PebbleDictionary out = new PebbleDictionary();
        out.addString(KEY_TEMPERATURE, tempStr);
        out.addUint8(KEY_CLOUD_COVER, (byte)Math.round(cloudCover * 100)); // from fraction (e.g. 0.25) to int percent, so it fits in a byte
        out.addUint8(KEY_WEATHER_ICON, icon.getValue());
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public static Date getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private void sendPrecipitation()
    {
        Collections.sort(m_precipitation);
        int utcOffset = TimeZone.getDefault().getRawOffset();
        long startOfToday = getStartOfDay().getTime();
        long minutesSinceStartOfToday = (Calendar.getInstance().getTime().getTime() - startOfToday) / 60000;
        for (Forecast f : m_precipitation) {
            if (m_nackCount > 5)
                return;
            long minInFuture = (f.getTimeFrom().getTime() - startOfToday + utcOffset) / 60000;
            // 172px, 1 px per half-hour period = 5160 minutes; but
            // minInFuture is from startOfToday so we usually need to go higher than 5160
            if (minInFuture - minutesSinceStartOfToday < 5200) {
                PebbleDictionary out = new PebbleDictionary();
                out.addInt16(KEY_PRECIPITATION_MINUTES, (short)minInFuture);
                out.addUint8(KEY_FORECAST_PRECIPITATION, (byte) Math.round(f.getPrecipitation().getPrecipitation() * 10));
                out.addUint8(KEY_FORECAST_PRECIPITATION_MIN, (byte) Math.round(f.getPrecipitation().getPrecipitationMin() * 10));
                out.addUint8(KEY_FORECAST_PRECIPITATION_MAX, (byte) Math.round(f.getPrecipitation().getPrecipitationMax() * 10));
                PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
                try {
                    Thread.sleep(SLEEP_BETWEEN_PACKETS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendForecast()
    {
        Collections.sort(m_forecast);
        int utcOffset = TimeZone.getDefault().getRawOffset();
        long startOfToday = getStartOfDay().getTime();
        long minutesSinceStartOfToday = (Calendar.getInstance().getTime().getTime() - startOfToday) / 60000;
        for (Forecast f : m_forecast) {
            if (m_nackCount > 5)
                return;
            long minInFuture = (f.getTimeFrom().getTime() - startOfToday + utcOffset) / 60000;
            // 172px, 1 px per half-hour period = 5160 minutes; but
            // minInFuture is from startOfToday so we usually need to go higher than 5160
            if (minInFuture - minutesSinceStartOfToday < 5200 && f.getTemperature() != null) {
                PebbleDictionary out = new PebbleDictionary();
                out.addInt16(KEY_FORECAST_MINUTES, (short)minInFuture);
                out.addInt16(KEY_FORECAST_TEMPERATURE, (short)Math.round(f.getTemperature().getTemperatureDouble() * 10));
                // TODO add wind speed? what else?
                PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
                try {
                    Thread.sleep(SLEEP_BETWEEN_PACKETS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void updateForecast(LinkedList<Forecast> fs)
    {
        if (fs == null)
            return;
        m_updatedSinceConnected = true;
        // Some Forecast objects have both times and precipitation;
        // others have everything else and only timeFrom.
        m_forecast = new LinkedList<>();
        m_precipitation = new LinkedList<>();
        int precipCount = 0;

        for (Forecast f : fs)
            if (f.getPrecipitation() == null) {
                m_forecast.add(f);
//                Log.d(TAG, "forecast:" + f.toString());
            } else if (f.getPrecipitation().getPrecipitation() > 0 && precipCount < 128) {
                m_precipitation.add(f);
                ++precipCount;
//                Log.d(TAG, f.getTimeFrom() + " precipitation: " + f.getPrecipitation());
            }
        if (m_busySending)
            return;
        m_busySending = true;
        PebbleDictionary out = new PebbleDictionary();
        out.addUint8(KEY_FORECAST_TRANSACTION, (byte)1);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
        sendPrecipitation();
        sendForecast();
        out = new PebbleDictionary();
        out.addUint8(KEY_FORECAST_TRANSACTION, (byte)0);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
        m_busySending = false;
    }

    private void sendNowCast()
    {
        if (m_busySending)
            return;
        m_busySending = true;
        long now = System.currentTimeMillis();
        int utcOffset = TimeZone.getDefault().getRawOffset();
        PebbleDictionary out = new PebbleDictionary();
        int len = m_nowcast.size();
        byte[] precipitation = new byte[len]; // in tenths of mm
        byte[] minutesInFuture = new byte[len];
        int i = 0;
        for (Forecast f : m_nowcast) {
            if (m_nackCount > 5)
                return;
            precipitation[i] = (byte) Math.round(f.getPrecipitation().getPrecipitation() * 10);
            minutesInFuture[i] = (byte)((f.getTimeFrom().getTime() - now + utcOffset) / 60000);
            Log.d(TAG, "nowcast " + f.getTimeFrom() + " (" + minutesInFuture[i] + " min from now)" + ": " + f.getPrecipitation());
            ++i;
            try {
                Thread.sleep(SLEEP_BETWEEN_PACKETS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        out.addBytes(KEY_NOWCAST_MINUTES, minutesInFuture);
        out.addBytes(KEY_NOWCAST_PRECIPITATION, precipitation);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
        m_busySending = false;
    }

    public void updateNowCast(LinkedList<Forecast> nowcast)
    {
        m_nowcast = nowcast;
        m_updatedSinceConnected = true;
        if (m_nowcast != null)
            sendNowCast();
    }

    public void updateSunriseSunset(int sunriseHour, int sunriseMinute, int sunsetHour, int sunsetMinute) { }
}
