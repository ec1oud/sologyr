package org.ecloud.sologyr;

import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import com.github.dvdme.ForecastIOLib.FIOCurrently;
import com.github.dvdme.ForecastIOLib.ForecastIO;
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

public class DarkSkyCurrentTask extends AsyncTask<Location, Void, ForecastIO>
{
    private final String TAG = this.getClass().getSimpleName();
    private static final String API_KEY = "5d3f78b4a7ddcbf231e66e9db97841fb";
    private WeatherService weatherService;

    DarkSkyCurrentTask(WeatherService ws) {
        weatherService = ws;
    }

    @Override
    protected ForecastIO doInBackground(Location... params) {
        ForecastIO fio = new ForecastIO("5d3f78b4a7ddcbf231e66e9db97841fb");
        fio.setUnits(ForecastIO.UNITS_SI);
        fio.setLang(ForecastIO.LANG_ENGLISH);
        fio.setExcludeURL("hourly,minutely");
        fio.getForecast(String.valueOf(params[0].getLatitude()), String.valueOf(params[0].getLongitude()));
        weatherService.setDarkSkyForecast(fio);
        return fio;
    }
}
