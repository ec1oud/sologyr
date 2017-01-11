package org.ecloud.sologyr;

import android.location.Address;
import android.location.Location;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class GisgraphySearchTask extends AsyncTask<Location, Void, List<Address> >
{
    private final String TAG = this.getClass().getSimpleName();
    private static final String SERVICE_URL = "http://services.gisgraphy.com/geoloc/search?format=json";
    private static final String SERVICE_ATTRIBUTE_LAT = "&lat=";
    private static final String SERVICE_ATTRIBUTE_LON = "&lng=";

    private static final int HTTP_OK = 200, HTTP_DEPRECATED = 203;
    private static final int READ_TIMEOUT = 10000, CONNECT_TIMEOUT = 15000;

    // where to send results
    private LocalityListener m_caller;

    GisgraphySearchTask(LocalityListener l) {
        m_caller = l;
    }

    @Override
    protected List<Address> doInBackground(Location... params) {
        HttpURLConnection connection = null;
        ArrayList<Address> ret = new ArrayList<>();
        try {
            URL url = new URL(SERVICE_URL
                    + SERVICE_ATTRIBUTE_LAT
                    + params[0].getLatitude()
                    + SERVICE_ATTRIBUTE_LON
                    + params[0].getLongitude());
            Log.d(TAG, url.toString());
            connection = (HttpURLConnection) url.openConnection();
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            int responseCode = connection.getResponseCode();

            if (responseCode == HTTP_OK || responseCode == HTTP_DEPRECATED) {
                Log.d(TAG, "got a response" + responseCode);

                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                JSONTokener tok = new JSONTokener(reader.readLine());
                JSONObject obj = (JSONObject)tok.nextValue();
//                Log.d(TAG, obj.toString());
                Log.d(TAG, obj.get("result").toString());
                JSONArray localities = obj.getJSONArray("result");
                for (int i = 0; i < localities.length(); ++i) {
                    JSONObject loc = (JSONObject) localities.get(i);
                    Address addr = new Address(Locale.getDefault());
                    addr.setLatitude(loc.getDouble("lat"));
                    addr.setLongitude(loc.getDouble("lng"));
                    addr.setCountryCode(loc.getString("countryCode"));
                    addr.setLocality(loc.getString("name"));
                    addr.setUrl(loc.getString("openstreetmap_map_url"));
                    ret.add(addr);
                    if (m_caller != null)
                        m_caller.addLocality(loc.getDouble("distance"), addr);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return ret;
    }
}
