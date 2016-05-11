package com.ubi.jason.sensorcollect;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.ubi.jason.sensorcollect.delegators.ViewCtrl;
import com.ubi.jason.sensorcollect.helper.Config;
import com.ubi.jason.sensorcollect.interfaces.SensorListener;
import com.ubi.jason.sensorcollect.interfaces.ServiceOptions;

import java.text.DecimalFormat;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by jasoncosta on 11/30/2015.
 */
public class SensorsService extends Service implements SensorListener, ServiceOptions {

    private static final String TAG = "SENSOR_SERVICE";
    // Notification
    private NotificationManager mNotifyManager;
    private Notification notification;
    private NotificationCompat.Builder mBuilder;
    private Files fileWriter;
    // Classes
    private Map<String, Sensor> sensorMap;
    private Sensors sensors;
    // Other
    private static int serviceStatus;
    private static int timestamp;
    private static int serviceID;
    private static float[] currentValues;
    private static float[] offset;
    private static float[] filteredValues;
    private Timer updateTime;
    private PowerManager.WakeLock wl;
    /*
    Class delegators
    */
    private static ViewCtrl viewCtrl = ViewCtrl.getInstance();

    public class LocalBinder extends Binder {
        SensorsService getService() {
            // Return this instance of LocalService so clients can call public methods
            return SensorsService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        SensorManager sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        sensors = new Sensors(sensorManager);
        sensorMap = sensors.getAvailableSensors();
        fileWriter = new Files(this, this);
        if (!fileWriter.hasFreeSpace()) {
            Toast.makeText(getApplicationContext(), "Não tem espaço livre suficiente. São necessários 75MB livres.",
                    Toast.LENGTH_LONG).show();
        }
        serviceStatus = Config.SERVICE_STATUS_STOP;
        PowerManager pm = (PowerManager) this.getSystemService(
                Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        return new LocalBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand: " + flags + "\nStart id: " +startId);
        this.serviceID = startId;
        if (flags == START_FLAG_REDELIVERY) {
            /***
             * Can be called if service is set to startForeground and is killed by the system under heavy memory pressure
             * The service had previously returned START_REDELIVER_INTENT but had been killed before calling stopSelf(int) for that Intent.
             */
            Log.i(TAG, "START_FLAG_REDELIVERY = TRUE");
            startOrPause();
        }
        viewCtrl.updateViewTime(timestamp);
        Log.i(TAG, "TEMPO: " + timestamp);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        if (sensors != null) {
            sensors.stop();
            notificationDone();
            serviceStatus = Config.SERVICE_STATUS_STOP;
            if (updateTime != null) {
                updateTime.cancel();
                updateTime.purge();
            }
            viewCtrl.updateViewTime(0);
            timestamp = 0;
        }
    }

    // Notification
    private void updateNotification(String s) {
        mBuilder.setContentText(s);
        mBuilder.setColor(ContextCompat.getColor(this, R.color.green));
        notification = mBuilder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        mNotifyManager.notify(1, notification);
    }

    private void notificationError() {
        mBuilder.setContentText("An error occured");
        mBuilder.setProgress(0, 0, false);
        mBuilder.setColor(ContextCompat.getColor(this, R.color.red));
        notification = mBuilder.build();
        mNotifyManager.notify(1, notification);
    }

    private void notificationPause() {
        mBuilder.setContentText("Paused");
        mBuilder.setColor(ContextCompat.getColor(this, R.color.yellow));
        notification = mBuilder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        mNotifyManager.notify(1, notification);
    }

    private void notificationDone() {
        if (mBuilder != null) {
            mBuilder.setContentText("Stopped");
            mBuilder.setProgress(0, 0, false);
            mBuilder.setColor(ContextCompat.getColor(this, R.color.red));
            notification = mBuilder.build();
            mNotifyManager.notify(1, notification);
        }
    }

    private void createNotification() {
        if (mNotifyManager == null || mBuilder == null || notification == null) {
            Intent resultIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setContentIntent(pendingIntent);
            mNotifyManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(this);
            mBuilder.setContentTitle("Sensor Collection")
                    .setContentText("Initializing..")
                    .setContentIntent(pendingIntent)
                    .setSmallIcon(R.mipmap.ic_launcher);
            mBuilder.setColor(ContextCompat.getColor(this, R.color.green));
            notification = mBuilder.build();
            notification.flags = Notification.FLAG_ONGOING_EVENT;
            mNotifyManager.notify(1, notification);
        } else {
            updateNotification("Initializing..");
        }
    }

    /**
     * Sensors callbacks
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        //Log.i(TAG, event.sensor.getType() + ": x: " + String.valueOf(event.values[0]) + " y: " + String.valueOf(event.values[1]) + " z: " + String.valueOf(event.values[2]));
        //fileWriter.writeSensorData(event);
        if (event.values[0] < event.sensor.getMaximumRange()) {
            currentValues = new float[]{event.values[0], event.values[1], event.values[2]};
            filteredValues = new float[]{event.values[0] - offset[0], event.values[1] - offset[1], event.values[2] - offset[2]};
            fileWriter.writeSensorData(filteredValues);
            //Log.i(TAG, "Calibrado: " + filteredValues[0] + ", " + filteredValues[1] + ", " + filteredValues[2]);
        }
    }

    private void getCalibrationValues() {
        SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.offset), 0);
        offset = new float[3];
        offset[0] = sharedPref.getFloat("x", 0.0f);
        offset[1] = sharedPref.getFloat("y", 0.0f);
        offset[2] = sharedPref.getFloat("z", 0.0f);
        Log.i(TAG, "Offset from settings: " + offset[0] + ", " + offset[1] + ", " + offset[2]);
    }

    @Override
    public void onError(String errorMessage) {
        Log.i(TAG, "callback error");
        fileWriter.closeFile();
        notificationError();
    }

    @Override
    public void startOrPause() {
        Log.i(TAG, "Pause");
        if (serviceStatus == Config.SERVICE_STATUS_RUNNING) {
            if (sensors != null) {
                sensors.stop();
                notificationPause();
                serviceStatus = Config.SERVICE_STATUS_PAUSED;
                updateTime.cancel();
                updateTime.purge();
            }
        } else {
            if (fileWriter.hasFreeSpace()) {
                wl.acquire();
                if (notification==null) {
                    createNotification();
                }
                if (offset == null) {
                    getCalibrationValues();
                }
                // Now that we have a notification, we disalow android to kill the service
                if (notification!=null) {
                    startForeground(1, notification);
                }
                sensors.addOnChangedListener(this);
                sensors.start(sensorMap);
                serviceStatus = Config.SERVICE_STATUS_RUNNING;
                if (updateTime != null) {
                    updateTime.cancel();
                    updateTime.purge();
                }
                updateTime = new Timer();
                updateTime.schedule(new updateTime(), 1000, 1000);
            } else {
                Toast.makeText(getApplicationContext(), "Não tem espaço livre suficiente. São necessários 75MB livres.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stop");
        if (serviceStatus != Config.SERVICE_STATUS_STOP) {
            if (sensors != null) {
                sensors.stop();
                serviceStatus = Config.SERVICE_STATUS_STOP;
                if (updateTime != null) {
                    updateTime.cancel();
                    updateTime.purge();
                }
                stopForeground(true);
                viewCtrl.updateViewTime(0);
                timestamp = 0;
            }
            SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.preference), 0);
            String server_address = sharedPref.getString(this.getResources().getString(R.string.server_address), "");
            new DataUpload(this, server_address);
            notificationDone();
        }
        if (wl.isHeld()) {
            wl.release();
        }
    }

    @Override
    public int getStatus() {
        return serviceStatus;
    }

    @Override
    public void error(String error) {
        stop();
        if (error.equals("FileNotFound")) {
            fileWriter = new Files(this, this);
            if (!fileWriter.hasFreeSpace()) {
                Toast.makeText(getApplicationContext(), "Não tem espaço livre suficiente. São necessários 75MB livres.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    class updateTime extends TimerTask {
        DecimalFormat numberFormat = new DecimalFormat("#.00");

        public void run() {
            if (viewCtrl != null) {
                updateNotification(numberFormat.format(filteredValues[0]) + ", " + numberFormat.format(filteredValues[1]) + ", " + numberFormat.format(filteredValues[2]));
                Handler refresh = new Handler(Looper.getMainLooper());
                refresh.post(new Runnable() {
                    public void run() {
                        viewCtrl.updateViewTime(timestamp);
                    }
                });
            }
            timestamp++;
        }
    }

}
