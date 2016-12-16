package org.ecloud.sologyr;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.github.dvdme.ForecastIOLib.FIOCurrently;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.oleaarnseth.weathercast.Forecast;
import com.oleaarnseth.weathercast.WeatherAPIHandler;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static android.location.LocationManager.PASSIVE_PROVIDER;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Cloud;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.DarkPartlyCloud;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.DarkSun;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Fog;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.PartlyCloud;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Rain;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Sleet;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Snow;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Sun;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.WEATHERUNKNOWN;
import static org.ecloud.sologyr.WeatherListener.WeatherIcon.Wind;

public class WeatherService extends Service {

    private List<WeatherListener> m_listeners = new ArrayList<>(2);
    private final String TAG = this.getClass().getSimpleName();
    private final long UPDATE_INTERVAL = 600000; // milliseconds
    PebbleUtil m_pebbleUtil;
    LocationManager m_locationManager;
    LocationListener m_locationListener;
    String m_periodicProvider = PASSIVE_PROVIDER;
    double curlat = 0, curlon = 0;
    Location curLocation = null;
    WeatherListener.WeatherIcon curWeatherIcon;
    double curTemperature = 0, curCloudCover = 0;
    int sunriseHour = 0, sunriseMinute = 0, sunsetHour = 0, sunsetMinute = 0;
    long lastUpdateTime = 0;
    WeatherAPIHandler weatherApiHandler = new WeatherAPIHandler(this);
    NowCastTask nowCastTask = null;
    DarkSkyCurrentTask darkSkyCurrentTask = null;

    public WeatherService() {
    }

    public void setForecasts(Forecast[] result) {
        for (Forecast f : result)
            Log.d(TAG, f.toString());
    }

    public void setLocality(String result) {
        Log.d(TAG, "locality " + result);
    }

    public void setNowCast(LinkedList<Forecast> nowcast) {
        for (Forecast f : nowcast)
            Log.d(TAG, f.toString());
    }

    public void setDarkSkyForecast(ForecastIO fio) {
        Log.d(TAG, "Timezone: "+fio.getTimezone());
//        Log.d(TAG, "Offset: "+fio.getOffset());
        FIOCurrently currently = new FIOCurrently(fio);
        String [] f  = currently.get().getFieldsArray();
        for(int i = 0; i < f.length; i++)
            Log.d(TAG, f[i] + ": " + currently.get().getByKey(f[i]));
        curTemperature = Double.valueOf(currently.get().getByKey("temperature"));
        curCloudCover = Double.valueOf(currently.get().getByKey("cloudCover"));
        String icon = currently.get().getByKey("icon");
        /* oughtta work, but partly-cloudy-night turns into unknown
        switch(icon) {
            case "clear-day":
                curWeatherIcon = Sun;
                break;
            case "clear-night":
                curWeatherIcon = DarkSun;
                break;
            case "rain":
                curWeatherIcon = Rain;
                break;
            case "snow":
                curWeatherIcon = Snow;
                break;
            case "sleet":
                curWeatherIcon = Sleet;
                break;
            case "wind":
                curWeatherIcon = Wind; // doesn't match the Yr icon set
                break;
            case "fog":
                curWeatherIcon = Fog;
                break;
            case "cloudy":
                curWeatherIcon = Cloud;
                break;
            case "partly-cloudy-day":
                curWeatherIcon = PartlyCloud;
                break;
            case "partly-cloudy-night":
                curWeatherIcon = DarkPartlyCloud;
                break;
            default:
                curWeatherIcon = WEATHERUNKNOWN;
                break;
        }
        */

        if (icon.contains("clear-day"))
            curWeatherIcon = Sun;
        else if (icon.contains("clear-night"))
            curWeatherIcon = DarkSun;
        else if (icon.contains("partly-cloudy-day"))
            curWeatherIcon = PartlyCloud;
        else if (icon.contains("partly-cloudy-night"))
            curWeatherIcon = DarkPartlyCloud;
        else if (icon.contains("rain"))
            curWeatherIcon = Rain;
        else if (icon.contains("snow"))
            curWeatherIcon = Snow;
        else if (icon.contains("sleet"))
            curWeatherIcon = Sleet;
        else if (icon.contains("wind"))
            curWeatherIcon = Wind; // doesn't match the Yr icon set
        else if (icon.contains("fog"))
            curWeatherIcon = Fog;
        else if (icon.contains("cloudy"))
            curWeatherIcon = Cloud;
        else
            curWeatherIcon = WEATHERUNKNOWN;

        Log.d(TAG, "for icon " + icon + " got enum " + curWeatherIcon);
        for (WeatherListener l : m_listeners)
            l.updateCurrentWeather(curTemperature, curCloudCover, curWeatherIcon);
        lastUpdateTime = System.currentTimeMillis();
    }

