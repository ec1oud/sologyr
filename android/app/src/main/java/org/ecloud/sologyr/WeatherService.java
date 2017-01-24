package org.ecloud.sologyr;

import android.Manifest;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.location.Address;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.RemoteViews;

import com.github.dvdme.ForecastIOLib.FIOCurrently;
import com.github.dvdme.ForecastIOLib.ForecastIO;
import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;
import com.oleaarnseth.weathercast.Forecast;

import java.util.ArrayList;
import java.util.Calendar;
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

public class WeatherService extends Service implements LocalityListener {

    final String TAG = this.getClass().getSimpleName();
    public static final String PREFERENCES_NAME = "org.ecloud.sologyr_preferences";
    public static final String PREF_WIDGET_WIDTH = "widgetWidth";
    public static final String PREF_WIDGET_HEIGHT = "widgetHeight";
    List<WeatherListener> m_listeners = new ArrayList<>(2);
    SharedPreferences m_prefs = null;
    long m_updateInterval = 10800000; // 3 hours in milliseconds
    long m_locationThreshold = 10000; // 10 km in meters
    PebbleUtil m_pebbleUtil;
    LocationManager m_locationManager;
    LocationListener m_locationListener;
    String m_initialProvider = PASSIVE_PROVIDER;
    String m_periodicProvider = PASSIVE_PROVIDER;
    double curlat = 0, curlon = 0;
    Location curLocation = null;
    String curLocationName = "";
    int curLocationDistance = -1;
    WeatherListener.WeatherIcon m_weatherIcon;
    double m_temperature = 0, m_cloudCover = 0, m_windSpeed = 0, m_windBearing = 0;
    double m_humidity = 0, m_dewPoint = 0, m_pressure = 0, m_ozone = 0, m_precipIntensity = 0;
    int sunriseHour = 0, sunriseMinute = 0, sunsetHour = 0, sunsetMinute = 0;
    long lastUpdateTime = 0;
    NowCastTask nowCastTask = null;
    ForecastTask forecastTask = null;
    DarkSkyCurrentTask darkSkyCurrentTask = null;
    GisgraphySearchTask geocoderTask = null;
    DatabaseHelper m_database = new DatabaseHelper(this);
    long m_startTime = System.currentTimeMillis();
    int m_darkSkyUpdateCount = 0;
    int m_locationUpdateCount = 0;
    int m_localityUpdateCount = 0;
    int m_forecastUpdateCount = 0;
    int m_nowcastUpdateCount = 0;
    LinkedList<Forecast> m_forecast;
    ForecastView m_forecastView = null;

    public WeatherService() { }

    public Bitmap getMeteogram() {
        if (m_forecastView == null)
            m_forecastView = new ForecastView(this);
        m_forecastView.updateForecast(curLocationName, m_forecast);

//        Log.d(TAG, "getMeteogram: all known prefs " + m_prefs.getAll());

        Bitmap ret =  Bitmap.createBitmap(m_prefs.getInt(PREF_WIDGET_WIDTH, 748),
                m_prefs.getInt(PREF_WIDGET_HEIGHT, 210), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(ret);
        m_forecastView.draw(c);

        return ret;
    }

    public void updateMeteogramWidgets() {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
        int[] allWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(getApplicationContext(), ForecastWidget.class));
        Bitmap bm = getMeteogram();
        for (int widgetId : allWidgetIds) {
            Log.d(TAG, "updating ForecastWidget " + widgetId);
            RemoteViews remoteViews = new RemoteViews(this
                    .getApplicationContext().getPackageName(),
                    R.layout.forecast_widget);
            remoteViews.setImageViewBitmap(R.id.imageView, bm);
            appWidgetManager.updateAppWidget(widgetId, remoteViews);
        }
    }

