package com.oleaarnseth.weathercast;

import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.LinkedList;


// Parser-klasse som parser all XML-data fra WeatherAPI.

public class XMLParser {
    private final String TAG = this.getClass().getSimpleName();

    public static final String NAMESPACE = null;

    // Start-tag for XML-data:
    public static final String START_TAG_WEATHERDATA = "weatherdata";

    // XML-tagger:
    public static final String TAG_FORECAST = "time";
    public static final String TAG_TEMPERATURE = "temperature";
    public static final String TAG_WINDSPEED = "windSpeed";
    public static final String TAG_PRECIPITATION = "precipitation";
    public static final String TAG_SYMBOL = "symbol";

    // XML-attributter:
    public static final String ATTRIBUTE_TIME_FROM = "from";
    public static final String ATTRIBUTE_TIME_TO = "to";
    public static final String ATTRIBUTE_TEMPERATURE = "value";
    public static final String ATTRIBUTE_UNIT = "unit";
    public static final String ATTRIBUTE_WINDSPEED = "mps";
    public static final String ATTRIBUTE_PRECIPITATION_VALUE = "value";
    public static final String ATTRIBUTE_PRECIPITATION_MAX_VALUE = "maxvalue";
    public static final String ATTRIBUTE_SYMBOL_NUMBER = "number";

    // Måleenheter for temperature og XML-feeden:
    public static final String TEMPERATURE_UNIT_CELSIUS = "celsius";
    public static final String TEMPERATURE_UNIT_FAHRENHEIT = "fahrenheit";

    // Måleenheter for precipitation og XML-feeden:
    public static final String PRECIPITATION_UNIT_MILLIMETERS = "mm";
    public static final String PRECIPITATION_UNIT_INCHES = "in";

    // Her starter parseren, som videre kaller alle private hjelpemetoder under:
    public LinkedList<Forecast> parse(InputStream in)
            throws XmlPullParserException, ParseException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();

            return readXmlFeed(parser);
        }
        finally {
            in.close();
        }
    }

    private LinkedList<Forecast> readXmlFeed(XmlPullParser parser)
            throws XmlPullParserException, ParseException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, START_TAG_WEATHERDATA);
        LinkedList<Forecast> forecasts = new LinkedList<Forecast>();

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() != XmlPullParser.START_TAG)
                continue;
            if (parser.getName().equals(TAG_FORECAST))
                forecasts.add(readForecast(parser));
        }

        return forecasts;
    }

    private Forecast readForecast(XmlPullParser parser)
            throws XmlPullParserException, ParseException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_FORECAST);

        String timeFrom = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_TIME_FROM);
        String timeTo = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_TIME_TO);

        Temperature temperature = null;
        double windspeed = Double.MIN_VALUE;
        Precipitation precipitation = null;
        int iconNumber = -1;

        int depth = 1;

        while (depth > 0) {
            int event = parser.next();

            if (event != XmlPullParser.START_TAG) {
                if (event == XmlPullParser.END_TAG) {
                    depth--;
                }
                continue;
            }
            else {
                depth++;
            }

            String name = parser.getName();

            if (name.equals(TAG_TEMPERATURE)) {
                temperature = readTemperature(parser);
            }
            else if (name.equals(TAG_WINDSPEED)) {
                windspeed = readWindSpeed(parser);
            }
            else if (name.equals(TAG_PRECIPITATION)) {
                precipitation = readPrecipitation(parser);
            }
            else if (name.equals(TAG_SYMBOL)) {
                iconNumber = readIconNumber(parser);
            }
        }

        return new Forecast(timeFrom, timeTo, temperature, windspeed, precipitation, new WeatherIcon(iconNumber));
    }

    private Temperature readTemperature(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_TEMPERATURE);

        String temperatureStr = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_TEMPERATURE);
        double temperature = Double.parseDouble(temperatureStr);
        String temperatureUnit = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_UNIT);

        if (temperatureUnit.equals(TEMPERATURE_UNIT_FAHRENHEIT)) {
            return new Temperature(Temperature.FAHRENHEIT, temperature);
        }
        else {
            return new Temperature(Temperature.CELSIUS, temperature);
        }
    }

    private double readWindSpeed(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_WINDSPEED);

        String windspeedStr = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_WINDSPEED);

        double windspeed = Double.parseDouble(windspeedStr);

        return windspeed;
    }

    private Precipitation readPrecipitation(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_PRECIPITATION);

        String precipitationStr = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_PRECIPITATION_VALUE);
        String precipitationMaxStr = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_PRECIPITATION_MAX_VALUE);
        double precipitation = Double.parseDouble(precipitationStr);
        // TODO store min and max separately; for now I just want to see optimistic amounts of precipitation
        if (precipitationMaxStr != null)
            precipitation = Double.parseDouble(precipitationMaxStr);
        String unit = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_UNIT);
        if (unit.equals(PRECIPITATION_UNIT_INCHES)) {
            return new Precipitation(Precipitation.UNIT_INCHES, precipitation);
        }
        else {
            return new Precipitation(Precipitation.UNIT_MILLIMETER, precipitation);
        }
    }

    private int readIconNumber(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, NAMESPACE, TAG_SYMBOL);

        String iconNumberStr = parser.getAttributeValue(NAMESPACE, ATTRIBUTE_SYMBOL_NUMBER);
        int iconNumber = Integer.parseInt(iconNumberStr);

        return iconNumber;
    }
}
