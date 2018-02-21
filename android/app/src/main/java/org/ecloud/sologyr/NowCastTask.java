package org.ecloud.sologyr;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.oleaarnseth.weathercast.Forecast;
import com.oleaarnseth.weathercast.XMLParser;

import org.ecloud.sologyr.R;
import org.ecloud.sologyr.WeatherService;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;

import static java.net.HttpURLConnection.HTTP_OK;

public class NowCastTask extends AsyncTask<Location, Void, LinkedList<Forecast>>
{
    private final String TAG = this.getClass().getSimpleName();
    private static final String WEATHER_URL = "https://api.met.no/weatherapi/nowcast/";
    private static final String WEATHER_VERSION = "0.9";
    private static final String WEATHER_ATTRIBUTE_LAT = "/?lat=";
    private static final String WEATHER_ATTRIBUTE_LON = ";lon=";

    private static final int HTTP_OK = 200, HTTP_DEPRECATED = 203;
    private static final int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 15000;

    // Formateringsstreng for SimpleDateFormat tilpasset datoformatet i XML-dataene:
    public static final String XML_DATE_FORMAT = "yyyy-MM-dd";

    // where to send results
    private WeatherService weatherService;

    NowCastTask(WeatherService ws) {
        weatherService = ws;
    }

    public void start(Location location) {
        if (location == null || getStatus() == AsyncTask.Status.RUNNING)
            return;

        execute(location);
    }

    @Override
    protected LinkedList<Forecast> doInBackground(Location... params) {
        URL url;
        HttpURLConnection connection = null;
        LinkedList<Forecast> rawData = null;

        try {
            url = new URL(WEATHER_URL
                    + WEATHER_VERSION
                    + WEATHER_ATTRIBUTE_LAT
                    + params[0].getLatitude()
                    + WEATHER_ATTRIBUTE_LON
                    + params[0].getLongitude());
//            Log.d(TAG, url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            int responseCode = connection.getResponseCode();

            if (responseCode == HTTP_OK || responseCode == HTTP_DEPRECATED) {
//                Log.d(TAG, "got a response" + responseCode);
                XMLParser parser = new XMLParser();
                rawData = parser.parse(connection.getInputStream());
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        weatherService.setNowCast(rawData);
        return rawData;
    }

//    @Override
//    protected void onPostExecute(Forecast[] result) {
//            weatherService.setNowCast(result);
//    }
}
