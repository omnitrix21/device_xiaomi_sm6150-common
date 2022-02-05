/*
 * Copyright (C) 2015 The CyanogenMod Project
 *               2017-2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lineageos.settings.doze;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;
import androidx.preference.PreferenceManager;

import org.lineageos.settings.utils.FileUtils;

public class DozeService extends Service {
    private static final String TAG = "DozeService";
    private static final boolean DEBUG = false;

    private static final String DC_DIMMING_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display/msm_fb_ea_enable";
    private static final String DC_DIMMING_ENABLE_KEY = "dc_dimming_enable";
    private static final String HBM_NODE = "/sys/devices/platform/soc/soc:qcom,dsi-display/hbm";
    private static final String HBM_ENABLE_KEY = "hbm_mode";
    private boolean enableDc;
    private boolean enableHbm;

    private AodSensor mAodSensor;
    private ProximitySensor mProximitySensor;
    private PickupSensor mPickupSensor;
    private SharedPreferences sharedPrefs;

    @Override
    public void onCreate() {
        if (DEBUG)
            Log.d(TAG, "Creating service");
        mAodSensor = new AodSensor(this);
        mProximitySensor = new ProximitySensor(this);
        mPickupSensor = new PickupSensor(this);
        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        IntentFilter screenStateFilter = new IntentFilter();
        screenStateFilter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateFilter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenStateReceiver, screenStateFilter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG)
            Log.d(TAG, "Starting service");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (DEBUG)
            Log.d(TAG, "Destroying service");
        super.onDestroy();
        this.unregisterReceiver(mScreenStateReceiver);
        mProximitySensor.disable();
        mPickupSensor.disable();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onDisplayOn() {
        if (DEBUG)
            Log.d(TAG, "Display on");
        if (DozeUtils.isPickUpEnabled(this)) {
            mPickupSensor.disable();
        }
        if (DozeUtils.isHandwaveGestureEnabled(this) || DozeUtils.isPocketGestureEnabled(this)) {
            mProximitySensor.disable();
        }
        if (DozeUtils.isDozeAutoBrightnessEnabled(this)) {
            mAodSensor.disable();
        }
        enableNode(true);
    }

    private void onDisplayOff() {
        if (DEBUG)
            Log.d(TAG, "Display off");
        if (DozeUtils.isPickUpEnabled(this)) {
            mPickupSensor.enable();
        }
        if (DozeUtils.isHandwaveGestureEnabled(this) || DozeUtils.isPocketGestureEnabled(this)) {
            mProximitySensor.enable();
        }
        if (DozeUtils.isDozeAutoBrightnessEnabled(this)) {
            mAodSensor.enable();
        }
    }

    private void enableNode(boolean status) {
        enableDc = (sharedPrefs.getBoolean(DC_DIMMING_ENABLE_KEY, false));
        enableHbm = (sharedPrefs.getBoolean(HBM_ENABLE_KEY, false));
        if (enableDc) {
            FileUtils.writeLine(DC_DIMMING_NODE, status ? "1" : "0");
        }
        if (enableHbm) {
            FileUtils.writeLine(HBM_NODE, status ? "1" : "0");
        }
    }

    private BroadcastReceiver mScreenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                onDisplayOn();
            } else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                onDisplayOff();
            }
        }
    };
}
