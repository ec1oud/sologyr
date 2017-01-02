package org.ecloud.sologyr;

public class GeoUtils {
    // http://stackoverflow.com/questions/3694380/calculating-distance-between-two-points-using-latitude-longitude-what-am-i-doi
    // converted to meters here
    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        double d = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) +
                Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(lon1 - lon2));
        return Math.acos(d) * 180.0 / Math.PI * 60 * 1.1515 * 1609.344;
    }

    public static double deg2rad(double deg) { return (deg * Math.PI / 180.0); }

    // convert degrees longitude to meters horizontally along a particular latitude
    public static double latDistance(double deg, double lat) {
        return deg2rad(deg) * metersPerDegreeLon(lat);
//        return Math.cos(rlat) * 111319.9 * deg;
    }

    // convert degrees latitude to meters vertically, near a particular latitude
    public static double lonDistance(double deg, double lat) {
        return deg * metersPerDegreeLat(lat);
    }

    public static double metersPerDegreeLon(double lat) {
        double rlat = deg2rad(lat);
        return 111132.92 - 559.82 * Math.cos(2 * rlat) + 1.175 * Math.cos(4 * rlat);
    }

    public static double metersPerDegreeLat(double lat) {
        double rlat = deg2rad(lat);
        return 111412.84 * Math.cos(rlat) - 93.5 * Math.cos(3 * rlat);
    }
}