    public void setForecast(LinkedList<Forecast> forecast) {
        if (forecast == null)
            return;
        m_forecast = forecast;
        for (WeatherListener el : m_listeners)
            el.updateForecast(forecast);
//        for (Forecast f : forecast)
//            Log.d(TAG, "forecast:" + f.toString());
        forecastTask = null;
        ++m_forecastUpdateCount;
        updateMeteogramWidgets();
    }

    public void setNowCast(LinkedList<Forecast> nowcast) {
        for (WeatherListener el : m_listeners)
            el.updateNowCast(nowcast);
//        for (Forecast f : nowcast)
//            Log.d(TAG, "nowcast " + f.getTimeFrom() + ": " + f.getPrecipitation());
        nowCastTask = null;
        ++m_nowcastUpdateCount;
    }

    public void setDarkSkyForecast(ForecastIO fio) {
//        Log.d(TAG, "Timezone: "+fio.getTimezone());
//        Log.d(TAG, "Offset: "+fio.getOffset());
        FIOCurrently currently = new FIOCurrently(fio);
        String [] f  = currently.get().getFieldsArray();
        for(int i = 0; i < f.length; i++)
            Log.d(TAG, "curr " + f[i] + ": " + currently.get().getByKey(f[i]));
        m_temperature = Double.valueOf(currently.get().getByKey("temperature"));
        m_cloudCover = Double.valueOf(currently.get().getByKey("cloudCover"));
        m_windSpeed = Double.valueOf(currently.get().getByKey("windSpeed"));
        m_windBearing = Double.valueOf(currently.get().getByKey("windBearing"));
        m_humidity = Double.valueOf(currently.get().getByKey("humidity"));
        m_dewPoint = Double.valueOf(currently.get().getByKey("dewPoint"));
        m_ozone = Double.valueOf(currently.get().getByKey("ozone"));
        m_pressure = Double.valueOf(currently.get().getByKey("pressure"));
        m_precipIntensity = Double.valueOf(currently.get().getByKey("precipIntensity"));

        String icon = currently.get().getByKey("icon");
        if (icon.contains("clear-day"))
            m_weatherIcon = Sun;
        else if (icon.contains("clear-night"))
            m_weatherIcon = DarkSun;
        else if (icon.contains("partly-cloudy-day"))
            m_weatherIcon = PartlyCloud;
        else if (icon.contains("partly-cloudy-night"))
            m_weatherIcon = DarkPartlyCloud;
        else if (icon.contains("rain"))
            m_weatherIcon = Rain;
        else if (icon.contains("snow"))
            m_weatherIcon = Snow;
        else if (icon.contains("sleet"))
            m_weatherIcon = Sleet;
        else if (icon.contains("wind"))
            m_weatherIcon = Wind; // doesn't match the Yr icon set
        else if (icon.contains("fog"))
            m_weatherIcon = Fog;
        else if (icon.contains("cloudy"))
            m_weatherIcon = Cloud;
        else
            m_weatherIcon = WEATHERUNKNOWN;

//        Log.d(TAG, "for icon " + icon + " got enum " + m_weatherIcon);
        for (WeatherListener l : m_listeners)
            l.updateCurrentWeather(m_temperature, m_cloudCover, m_weatherIcon, m_windSpeed, m_windBearing,
                    m_humidity, m_dewPoint, m_pressure, m_ozone, m_precipIntensity);
        lastUpdateTime = System.currentTimeMillis();
        darkSkyCurrentTask = null;
        ++m_darkSkyUpdateCount;
    }

    public void addLocality(String name, String country, double lat, double lon, double distance) {
        // TODO can be called multiple times: pick the closest to present location
        Log.d(TAG, "we seem to find ourselves " + (int)distance + "m from " + name + ", " + country);
        if (curLocationDistance < 0 || curLocationDistance > distance) {
            if (curLocationDistance < 0)
                updateWeather(true); // immediately because we know the location has changed, probably substantially
            curLocationDistance = (int) Math.round(distance);
            curLocationName = name;
            for (WeatherListener l : m_listeners)
                l.updateLocation(curlat, curlon, curLocationName, curLocationDistance);
            ++m_localityUpdateCount;
        }
    }

