package com.example.android.alertia;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;

public class VoiceRecognition extends AppCompatActivity implements RecognitionListener {
    private static final String LOG_TAG = VoiceRecognition.class.getSimpleName();
    private final int REQUEST_SPEECH_RECOGNIZER = 3000;
    private final int REQUEST_CALL_SEND_PHONE = 5000;
    private final int REQUEST_LOCATION = 7000;
    private final String[] mQuestion = {"Do you need any assistance?",
            "Are you feeling sleepy?"};
    @BindView(R.id.tvstt)
    TextView mTextView;
    private String mAnswer = "";
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
        setContentView(R.layout.voicerecog);
        // Bind Views to Objects
        ButterKnife.bind(this);

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

        startSpeechRecognizer();
    }

    @Override
    public void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (speech != null) {
            speech.destroy();
        }
        super.onDestroy();
    }

    // Set listener to recognize voice and request permission if not granted
    private void startSpeechRecognizer() {
        // Recognize voice creation and extras
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                "en");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                this.getPackageName());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        // Request Permissions if SDK API > 23 and start listening for recognition
        if (Build.VERSION.SDK_INT < 23) {
            speech.startListening(recognizerIntent);
        } else {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                speech.startListening(recognizerIntent);
            } else {
                final String[] PERMISSIONS_AUDIO = {Manifest.permission.RECORD_AUDIO};
                //Asking request Permissions
                ActivityCompat.requestPermissions(this, PERMISSIONS_AUDIO, REQUEST_SPEECH_RECOGNIZER);
            }
        }
    }

    // TTS speak
    private void speak(String text) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    // Helper function to call and send SMS in casse of emergency
    // Request permissions if not granted
    private void callPhone() {
        if (Build.VERSION.SDK_INT < 23) {
            callForHelp();
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                callForHelp();
            } else {
                final String[] PERMISSIONS_STORAGE = {Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
                //Requesting Permissions
                ActivityCompat.requestPermissions(this, PERMISSIONS_STORAGE, REQUEST_CALL_SEND_PHONE);
            }
        }
    }

    // Send SMS with location embedded and call phone
    // Number to send and call is retrieved through the SharedPreferences settings_phone_key in which the user
    // has set.
    private void callForHelp() {

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // getString retrieves a String value from the preferences.
        // The second parameter is the default value for this preference.
        String phoneNumber = sharedPrefs.getString(getString(R.string.settings_phone_key),
                getString(R.string.settings_phone_default));


        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG, "Sending SMS");
            String message;
            message = getLocationMessage();
            sendSMS(phoneNumber, message);
        } else {
            Toast.makeText(this, "You don't assign permission to send SMS.", Toast.LENGTH_SHORT).show();
        }

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Log.e(LOG_TAG,"Calling Phone");
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + phoneNumber));
            this.startActivity(callIntent);
        } else {
            Toast.makeText(this, "You don't assign permission to call.", Toast.LENGTH_SHORT).show();
        }
    }

    //---sends an SMS message to another device---
    private void sendSMS(String phoneNumber, String message) {
        SmsManager sms = SmsManager.getDefault();
        sms.sendTextMessage(phoneNumber, null, message, null, null);
    }

    private String getLocationMessage() {
        String sms = "I HAVE FALLEN HERE AND I NEED HELP: ";
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return null;
        }
        if (locationManager != null) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, new LocationListener() {
                @Override
                public void onStatusChanged(String s, int i, Bundle bundle) {
                }

                @Override
                public void onProviderEnabled(String s) {
                }

                @Override
                public void onProviderDisabled(String s) {
                }

                @Override
                public void onLocationChanged(final Location location) {
                }
            });
        }

        Location myLocation = null;
        if (locationManager != null) {
            myLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
        }
        double longitude = 0;
        if (myLocation != null) {
            longitude = myLocation.getLongitude();
        }
        double latitude = 0;
        if (myLocation != null) {
            latitude = myLocation.getLatitude();
        }
        sms += "https://www.google.com/maps/?q=" + latitude + "," + longitude;
        Log.e(LOG_TAG, sms);
        return sms;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, int[] grantResults) {
        boolean permissionGranted = false;
        switch (requestCode) {
            case REQUEST_SPEECH_RECOGNIZER:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startSpeechRecognizer();
                } else {
                    Toast.makeText(VoiceRecognition.this, "Permission denied to hear you!", Toast
                            .LENGTH_SHORT).show();
                }
                break;
            case REQUEST_CALL_SEND_PHONE:

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = true;
                }

                if (permissionGranted) {
                    callPhone();
                } else {
                    Toast.makeText(this, "Permission denied to call/sent sms to phone.", Toast.LENGTH_SHORT).show();
                }
                break;
            case REQUEST_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this, "Location permission denied!", Toast
                            .LENGTH_SHORT).show();
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
    public void onError(int errorCode) {
        String errorMessage = getErrorText(errorCode);
        Log.d(LOG_TAG, "FAILED " + errorMessage);
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