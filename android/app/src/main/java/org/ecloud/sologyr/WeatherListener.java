package org.ecloud.sologyr;

import com.oleaarnseth.weathercast.Forecast;

import java.util.LinkedList;

public interface WeatherListener {

    public enum WeatherIcon {
        WEATHERUNKNOWN(0),
        Sun(1),
        LightCloud(2),
        PartlyCloud(3),
        Cloud(4),
        LightRainSun(5),
        LightRainThunderSun(6),
        SleetSun(7),
        SnowSun(8),
        LightRain(9),
        Rain(10),
        RainThunder(11),
        Sleet(12),
        Snow(13),
        SnowThunder(14),
        Fog(15),
        SleetSunThunder(20),
        SnowSunThunder(21),
        LightRainThunder(22),
        SleetThunder(23),
        DrizzleThunderSun(24),
        RainThunderSun(25),
        LightSleetThunderSun(26),
        HeavySleetThunderSun(27),
        LightSnowThunderSun(28),
        HeavySnowThunderSun(29),
        DrizzleThunder(30),
        LightSleetThunder(31),
        HeavySleetThunder(32),
        LightSnowThunder(33),
        HeavySnowThunder(34),
        DrizzleSun(40),
        RainSun(41),
        LightSleetSun(42),
        HeavySleetSun(43),
        LightSnowSun(44),
        HeavysnowSun(45),
        Drizzle(46),
        LightSleet(47),
        HeavySleet(48),
        LightSnow(49),
        HeavySnow(50),
        DarkSun(101),
        DarkPartlyCloud(103),
        Wind(200);

        private final byte value;
        public byte getValue() {
            return value;
        }
        WeatherIcon(int val) {
            value = (byte)val;
        }
    }

    public void updateLocation(double lat, double lon, String name, int distance);
    public void updateCurrentWeather(double temperature, double cloudCover, WeatherIcon icon);
    public void updateForecast(LinkedList<Forecast> nowcast);
    public void updateNowCast(LinkedList<Forecast> nowcast);
    public void updateSunriseSunset(int sunriseHour, int sunriseMinute, int sunsetHour, int sunsetMinute);
}
