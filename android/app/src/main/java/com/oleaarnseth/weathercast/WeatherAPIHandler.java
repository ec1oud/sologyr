package com.oleaarnseth.weathercast;

import android.app.Fragment;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import org.ecloud.sologyr.R;
import org.ecloud.sologyr.WeatherService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/*
    WeatherAPIHandler står for henting og behandling
    av all data fra yr sitt WeatherAPI.
 */
public class WeatherAPIHandler {
    private final String TAG = this.getClass().getSimpleName();

    private static final String INTERNET_CONNECTION_CHECK_HOST = "www.google.com";

    private static final String WEATHER_URL = "http://api.met.no/weatherapi/locationforecast/";
    private static final String WEATHER_VERSION = "1.9";
    private static final String WEATHER_ATTRIBUTE_LAT = "/?lat=";
    private static final String WEATHER_ATTRIBUTE_LON = ";lon=";

    private static final String WEATHER_ICON_URL = "http://api.met.no/weatherapi/weathericon/";
    private static final String WEATHER_ICON_VERSION = "1.1";
    private static final String WEATHER_ICON_ATTRIBUTE_ICON_NUMBER = "/?symbol=";
    private static final String WEATHER_ICON_ATTRIBUTE_CONTENT_TYPE = ";content_type=image/png";

    private static final int HTTP_OK = 200, HTTP_DEPRECATED = 203;
    private static final int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 15000;

    /* Angir for hvor mange dager varsel skal gis for (weatherAPI gir
    varsler for maks 9 dager fram i tid): */
    public static final int NUM_DAYS = 8;

    // Formateringsstreng for SimpleDateFormat tilpasset datoformatet i XML-dataene:
    public static final String XML_DATE_FORMAT = "yyyy-MM-dd";

    // Siste halvdel av dato-oppføringer i XML-data:
    private static final String XML_DATE_STRING_END_TIME_06 = "T06:00:00Z";
    private static final String XML_DATE_STRING_END_TIME_12 = "T12:00:00Z";
    private static final String XML_DATE_STRING_END_TIME_18 = "T18:00:00Z";

    // AsyncTasker som henter værvarsler og utfører reverse geocoding:
    private FetchForecastTask fetchForeCastTask = null;
    private FetchLocalityTask fetchLocalityTask = null;

    // Lokasjon som værvarsel skal hentes for:
    private Location location;

    // where to send results
    private WeatherService weatherService;

    public void setLocation(Location location) { this.location = location; }

    public WeatherAPIHandler(WeatherService ws) {
        weatherService = ws;
    }

    // Hjelpemetode som sjekker at internettilkobling er tilgjengelig:
    private boolean internetIsConnected() {
        try {
            InetAddress ip = InetAddress.getByName(INTERNET_CONNECTION_CHECK_HOST);

            if (ip.equals("")) {
                return false;
            }

            return true;
        }
        catch (UnknownHostException e) {
            return false;
        }
    }

    // Starter AsyncTask i hodeløst fragment, kalles fra WeatherActivity:
    public void startFetchForecastTask() {
        if (fetchForeCastTask == null) {
            fetchForeCastTask = new FetchForecastTask();
        }
        if (location == null || fetchForeCastTask.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }

        if (fetchForeCastTask.getStatus() == AsyncTask.Status.FINISHED) {
            fetchForeCastTask = new FetchForecastTask();
        }

        fetchForeCastTask.execute(location);
    }

    public AsyncTask.Status getFetchForecastTaskStatus() {
        return fetchForeCastTask.getStatus();
    }

    public AsyncTask.Status getFetchLocalityStatus() { return fetchLocalityTask.getStatus(); }

    private class FetchForecastTask extends AsyncTask<Location, Void, Forecast[]> {
        @Override
        protected Forecast[] doInBackground(Location... params) {
            if (!internetIsConnected()) {
                return null;
            }

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
                Log.d(TAG, url.toString());
                connection = (HttpURLConnection) url.openConnection();
                connection.setReadTimeout(READ_TIMEOUT);
                connection.setConnectTimeout(CONNECT_TIMEOUT);
                int responseCode = connection.getResponseCode();

                if (responseCode == HTTP_OK || responseCode == HTTP_DEPRECATED) {
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

            if (rawData != null) {
                Forecast[] result = organizeData(rawData);
                return result;
            } else {
                // Bad connection:
                return null;
            }
        }

        @Override
        protected void onPostExecute(Forecast[] result) {
            if (result != null) {
                weatherService.setForecasts(result);
                startFetchLocalityTask();
            }
            // Hvis result == null har det skjedd en feil, og feildialog vises i WeatherActivity:
            else {
                Log.wtf(TAG, "null forecast result");
//                weatherService.internetConnectionProblem();
            }
        }
    }

    private void startFetchLocalityTask() {
        if (fetchLocalityTask == null) {
            fetchLocalityTask = new FetchLocalityTask();
        }
        if (location == null || fetchLocalityTask.getStatus() == AsyncTask.Status.RUNNING) {
            return;
        }

        if (fetchLocalityTask.getStatus() == AsyncTask.Status.FINISHED) {
            fetchLocalityTask = new FetchLocalityTask();
        }

        fetchLocalityTask.execute(location);
    }

