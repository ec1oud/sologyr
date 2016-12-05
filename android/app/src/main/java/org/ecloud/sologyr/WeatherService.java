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

import com.luckycatlabs.sunrisesunset.SunriseSunsetCalculator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static android.location.LocationManager.PASSIVE_PROVIDER;

/*
import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.floor;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
*/

public class WeatherService extends Service {

    private List<WeatherListener> m_listeners = new ArrayList<>(2);
    private final String TAG = this.getClass().getSimpleName();
    PebbleUtil m_pebbleUtil;
    LocationManager m_locationManager;
    LocationListener m_locationListener;
    String m_periodicProvider = PASSIVE_PROVIDER;
    double curlat = 0, curlon = 0;
    int sunriseHour = 0, sunriseMinute = 0, sunsetHour = 0, sunsetMinute = 0;

    public WeatherService() {
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

        if (m_locationManager != null && m_periodicProvider != null && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            m_locationManager.requestLocationUpdates(m_periodicProvider, 600000, 10000, m_locationListener);
            Log.i(TAG, "location providers " + m_locationManager.getProviders(true));
            if (initialProvider != null)
                m_locationManager.requestSingleUpdate(initialProvider, m_locationListener, null);
        } else {
            Log.w(TAG, "requestRegularUpdates failed: provider " + m_periodicProvider);
        }
    }

    private double zeroToSomething(double limit, double val) {
        double ret = val % limit;
        if (ret < 0)
            ret += limit;
        return ret;
    }

    public void updateSunriseSunset(double latitude, double longitude) {
        /*
        // from http://williams.best.vwh.net/sunrise_sunset_algorithm.htm
        Calendar cal = Calendar.getInstance();
        int dayOfYear = cal.get(Calendar.DAY_OF_YEAR);

//        Sun's zenith for sunrise/sunset
//            official     = 90 degrees 50'
//            civil        = 96 degrees
//            nautical     = 102 degrees
//            astronomical = 108 degrees

        double cosZenith = cos((90.0 + 50.0 / 60.0) * Math.PI / 180);
        double cosLatitude = cos(latitude * Math.PI / 180);
        double sinLatitude = sin(latitude * Math.PI / 180);
        Log.d(TAG, "lat,lon " + latitude + ", " + longitude + " dayOfYear " + dayOfYear);

        double lngHour = longitude / 15;

        double rising = dayOfYear + ((6 - lngHour) / 24);
        double setting = dayOfYear + ((18 - lngHour) / 24);
        Log.d(TAG, "lngHour " + lngHour + " rising " + rising + " setting " + setting);

        double meanAnomalyRising = (0.9856 * rising) - 3.289;
        double meanAnomalySetting = (0.9856 * setting) - 3.289;

        double sunLongitudeRising = zeroToSomething(360, meanAnomalyRising +
                (1.916 * sin(meanAnomalyRising * Math.PI / 180)) +
                (0.020 * sin(2 * meanAnomalyRising * Math.PI / 180)) + 282.634);
        double sunLongitudeSetting = zeroToSomething(360, meanAnomalySetting +
                (1.916 * sin(meanAnomalySetting * Math.PI / 180)) +
                (0.020 * sin(2 * meanAnomalySetting * Math.PI / 180)) + 282.634);

        Log.d(TAG, "sun longitudes " + sunLongitudeRising + ", " + sunLongitudeSetting);

        double sunriseRA = atan(0.91764 * tan(sunLongitudeRising * Math.PI / 180)) * 180 / Math.PI;
        double sunsetRA = atan(0.91764 * tan(sunLongitudeSetting * Math.PI / 180)) * 180 / Math.PI;

        sunriseRA += (floor(sunLongitudeRising / 90) * 90 - floor(sunriseRA / 90) * 90) / 15; // in hours
        sunsetRA += (floor(sunLongitudeSetting / 90) * 90 - floor(sunsetRA / 90) * 90) / 15;

        Log.d(TAG, "sun RAs " + sunriseRA + ", " + sunsetRA);

        double sunriseSinDeclination = 0.39782 * sin(sunLongitudeRising * Math.PI / 180);
        double sunriseCosDeclination = cos(asin(sunriseSinDeclination));

        double sunsetSinDeclination = 0.39782 * sin(sunLongitudeSetting * Math.PI / 180);
        double sunsetCosDeclination = cos(asin(sunsetSinDeclination));

        Log.d(TAG, "sun sin/cos DECs " + sunriseSinDeclination + ", " + sunriseCosDeclination + ", " + sunsetSinDeclination + ", " + sunsetCosDeclination);

        double cosSunriseHourAngle = (cosZenith - (sunriseSinDeclination * sinLatitude)) /
                (sunriseCosDeclination * cosLatitude);
        double cosSunsetHourAngle = (cosZenith - (sunsetSinDeclination * sinLatitude)) /
                (sunsetCosDeclination * cosLatitude);

        Log.d(TAG, "sun hour angle cosines " + cosSunriseHourAngle + ", " + cosSunsetHourAngle);

        // if (cosH >  1) the sun never rises on this location (on the specified date)
        if (cosSunriseHourAngle > 1)
            sunriseHour = 1;
        else {
            double sunriseHour = (360 - (acos(cosSunriseHourAngle) * 180 / Math.PI)) / 15 - 12;
            double sunriseUTC = zeroToSomething(24, sunriseHour + sunriseRA - (0.06571 * rising) - 6.622 - lngHour);
            Log.d(TAG, "sunrise " + sunriseHour + " UTC " + sunriseUTC);
        }

        // if (cosH < -1) the sun never sets on this location (on the specified date)
        if (cosSunsetHourAngle < -1)
            sunsetHour = -1;
        else {
            double sunsetHour = (acos(cosSunsetHourAngle) * 180 / Math.PI) / 15 + 12;
            double sunsetUTC = zeroToSomething(24, sunsetHour + sunsetRA - (0.06571 * setting) - 6.622 - lngHour);
            Log.d(TAG, "sunset " + sunsetHour + " UTC " + sunsetUTC);
        }
        */
    }

    public void setLocation(Location location) {
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

    public void updateEverything(WeatherListener l) {
        l.updateLocation(curlat, curlon);
        l.updateSunriseSunset(sunriseHour, sunriseMinute, sunsetHour, sunsetMinute);
        // TODO updateWeather
    }
}
