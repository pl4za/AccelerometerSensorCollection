package com.ubi.jason.sensorcollect;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Created by jasoncosta on 1/3/2016.
 */
public class NetworkReceiver extends BroadcastReceiver {

    public NetworkReceiver() {
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPref = context.getSharedPreferences(context.getResources().getString(R.string.preference), 0);
        String server_address = sharedPref.getString(context.getResources().getString(R.string.server_address), "");
        new DataUpload(context, server_address);
    }
}
