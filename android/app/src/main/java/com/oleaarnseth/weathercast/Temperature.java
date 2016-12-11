package com.oleaarnseth.weathercast;

import java.io.Serializable;

/**
 * Denne klassen inneholder temperatur for et værvarsel, samt om måleenheten er celsius eller
 * fahrenheit:
 */
public class Temperature implements Serializable {
    // Konstanter som angir måleenhet for temperatur:
    public static final int CELSIUS = 1, FAHRENHEIT = 2;

    // String-konstanter brukt av toString-metoden:
    private static final String STRING_CELSIUS = "°C", STRING_FAHRENHEIT = "°F";

    // Måleenhet-konstantene er eneste tillatte verdier for unit:
    private int unit;

    // Temperatur;
    private double temperature;

    public Temperature(int unit, double temperature) {
        this.unit = unit;
        this.temperature = temperature;
    }

    public double getTemperatureDouble() { return temperature; }

    @Override
    public String toString() {
        switch (unit) {
            case FAHRENHEIT:
                return temperature
                        + " "
                        + STRING_FAHRENHEIT;
            default:
                return temperature
                        + " "
                        + STRING_CELSIUS;
        }
    }
}
