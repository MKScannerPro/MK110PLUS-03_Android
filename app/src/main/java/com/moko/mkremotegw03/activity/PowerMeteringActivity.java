package com.moko.mkremotegw03.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mkremotegw03.AppConstants;
import com.moko.mkremotegw03.R;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityPowerMeteringBinding;
import com.moko.mkremotegw03.dialog.AlertMessage03Dialog;
import com.moko.mkremotegw03.entity.MQTTConfig;
import com.moko.mkremotegw03.entity.MokoDevice;
import com.moko.mkremotegw03.utils.SPUtiles;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw03.MQTTConstants03;
import com.moko.support.remotegw03.MQTTSupport03;
import com.moko.support.remotegw03.entity.MsgConfigResult;
import com.moko.support.remotegw03.entity.MsgReadResult;
import com.moko.support.remotegw03.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

/**
 * @author: jun.liu
 * @date: 2023/7/5 11:33
 * @des:
 */
public class PowerMeteringActivity extends BaseActivity<ActivityPowerMeteringBinding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;
    private boolean isMeteringSwitch;

    @Override
    protected ActivityPowerMeteringBinding getViewBinding() {
        return ActivityPowerMeteringBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mBind.tvReset.setOnClickListener(v -> resetEnergyData());
        mBind.layoutMeteringSwitch.setOnClickListener(v -> {
            if (isWindowLocked()) return;
            if (!MQTTSupport03.getInstance().isConnected()) {
                ToastUtils.showToast(this, R.string.network_error);
                return;
            }
            Intent i = new Intent(this, MeteringSettingsActivity.class);
            i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
            i.putExtra("enable", isMeteringSwitch);
            startActivity(i);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPowerMeteringEnable();
    }

    private void resetEnergyData() {
        AlertMessage03Dialog dialog = new AlertMessage03Dialog();
        dialog.setMessage("After reset, energy data will be deleted, please confirm again whether to reset it？");
        dialog.setTitle("Reset Energy Data");
        dialog.setOnAlertConfirmListener(() -> {
            mHandler.postDelayed(this::dismissLoadingProgressDialog, 30 * 1000);
            showLoadingProgressDialog();
            int msgId = MQTTConstants03.CONFIG_MSG_ID_RESET_ENERGY_DATA;
            //这里不能直接传null
            String message = assembleWriteCommonData(msgId, mMokoDevice.mac, new JsonObject());
            try {
                MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message))
            return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants03.READ_MSG_ID_POWER_METERING_ENABLE) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            int enable = result.data.get("switch_value").getAsInt();
            if (enable == 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            isMeteringSwitch = enable == 1;
            mBind.tvMeteringSwitch.setText(enable == 1 ? "ON" : "OFF");
            mBind.layoutMetering.setVisibility(enable == 1 ? View.VISIBLE : View.GONE);
            if (enable == 1) {
                getPowerMeteringData();
                getEnergyData();
            }
        }
        if (msg_id == MQTTConstants03.READ_MSG_ID_POWER_DATA || msg_id == MQTTConstants03.NOTIFY_MSG_ID_POWER_DATA) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            String voltage = result.data.get("voltage").getAsString();
            int current = (int) ((result.data.get("current").getAsDouble()) * 1000);
            String power = result.data.get("power").getAsString();
            mBind.tvVoltage.setText(voltage);
            mBind.tvCurrentMa.setText(String.valueOf(current));
            mBind.tvPower.setText(power);
        }
        if (msg_id == MQTTConstants03.READ_MSG_ID_ENERGY_DATA || msg_id == MQTTConstants03.NOTIFY_MSG_ID_ENERGY_DATA) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            String energy = result.data.get("energy").getAsString();
            mBind.tvEnergy.setText(energy);
        }

        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_RESET_ENERGY_DATA) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code == 0) {
                getPowerMeteringData();
                getEnergyData();
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
    }

    private void getPowerMeteringEnable() {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        int msgId = MQTTConstants03.READ_MSG_ID_POWER_METERING_ENABLE;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getPowerMeteringData() {
        int msgId = MQTTConstants03.READ_MSG_ID_POWER_DATA;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getEnergyData() {
        int msgId = MQTTConstants03.READ_MSG_ID_ENERGY_DATA;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void back(View view) {
        finish();
    }
}
