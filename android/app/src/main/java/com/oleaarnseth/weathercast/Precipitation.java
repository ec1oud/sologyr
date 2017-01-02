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
    private double precipitation, min, max;

    // Konstruktør:
    public Precipitation (int unit, double precipitation, double min, double max) {
        this.unit = unit;
        this.precipitation = precipitation;
        this.min = min;
        this.max = max;
    }

    public double getPrecipitation() { return precipitation; }
    public double getPrecipitationMin() { return min; }
    public double getPrecipitationMax() { return max; }
    public void setPrecipitationDouble(double precipitation) { this.precipitation = precipitation; }

    @Override
    public String toString() {
        String ret = min + "/" + precipitation + "/" + max + " ";
        switch (unit) {
            case UNIT_INCHES:
                ret += STRING_INCHES;
                break;
            default:
                ret+= STRING_MILLIMETER;
        }
        return ret;
    }
}
