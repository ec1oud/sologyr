package org.ecloud.sologyr;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
    A service for fetching anything from the net... but mainly bitmaps so far
 */
public class FetchService extends IntentService {

    private final String TAG = this.getClass().getSimpleName();
    public static final String ACTION_FETCH_PREFERRED_BITMAP = "org.ecloud.sologyr.action.ACTION_FETCH_PREFERRED_BITMAP";
    public static final String ACTION_FETCH_DONE = "org.ecloud.sologyr.action.ACTION_FETCH_DONE";

    public FetchService() {
        super("FetchService");
    }

    public static void startActionFetchPreferredBitmapUrl(Context context, String preferenceId,
                                                          String defaultUrl, ResultReceiver resultReceiver) {
        Intent intent = new Intent(context, FetchService.class);
        intent.setAction(ACTION_FETCH_PREFERRED_BITMAP);
        intent.putExtra("prefId", preferenceId);
        intent.putExtra("defaultUrl", defaultUrl);
        intent.putExtra("resultReceiver", resultReceiver);
        context.startService(intent);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            final String action = intent.getAction();
            Log.d(TAG, "handling intent " + action);
            if (ACTION_FETCH_PREFERRED_BITMAP.equals(action)) {
                String url = getSharedPreferences("org.ecloud.sologyr_preferences", MODE_PRIVATE)
                        .getString(intent.getStringExtra("prefId"), intent.getStringExtra("defaultUrl"));
                Log.d(TAG, url);
                handleActionFetchBitmap((ResultReceiver)intent.getParcelableExtra("resultReceiver"), url);
            }
        }
    }

    /**
        Handle action in the provided background thread
     */
    private void handleActionFetchBitmap(ResultReceiver resultReceiver, String url) {
        if (resultReceiver != null) {
            Bundle bundle = new Bundle();
            bundle.putByteArray("byteArray", getByteArray(url));
            resultReceiver.send(0, bundle);
        } else {
            final Intent doneIntent = new Intent(ACTION_FETCH_DONE);
            byte[] data = getByteArray(url);
            if (data == null) {
                Log.e(TAG, "failed to fetch " + url);
                return;
            }
            doneIntent.putExtra("byteArray", data);
            final PendingIntent donePendingIntent = PendingIntent.getBroadcast(this, 0, doneIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            try {
                donePendingIntent.send();
            }
            catch (Exception e) {
                Log.e(TAG, "send PendingIntent: " + e.getMessage() + " while attempting to fetch " + url);
            }
        }
    }

    // from http://stackoverflow.com/questions/2295221/java-net-url-read-stream-to-byte
    private byte[] getByteArray(String url) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream is = null;
        try {
            URL aURL = new URL(url);
            URLConnection conn = aURL.openConnection();
            conn.connect();
            is = conn.getInputStream();
            byte[] byteChunk = new byte[4096];
            int n;
            while ( (n = is.read(byteChunk)) > 0 )
                baos.write(byteChunk, 0, n);
            is.close();
        }
        catch (IOException e) {
            Log.e(TAG, "Error getting byte array", e);
        }
        return baos.toByteArray();
    }
}
