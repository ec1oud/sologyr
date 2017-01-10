package org.ecloud.sologyr;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ResultReceiver;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.oleaarnseth.weathercast.Forecast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;

public class MainActivity extends Activity implements WeatherListener {

    private final String TAG = this.getClass().getSimpleName();
    private static final String WATCHAPP_FILENAME = "sologyr.pbw";
    public static final String ACTION_GOT_RADAR = "org.ecloud.sologyr.action.GOT_RADAR";
    SharedPreferences m_prefs = null;
    private WeatherService m_weatherService = null;
    private ServiceConnection m_connection = null;

    double m_lat, m_lon;
    String m_locationName;
    double m_temperature, m_cloudCover;
    WeatherIcon m_weatherIcon;
    int m_sunriseHour, m_sunriseMinute, m_sunsetHour, m_sunsetMinute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        m_connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.d(TAG, "onServiceConnected " + className);
                // This is called when the connection with the service has been
                // established, giving us the service object we can use to
                // interact with the service.  Because we have bound to a explicit
                // service that we know is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                m_weatherService = ((WeatherService.LocalBinder)service).getService();
                m_weatherService.addWeatherListener(MainActivity.this);
                m_weatherService.resendEverything(MainActivity.this);
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
        m_prefs.registerOnSharedPreferenceChangeListener(
                new SharedPreferences.OnSharedPreferenceChangeListener() {
                    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
                        if (key.equals("pref_title_radar_url"))
                            MainActivity.this.updateRadar();
                    }
                });
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
        if (m_connection != null) {
            Intent intent = new Intent(this, WeatherService.class);
            startService(intent);
            bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
        }
        updateRadar();
        ((AnimationView)findViewById(R.id.radarImageView)).start();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        ((AnimationView)findViewById(R.id.radarImageView)).stop();
        if (m_weatherService != null) {
            m_weatherService.removeWeatherListener(this);
            m_weatherService = null;
        }
        if (m_connection != null)
            unbindService(m_connection);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.action_install_pebble:
                Toast.makeText(getApplicationContext(), R.string.pebble_install_toast, Toast.LENGTH_SHORT).show();
                sideloadInstall(getApplicationContext(), WATCHAPP_FILENAME);
                return true;
            case R.id.action_settings:
                intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_refresh:
                m_weatherService.requestLocationUpdate();
                return true;
            case R.id.action_about:
                showAbout();
                return true;
            case R.id.action_debug:
                intent = new Intent(this, DebugActivity.class);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public class RadarReceiver extends ResultReceiver {
        public RadarReceiver(Handler h) {
            super(h);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            Log.d(TAG, "got radar " + resultCode + " " + resultData);
            AnimationView v = (AnimationView)findViewById(R.id.radarImageView);
            byte[] imageData = resultData.getByteArray("byteArray");
            Log.d(TAG, "got a byte array with len " + imageData.length);
            v.setByteArray(imageData);
        }
    }

    public void updateRadar() {
        FetchService.startActionFetchPreferredBitmapUrl(this, "radar_url",
                "http://api.met.no/weatherapi/radar/1.5/?radarsite=south_norway;type=reflectivity;content=animation;size=large",
                new RadarReceiver(new Handler(Looper.getMainLooper())));
    }

    protected void showAbout() {
        // Inflate the about message contents
        View messageView = getLayoutInflater().inflate(R.layout.content_about, null, false);

        TextView t = (TextView)messageView.findViewById(R.id.aboutDarkSkyText);
        t.setMovementMethod(LinkMovementMethod.getInstance());
        t.setText(Html.fromHtml(
                "The real-time weather data is <a href=\"https://darksky.net/poweredby/\">powered by Dark Sky.</a>"));

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
//        builder.setIcon(R.drawable.app_icon);
        builder.setTitle(R.string.app_name);
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    /**
     * Alternative sideloading method
     * Source: http://forums.getpebble.com/discussion/comment/103733/#Comment_103733
     */
    public static void sideloadInstall(Context ctx, String assetFilename) {
        try {
            // Read .pbw from assets/
            Intent intent = new Intent(Intent.ACTION_VIEW);
            File file = new File(ctx.getExternalFilesDir(null), assetFilename);
            InputStream is = ctx.getResources().getAssets().open(assetFilename);
            OutputStream os = new FileOutputStream(file);
            byte[] pbw = new byte[is.available()];
            is.read(pbw);
            os.write(pbw);
            is.close();
            os.close();

            // Install via Pebble Android app
            intent.setDataAndType(Uri.fromFile(file), "application/pbw");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (IOException e) {
            Toast.makeText(ctx, "App install failed: " + e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void updateLocation(final double lat, final double lon, final String name, final int distance) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView t = (TextView)findViewById(R.id.locationTextView);
                if (distance < 0 || name.isEmpty())
                    t.setText(String.format(Locale.getDefault(), "as of %4$tH:%4$tM location %1$3.4f,%2$3.4f",
                            lat, lon, name, Calendar.getInstance(), distance));
                else
                    t.setText(String.format(Locale.getDefault(), "as of %4$tH:%4$tM location %1$3.4f,%2$3.4f %5$d m from %3$s",
                            lat, lon, name, Calendar.getInstance(), distance));
            }
        });
    }

    @Override
    public void updateCurrentWeather(final double temperature, final double cloudCover, final WeatherIcon icon) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView t = (TextView) findViewById(R.id.currentWeatherTextView);
                t.setText(String.format(Locale.getDefault(), "%1$3.2f°C %2$2.0f%% cloudy; icon '%3$s'",
                        temperature, cloudCover, icon == null ? "" : icon.name()));
            }
        });
    }

    @Override
    public void updateForecast(LinkedList<Forecast> nowcast) {
        // TODO
    }

    @Override
    public void updateNowCast(LinkedList<Forecast> nowcast) {
        // TODO
    }

    @Override
    public void updateSunriseSunset(final int sunriseHour, final int sunriseMinute, final int sunsetHour, final int sunsetMinute) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView t = (TextView) findViewById(R.id.sunriseSunsetTextView);
                t.setText(String.format(Locale.getDefault(), "sunrise %1$d:%2$02d sunset %3$d:%4$02d",
                        sunriseHour, sunriseMinute, sunsetHour, sunsetMinute));
            }
        });
    }
}
