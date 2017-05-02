package iot.nimbl3.example;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.speech.tts.TextToSpeech;
import android.content.BroadcastReceiver;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.Pwm;

import java.io.IOException;
import java.nio.ByteBuffer;

import java.util.Locale;
import java.util.Objects;

import iot.nimbl3.example.model.BleHelper;
import iot.nimbl3.example.model.DoorCameraManager;

public class DoorCameraActivity extends Activity {
    private static final String TAG = DoorCameraActivity.class.getSimpleName();

    // GPIO PIN
    private final String BUTTON_CAMERA_GPIO_PIN = "BCM27";
    private final String BUTTON_BLUETOOTH_GPIO_PIN = "BCM17";
    private final String LED_GPIO_PIN = "BCM4";
    //----

    // Constant var
    private static final int DISCOVERABLE_TIMEOUT_MS = 300;
    private static final int REQUEST_CODE_ENABLE_DISCOVERABLE = 100;
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 500;
    private static final String UTTERANCE_ID = "iot.nimbl3.example.UTTERANCE_ID";

    // Photo & camera
    private Handler mCameraHandler;
    private HandlerThread mCameraThread;
    private DoorCameraManager mCamera;

    private Button mCameraButton;
    private Button mBluetoothButton;
    private ImageView mIvPhoto;
    //----

    // Sounding & bluetooth
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothProfile mA2DPSinkProxy;

    private ButtonInputDriver mPairingButtonDriver;
    private ButtonInputDriver mDisconnectAllButtonDriver;
    private TextToSpeech mTtsEngine;
    //-----

    // Light:
    private Gpio mLedGpio;
    //----

