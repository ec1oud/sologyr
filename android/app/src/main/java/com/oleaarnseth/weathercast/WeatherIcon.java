package com.oleaarnseth.weathercast;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Denne klassen inneholder all filinformasjon om værikoner fra WeatherAPI for værvarsler:
 */
public class WeatherIcon implements Serializable {
    /* Denne variablelen rommer iden for værvarselets værikon, som lastes ned fra:
       http://api.yr.no/weatherapi/weathericon/1.1/documentation */
    private int iconNumber;

    // Selve ikonfilen representert som en Java File:
    private File iconFile;

    public WeatherIcon(int iconNumber) {
        this.iconNumber = iconNumber;
        iconFile = null;
    }

    public int getIconNumber() { return iconNumber; }

    public void setWeatherIconFile(File iconFile) { this.iconFile = iconFile; }

    public Bitmap getIconBitmap() {
        FileHandler fh = new FileHandler();
        return fh.readIconBitmapFromFile(iconFile);
    }
}
