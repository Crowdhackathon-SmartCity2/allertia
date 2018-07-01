package com.example.android.alertia;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.apache.commons.math3.complex.Quaternion;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener,
        SensorEventListener {

    static final float ALPHA = 0.25f; // if ALPHA = 1 OR 0, no filter applies.
    private final String LOG_TAG = MainActivity.class.getSimpleName();
    @BindView(R.id.textView)
    TextView accel;

    //@BindView(R.id.theta)
    //TextView thetaView;

    private SensorManager mSensorManager;

    private Sensor mAccelerometer;
    private long lastUpdate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        lastUpdate = 0;
        // Obtain a reference to the SharedPreferences file for this app
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        // And register to be notified of preference changes
        // So we know when the user has adjusted the query settings
        prefs.registerOnSharedPreferenceChangeListener(this);

        ButterKnife.bind(this);
        // Get SensorManager and register for changes
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float[] gravValues = event.values;
            final long curTime = System.currentTimeMillis();
            if ((curTime - lastUpdate) > 100) {
                Quaternion gbefore = new Quaternion(0, gravValues[0], gravValues[1], gravValues[2]);
                double gBeforeMag = gbefore.getNorm();

                accel.setText("" + gBeforeMag);

                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                String sensString = sharedPrefs.getString(getString(R.string.sensitivity_key), "3");
                int sens = Integer.valueOf(sensString);
                double sensitivity;

                switch (sens) {
                    case 1:
                        sensitivity = 4;
                        break;
                    case 2:
                        sensitivity = 3;
                        break;
                    case 3:
                        sensitivity = 2;
                        break;
                    case 4:
                        sensitivity = 1.75;
                        break;
                    case 5:
                        sensitivity = 1.5;
                        break;
                    default:
                        sensitivity = 2;
                }
                //Log.e(LOG_TAG,"Sensitivity is " + sensitivity);

                if (gBeforeMag < sensitivity * SensorManager.GRAVITY_EARTH) {
                    lastUpdate = curTime;
                } else {
                    Log.e(LOG_TAG, "ACCEL THRESHOLD PASSED");
                    accel.setText("FALL DETECTED");
                    //FallDetection.setLastPeak(curTime);
                    lastUpdate = curTime;
                    Intent i = new Intent(this, VoiceRecognition.class);
                    startActivity(i);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    protected float[] lowPass(float[] input, float[] output) {
        if (output == null) return input;
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }
        return output;
    }

/*    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        boolean permissionGranted = false;
        switch (requestCode) {
            case 9:
                permissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (permissionGranted) {
            callForHelp();
        } else {
            Toast.makeText(this, "You don't assign permission to call.", Toast.LENGTH_SHORT).show();
        }
    }*/

/*    private void callForHelp() {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // getString retrieves a String value from the preferences.
        // The second parameter is the default value for this preference.
        String phoneNumber = sharedPrefs.getString(getString(R.string.settings_phone_key),
                getString(R.string.settings_phone_default));
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + phoneNumber));
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            this.startActivity(callIntent);
        } else {
            Toast.makeText(this, "You don't assign permission.", Toast.LENGTH_SHORT).show();
        }
    }*/


    @Override
    // This method initialize the contents of the Activity's options menu.
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the Options Menu we specified in XML
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

    }

    public void checkbluetooth(View view) {
        Intent i = new Intent(this, BluetoothSelection.class);
        startActivity(i);
    }
}
