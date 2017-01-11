package org.ecloud.sologyr;

import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Address;
import android.util.Log;

import java.io.File;
import java.util.Locale;

/**
    Database for storing stuff we learned from web services, to avoid asking again.
 */
public class DatabaseHelper extends SQLiteOpenHelper {
    private final String TAG = this.getClass().getSimpleName();
    private static final String DATABASE_NAME = "locations.db";
    private static final int DATABASE_VERSION = 1;
    public SQLiteDatabase m_database = null;
    private static final int LOCATION_TOLERANCE = 5000; // meters

    private static DatabaseHelper m_instance = null;

    public static DatabaseHelper instance() { return m_instance; }

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        m_instance = this;
    }

    public boolean isExisting(ContextWrapper context) {
        File dbFile = context.getDatabasePath(DATABASE_NAME);
        if (!dbFile.exists())
            Log.d(TAG, "isExisting: doesn't exist " + dbFile.toString());
        return dbFile.exists();
    }

    public boolean openRW() {
        if (m_database == null)
            m_database = getWritableDatabase();
        return m_database != null;
    }

    public void recreate() {
        onCreate(m_database);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        Log.d(TAG, "creating database");

        database.execSQL("drop table if exists locations;");
        database.execSQL("create table locations (_id integer primary key autoincrement not null, lat real, lon real, city string, country string);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(DatabaseHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        onCreate(db);
    }

    public Cursor getNearbyLocations(double lat, double lon) {
        final double dLatMax = LOCATION_TOLERANCE / GeoUtils.metersPerDegreeLat(lat);
        final double dLonMax = LOCATION_TOLERANCE / GeoUtils.metersPerDegreeLon(lat);
//        Log.d(TAG, "getNearbyLocations " + lat + "," + lon + " dLatMax " + dLatMax + " dLonMax " + dLonMax);
        //  public Cursor query (String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy)
        return m_database.query("locations", null,
                "lat > " + (lat - dLatMax) + " and lat < " + (lat + dLatMax) +
                        " and lon > " + (lon - dLonMax) + " and lon < " + (lon + dLonMax),
                null, null, null, null);
    }

    public Address getNearestLocation(double lat, double lon) {
        Cursor cur = getNearbyLocations(lat, lon);
//        Log.d(TAG, "getNearestLocation " + lat + "," + lon);
        cur.moveToFirst();
        double minDistance = 99999;
        String retCity = null;
        String retCountry = null;
        double retLat = 500;
        double retLon = 500;
        while (!cur.isAfterLast()) {
            double clat = cur.getDouble(cur.getColumnIndex("lat"));
            double clon = cur.getDouble(cur.getColumnIndex("lon"));
            double dist = GeoUtils.distance(lat, lon, clat, clon);
            if (dist < minDistance) {
                minDistance = dist;
                retLat = clat;
                retLon = clon;
                retCity = cur.getString(cur.getColumnIndex("city"));
                retCountry = cur.getString(cur.getColumnIndex("country"));
            }
            cur.moveToNext();
        }
        if (retCity != null) {
            Address ret = new Address(Locale.getDefault());
            ret.setLatitude(retLat);
            ret.setLongitude(retLon);
            ret.setLocality(retCity);
            ret.setCountryCode(retCountry);
//            Log.d(TAG, "getNearestLocation " + lat + "," + lon + " came up with " + retCity + " @ " + retLat + "," + retLon);
            return ret;
        }
        return null;
    }

    public long locationId(double lat, double lon, String city, String country) {
        Cursor cur = m_database.query("locations", null,
                    "city='" + city + "' and country='" + country + "'",
                    null, null, null, null);
//        Log.d(TAG, "locationId " + lat + "," + lon + " " + city + ", " + country);
        cur.moveToFirst();
        while (!cur.isAfterLast()) {
            double clat = cur.getDouble(cur.getColumnIndex("lat"));
            double clon = cur.getDouble(cur.getColumnIndex("lon"));
            double dist = GeoUtils.distance(lat, lon, clat, clon);
            if (dist < LOCATION_TOLERANCE)
                return cur.getLong(cur.getColumnIndex("_id"));
        }
        cur.close();
        return -1;
    }

    public long insertLocation(double lat, double lon, String city, String country) {
        if (!openRW())
            return -1;
        long id = locationId(lat, lon, city, country);
//        Log.d(TAG, "insertLocation " + lat + "," + lon + " " + city + ", " + country + "; existing? " + id);
        if (id >= 0)
            return id;
        ContentValues v = new ContentValues(4);
        v.put("lat", lat);
        v.put("lon", lon);
        v.put("city", city);
        v.put("country", country);
        return m_database.insert("locations", null, v);
    }

    public void clear() {
        m_database.execSQL("delete from locations;");
    }

    public int totalLocations() {
        Cursor cur = m_database.query("locations", null, null, null, null, null, null);
        return cur.getCount();
    }
}
