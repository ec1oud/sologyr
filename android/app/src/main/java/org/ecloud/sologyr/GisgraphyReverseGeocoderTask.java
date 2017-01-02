package org.ecloud.sologyr;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GisgraphyReverseGeocoderTask extends AsyncTask<Location, Void, Void>
{
    private final String TAG = this.getClass().getSimpleName();
    private static final String SERVICE_URL = "http://services.gisgraphy.com/reversegeocoding/search?format=json";
    private static final String SERVICE_ATTRIBUTE_LAT = "&lat=";
    private static final String SERVICE_ATTRIBUTE_LON = "&lng=";

    private static final int HTTP_OK = 200, HTTP_DEPRECATED = 203;
    private static final int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 15000;

    // where to send results
    private WeatherService weatherService;

    GisgraphyReverseGeocoderTask(WeatherService ws) {
        weatherService = ws;
    }

    public void start(Location location) {
        if (location == null || getStatus() == AsyncTask.Status.RUNNING)
            return;

        execute(location);
    }

    @Override
    protected Void doInBackground(Location... params) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(SERVICE_URL
                    + SERVICE_ATTRIBUTE_LAT
                    + params[0].getLatitude()
                    + SERVICE_ATTRIBUTE_LON
                    + params[0].getLongitude());
//            Log.d(TAG, url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            int responseCode = connection.getResponseCode();

            if (responseCode == HTTP_OK || responseCode == HTTP_DEPRECATED) {
//                Log.d(TAG, "got a response" + responseCode);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                JSONTokener tok = new JSONTokener(reader.readLine());
                JSONObject obj = (JSONObject)tok.nextValue();
//                Log.d(TAG, obj.toString());
//                Log.d(TAG, obj.get("result").toString());
                JSONArray localities = obj.getJSONArray("result");
                JSONObject loc = (JSONObject)localities.get(0);
                weatherService.setLocality(loc.getString("city"), loc.getString("countryCode"),
                        loc.getDouble("lat"), loc.getDouble("lng"), loc.getDouble("distance"));
                Log.d(TAG, loc.getString("formatedFull"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return null;
    }
}
