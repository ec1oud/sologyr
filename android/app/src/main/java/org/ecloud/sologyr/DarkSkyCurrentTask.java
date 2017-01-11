package org.ecloud.sologyr;

import android.location.Location;
import android.os.AsyncTask;

import com.github.dvdme.ForecastIOLib.ForecastIO;

/**
    Task to get current weather conditions from darksky.net
 */
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
        ForecastIO fio = new ForecastIO(API_KEY);
        fio.setUnits(ForecastIO.UNITS_SI);
        fio.setLang(ForecastIO.LANG_ENGLISH);
        fio.setExcludeURL("hourly,minutely");
        fio.getForecast(String.valueOf(params[0].getLatitude()), String.valueOf(params[0].getLongitude()));
        weatherService.setDarkSkyForecast(fio);
        return fio;
    }
}
