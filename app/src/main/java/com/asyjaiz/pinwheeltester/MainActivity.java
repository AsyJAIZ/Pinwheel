package com.asyjaiz.pinwheeltester;

import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.ENTER;
import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.EXIT;
import static com.asyjaiz.pinwheeltester.PinwheelBatteryDrawable.STABLE;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

//import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ImageView batteryView = findViewById(R.id.batteryView);
        /*CircleBatteryDrawable batteryDrawable = new CircleBatteryDrawable(this, this.getColor(R.color.meter_background_color));
        batteryDrawable.setBatteryLevel(5);
        batteryDrawable.setCharging(false);
        batteryDrawable.setShowPercent(false);
        batteryDrawable.setPowerSaveEnabled(true);*/
        PinwheelBatteryDrawable batteryDrawable = new PinwheelBatteryDrawable(this, getColor(R.color.meter_background_color), getResources());
        batteryDrawable.setBatteryLevel(50);
        batteryView.setImageDrawable(batteryDrawable);

        TextView percentageView = findViewById(R.id.percentageView);

        SeekBar progress = findViewById(R.id.progress);
        final int[] level = {50};
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                batteryDrawable.setBatteryLevel(progress);
                level[0] = progress;
                percentageView.setText(progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        Switch saver = findViewById(R.id.power_saver);
        Switch charge = findViewById(R.id.charge);

        saver.setOnCheckedChangeListener((buttonView, isChecked) -> batteryDrawable.setPowerSaveEnabled(isChecked));
        charge.setOnCheckedChangeListener((buttonView, isChecked) -> {
            batteryDrawable.setCharging(isChecked);
            /*if (batteryDrawable.animation) {
                if (batteryDrawable.fillAnim.isRunning())
                    batteryDrawable.fillAnim.cancel();
                else {
                    batteryDrawable.fillAnim.setFloatValues(0, level[0]);
                    batteryDrawable.fillAnim.setDuration(15L * level[0]);
                    batteryDrawable.fillAnim.reverse();
                }
                if (batteryDrawable.rotatingState == STABLE || batteryDrawable.rotatingState == ENTER)
                    batteryDrawable.rotatingState = EXIT;
                else if (isChecked) {
                    batteryDrawable.rotatingState = ENTER;
                }
            }*/
        });

        TextView wattageText = findViewById(R.id.wattageText);

        SeekBar wattage = findViewById(R.id.wattage);
        wattage.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float wattage = (float) progress / 10;
                batteryDrawable.setWattage(wattage);
                wattageText.setText(wattage + " Watt");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //batteryDrawable.setColors(Color.WHITE, Color.WHITE, Color.WHITE);
    }
}