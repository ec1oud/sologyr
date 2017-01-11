package org.ecloud.sologyr;

public interface LocalityListener {
    void addLocality(String city, String country, double lat, double lon, double distance);
}