    public void addLocality(double distance, Address address) {
        addLocality(address.getLocality(), address.getCountryCode(),
                address.getLatitude(), address.getLongitude(), distance);
        if (m_database.openRW())
            m_database.insertLocation(address.getLatitude(), address.getLongitude(),
                    address.getLocality(), address.getCountryCode());
    }

    public class LocalBinder extends Binder {
        WeatherService getService() {
            return WeatherService.this;
        }
    }

    private final IBinder m_binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
//        Log.d(TAG, "onBind");
        return m_binder;
    }

    public void onCreate() {
        m_prefs = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE);
        m_updateInterval = Integer.parseInt(m_prefs.getString("weather_update_frequency", "180")) * 60000;
        m_locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Criteria crit = new Criteria();
            crit.setAccuracy(Criteria.ACCURACY_COARSE);
            crit.setPowerRequirement(Criteria.POWER_LOW);
            m_initialProvider = m_locationManager.getBestProvider(crit, false);
            if (m_periodicProvider != null && m_initialProvider == null)
                m_initialProvider = m_periodicProvider;
//            if (m_periodicProvider != null)
//                Log.d(TAG, "best low-power provider is " + m_periodicProvider + " from " + m_locationManager.getAllProviders() +
//                        ", enabled? " + m_locationManager.isProviderEnabled(m_periodicProvider));
//            if (initialProvider != null)
//                Log.d(TAG, "best provider is " + initialProvider + " from " + m_locationManager.getAllProviders() +
//                        ", enabled? " + m_locationManager.isProviderEnabled(initialProvider));
        }
        m_pebbleUtil = new PebbleUtil(this);

        m_locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
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
            m_locationManager.requestLocationUpdates(m_periodicProvider, 600000, m_locationThreshold, m_locationListener);
            Log.i(TAG, "location providers " + m_locationManager.getProviders(true));
            if (m_initialProvider != null)
                m_locationManager.requestSingleUpdate(m_initialProvider, m_locationListener, null);
        } else {
            Log.w(TAG, "requestRegularUpdates failed: provider " + m_periodicProvider);
        }

        Location savedLoc = new Location("saved");
        savedLoc.setLatitude(m_prefs.getFloat("latitude", (float)59.9132694));
        savedLoc.setLongitude(m_prefs.getFloat("longitude", (float)10.7391112));
        setLocation(savedLoc);

        m_prefs.registerOnSharedPreferenceChangeListener(
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                    if (key.equals("weather_update_frequency"))
                        m_updateInterval = Integer.parseInt(m_prefs.getString("weather_update_frequency", "180")) * 60000; // milliseconds
                    else if (key.equals("location_threshold")) {
                        m_locationThreshold = Integer.parseInt(m_prefs.getString("location_threshold", "10000")); // meters
                        try {
                            m_locationManager.requestLocationUpdates(m_periodicProvider, 600000, m_locationThreshold, m_locationListener);
                        } catch (SecurityException e) { }
                    }
                }
            });
    }

    public void requestLocationUpdate() {
        if (m_locationManager != null && m_initialProvider != null && ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            m_locationManager.requestSingleUpdate(m_initialProvider, m_locationListener, null);
    }

    public void setLocation(Location location) {
        Log.d(TAG, "setLocation " + location);
        curLocation = location;
        curlat = location.getLatitude();
        curlon = location.getLongitude();
        m_prefs.edit().putFloat("latitude", (float)curlat).putFloat("longitude", (float)curlon).apply();
        Calendar today = Calendar.getInstance();
        com.luckycatlabs.sunrisesunset.dto.Location lcLocation = new com.luckycatlabs.sunrisesunset.dto.Location(curlat, curlon);
        SunriseSunsetCalculator calculator = new SunriseSunsetCalculator(lcLocation, today.getTimeZone());
        Calendar sunrise = calculator.getOfficialSunriseCalendarForDate(today);
        Calendar sunset = calculator.getOfficialSunsetCalendarForDate(today);
        if (sunrise == null) {
            sunriseHour = 0;
            sunsetMinute = 0;
        } else {
            Log.d(TAG, "sunrise " + sunrise.getTime() + ", sunset " + sunset.getTime());
            sunriseHour = sunrise.get(Calendar.HOUR_OF_DAY);
            sunriseMinute = sunrise.get(Calendar.MINUTE);
        }
        if (sunset == null) {
            sunsetHour = 0;
            sunsetMinute = 0;
        } else {
            sunsetHour = sunset.get(Calendar.HOUR_OF_DAY);
            sunsetMinute = sunset.get(Calendar.MINUTE);
        }
        for (WeatherListener l : m_listeners)
            l.updateSunriseSunset(sunriseHour, sunriseMinute, sunsetHour, sunsetMinute);
        boolean locationNameFound = false;
        curLocationName = "";
        curLocationDistance = -1;
        if (!m_database.openRW())
            Log.e(TAG, "failed to open database");
        else {
            Address here = m_database.getNearestLocation(curlat, curlon);
            Log.d(TAG, "locality from database: " + here);
            if (here != null) {
                addLocality(here.getLocality(), here.getCountryCode(), here.getLatitude(), here.getLongitude(),
                        (int)Math.round(GeoUtils.distance(curlat, curlon, here.getLatitude(), here.getLongitude())));
                locationNameFound = true;
            }
        }
        if (!locationNameFound) {
            Log.d(TAG, "locality unknown, asking Gisgrahy");
            geocoderTask = new GisgraphySearchTask(this) {
                @Override
                protected void onPostExecute(List<Address> addrs) {
                    super.onPostExecute(addrs);
                    geocoderTask = null;
                }
            };
            geocoderTask.execute(curLocation);
        }
        ++m_locationUpdateCount;
    }

    public void updateWeather(boolean immediately) {
        if (curLocation != null && (immediately || (m_updateInterval > 0 &&
                System.currentTimeMillis() - lastUpdateTime > m_updateInterval))) {
            forecastTask = new ForecastTask(this);
            forecastTask.start(curLocation);
            nowCastTask = new NowCastTask(this);
            nowCastTask.start(curLocation);
            darkSkyCurrentTask = new DarkSkyCurrentTask(this);
            darkSkyCurrentTask.execute(curLocation);
        }
    }

    public void addWeatherListener(WeatherListener l) {
        for (WeatherListener el : m_listeners)
            if (el == l)
                return;
        m_listeners.add(l);
//        Log.d(TAG, "addWeatherListener " + l.getClass().getName() + ": count " + m_listeners.size());
    }

    public void removeWeatherListener(WeatherListener l) {
        m_listeners.remove(l);
//        Log.d(TAG, "removeWeatherListener " + l + ": count " + m_listeners.size());
//        if (m_listeners.isEmpty())
//            Log.d(TAG, "   all Weather listeners removed");
    }

    public void resendEverything(WeatherListener l) {
        l.updateLocation(curlat, curlon, curLocationName, curLocationDistance);
        l.updateSunriseSunset(sunriseHour, sunriseMinute, sunsetHour, sunsetMinute);
        l.updateCurrentWeather(m_temperature, m_cloudCover, m_weatherIcon, m_windSpeed, m_windBearing,
                m_humidity, m_dewPoint, m_pressure, m_ozone, m_precipIntensity);
    }

    public void resetUpdateCounters() {
        m_darkSkyUpdateCount = 0;
        m_locationUpdateCount = 0;
        m_localityUpdateCount = 0;
        m_forecastUpdateCount = 0;
        m_nowcastUpdateCount = 0;
        m_startTime = System.currentTimeMillis();
    }
}
