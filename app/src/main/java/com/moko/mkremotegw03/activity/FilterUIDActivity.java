package com.moko.mkremotegw03.activity;


import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.mkremotegw03.AppConstants;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityFilterUidBinding;
import com.moko.mkremotegw03.entity.MQTTConfig;
import com.moko.mkremotegw03.entity.MokoDevice;
import com.moko.mkremotegw03.utils.SPUtiles;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw.MQTTConstants;
import com.moko.support.remotegw.MQTTSupport;
import com.moko.support.remotegw.entity.MsgConfigResult;
import com.moko.support.remotegw.entity.MsgReadResult;
import com.moko.support.remotegw.event.DeviceOnlineEvent;
import com.moko.support.remotegw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class FilterUIDActivity extends BaseActivity<ActivityFilterUidBinding> {

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;

    public Handler mHandler;

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
        mHandler = new Handler(Looper.getMainLooper());
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        getFilterUid();
    }

    @Override
    protected ActivityFilterUidBinding getViewBinding() {
        return ActivityFilterUidBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
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
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_UID) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbUid.setChecked(result.data.get("switch_value").getAsInt() == 1);
            mBind.etUidNamespace.setText(result.data.get("namespace").getAsString());
            mBind.etUidInstanceId.setText(result.data.get("instance").getAsString());
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_UID) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac))
                return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        super.offline(event, mMokoDevice.mac);
    }

    private void getFilterUid() {
        int msgId = MQTTConstants.READ_MSG_ID_FILTER_UID;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }


    private void saveParams() {
        int msgId = MQTTConstants.CONFIG_MSG_ID_FILTER_UID;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", mBind.cbUid.isChecked() ? 1 : 0);
        jsonObject.addProperty("namespace", mBind.etUidNamespace.getText().toString());
        jsonObject.addProperty("instance", mBind.etUidInstanceId.getText().toString());
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        final String namespace = mBind.etUidNamespace.getText().toString();
        final String instanceId = mBind.etUidInstanceId.getText().toString();
        if (!TextUtils.isEmpty(namespace)) {
            int length = namespace.length();
            if (length % 2 != 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
        }
        if (!TextUtils.isEmpty(instanceId)) {
            int length = instanceId.length();
            if (length % 2 != 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
        }
        return true;
    }
}
