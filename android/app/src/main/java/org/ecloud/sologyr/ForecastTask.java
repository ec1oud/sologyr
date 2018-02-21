package org.ecloud.sologyr;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.oleaarnseth.weathercast.Forecast;
import com.oleaarnseth.weathercast.XMLParser;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;

/**
    Task to get weather forecast by location from api.met.no
 */
public class ForecastTask extends AsyncTask<Location, Void, LinkedList<Forecast>>
{
    private final String TAG = this.getClass().getSimpleName();
    private static final String WEATHER_URL = "https://api.met.no/weatherapi/locationforecast/";
    private static final String WEATHER_VERSION = "1.9";
    private static final String WEATHER_ATTRIBUTE_LAT = "/?lat=";
    private static final String WEATHER_ATTRIBUTE_LON = ";lon=";

    private static final int HTTP_OK = 200, HTTP_DEPRECATED = 203;
    private static final int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 15000;

    // where to send results
    private WeatherService weatherService;

    ForecastTask(WeatherService ws) {
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
        LinkedList<Forecast> forecasts = null;

        try {
            url = new URL(WEATHER_URL
                    + WEATHER_VERSION
                    + WEATHER_ATTRIBUTE_LAT
                    + params[0].getLatitude()
                    + WEATHER_ATTRIBUTE_LON
                    + params[0].getLongitude());
            Log.d(TAG, url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            int responseCode = connection.getResponseCode();

            if (responseCode == HTTP_OK || responseCode == HTTP_DEPRECATED) {
//                Log.d(TAG, "got a response" + responseCode);
                XMLParser parser = new XMLParser();
                forecasts = parser.parse(connection.getInputStream());
            }
        }
        catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        weatherService.setForecast(forecasts);
        return forecasts;
    }
}
