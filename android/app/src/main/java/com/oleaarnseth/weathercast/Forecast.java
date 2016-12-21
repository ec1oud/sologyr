package com.oleaarnseth.weathercast;

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Denne klassen rommer værvarsel-data for et gjeldende tidsrom.
 */
public class Forecast implements Serializable, Comparable<Forecast> {
    // XML-oppføringens tid lagres som String for enkelthetens skyld:
    private Date timeFrom, timeTo;

    private double windspeed;

    private Temperature temperature;
    private Precipitation precipitation;
    private WeatherIcon weatherIcon;

    // example datetime: 2016-12-18T01:15:00Z
    private static final SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);

    // Konstruktør:
    public Forecast(String timeFrom, String timeTo, Temperature temperature, double windspeed,
                    Precipitation precipitation, WeatherIcon weatherIcon) throws ParseException {
        this.timeFrom = timeFormatter.parse(timeFrom);
        this.timeTo = timeFormatter.parse(timeTo);
        this.temperature = temperature;
        this.windspeed = windspeed;
        this.precipitation = precipitation;
        this.weatherIcon = weatherIcon;
    }


    public Date getTimeFrom() { return timeFrom; }

    public Date getTimeTo() { return timeTo; }

    public Temperature getTemperature() { return temperature; }

    public double getWindspeed() { return windspeed; }

    public void setPrecipitation(Precipitation precipitation) {
        this.precipitation = precipitation;
    }

    public void setForecastWeatherIcon(WeatherIcon weatherIcon) { this.weatherIcon = weatherIcon; }

    public Precipitation getPrecipitation() { return precipitation; }

    public WeatherIcon getWeatherIcon() {
        return weatherIcon;
    }

    @Override
    public String toString() {
        return "Time from: " + timeFrom
                + " to: " + timeTo
                + " temperature: " + (temperature == null ? "none" : temperature.toString())
                + " windspeed: " + windspeed
                + (precipitation == null ? "" : " precipitation: " + precipitation);
    }

    @Override
    public final int compareTo(Forecast other) {
        return getTimeFrom().compareTo(other.getTimeFrom());
    }

//    public class ForecastComparator extends Comparator<Forecast> {
//        @Override
//        public int compare(Forecast f1, Forecast f2) {
//            return f1.getTimeFrom().compareTo(f2.getTimeFrom());
//        }
//
//        @Override
//        public boolean equals(Object o) {
//            return false;
//        }
//    }
}
