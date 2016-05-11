package com.ubi.jason.sensorcollect;

import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.ubi.jason.sensorcollect.delegators.ActivityCtrl;
import com.ubi.jason.sensorcollect.delegators.ServiceCtrl;
import com.ubi.jason.sensorcollect.delegators.SettingsCtrl;
import com.ubi.jason.sensorcollect.delegators.StatusCtrl;
import com.ubi.jason.sensorcollect.fragments.FragmentCalibrate;
import com.ubi.jason.sensorcollect.helper.Config;
import com.ubi.jason.sensorcollect.helper.CustomDialog;
import com.ubi.jason.sensorcollect.helper.CustomListAdapterDrawer;
import com.ubi.jason.sensorcollect.interfaces.ActivityOptions;
import com.ubi.jason.sensorcollect.interfaces.DialogListener;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements DialogListener, ActivityOptions {

    private static final String TAG = "MAIN_ACT";
    static final int DIALOG_OK = -1;
    static final int DIALOG_CANCEL = -2;
    private static boolean START_COMMAND = false;
    private boolean mBound = false;
    private SensorsService sensorsService;
    private Intent serviceIntent;
    static final int DRAWER_DELAY = 200;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;
    private ListView drawerList;
    private static String appTitle;
    private ArrayList<String> drawerValues;
    /*
    Class delegators
     */
    private static ServiceCtrl serviceCtrl = ServiceCtrl.getInstance();
    private static StatusCtrl statusCtrl = StatusCtrl.getInstance();

    private final ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "onServiceConnected");
            SensorsService.LocalBinder binder = (SensorsService.LocalBinder) service;
            sensorsService = binder.getService();
            mBound = true;
            if (START_COMMAND) {
                serviceCtrl.startOrPause();
            }
            serviceCtrl.setService(sensorsService);
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "onServiceDisconnected");
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        appTitle = getResources().getString(R.string.app_name);
        drawerList = (ListView) findViewById(R.id.left_drawer);
        drawerList.setOnItemClickListener(new DrawerItemClickListener());
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close) {
            public void onDrawerClosed(View view) {
                super.onDrawerClosed(view);
                getSupportActionBar().setTitle(appTitle);
                invalidateOptionsMenu();
            }

            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                getSupportActionBar().setTitle("User info");
                invalidateOptionsMenu();
            }
        };
        drawerLayout.setDrawerListener(drawerToggle);
        ActivityCtrl.getInstance().setActivity(this);
        SettingsCtrl.getInstance().setContext(this);
        updateDrawerContent();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        drawerToggle.syncState();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume");
        if (!mBound) {
            Log.i(TAG, "startService");
            if (sensorsService == null) {
                serviceIntent = new Intent(this, SensorsService.class);
                startService(serviceIntent);
                bindService(serviceIntent, mConnection, BIND_AUTO_CREATE);
            } else {
                if (START_COMMAND) {
                    serviceCtrl.startOrPause();
                }
            }
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause");
        super.onPause();
        if (mBound) {
            if (sensorsService.getStatus() == Config.SERVICE_STATUS_STOP) {
                stopService(serviceIntent);
            }
            unbindService(mConnection);
            mBound = false;
        }
        START_COMMAND = false;
    }

    private void updateDrawerContent() {
        Log.i(TAG, "Populating drawer..");
        String drawerItems[] = getResources().getStringArray(R.array.drawer_items);
        String userInfoKeys[] = getResources().getStringArray(R.array.user_info_keys);
        drawerValues = getValuesFromPref(userInfoKeys);
        boolean tempComplete = true;
        for (String a : drawerValues) {
            if (a.equals("")) {
                tempComplete = false;
                if (!drawerLayout.isDrawerOpen(Gravity.LEFT)) {
                    Toast.makeText(getApplicationContext(), "Por favor complete os seus dados",
                            Toast.LENGTH_LONG).show();
                    new Handler().postDelayed(openDrawerRunnable(), DRAWER_DELAY);
                }
                break;
            }
        }
        statusCtrl.setINFO_COMPLETE(tempComplete);
        TypedArray drawerIcons = getResources().obtainTypedArray(R.array.drawer_icons);
        BaseAdapter adapter = new CustomListAdapterDrawer(this, drawerItems, drawerValues, drawerIcons);
        drawerList.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            createSettingsDialog();
            return true;
        } else if (id == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);  // OPEN DRAWER
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    public ArrayList getValuesFromPref(String[] drawerItems) {
        SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.preference), 0);
        ArrayList temp = new ArrayList<>();
        for (String a : drawerItems) {
            Log.i(TAG, "Got: " + sharedPref.getString(a, ""));
            temp.add(sharedPref.getString(a, ""));
        }
        return temp;
    }

    @Override
    public void showToast(String text) {
        Toast.makeText(this, text,
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void calibrate() {
        FragmentCalibrate f = new FragmentCalibrate();
        FragmentManager fm = getSupportFragmentManager(); //or getFragmentManager() if you are not using support library.
        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.container, f);
        ft.addToBackStack("fragmentCalibrate");
        ft.commit();
    }

    @Override
    public void openDrawer() {
        new Handler().postDelayed(openDrawerRunnable(), 0);
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            Log.i(TAG, "Clicked: " + position);
            if (position == 0) {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.ubi.pt"));
                startActivity(browserIntent);
            } else {
                if (serviceCtrl.getStatus() == Config.SERVICE_STATUS_STOP) {
                    createDialog(position);
                } else {
                    Toast.makeText(getApplicationContext(), "Monitorização em progresso.",
                            Toast.LENGTH_LONG).show();
                }
            }
        }

        private void createDialog(int position) {
            DialogFragment dialog = new CustomDialog();
            Bundle args = new Bundle();
            args.putInt("position", position);
            args.putString("value", drawerValues.get(position - 1));
            dialog.setArguments(args);
            dialog.show(getFragmentManager(), "custom_dialog");
        }
    }

    private void createSettingsDialog() {
        DialogFragment dialog = new CustomDialog();
        Bundle args = new Bundle();
        args.putInt("position", 0);
        SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.preference), 0);
        String server_address = sharedPref.getString(this.getResources().getString(R.string.server_address), "");
        args.putString("value", server_address);
        dialog.setArguments(args);
        dialog.show(getFragmentManager(), "custom_dialog");
    }

    @Override
    public void onDimiss(int code, int position, String input) {
        Log.i(TAG, "Clicked: " + code + " Input: " + input);
        if (code == DIALOG_OK) {
            if (!input.equals("")) {
                if (position == -1) {
                    SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.preference), 0);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString(this.getResources().getString(R.string.server_address), input);
                    editor.commit();
                } else {
                    SharedPreferences sharedPref = this.getSharedPreferences(getResources().getString(R.string.preference), 0);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    String title = this.getResources().getStringArray(R.array.user_info_keys)[position];
                    editor.putString(title, input);
                    editor.commit();
                    updateDrawerContent();
                }
            }
        }
    }

    private Runnable openDrawerRunnable() {
        return new Runnable() {
            @Override
            public void run() {
                ViewTreeObserver vto = drawerLayout.getViewTreeObserver();
                if (vto != null) vto.addOnPreDrawListener(new ShouldShowListener(drawerLayout));
            }
        };
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(TAG, "Codigo: " + requestCode);
    }

    private static class ShouldShowListener implements ViewTreeObserver.OnPreDrawListener {

        private final DrawerLayout drawerLayout;

        private ShouldShowListener(DrawerLayout drawerLayout) {
            this.drawerLayout = drawerLayout;
        }

        @Override
        public boolean onPreDraw() {
            if (drawerLayout != null) {
                ViewTreeObserver vto = drawerLayout.getViewTreeObserver();
                if (vto != null) {
                    vto.removeOnPreDrawListener(this);
                }
            }
            drawerLayout.openDrawer(Gravity.LEFT);
            return true;
        }
    }

}
