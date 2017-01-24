package org.ecloud.sologyr;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.RemoteViews;

import static android.content.Context.MODE_PRIVATE;

/**
    Widget which uses FetchService to get the latest "meteogram" image from yr.no.
 */
public class ForecastWidget extends AppWidgetProvider { 

    private final String TAG = this.getClass().getSimpleName();
    private ServiceConnection m_connection = null;
    private static WeatherService m_weatherService = null;

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        Log.i("ForecastWidget", "updateAppWidget " + appWidgetId);
        if (m_weatherService != null) {
            // Construct the RemoteViews object
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.forecast_widget);
            Bitmap bm = m_weatherService.getMeteogram();
            if (bm == null) {
                Log.w("ForecastWidget", "WeatherService doesn't have a meteogram available yet");
                return;
            }
            Log.d("ForecastWidget", "meteogram bitmap: " + bm.getWidth() + " x " + bm.getHeight());
            views.setImageViewBitmap(R.id.imageView, bm);
            Log.i("ForecastWidget", "   updating via " + views);
            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.i(TAG, "onUpdate");
        SharedPreferences prefs = ctx.getSharedPreferences(WeatherService.PREFERENCES_NAME, MODE_PRIVATE);
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds)
            updateAppWidget(ctx, appWidgetManager, appWidgetId);
    }

    @Override
    public void onEnabled(final Context context) {
        Log.i(TAG, "onEnabled");
        // Enter relevant functionality for when the first widget is created
        m_connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                // This is called when the connection with the service has been
                // established, giving us the service object we can use to
                // interact with the service.  Because we have bound to a explicit
                // service that we know is running in our own process, we can
                // cast its IBinder to a concrete class and directly access it.
                m_weatherService = ((WeatherService.LocalBinder)service).getService();
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
        Intent intent = new Intent(context.getApplicationContext(), WeatherService.class);
        context.startService(intent);
        context.getApplicationContext().bindService(intent, m_connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDisabled(final Context context) {
        SharedPreferences prefs = context.getSharedPreferences(WeatherService.PREFERENCES_NAME, MODE_PRIVATE);
        prefs.edit().remove(WeatherService.PREF_WIDGET_WIDTH);
        prefs.edit().remove(WeatherService.PREF_WIDGET_HEIGHT);
    }

    @Override
    public void onAppWidgetOptionsChanged (Context context, AppWidgetManager appWidgetManager,
                                    int appWidgetId, Bundle options) {
        // Get min width and height.
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        int minWidth = Math.round(displayMetrics.density * options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH));
        int minHeight = Math.round(displayMetrics.density * options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));

//        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(WeatherService.PREFERENCES_NAME, MODE_PRIVATE);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Log.i(TAG, "onAppWidgetOptionsChanged " + minWidth + "x" + minHeight + " density " + displayMetrics.density +
         " ws " + m_weatherService + " prefs " + prefs.getAll());

        // TODO these don't actually persist, for whatever reason
        if (!prefs.contains(WeatherService.PREF_WIDGET_WIDTH) || minWidth > prefs.getInt(WeatherService.PREF_WIDGET_WIDTH, 320))
            prefs.edit().putInt(WeatherService.PREF_WIDGET_WIDTH, minWidth);
        if (!prefs.contains(WeatherService.PREF_WIDGET_HEIGHT) || minHeight > prefs.getInt(WeatherService.PREF_WIDGET_HEIGHT, 120))
            prefs.edit().putInt(WeatherService.PREF_WIDGET_HEIGHT, minHeight);

        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, options);
    }
}
