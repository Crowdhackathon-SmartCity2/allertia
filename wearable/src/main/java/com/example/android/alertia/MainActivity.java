package com.example.android.alertia;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.math3.complex.Quaternion;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;


public class MainActivity extends WearableActivity implements SensorEventListener,
        ActivityCompat.OnRequestPermissionsResultCallback, RecognitionListener {

    public static final String TAG = MainActivity.class.getSimpleName();
    static final float ALPHA = 0.25f;
    private static final int PERMISSION_REQUEST_SPEECH_RECOGNIZER = 101;
    private static final int PERMISSION_REQUEST_READ_BODY_SENSORS = 1;
    private static final int PERMISSION_REQUEST_CALL_PHONE = 9;
    @BindView(R.id.text)
    TextView mTextView;
    @BindView(R.id.text2)
    TextView mTextViewStepCount;
    @BindView(R.id.text3)
    TextView mTextViewStepDetect;
    private SensorManager mSensorManager;
    private float[] gravValues = new float[3];

    private Sensor mHeartRateSensor;
    private Sensor mStepCountSensor;
    private Sensor mStepDetectSensor;
    private Sensor mAccelerometer;
    private long lastUpdate;
    private Quaternion gbefore;

    private TextToSpeech tts;
    private SpeechRecognizer speech;
    private Intent recognizerIntent;

    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[]{Manifest.permission.BODY_SENSORS},
                PERMISSION_REQUEST_READ_BODY_SENSORS);

        ActivityCompat.requestPermissions(
                MainActivity.this,
                new String[]{Manifest.permission.CALL_PHONE},
                PERMISSION_REQUEST_CALL_PHONE);

        // Create TextToSpeech service in order to ask questions and wait till initialized
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "This Lang is not supported");
                    } else {
                        speak("Are you feeling ok?");
                        while (tts.isSpeaking()) {
                            try {
                                TimeUnit.SECONDS.sleep(2);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else {
                    Log.e("TTS", "Init Failed");
                }
            }
        });

        // Create a Speech Recognizer to retrieve answer by users
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(this);

        //speak("Are you feeling ok?");
        startSpeechRecognizer();

        // Enables Always-on
        setAmbientEnabled();

        getStepCount();

    }

    private void startSpeechRecognizer() {
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        if (Build.VERSION.SDK_INT < 23) {
            speak("Are you feeling ok?");
            voice();
        } else {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                speak("Are you feeling ok?");
                voice();
            } else {
                final String[] PERMISSIONS_AUDIO = {Manifest.permission.RECORD_AUDIO};
                //Asking request Permissions
                ActivityCompat.requestPermissions(this, PERMISSIONS_AUDIO, PERMISSION_REQUEST_SPEECH_RECOGNIZER);
            }
        }
    }

    private void voice() {
        speech.startListening(recognizerIntent);
    }

    private void getStepCount() {
        mHeartRateSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        mStepCountSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        mStepDetectSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        mSensorManager.registerListener(this, mHeartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mStepCountSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mStepDetectSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        Sensor mySensor = event.sensor;

        if (mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravValues = event.values;
            final long curTime = System.currentTimeMillis();
            if ((curTime - lastUpdate) > 100) {
                gbefore = new Quaternion(0, gravValues[0], gravValues[1], gravValues[2]);
                double gBeforeMag = gbefore.getNorm();

                mTextView.setText("" + gBeforeMag);

                if (gBeforeMag < 5 * SensorManager.GRAVITY_EARTH) {
                    lastUpdate = curTime;
                } else {
                    Log.e(TAG, "ACCEL THRESHOLD PASSED");
                    mTextView.setText("FALL DETECTED");
                    //FallDetection.setLastPeak(curTime);
                    lastUpdate = curTime;
                    startSpeechRecognizer();
                    //Intent i = new Intent(this, VoiceRecognition.class);
                    //startActivity(i);
                }
            }
        }
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (speech != null) {
            speech.destroy();
        }
        mSensorManager.unregisterListener(this);
        super.onDestroy();
    }

    private void callPhone() {
        if (Build.VERSION.SDK_INT < 23) {
            callForHelp();
        } else {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                callForHelp();
            } else {
                final String[] PERMISSIONS_CALL = {Manifest.permission.CALL_PHONE};
                //Asking request Permissions
                ActivityCompat.requestPermissions(this, PERMISSIONS_CALL, PERMISSION_REQUEST_CALL_PHONE);
            }
        }
    }

    private void callForHelp() {

 /*       SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // getString retrieves a String value from the preferences.
        // The second parameter is the default value for this preference.
        String phoneNumber = sharedPrefs.getString(getString(R.string.settings_phone_key),
                getString(R.string.settings_phone_default));
 */
        Intent intent = new Intent(Intent.ACTION_CALL);
        intent.setData(Uri.parse("tel:" + "6998519461"));
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + "6998519461"));
            this.startActivity(callIntent);
        } else {
            Toast.makeText(this, "You don't assign permission.", Toast.LENGTH_SHORT).show();
        }
    }

    private void speak(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged - accuracy: " + accuracy);
    }

    /*
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        String permissionResult = "Request code: " + requestCode + ", Permissions: " + permissions
                + ", Results: " + grantResults;
        Log.d(TAG, "onRequestPermissionsResult(): " + permissionResult);

        switch (requestCode) {
            case PERMISSION_REQUEST_READ_BODY_SENSORS:
                if ((grantResults.length == 1)
                        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                } else {
                    Toast.makeText(MainActivity.this, "Body Sensors Permission Denied!",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_REQUEST_SPEECH_RECOGNIZER:
                if ((grantResults.length == 1)
                        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    startSpeechRecognizer();
                } else {
                    Toast.makeText(MainActivity.this, "Speech Recognition Permission Denied!",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case PERMISSION_REQUEST_CALL_PHONE:
                if ((grantResults.length == 1)
                        && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    callPhone();
                } else {
                    Toast.makeText(MainActivity.this, "Call Phone Permission Denied!",
                            Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {

    }

    @Override
    public void onBeginningOfSpeech() {

    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {

    }

    @Override
    public void onEndOfSpeech() {

    }

    @Override
    public void onError(int error) {
        String errorMessage = getErrorText(error);
        Log.d(TAG, "FAILED " + errorMessage);
        mTextView.setText(errorMessage);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String text = "";
        for (String result : matches) {
            text += result + "\n";
        }
        mTextView.setText(text);
        if (text.toUpperCase().contains("YES")) {
            mTextView.setBackgroundColor(Color.GREEN);
        } else {
            mTextView.setBackgroundColor(Color.RED);
            callPhone();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }
}
