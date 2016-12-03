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

import java.util.ArrayList;
import java.util.List;

import static android.location.LocationManager.PASSIVE_PROVIDER;

public class WeatherService extends Service {

    private List<WeatherListener> m_listeners = new ArrayList<>(2);
    private final String TAG = this.getClass().getSimpleName();
    PebbleUtil m_pebbleUtil;
    LocationManager m_locationManager;
    LocationListener m_locationListener;
    String m_periodicProvider = PASSIVE_PROVIDER;
    double curlat = 0, curlon = 0;

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
                setLocation(location.getLatitude(), location.getLongitude());
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

    public void setLocation(double lat, double lon) {
        curlat = lat; curlon = lon;
        for (WeatherListener l : m_listeners)
            l.updateLocation(lat, lon);
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
}