    private class FetchLocalityTask extends AsyncTask<Location, Void, String> {
        @Override
        protected String doInBackground(Location... params) {
            Geocoder geocoder = new Geocoder(weatherService, Locale.getDefault());
            List<Address> addresses = null;

            // Utfør reverse geocoding og lever resultat:
            try {
                addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            }
            catch (IOException e) {
                Log.wtf(TAG, "IOExcpetion when reverse geocoding.");
            }
            catch (IllegalArgumentException e) {
                Log.wtf(TAG, "IllegalArgumentException when reverse geocoding.");
            }

            // Sjekk at adresse ble funnet:
            if (addresses == null || addresses.size() == 0) {
                return "";
            }

            Address address = addresses.get(0);
            return address.getLocality();
        }

        @Override
        protected void onPostExecute(String result) {
            weatherService.setLocality(result);
        }
    }


    /* Denne metoden organiserer rådataene fra XML-parseren slik at første XML-oppføring
    blir "dagens" værvarsel, mens værvarsel for de neste dagene blir representert av
    varselet for klokka 12:00 for den dagen. Nedbør og ikon-id hentes fra oppføringen
    som kommer rett etter det gjeldende varselet, siden den oppføringen vil gjelde for
    samme tidsrom. */
    private Forecast[] organizeData(LinkedList<Forecast> rawData) {
        if (rawData.size() < (NUM_DAYS * 2)) {
            throw new IllegalStateException("Not enough data from XML-feed.");
        }

        Forecast[] forecasts = new Forecast[NUM_DAYS];
        Iterator<Forecast> iterator = rawData.iterator();

        // Sett sammen dagens værvarsel basert på første og andre oppføring i listen:
        forecasts[0] = iterator.next();
        Forecast extra = iterator.next();

        if (!forecasts[0].getTimeTo().equals(extra.getTimeTo())) {
            throw new IllegalStateException("Inconsistent dates from XML-feed.");
        }

        forecasts[0].setPrecipitation(extra.getPrecipitation());
        forecasts[0].setForecastWeatherIcon(extra.getWeatherIcon());
//        forecasts[0].setDisplayDate(getResources().getString(R.string.displaydate_today));

        // Initialiser kalender, SimpleDateFormat og arrayer med månedsnavn og dager før løkke:
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat xmlFormat = new SimpleDateFormat(XML_DATE_FORMAT);
        String[] months = weatherService.getResources().getStringArray(R.array.month_list);
        String[] days = weatherService.getResources().getStringArray(R.array.days_of_month_list);

        if (months.length < 12 || days.length < 31) {
            throw new IllegalStateException("Incomplete string array resources.");
        }

        // Sett sammen værvarsler for resten av dagene:
        for (int i = 1; i < forecasts.length; i++) {
            cal.add(Calendar.DATE, 1);
            forecasts[i] = assembleForecast(iterator, xmlFormat.format(cal.getTime()));

            String displayDate = days[Integer.parseInt(forecasts[i].getTimeTo().substring(8, 10)) - 1]
                    + System.getProperty("line.separator")
                    + months[Integer.parseInt(forecasts[i].getTimeTo().substring(5, 7)) - 1];

            forecasts[i].setDisplayDate(displayDate);
        }

        return forecasts;
    }

    /* Hjelpemetode som setter sammen et værvarsel for dato angitt i
       i Stringen date: */
    private Forecast assembleForecast(Iterator<Forecast> iterator, String dateString) {
        /* Flytt iterator til neste værvarsel for klokka 12:00 den gjeldende datoen,
           og hent ut forecast der timeFrom og timeTo er like: */
        String forecastTime = dateString + XML_DATE_STRING_END_TIME_12;

        Forecast forecast = null;

        while (iterator.hasNext()) {
            forecast = iterator.next();
            if (forecast.getTimeFrom().equals(forecastTime) && forecast.getTimeTo().equals(forecastTime)) {
                break;
            }
        }

        if (!iterator.hasNext()) {
            throw new IllegalStateException("Not enough XML data from parser.");
        }

        String extraTimeFrom = dateString + XML_DATE_STRING_END_TIME_06;
        Forecast extra = null;

        // Hent precipitation og ikon i oppføring fra 06:00 til 12:00:
        while (iterator.hasNext()) {
            extra = iterator.next();
            if ((extra.getTimeFrom().equals(extraTimeFrom) && extra.getTimeTo().equals(forecastTime))
                    || !extra.getTimeTo().substring(0, 10).equals(dateString)) {
                break;
            }
        }

        if (!extra.getTimeTo().equals(forecastTime)) {
            throw new IllegalStateException("Not enough XML data to assemble complete weather forecast.");
        }

        forecast.setPrecipitation(extra.getPrecipitation());
        forecast.setForecastWeatherIcon(extra.getWeatherIcon());

        // Hent precipitation i oppføring fra 12:00 til 18:00, og adder den til den over:
        String extraTimeTo = dateString + XML_DATE_STRING_END_TIME_18;

        while (iterator.hasNext()) {
            extra = iterator.next();
            if ((extra.getTimeFrom().equals(forecastTime) && extra.getTimeTo().equals(extraTimeTo))
                    || !extra.getTimeTo().substring(0, 10).equals(dateString)) {
                break;
            }
        }

        // Adder precipitation for total fra 06:00 til 18:00:
        if (extra.getTimeTo().equals(extraTimeTo)) {
            double precipitation = extra.getPrecipitation().getPrecipitationDouble()
                    + forecast.getPrecipitation().getPrecipitationDouble();

            forecast.getPrecipitation().setPrecipitationDouble(precipitation);
        }

        return forecast;
    }
}
