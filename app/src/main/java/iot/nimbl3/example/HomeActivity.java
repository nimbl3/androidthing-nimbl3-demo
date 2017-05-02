package iot.nimbl3.example;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.FloatProperty;
import android.util.Log;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.Button;

import com.google.android.things.pio.PeripheralManagerService;
import com.google.android.things.pio.Pwm;

import java.io.IOException;

public class HomeActivity extends Activity {
    private static final String TAG = "HomeActivity";

    private final String LED_GPIO_PIN = "PWM0";
    private static final int DURATION_MS = 350;
    private static final int DELAY_MS = 150;

    private Button mBtPushButton;
    private PeripheralManagerService mService = new PeripheralManagerService();
    private Pwm mLed;
    private ObjectAnimator mFlameAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Log.d(TAG, "Available GPIO: " + mService.getGpioList());
        Log.d(TAG, "Available PWM: " +mService.getPwmList());
        /*try {
            mLed = openLed(LED_GPIO_PIN);
        } catch (IOException ex) {
            Log.e(TAG, ex.getMessage());
        }*/
        //mFlameAnimator = animateFlicker(mLed, DELAY_MS);
        //mFlameAnimator.start();

        mBtPushButton = (Button) findViewById(R.id.home_bt_push_button);
        mBtPushButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                moveToPushActivity();
            }
        });
    }

    // Not using for now
    private Pwm openLed(String name) throws IOException {
        Pwm led = mService.openPwm(name);
        led.setPwmFrequencyHz(240.0f);
        led.setPwmDutyCycle(25.0);
        led.setEnabled(true);

        return led;
    }

    // Close an LED connection
    private void closeLed(Pwm pwm) throws IOException {
        if (pwm != null) {
            pwm.setEnabled(false);
            pwm.close();
        }
    }

    // Not using for now
    private ObjectAnimator animateFlicker(Pwm led, long delay) {
        ObjectAnimator animator = ObjectAnimator
                .ofFloat(led, new BrightnessProperty(), 100, 25)
                .setDuration(DURATION_MS + delay);

        animator.setInterpolator(new BounceInterpolator());
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setRepeatCount(ValueAnimator.INFINITE);

        return animator;
    }

    private void moveToPushActivity() {
        Intent intent = new Intent(this, PushButtonActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //mFlameAnimator.cancel();
        try {
            closeLed(mLed);
        } catch (IOException ex) {

        }
    }



    /**
     * Property used to animate the "brightness" of an
     * LED as the duty cycle of a PWM.
     */
    private class BrightnessProperty extends FloatProperty<Pwm> {
        private static final String TAG = "BrightnessProperty";

        // Cache the last set value since PWM can't report its state
        private float mValue;

        BrightnessProperty() {
            super("PWM Brightness");
        }

        @Override
        public void setValue(Pwm pwm, float value) {
            mValue = Math.max(0f, Math.min(value, 100f));
            try {
                pwm.setPwmDutyCycle(mValue);
            } catch (IOException e) {
                Log.w(TAG, "Unable to set PWM duty cycle", e);
            }
        }

        @Override
        public Float get(Pwm pwm) {
            // We can't ask PWM for its current duty value
            return mValue;
        }
    }
}

