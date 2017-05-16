package iot.nimbl3.example;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.things.pio.PeripheralManagerService;

import java.io.IOException;

public class HomeActivity extends Activity {
    private static final String TAG = "HomeActivity";

    private final String LED_GPIO_PIN = "PWM0";
    private static final int DURATION_MS = 350;
    private static final int DELAY_MS = 150;

    private Button mBtPushButton;
    private PeripheralManagerService mService = new PeripheralManagerService();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        // For Debug:
        Log.d(TAG, "Available GPIO: " + mService.getGpioList());
        Log.d(TAG, "Available PWM: " +mService.getPwmList());

        mBtPushButton = (Button) findViewById(R.id.home_bt_push_button);
        mBtPushButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToPushActivity();
            }
        });
    }

    private void moveToPushActivity() {
        Intent intent = new Intent(this, PushButtonActivity.class);
        startActivity(intent);
        finish();
    }
}

