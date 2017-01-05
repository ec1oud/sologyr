package org.ecloud.sologyr;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.widget.RemoteViews;

/**
    Widget which uses FetchService to get the latest "meteogram" image from yr.no.
 */
public class ForecastWidget extends AppWidgetProvider { 

    private final String TAG = this.getClass().getSimpleName();
    public static final String ACTION_GOT_METEOGRAM = "org.ecloud.sologyr.action.GOT_METEOGRAM";
    private static Bitmap m_bitmap = null;

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {

        if (m_bitmap != null) {
            // Construct the RemoteViews object
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.forecast_widget);
            views.setImageViewBitmap(R.id.imageView, m_bitmap);

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onUpdate(Context ctx, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds)
            updateAppWidget(ctx, appWidgetManager, appWidgetId);
        Log.i(TAG, "requesting meteogram");
        FetchService.startActionFetchPreferredBitmapUrl(ctx.getApplicationContext(), "meteogram_url",
                "http://api.met.no/weatherapi/radar/1.5/?radarsite=south_norway;type=reflectivity;content=animation;size=large",
                null);
    }

    @Override
    public void onEnabled(Context context) {
        Log.i(TAG, "onEnabled");
        // Enter relevant functionality for when the first widget is created
    }

    @Override
    public void onDisabled(Context context) {
        Log.i(TAG, "onDisabled");
        // Enter relevant functionality for when the last widget is disabled
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        final String action = intent.getAction();
        Log.i(TAG, "onReceive " + action);
        if (action.equals(FetchService.ACTION_FETCH_DONE)) {
            byte[] imageData = intent.getByteArrayExtra("byteArray");
            Log.d(TAG, "got a byte array with len " + imageData.length);
            m_bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            Log.d(TAG, "got a bitmap " + m_bitmap.getWidth() + "x" + m_bitmap.getHeight());
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(ctx);
            RemoteViews rv = new RemoteViews(ctx.getPackageName(),  R.layout.forecast_widget);
            rv.setImageViewBitmap(R.id.imageView, m_bitmap);
            ComponentName me = new ComponentName(ctx, ForecastWidget.class);
            appWidgetManager.updateAppWidget(me, rv);
        }

        super.onReceive(ctx, intent);
    }
}
