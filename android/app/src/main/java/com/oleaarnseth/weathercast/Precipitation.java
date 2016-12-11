package com.oleaarnseth.weathercast;

import java.io.Serializable;

/**
 * Denne klassen inneholder nedbør og måleenhet for nedbør, som enten er i
 * millimeter eller tommer:
 */
public class Precipitation implements Serializable {
    // Konstanter som angir måleenhet:
    public static final int UNIT_MILLIMETER = 1, UNIT_INCHES = 2;

    // String-konstanter brukt i toString-metoden:
    private static final String STRING_MILLIMETER = "mm", STRING_INCHES = "in";

    // Konstantene over er eneste tillatte verdier:
    private int unit;

    // Nedbør:
    private double precipitation;

    // Konstruktør:
    public Precipitation (int unit, double precipitation) {
        this.unit = unit;
        this.precipitation = precipitation;
    }

    public double getPrecipitationDouble() { return precipitation; }
    public void setPrecipitationDouble(double precipitation) { this.precipitation = precipitation; }

    @Override
    public String toString() {
        switch (unit) {
            case UNIT_INCHES:
                return precipitation
                        + " "
                        + STRING_INCHES;
            default:
                return precipitation
                        + " "
                        + STRING_MILLIMETER;
        }
    }
}
