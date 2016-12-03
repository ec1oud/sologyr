package org.ecloud.sologyr;

/**
 * Created by rutledge on 12/3/16.
 */

public interface WeatherListener {
    public void updateLocation(double lat, double lon);
    public void updateWeather(String temperature);
}
