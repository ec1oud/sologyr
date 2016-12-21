package org.ecloud.sologyr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;
import com.oleaarnseth.weathercast.Forecast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
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
            KEY_TEMPERATURE = 20,
            KEY_WEATHER_ICON = 21,
            KEY_CLOUD_COVER = 22,
            KEY_NOWCAST_MINUTES = 40, // how far in the future
            KEY_NOWCAST_PRECIPITATION = 41;

    private final String TAG = this.getClass().getSimpleName();
    List<BroadcastReceiver> m_receivers = new ArrayList<>();
    int m_sendingTrans = -1;
    int m_nackCount = 0;
    boolean m_connected = false;
    WeatherService m_weatherService;

    public void close() {
        for (BroadcastReceiver r : m_receivers)
            m_weatherService.unregisterReceiver(r);
    }

    private void onConnected() {
        if (m_connected)
            return;
        Log.i(TAG, "Pebble connected!");
        m_connected = true;
        m_nackCount = 0;
        m_sendingTrans = 0;
        m_weatherService.addWeatherListener(PebbleUtil.this);
    }

    public PebbleUtil(WeatherService ws) {
        Log.d(TAG, "created");
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
                if (++m_nackCount > 5) {
                    m_connected = false;
                    m_weatherService.removeWeatherListener(PebbleUtil.this);
                }
                if (transactionId != 255)
                    m_sendingTrans = transactionId;
                Log.i(TAG, "Received nack " + m_nackCount + " for transaction " + transactionId +
                        " was sending trans " + m_sendingTrans + " still assume connected? " + m_connected);
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
                    m_weatherService.resendEverything(PebbleUtil.this);
                } else if (data.contains(PebbleUtil.KEY_ACTIVE_INTERVAL)) {
                    Log.i(TAG, "Pebble says predicted activity level will be " + data.getUnsignedIntegerAsLong(KEY_ACTIVE_INTERVAL));
                    m_weatherService.updateWeather(false);
                    m_weatherService.resendEverything(PebbleUtil.this);
                } else if (data.contains(PebbleUtil.KEY_TAP)) {
                    long axisDirn = data.getInteger(KEY_TAP);
                    Log.i(TAG, "Pebble reports tap: axis " + (axisDirn & 0x0F) + " direction " + (axisDirn >> 4));
                    m_weatherService.resendEverything(PebbleUtil.this);
                }
            }
        };
        PebbleKit.registerReceivedDataHandler(m_weatherService, pdrcvr);
        m_receivers.add(pdrcvr);

        m_weatherService.addWeatherListener(this); // in case the Android app was restarted separately
    }

    public void updateLocation(double lat, double lon) {
        Log.d(TAG, "updateLocation " + lat + " " + lon);
        PebbleDictionary out = new PebbleDictionary();
        out.addInt32(KEY_LAT, (int)Math.round(lat * 1000));
        out.addInt32(KEY_LON, (int)Math.round(lon * 1000));
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public void updateCurrentWeather(double temperature, double cloudCover, WeatherIcon icon) {
        String tempStr = Math.round(temperature) + "°";
        if (icon == null)
            return;
        Log.d(TAG, "updateCurrentWeather " + tempStr + " " + cloudCover + "% " + icon.getValue());
        PebbleDictionary out = new PebbleDictionary();
        out.addString(KEY_TEMPERATURE, tempStr);
        out.addUint8(KEY_CLOUD_COVER, (byte)Math.round(cloudCover * 100)); // from fraction (e.g. 0.25) to int percent, so it fits in a byte
        out.addUint8(KEY_WEATHER_ICON, icon.getValue());
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public LinkedList<Forecast> mergeForecasts(LinkedList<Forecast> one, LinkedList<Forecast> other)
    {
        LinkedList<Forecast> ret = (LinkedList<Forecast>)one.clone();
        ret.addAll(other);
        Collections.sort(ret);
        // TODO remove dups
        return ret;
    }

    private void sendPrecipitation(LinkedList<Forecast> fs)
    {
        // TODO
    }

    private void sendForecast(LinkedList<Forecast> fs)
    {
        // TODO
    }

    public void updateForecast(LinkedList<Forecast> fs)
    {
        // Some Forecast objects have bpth times and precipitation;
        // others have everything else and only timeFrom.
        LinkedList<Forecast> forecast = new LinkedList<>();
        LinkedList<Forecast> precipitation = new LinkedList<>();

        for (Forecast f : fs)
            if (f.getPrecipitation() == null) {
                forecast.add(f);
                Log.d(TAG, "forecast:" + f.toString());
            } else {
                precipitation.add(f);
                Log.d(TAG, f.getTimeFrom() + " precipitation: " + f.getPrecipitation());
            }
        sendPrecipitation(precipitation);
        sendForecast(forecast);
    }

    public void updateNowCast(LinkedList<Forecast> nowcast)
    {
        long now = System.currentTimeMillis();
        int utcOffset = TimeZone.getDefault().getRawOffset();
        PebbleDictionary out = new PebbleDictionary();
        int len = nowcast.size();
        byte[] precipitation = new byte[len]; // in tenths of mm
        byte[] minutesInFuture = new byte[len];
        int i = 0;
        for (Forecast f : nowcast) {
            precipitation[i] = (byte) Math.round(f.getPrecipitation().getPrecipitationDouble() * 10);
            minutesInFuture[i] = (byte)((f.getTimeFrom().getTime() - now + utcOffset) / 60000);
            Log.d(TAG, "nowcast " + f.getTimeFrom() + " (" + minutesInFuture[i] + " min from now)" + ": " + f.getPrecipitation());
            ++i;
        }
        out.addBytes(KEY_NOWCAST_MINUTES, minutesInFuture);
        out.addBytes(KEY_NOWCAST_PRECIPITATION, precipitation);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }

    public void updateSunriseSunset(int sunriseHour, int sunriseMinute, int sunsetHour, int sunsetMinute) {
        Log.d(TAG, "updateSunriseSunset " + sunriseHour + ":" + sunriseMinute + ", " + sunsetHour + ":" + sunsetMinute);
        PebbleDictionary out = new PebbleDictionary();
        out.addInt8(KEY_SUNRISE_HOUR, (byte)sunriseHour);
        out.addInt8(KEY_SUNRISE_MINUTE, (byte)sunriseMinute);
        out.addInt8(KEY_SUNSET_HOUR, (byte)sunsetHour);
        out.addInt8(KEY_SUNSET_MINUTE, (byte)sunsetMinute);
        PebbleKit.sendDataToPebble(m_weatherService, WATCHAPP_UUID, out);
    }
}
