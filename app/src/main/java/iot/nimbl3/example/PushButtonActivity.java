package iot.nimbl3.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class PushButtonActivity extends Activity {

    private static final String TAG = "ButtonActivity";

    private static final int INTERVAL_BETWEEN_BLINKS_MS = 500;

    private final String BUTTON_PIN_NAME = "BCM17"; // GPIO port wired to the button
    private final String LED_GPIO_PIN = "BCM4"; // GPIO port wired to the LED
    private Handler mHandler = new Handler();

    private Gpio mLedGpio;
    private Gpio mButtonGpio;

    private PeripheralManagerService mPeripheralManagerService;

    private Button mBtMoveToCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_push_button);
        mPeripheralManagerService = new PeripheralManagerService();
        mBtMoveToCamera = (Button) findViewById(R.id.push_bt_to_camera);
        mBtMoveToCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLedGpio != null) {
                    try {
                        mLedGpio.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error on PeripheralIO API", e);
                    }
                }
                Intent cameraIntent = new Intent(PushButtonActivity.this, DoorCameraActivity.class);
                startActivity(cameraIntent);
                PushButtonActivity.this.finish();
            }
        });
        connectButtonBcm();
        connectLedBcm();
    }

    private void connectButtonBcm() {
        try {
            // Create GPIO connection.
            mButtonGpio = mPeripheralManagerService.openGpio(BUTTON_PIN_NAME);
            // Configure as an input.
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            // Enable edge trigger events.
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            // Register an event callback.
            mButtonGpio.registerGpioCallback(mCallback);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private void connectLedBcm() {
        try {
            mLedGpio = mPeripheralManagerService.openGpio(LED_GPIO_PIN);
            // Configure as an output.
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
            Toast.makeText(this, "LED connected!", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    private Runnable mBlinkLedRunnable = new Runnable() {
        int blinkCounter = 0;
        @Override
        public void run() {
            // Exit if the GPIO is already closed
            if (mLedGpio == null) {
                return;
            }
            if (blinkCounter < 10) {
                try {
                    // Toggle the LED state
                    mLedGpio.setValue(!mLedGpio.getValue());
                    blinkCounter++;
                    // Schedule another event after delay.
                    mHandler.postDelayed(mBlinkLedRunnable, INTERVAL_BETWEEN_BLINKS_MS);
                    Log.e(TAG, "blink counter = " +blinkCounter);
                } catch (IOException e) {
                    Log.e(TAG, "Error on PeripheralIO API", e);
                }
            } else {
                blinkCounter = 0;
            }
        }
    };

    // Register an event callback.
    private GpioCallback mCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            Toast.makeText(PushButtonActivity.this, "Button pressed on: " +BUTTON_PIN_NAME, Toast.LENGTH_SHORT).show();
            toggleLed();
            // Return true to keep callback active.
            return true;
        }
    };

    private void toggleLed() {
        if (mLedGpio == null) {
            Toast.makeText(this, "Error!", Toast.LENGTH_SHORT).show();
            return;
        }
        mHandler.post(mBlinkLedRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacks(mBlinkLedRunnable);
        // Close the resource
        if (mButtonGpio != null) {
            mButtonGpio.unregisterGpioCallback(mCallback);
            try {
                mButtonGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    }
}