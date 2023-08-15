package com.moko.mkremotegw03.activity;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.mkremotegw03.AppConstants;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityMeteringSettingsBinding;
import com.moko.mkremotegw03.entity.MQTTConfig;
import com.moko.mkremotegw03.entity.MokoDevice;
import com.moko.mkremotegw03.utils.SPUtiles;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw03.MQTTConstants03;
import com.moko.support.remotegw03.MQTTSupport03;
import com.moko.support.remotegw03.MokoSupport03;
import com.moko.support.remotegw03.OrderTaskAssembler;
import com.moko.support.remotegw03.entity.MsgConfigResult;
import com.moko.support.remotegw03.entity.MsgReadResult;
import com.moko.support.remotegw03.entity.OrderCHAR;
import com.moko.support.remotegw03.entity.ParamsKeyEnum;
import com.moko.support.remotegw03.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: jun.liu
 * @date: 2023/7/3 16:20
 * @des:
 */
public class MeteringSettingsActivity extends BaseActivity<ActivityMeteringSettingsBinding> {
    private boolean meteringReportSuc;
    private boolean loadDetectionNotifySuc;
    private boolean powerReportIntervalSuc;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    public Handler mHandler;

    @Override
    protected ActivityMeteringSettingsBinding getViewBinding() {
        return ActivityMeteringSettingsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        if (null == mMokoDevice) {
            showLoadingProgressDialog();
            mBind.tvTitle.postDelayed(() -> {
                List<OrderTask> orderTasks = new ArrayList<>(4);
                orderTasks.add(OrderTaskAssembler.getMeteringReportEnable());
                orderTasks.add(OrderTaskAssembler.getLoadDetectionNotifyEnable());
                orderTasks.add(OrderTaskAssembler.getPowerReportInterval());
                orderTasks.add(OrderTaskAssembler.getEnergyReportInterval());
                MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
            }, 500);
        } else {
            String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
            appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
            mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
            mHandler = new Handler(Looper.getMainLooper());
            boolean isMeteringSwitch = getIntent().getBooleanExtra("enable", false);
            mBind.cbMetering.setChecked(isMeteringSwitch);
            mBind.layoutMetering.setVisibility(isMeteringSwitch ? View.VISIBLE : View.GONE);
            getLoadState();
        }
        mBind.cbMetering.setOnCheckedChangeListener((buttonView, isChecked) -> mBind.layoutMetering.setVisibility(isChecked ? View.VISIBLE : View.GONE));
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 100)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                finish();
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            dismissLoadingProgressDialog();
        }
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            byte[] value = response.responseValue;
            if (orderCHAR == OrderCHAR.CHAR_PARAMS) {
                if (value.length >= 4) {
                    int header = value[0] & 0xFF;// 0xED or 0xEE
                    int flag = value[1] & 0xFF;// read or write
                    int cmd = value[2] & 0xFF;
                    if (header == 0xED) {
                        ParamsKeyEnum configKeyEnum = ParamsKeyEnum.fromParamKey(cmd);
                        if (configKeyEnum == null) {
                            return;
                        }
                        int length = value[3] & 0xFF;
                        if (flag == 0x01) {
                            // write
                            int result = value[4] & 0xFF;
                            switch (configKeyEnum) {
                                case KEY_METERING_REPORT_ENABLE:
                                    if (!mBind.cbMetering.isChecked()) {
                                        if (result == 1) {
                                            ToastUtils.showToast(this, "Setup succeed！");
                                        } else {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        }
                                    } else {
                                        meteringReportSuc = result == 1;
                                    }
                                    break;

                                case KEY_LOAD_DETECTION_NOTIFY_ENABLE:
                                    loadDetectionNotifySuc = result == 1;
                                    if (!mBind.cbDetectionNotify.isChecked()) {
                                        if (result == 1) {
                                            ToastUtils.showToast(this, "Setup succeed！");
                                        } else {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        }
                                    }
                                    break;

                                case KEY_POWER_REPORT_INTERVAL:
                                    powerReportIntervalSuc = result == 1;
                                    break;

                                case KEY_ENERGY_REPORT_INTERVAL:
                                    if (meteringReportSuc && loadDetectionNotifySuc && powerReportIntervalSuc && result == 1) {
                                        ToastUtils.showToast(this, "Setup succeed！");
                                    } else {
                                        ToastUtils.showToast(this, "Setup failed！");
                                    }
                                    break;
                            }
                        }
                        if (flag == 0x00) {
                            if (length == 0) return;
                            // read
                            switch (configKeyEnum) {
                                case KEY_METERING_REPORT_ENABLE:
                                    if (length == 1) {
                                        int enable = value[4] & 0xff;
                                        mBind.cbMetering.setChecked(enable == 1);
                                        mBind.layoutMetering.setVisibility(enable == 1 ? View.VISIBLE : View.GONE);
                                    }
                                    break;

                                case KEY_LOAD_DETECTION_NOTIFY_ENABLE:
                                    if (length == 1) {
                                        int enable = value[4] & 0xff;
                                        mBind.cbDetectionNotify.setChecked(enable == 1);
                                    }
                                    break;

                                case KEY_POWER_REPORT_INTERVAL:
                                    if (length == 4) {
                                        int interval = MokoUtils.toInt(Arrays.copyOfRange(value, 4, value.length));
                                        mBind.etPowerInterval.setText(String.valueOf(interval));
                                        mBind.etPowerInterval.setSelection(mBind.etPowerInterval.getText().length());
                                    }
                                    break;

                                case KEY_ENERGY_REPORT_INTERVAL:
                                    if (length == 2) {
                                        int interval = MokoUtils.toInt(Arrays.copyOfRange(value, 4, value.length));
                                        mBind.etEnergyInterval.setText(String.valueOf(interval));
                                        mBind.etEnergyInterval.setSelection(mBind.etEnergyInterval.getText().length());
                                    }
                                    break;
                            }
                        }
                    }
                }
            }
        }
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
        if (msg_id == MQTTConstants03.READ_MSG_ID_LOAD_CHANGE_ENABLE || msg_id == MQTTConstants03.NOTIFY_MSG_ID_LOAD_CHANGE_ENABLE) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            String key = msg_id == MQTTConstants03.READ_MSG_ID_LOAD_CHANGE_ENABLE ? "switch_value" : "load_state";
            int enable = result.data.get(key).getAsInt();
            if (enable == 0) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
            mBind.cbDetectionNotify.setChecked(enable == 1);
            getPowerReportInterval();
            getEnergyReportInterval();
        }
        if (msg_id == MQTTConstants03.READ_MSG_ID_POWER_REPORT_INTERVAL || msg_id == MQTTConstants03.READ_MSG_ID_ENERGY_REPORT_INTERVAL) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            int interval = result.data.get("interval").getAsInt();
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (msg_id == MQTTConstants03.READ_MSG_ID_POWER_REPORT_INTERVAL) {
                mBind.etPowerInterval.setText(String.valueOf(interval));
                mBind.etPowerInterval.setSelection(mBind.etPowerInterval.getText().length());
            }
            if (msg_id == MQTTConstants03.READ_MSG_ID_ENERGY_REPORT_INTERVAL) {
                mBind.etEnergyInterval.setText(String.valueOf(interval));
                mBind.etEnergyInterval.setSelection(mBind.etEnergyInterval.getText().length());
            }
        }

        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_POWER_METERING_ENABLE) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (!mBind.cbMetering.isChecked()) {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, result.result_code == 0 ? "Set up succeed" : "Set up failed");
            } else {
                //打开状态 继续设置负载开关的状态
                if (result.result_code == 0) {
                    setMeteringEnable(mBind.cbDetectionNotify.isChecked() ? 1 : 0, MQTTConstants03.CONFIG_MSG_ID_LOAD_CHANGE_ENABLE);
                } else {
                    ToastUtils.showToast(this, "Set up failed");
                    dismissLoadingProgressDialog();
                    mHandler.removeMessages(0);
                }
            }
        }
        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_LOAD_CHANGE_ENABLE) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code == 0) {
                setPowerEnergyReportInterval(MQTTConstants03.CONFIG_MSG_ID_POWER_REPORT_INTERVAL, Integer.parseInt(mBind.etPowerInterval.getText().toString()));
            } else {
                ToastUtils.showToast(this, "Set up failed");
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_POWER_REPORT_INTERVAL) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            if (result.result_code == 0) {
                setPowerEnergyReportInterval(MQTTConstants03.CONFIG_MSG_ID_ENERGY_REPORT_INTERVAL, Integer.parseInt(mBind.etEnergyInterval.getText().toString()));
            } else {
                ToastUtils.showToast(this, "Set up failed");
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
            }
        }
        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_ENERGY_REPORT_INTERVAL) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            ToastUtils.showToast(this, result.result_code == 0 ? "Set up succeed" : "Set up failed");
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
        }
    }

    private void setPowerEnergyReportInterval(int msgId, int interval) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("interval", interval);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getPowerReportInterval() {
        int msgId = MQTTConstants03.READ_MSG_ID_POWER_REPORT_INTERVAL;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getEnergyReportInterval() {
        int msgId = MQTTConstants03.READ_MSG_ID_ENERGY_REPORT_INTERVAL;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getLoadState() {
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        showLoadingProgressDialog();
        int msgId = MQTTConstants03.READ_MSG_ID_LOAD_CHANGE_ENABLE;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (null == mMokoDevice) {
            //蓝牙
            if (!mBind.cbMetering.isChecked()) {
                showLoadingProgressDialog();
                MokoSupport03.getInstance().sendOrder(OrderTaskAssembler.setMeteringReportEnable(0));
            } else {
                List<OrderTask> orderTasks = new ArrayList<>(4);
                orderTasks.add(OrderTaskAssembler.setMeteringReportEnable(1));
                if (!isValid()) {
                    ToastUtils.showToast(this, "Para Error");
                    return;
                }
                showLoadingProgressDialog();
                int powerInterval = Integer.parseInt(mBind.etPowerInterval.getText().toString());
                int energyInterval = Integer.parseInt(mBind.etEnergyInterval.getText().toString());
                orderTasks.add(OrderTaskAssembler.setLoadDetectionNotifyEnable(mBind.cbDetectionNotify.isChecked() ? 1 : 0));
                orderTasks.add(OrderTaskAssembler.setPowerReportInterval(powerInterval));
                orderTasks.add(OrderTaskAssembler.setEnergyReportInterval(energyInterval));
                MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
            }
        } else {
            if (!mBind.cbMetering.isChecked()) {
                setMeteringEnable(0, MQTTConstants03.CONFIG_MSG_ID_POWER_METERING_ENABLE);
            } else {
                if (!isValid()) {
                    ToastUtils.showToast(this, "Para Error");
                    return;
                }
                setMeteringEnable(1, MQTTConstants03.CONFIG_MSG_ID_POWER_METERING_ENABLE);
            }
        }
    }

    private void setMeteringEnable(int enable, int msgId) {
        if (msgId == MQTTConstants03.CONFIG_MSG_ID_POWER_METERING_ENABLE) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            showLoadingProgressDialog();
        }
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", enable);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        if (TextUtils.isEmpty(mBind.etPowerInterval.getText())) return false;
        int powerInterval = Integer.parseInt(mBind.etPowerInterval.getText().toString());
        if (powerInterval < 1 || powerInterval > 86400) return false;
        if (TextUtils.isEmpty(mBind.etEnergyInterval.getText())) return false;
        int energyInterval = Integer.parseInt(mBind.etEnergyInterval.getText().toString());
        return energyInterval >= 1 && energyInterval <= 1440;
    }

    public void onBack(View view) {
        finish();
    }
}