    private PeripheralManagerService mPeripheralManagerService;
    private Handler mHandler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_door_camera);
        mIvPhoto = (ImageView) findViewById(R.id.camera_iv_photo);
        mPeripheralManagerService = new PeripheralManagerService();

        checkPermission();
        initGpio();
        initCameraComponents();
        initBluetoothComponents();
        initLight();
    }

    private void initLight() {
        try {
            mLedGpio = mPeripheralManagerService.openGpio(LED_GPIO_PIN);
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Toast.makeText(this, "LED connected!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void initBluetoothComponents() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "No default Bluetooth adapter. Device likely does not support bluetooth.");
            return;
        }

        // We use Text-to-Speech to indicate status change to the user
        initTts();

        registerReceiver(mAdapterStateChangeReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_STATE_CHANGED));
        registerReceiver(mSinkProfileStateChangeReceiver, new IntentFilter(
                BleHelper.ACTION_CONNECTION_STATE_CHANGED));
        registerReceiver(mSinkProfilePlaybackChangeReceiver, new IntentFilter(
                BleHelper.ACTION_PLAYING_STATE_CHANGED));

        if (mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth Adapter is already enabled.");
            initA2DPSink();
        } else {
            Log.d(TAG, "Bluetooth adapter not enabled. Enabling.");
            mBluetoothAdapter.enable();
        }

    }

    private void toggleLed() {
        if (mLedGpio == null) {
            Toast.makeText(this, "Error!", Toast.LENGTH_SHORT).show();
            return;
        }
        mHandler.post(mBlinkRunnable);
    }

    private void initGpio() {
        try {
            mCameraButton = new Button(BUTTON_CAMERA_GPIO_PIN, Button.LogicState.PRESSED_WHEN_LOW);
            mCameraButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        Toast.makeText(DoorCameraActivity.this, BUTTON_CAMERA_GPIO_PIN +" clicked!", Toast.LENGTH_SHORT).show();
                        // Doorbell rang!
                        mCamera.takePicture();
                        toggleLed();
                        playAlertSound();
                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
    }

    private void playAlertSound() {
        int resID=getResources().getIdentifier("sound", "raw", getPackageName());
        MediaPlayer mediaPlayer=MediaPlayer.create(this,resID);
        mediaPlayer.start();
    }

    private void initCameraComponents() {
        mCamera = DoorCameraManager.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);
    }


    // Callback to receive captured camera image data
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // Get the raw image bytes
                    Image image = reader.acquireLatestImage();
                    ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                    final byte[] imageBytes = new byte[imageBuf.remaining()];
                    imageBuf.get(imageBytes);
                    image.close();

                    onPictureTaken(imageBytes);
                }
            };

    private void onPictureTaken(byte[] imageBytes) {
        if (imageBytes != null) {
            // Show it:
            Toast.makeText(this, "Photo taken!!", Toast.LENGTH_SHORT).show();
            Bitmap bitmapImage = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, null);
            mIvPhoto.setImageBitmap(bitmapImage);
        }
    }

    private void checkPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.d(TAG, "No Camera permission");
        }

        if (checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "No Bluetooth permission");
        }
    }

    private final BroadcastReceiver mAdapterStateChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            int oldState = BleHelper.getPreviousAdapterState(intent);
            int newState = BleHelper.getCurrentAdapterState(intent);
            Log.d(TAG, "Bluetooth Adapter changing state from " + oldState + " to " + newState);
            if (newState == BluetoothAdapter.STATE_ON) {
                Log.i(TAG, "Bluetooth Adapter is ready");
                initA2DPSink();
            }
        }
    };

    private final BroadcastReceiver mSinkProfileStateChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BleHelper.ACTION_CONNECTION_STATE_CHANGED)) {
                int oldState = BleHelper.getPreviousProfileState(intent);
                int newState = BleHelper.getCurrentProfileState(intent);
                BluetoothDevice device = BleHelper.getDevice(intent);
                Log.d(TAG, "Bluetooth A2DP sink changing connection state from " + oldState +
                        " to " + newState + " device " + device);
                if (device != null) {
                    String deviceName = Objects.toString(device.getName(), "a device");
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        speak("Connected to " + deviceName);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        speak("Disconnected from " + deviceName);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mSinkProfilePlaybackChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BleHelper.ACTION_PLAYING_STATE_CHANGED)) {
                int oldState = BleHelper.getPreviousProfileState(intent);
                int newState = BleHelper.getCurrentProfileState(intent);
                BluetoothDevice device = BleHelper.getDevice(intent);
                Log.d(TAG, "Bluetooth A2DP sink changing playback state from " + oldState +
                        " to " + newState + " device " + device);
                if (device != null) {
                    if (newState == BleHelper.STATE_PLAYING) {
                        Log.i(TAG, "Playing audio from device " + device.getAddress());
                    } else if (newState == BleHelper.STATE_NOT_PLAYING) {
                        Log.i(TAG, "Stopped playing audio from " + device.getAddress());
                    }
                }
            }
        }
    };

    private void initA2DPSink() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not available or not enabled.");
            return;
        }
        Log.d(TAG, "Set up Bluetooth Adapter name and profile");
        mBluetoothAdapter.setName("Nimbl3 Things");
        mBluetoothAdapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                mA2DPSinkProxy = proxy;
                enableDiscoverable();
            }
            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BleHelper.A2DP_SINK_PROFILE);

        configureBluetoothButton();
    }

    /**
     * Enable the current {@link BluetoothAdapter} to be discovered (available for pairing) for
     * the next {@link #DISCOVERABLE_TIMEOUT_MS} ms.
     */
    private void enableDiscoverable() {
        Log.d(TAG, "Registering for discovery.");
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                DISCOVERABLE_TIMEOUT_MS);
        startActivityForResult(discoverableIntent, REQUEST_CODE_ENABLE_DISCOVERABLE);
    }

    private void speak(String utterance) {
        Log.i(TAG, utterance);
        if (mTtsEngine != null) {
            mTtsEngine.speak(utterance, TextToSpeech.QUEUE_ADD, null, UTTERANCE_ID);
        }
    }

    private void startBackgroundThread() {
        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_DISCOVERABLE) {
            Log.d(TAG, "Enable discoverable returned with result " + resultCode);

            // ResultCode, as described in BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, is either
            // RESULT_CANCELED or the number of milliseconds that the device will stay in
            // discoverable mode. In a regular Android device, the user will see a popup requesting
            // authorization, and if they cancel, RESULT_CANCELED is returned. In Android Things,
            // on the other hand, the authorization for pairing is always given without user
            // interference, so RESULT_CANCELED should never be returned.
            if (resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Enable discoverable has been cancelled by the user. " +
                        "This should never happen in an Android Things device.");
                return;
            }
            Log.i(TAG, "Bluetooth adapter successfully set to discoverable mode. " +
                    "Any A2DP source can find it with the name " + "Nimbl3 Things" +
                    " and pair for the next " + DISCOVERABLE_TIMEOUT_MS + " ms. " +
                    "Try looking for it on your phone, for example.");

            // There is nothing else required here, since Android framework automatically handles
            // A2DP Sink. Most relevant Bluetooth events, like connection/disconnection, will
            // generate corresponding broadcast intents or profile proxy events that you can
            // listen to and react appropriately.

            speak("Bluetooth audio sink is discoverable for " + DISCOVERABLE_TIMEOUT_MS +
                    " milliseconds. Look for a device named " + "Nimbl3 Things");

        }
    }

    private void disconnectConnectedDevices() {
        if (mA2DPSinkProxy == null || mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return;
        }
        speak("Disconnecting devices");
        for (BluetoothDevice device: mA2DPSinkProxy.getConnectedDevices()) {
            Log.i(TAG, "Disconnecting device " + device);
            BleHelper.disconnect(mA2DPSinkProxy, device);
        }
    }

    private void configureBluetoothButton() {
        try {
            mBluetoothButton = new Button(BUTTON_BLUETOOTH_GPIO_PIN, Button.LogicState.PRESSED_WHEN_LOW);
            mBluetoothButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    if (pressed) {
                        Toast.makeText(DoorCameraActivity.this, BUTTON_BLUETOOTH_GPIO_PIN +" clicked!", Toast.LENGTH_SHORT).show();

                    }
                }
            });
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
    }

    private boolean isEnabled;
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_P:
                // Enable Pairing mode (discoverable)
                if (!isEnabled) {
                    enableDiscoverable();
                    isEnabled = true;
                } else {
                    disconnectConnectedDevices();
                    isEnabled = false;
                }
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void initTts() {
        mTtsEngine = new TextToSpeech(DoorCameraActivity.this,
                new TextToSpeech.OnInitListener() {
                    @Override
                    public void onInit(int status) {
                        if (status == TextToSpeech.SUCCESS) {
                            mTtsEngine.setLanguage(Locale.US);
                        } else {
                            Log.w(TAG, "Could not open TTS Engine (onInit status=" + status
                                    + "). Ignoring text to speech");
                            mTtsEngine = null;
                        }
                    }
                });
    }

    private Runnable mBlinkRunnable = new Runnable() {
        int blinkCounter = 0;
        @Override
        public void run() {
            // Exit if the GPIO is already closed
            if (mLedGpio == null) {
                return;
            }
            if (blinkCounter < 10) {
                try {
                    // Step 3. Toggle the LED state
                    mLedGpio.setValue(!mLedGpio.getValue());
                    blinkCounter++;
                    // Step 4. Schedule another event after delay.
                    mHandler.postDelayed(mBlinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);
                    Log.e(TAG, "blink counter = " +blinkCounter);
                } catch (IOException e) {
                    Log.e(TAG, "Error on PeripheralIO API", e);
                }
            } else {
                blinkCounter = 0;
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCameraThread.quitSafely();
        mCamera.shutDown();
        try {
            mCameraButton.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }


        try {
            if (mPairingButtonDriver != null) mPairingButtonDriver.close();
        } catch (IOException e) { /* close quietly */}
        try {
            if (mDisconnectAllButtonDriver != null) mDisconnectAllButtonDriver.close();
        } catch (IOException e) { /* close quietly */}

        unregisterReceiver(mAdapterStateChangeReceiver);
        unregisterReceiver(mSinkProfileStateChangeReceiver);
        unregisterReceiver(mSinkProfilePlaybackChangeReceiver);

        if (mA2DPSinkProxy != null) {
            mBluetoothAdapter.closeProfileProxy(BleHelper.A2DP_SINK_PROFILE,
                    mA2DPSinkProxy);
        }

        if (mTtsEngine != null) {
            mTtsEngine.stop();
            mTtsEngine.shutdown();
        }
    }
}
