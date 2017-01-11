package org.ecloud.sologyr;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

/**
    Activity with debug-only features (to be removed for release)
 */
public class DebugActivity extends Activity  {

    private final String TAG = this.getClass().getSimpleName();
    SharedPreferences m_prefs = null;
    private WeatherService m_weatherService = null;
    private ServiceConnection m_connection = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);
        m_connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d(TAG, "onServiceConnected " + className);
                // This is called when the connection with the service has been
                // established, giving us the service object we can use to
                // interact with the service.  Because we have bound to a explicit
                // service that we know is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                m_weatherService = ((WeatherService.LocalBinder)service).getService();
                update(null);
            }

            public void onServiceDisconnected(ComponentName className) {
                Log.d(TAG, "onServiceDisconnected " + className);
                // This is called when the connection with the service has been
                // unexpectedly disconnected -- that is, its process crashed.
                // Because it is running in our same process, we should never
                // see this happen.
                m_weatherService = null;
            }
        };
        m_prefs = getSharedPreferences("org.ecloud.sologyr_preferences", MODE_PRIVATE);
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (m_connection != null) {
            Intent intent = new Intent(this, WeatherService.class);
            bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        if (m_weatherService != null)
            m_weatherService = null;
        if (m_connection != null)
            unbindService(m_connection);
    }

    public void update(View view) {
        if (m_weatherService != null) {
            ((TextView) findViewById(R.id.locationUpdateCount))
                    .setText(String.valueOf(m_weatherService.m_locationUpdateCount));
            ((TextView) findViewById(R.id.localityUpdateCount))
                    .setText(String.valueOf(m_weatherService.m_localityUpdateCount));
            ((TextView) findViewById(R.id.forecastUpdateCount))
                    .setText(String.valueOf(m_weatherService.m_forecastUpdateCount));
            ((TextView) findViewById(R.id.nowcastUpdateCount))
                    .setText(String.valueOf(m_weatherService.m_nowcastUpdateCount));
            ((TextView) findViewById(R.id.currentWeatherUpdateCount))
                    .setText(String.valueOf(m_weatherService.m_darkSkyUpdateCount));
        }
        ((TextView) findViewById(R.id.storedLocationsCount))
                .setText(String.valueOf(DatabaseHelper.instance().totalLocations()));
    }

    protected void teleport(double lat, double lon) {
        Location l = new Location("teleport");
        l.setLatitude(lat);
        l.setLongitude(lon);
        m_weatherService.setLocation(l);
        update(null);
    }

    public void teleportMajorstuen(View view) {
        teleport(59.9301335, 10.7148295);
    }

    public void teleportNittedal(View view) {
        teleport(60.0579, 10.8657);
    }

    public void teleportKirkenes(View view) {
        teleport(69.7238, 30.0587);
    }

    public void teleportPhoenix(View view) {
        teleport(33.4355, -111.9981);
    }

    public void clearDatabase(View view) {
        DatabaseHelper.instance().clear();
        update(view);
    }
}