    public class LocalBinder extends Binder {
        WeatherService getService() {
            return WeatherService.this;
        }
    }

    private final IBinder m_binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return m_binder;
    }

    public void onCreate() {
        m_locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        String initialProvider = null;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Criteria crit = new Criteria();
            crit.setAccuracy(Criteria.ACCURACY_COARSE);
            crit.setPowerRequirement(Criteria.POWER_LOW);
            initialProvider = m_locationManager.getBestProvider(crit, false);
            if (m_periodicProvider != null && initialProvider == null)
                initialProvider = m_periodicProvider;
            if (m_periodicProvider != null)
                Log.d(TAG, "best low-power provider is " + m_periodicProvider + " from " + m_locationManager.getAllProviders() +
                        ", enabled? " + m_locationManager.isProviderEnabled(m_periodicProvider));
            if (initialProvider != null)
                Log.d(TAG, "best provider is " + initialProvider + " from " + m_locationManager.getAllProviders() +
                        ", enabled? " + m_locationManager.isProviderEnabled(initialProvider));
        }
        m_pebbleUtil = new PebbleUtil(this);

        m_locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d(TAG, location.toString());
                setLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.d(TAG, "onStatusChanged " + provider + status);
            }

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        if (m_locationManager != null && m_periodicProvider != null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            m_locationManager.requestLocationUpdates(m_periodicProvider, 600000, 10000, m_locationListener);
            Log.i(TAG, "location providers " + m_locationManager.getProviders(true));
            if (initialProvider != null)
                m_locationManager.requestSingleUpdate(initialProvider, m_locationListener, null);
        } else {
            Log.w(TAG, "requestRegularUpdates failed: provider " + m_periodicProvider);
        }

        // TODO store last known location, restore here; using central Oslo for now
        Location savedLoc = new Location("saved");
        savedLoc.setLatitude(59.9132694);
        savedLoc.setLongitude(10.7391112);
        setLocation(savedLoc);
    }

    public void setLocation(Location location) {
        curLocation = location;
        curlat = location.getLatitude();
        curlon = location.getLongitude();
        Calendar today = Calendar.getInstance();
        com.luckycatlabs.sunrisesunset.dto.Location lcLocation = new com.luckycatlabs.sunrisesunset.dto.Location(curlat, curlon);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(lcLocation, today.getTimeZone());
        Calendar sunrise = calculator.getOfficialSunriseCalendarForDate(today);
        Calendar sunset = calculator.getOfficialSunsetCalendarForDate(today);
        Log.d(TAG, "sunrise " + sunrise.toString() + ", sunset " + sunset.toString());
        sunriseHour = sunrise.get(Calendar.HOUR_OF_DAY);
        sunriseMinute = sunrise.get(Calendar.MINUTE);
        sunsetHour = sunset.get(Calendar.HOUR_OF_DAY);
        sunsetMinute = sunset.get(Calendar.MINUTE);
        for (WeatherListener l : m_listeners) {
            l.updateLocation(curlat, curlon);
            l.updateSunriseSunset(sunriseHour, sunriseMinute, sunsetHour, sunsetMinute);
        }
//        nowCastTask = new NowCastTask(this);
//        nowCastTask.start(location);
        weatherApiHandler.setLocation(location);
        updateWeather(true); // immediately because we know the location is different by at least 10km
    }

    public void updateWeather(boolean immediately) {
        if (curLocation != null && (immediately || System.currentTimeMillis() - lastUpdateTime > UPDATE_INTERVAL)) {
            weatherApiHandler.startFetchForecastTask();
            darkSkyCurrentTask = new DarkSkyCurrentTask(this);
            darkSkyCurrentTask.execute(curLocation);
        }
    }

    public void addWeatherListener(WeatherListener l) {
        for (WeatherListener el : m_listeners)
            if (el == l)
                return;
        m_listeners.add(l);
        Log.d(TAG, "addWeatherListener " + l.getClass().getName() + ": count " + m_listeners.size());
    }

    public void removeWeatherListener(WeatherListener l) {
        m_listeners.remove(l);
        Log.d(TAG, "removeWeatherListener " + l + ": count " + m_listeners.size());
        if (m_listeners.isEmpty()) {
            Log.d(TAG, "   all Weather listeners removed");
        }
    }

    public void resendEverything(WeatherListener l) {
        l.updateLocation(curlat, curlon);
        l.updateSunriseSunset(sunriseHour, sunriseMinute, sunsetHour, sunsetMinute);
        l.updateCurrentWeather(curTemperature, curCloudCover, curWeatherIcon);
    }
}
