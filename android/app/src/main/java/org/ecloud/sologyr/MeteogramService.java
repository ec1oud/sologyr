package org.ecloud.sologyr;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class MeteogramService extends IntentService {

    private final String TAG = this.getClass().getSimpleName();
    public static final String ACTION_GET_METEOGRAM = "org.ecloud.sologyr.action.GET_METEOGRAM";
    private static Bitmap m_bitmap;

    public MeteogramService() {
        super("MeteogramService");
    }

    public static void startActionGetMeteogram(Context context) {
        Intent intent = new Intent(context, MeteogramService.class);
        intent.setAction(ACTION_GET_METEOGRAM);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            if (ACTION_GET_METEOGRAM.equals(action)) {
                handleActionGetMeteogram();
            }
        }
    }

    public static Bitmap getBitmap() {
        return m_bitmap;
    }

    /**
        Handle action in the provided background thread
     */
    private void handleActionGetMeteogram() {
        m_bitmap = getImageBitmap("http://www.yr.no/sted/Norge/Akershus/B%C3%A6rum/Haslum/meteogram.png");

        // tell widget to come 'n get it
        final Intent doneIntent = new Intent(this, ForecastWidget.class);
        doneIntent.setAction(ForecastWidget.ACTION_GOT_METEOGRAM);
        final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        try {
            Log.i(TAG, "onHandleIntent: launching pending Intent for loading done "
                    + m_bitmap.getWidth() + "x" + m_bitmap.getHeight());
            donePendingIntent.send();
        }
        catch (PendingIntent.CanceledException ce) {
            Log.i(TAG, "onHandleIntent: Exception: "+ce.toString());
        }
    }

    // from https://groups.google.com/forum/?fromgroups=#!topic/android-developers/jupslaeAEuo
    private Bitmap getImageBitmap(String url) {
        Bitmap bm = null;
        try {
            URL aURL = new URL(url);
            URLConnection conn = aURL.openConnection();
            conn.connect();
            InputStream is = conn.getInputStream();
            BufferedInputStream bis = new BufferedInputStream(is);
            bm = BitmapFactory.decodeStream(bis);
            bis.close();
            is.close();
        } catch (IOException e) {
            Log.e(TAG, "Error getting bitmap", e);
        }
        return bm;
    }
}
