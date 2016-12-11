package com.oleaarnseth.weathercast;

import java.io.Serializable;

/**
 * Denne klassen rommer værvarsel-data for et gjeldende tidsrom.
 */
public class Forecast implements Serializable {
    // XML-oppføringens tid lagres som String for enkelthetens skyld:
    private String timeFrom, timeTo;

    private String displayDate;

    private double windspeed;

    private Temperature temperature;
    private Precipitation precipitation;
    private WeatherIcon weatherIcon;

    // Konstruktør:
    public Forecast(String timeFrom, String timeTo, Temperature temperature, double windspeed, Precipitation precipitation, WeatherIcon weatherIcon) {
        this.timeFrom = timeFrom;
        this.timeTo = timeTo;
        this.temperature = temperature;
        this.windspeed = windspeed;
        this.precipitation = precipitation;
        this.weatherIcon = weatherIcon;
        displayDate = "";
    }


    public String getTimeFrom() { return timeFrom; }

    public String getTimeTo() { return timeTo; }

    public String getDisplayDate() { return displayDate; }

    public Temperature getTemperature() { return temperature; }

    public double getWindspeed() { return windspeed; }

    public void setDisplayDate(String displayDate) { this.displayDate = displayDate; }

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
        return "Time from: "
                + timeFrom
                + "\nTime to: "
                + timeTo
                + "\nTemperature: "
                + (temperature == null ? "none" :temperature.toString())
                + "\nWindspeed: "
                + windspeed
                + "\nPrecipitation: "
                + precipitation
                + "\nDisplay date: "
                + displayDate;
    }
}
