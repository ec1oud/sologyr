package org.ecloud.sologyr;

import android.location.Address;

public interface LocalityListener {
    void addLocality(double distance, Address address);
}
