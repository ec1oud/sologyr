package org.ecloud.sologyr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class Autostart extends BroadcastReceiver {
    public Autostart() {
        Log.d("Autostart", "constructor");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent intent2 = new Intent(context, WeatherService.class);
        context.startService(intent2);
        Log.d("Autostart", "started");
    }
}
