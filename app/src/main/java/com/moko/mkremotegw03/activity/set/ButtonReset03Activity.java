package com.moko.mkremotegw03.activity.set;

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
import com.moko.mkremotegw03.databinding.ActivityButtonReset03Binding;
import com.moko.mkremotegw03.entity.MQTTConfig;
import com.moko.mkremotegw03.entity.MokoDevice;
import com.moko.mkremotegw03.utils.SPUtiles;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw03.MQTTConstants03;
import com.moko.support.remotegw03.MQTTSupport03;
import com.moko.support.remotegw03.entity.MsgConfigResult;
import com.moko.support.remotegw03.entity.MsgReadResult;
import com.moko.support.remotegw03.event.DeviceOnlineEvent;
import com.moko.support.remotegw03.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class ButtonReset03Activity extends BaseActivity<ActivityButtonReset03Binding> {
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
        getButtonReset();
    }

    @Override
    protected ActivityButtonReset03Binding getViewBinding() {
        return ActivityButtonReset03Binding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id == MQTTConstants03.READ_MSG_ID_BUTTON_RESET) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int resetType = result.data.get("key_reset_type").getAsInt();
            if (resetType == 0) {
                mBind.rbFixedTime.setChecked(true);
            } else if (resetType == 1) {
                mBind.rbAnyTime.setChecked(true);
            }
            mBind.rgButtonReset.setOnCheckedChangeListener((group, checkedId) -> {
                int value = 0;
                if (checkedId == R.id.rb_any_time) value = 1;
                mHandler.postDelayed(() -> {
                    dismissLoadingProgressDialog();
                    ToastUtils.showToast(this, "Set up failed");
                }, 30 * 1000);
                showLoadingProgressDialog();
                setButtonReset(value);
            });
        }
        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_BUTTON_RESET) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
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

    public void onBack(View view) {
        finish();
    }

    private void getButtonReset() {
        int msgId = MQTTConstants03.READ_MSG_ID_BUTTON_RESET;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setButtonReset(int value) {
        int msgId = MQTTConstants03.CONFIG_MSG_ID_BUTTON_RESET;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("key_reset_type", value);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
