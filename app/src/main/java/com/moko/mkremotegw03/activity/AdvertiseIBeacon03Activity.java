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
import com.moko.mkremotegw03.databinding.ActivityAdvertiseIbeacon03Binding;
import com.moko.mkremotegw03.dialog.Bottom03Dialog;
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
 * @date: 2023/7/3 11:24
 * @des:
 */
public class AdvertiseIBeacon03Activity extends BaseActivity<ActivityAdvertiseIbeacon03Binding> {
    private final String[] txPowerArr = {"-24dBm", "-21dBm", "-18dBm", "-15dBm", "-12dBm", "-9dBm", "-6dBm", "-3dBm", "0dBm", "3dBm", "6dBm",
            "9dBm", "12dBm", "15dBm", "18dBm", "21dBm"};
    private int mSelected;
    private boolean isIBeaconEnableSuc;
    private boolean isIBeaconMajorSuc;
    private boolean isIBeaconMinorSuc;
    private boolean isIBeaconUuidSuc;
    private boolean isIBeaconIntervalSuc;

    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    private String mAppTopic;
    private Handler mHandler;
    private int major;
    private int minor;
    private String uuid;
    private int advInterval;

    @Override
    protected ActivityAdvertiseIbeacon03Binding getViewBinding() {
        return ActivityAdvertiseIbeacon03Binding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        if (null != mMokoDevice) {
            String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
            appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
            mAppTopic = TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
            mHandler = new Handler(Looper.getMainLooper());
            getBeaconParams();
        } else {
            showLoadingProgressDialog();
            mBind.tvTitle.postDelayed(() -> {
                List<OrderTask> orderTasks = new ArrayList<>(8);
                orderTasks.add(OrderTaskAssembler.getIBeaconEnable());
                orderTasks.add(OrderTaskAssembler.getIBeaconMajor());
                orderTasks.add(OrderTaskAssembler.getIBeaconMinor());
                orderTasks.add(OrderTaskAssembler.getIBeaconUUid());
                orderTasks.add(OrderTaskAssembler.getIBeaconAdInterval());
                orderTasks.add(OrderTaskAssembler.getIBeaconTxPower());
                MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
            }, 500);
        }
        mBind.tvTxPowerVal.setOnClickListener(v -> {
            if (isWindowLocked()) return;
            Bottom03Dialog dialog = new Bottom03Dialog();
            dialog.setDatas(new ArrayList<>(Arrays.asList(txPowerArr)), mSelected);
            dialog.setListener(value -> {
                mSelected = value;
                mBind.tvTxPowerVal.setText(txPowerArr[value]);
            });
            dialog.show(getSupportFragmentManager());
        });
        mBind.cbIBeacon.setOnCheckedChangeListener((buttonView, isChecked) -> mBind.layoutAdvertise.setVisibility(isChecked ? View.VISIBLE : View.GONE));
    }

    private void getBeaconParams() {
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        int msgId = MQTTConstants03.READ_MSG_ID_BEACON_PARAMS;
        String message = assembleReadCommon(msgId, mMokoDevice.mac);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
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
        if (msg_id == MQTTConstants03.READ_MSG_ID_BEACON_PARAMS) {
            Type type = new TypeToken<MsgReadResult<JsonObject>>() {
            }.getType();
            MsgReadResult<JsonObject> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.mac.equalsIgnoreCase(result.device_info.mac)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            int enable = result.data.get("switch_value").getAsInt();
            mBind.cbIBeacon.setChecked(enable == 1);
            mBind.layoutAdvertise.setVisibility(enable == 1 ? View.VISIBLE : View.GONE);
            major = result.data.get("major").getAsInt();
            mBind.etMajor.setText(String.valueOf(major));
            mBind.etMajor.setSelection(mBind.etMajor.getText().length());
            minor = result.data.get("minor").getAsInt();
            mBind.etMinor.setText(String.valueOf(minor));
            mBind.etMinor.setSelection(mBind.etMinor.getText().length());
            uuid = result.data.get("uuid").getAsString();
            mBind.etUUid.setText(uuid);
            mBind.etUUid.setSelection(mBind.etUUid.getText().length());
            advInterval = result.data.get("adv_interval").getAsInt();
            mBind.etAdInterval.setText(String.valueOf(advInterval));
            mBind.etAdInterval.setSelection(mBind.etAdInterval.getText().length());
            mSelected = result.data.get("tx_power").getAsInt();
            mBind.tvTxPowerVal.setText(txPowerArr[mSelected]);
        }
        if (msg_id == MQTTConstants03.CONFIG_MSG_ID_BEACON_PARAMS) {
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
                        if (configKeyEnum == null) return;
                        int length = value[3] & 0xFF;
                        if (flag == 0x01) {
                            // write
                            int result = value[4] & 0xFF;
                            switch (configKeyEnum) {
                                case KEY_I_BEACON_SWITCH:
                                    if (!mBind.cbIBeacon.isChecked()) {
                                        if (result == 1) {
                                            ToastUtils.showToast(this, "Setup succeed！");
                                        } else {
                                            ToastUtils.showToast(this, "Setup failed！");
                                        }
                                    } else {
                                        isIBeaconEnableSuc = result == 1;
                                    }
                                    break;

                                case KEY_I_BEACON_MAJOR:
                                    isIBeaconMajorSuc = result == 1;
                                    break;

                                case KEY_I_BEACON_MINOR:
                                    isIBeaconMinorSuc = result == 1;
                                    break;

                                case KEY_I_BEACON_UUID:
                                    isIBeaconUuidSuc = result == 1;
                                    break;

                                case KEY_I_BEACON_AD_INTERVAL:
                                    isIBeaconIntervalSuc = result == 1;
                                    break;

                                case KEY_I_BEACON_TX_POWER:
                                    if (isIBeaconEnableSuc && isIBeaconMajorSuc && isIBeaconMinorSuc && isIBeaconUuidSuc && isIBeaconIntervalSuc && result == 1) {
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
                                case KEY_I_BEACON_SWITCH:
                                    if (length == 1) {
                                        int enable = value[4] & 0xff;
                                        mBind.cbIBeacon.setChecked(enable == 1);
                                        mBind.layoutAdvertise.setVisibility(enable == 1 ? View.VISIBLE : View.GONE);
                                    }
                                    break;

                                case KEY_I_BEACON_MAJOR:
                                    if (length == 2) {
                                        int major = MokoUtils.toInt(Arrays.copyOfRange(value, 4, value.length));
                                        mBind.etMajor.setText(String.valueOf(major));
                                        mBind.etMajor.setSelection(mBind.etMajor.getText().length());
                                    }
                                    break;

                                case KEY_I_BEACON_MINOR:
                                    if (length == 2) {
                                        int minor = MokoUtils.toInt(Arrays.copyOfRange(value, 4, value.length));
                                        mBind.etMinor.setText(String.valueOf(minor));
                                        mBind.etMinor.setSelection(mBind.etMinor.getText().length());
                                    }
                                    break;

                                case KEY_I_BEACON_UUID:
                                    if (length == 16) {
                                        String uuid = MokoUtils.bytesToHexString(Arrays.copyOfRange(value, 4, value.length));
                                        mBind.etUUid.setText(uuid);
                                        mBind.etUUid.setSelection(mBind.etUUid.getText().length());
                                    }
                                    break;

                                case KEY_I_BEACON_AD_INTERVAL:
                                    if (length == 1) {
                                        int interval = value[4] & 0xff;
                                        mBind.etAdInterval.setText(String.valueOf(interval));
                                        mBind.etAdInterval.setSelection(mBind.etAdInterval.getText().length());
                                    }
                                    break;

                                case KEY_I_BEACON_TX_POWER:
                                    if (length == 1) {
                                        mSelected = value[4] & 0xff;
                                        mBind.tvTxPowerVal.setText(txPowerArr[mSelected]);
                                    }
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (null == mMokoDevice) {
            if (!mBind.cbIBeacon.isChecked()) {
                showLoadingProgressDialog();
                MokoSupport03.getInstance().sendOrder(OrderTaskAssembler.setIBeaconEnable(0));
            } else {
                if (isValid()) {
                    showLoadingProgressDialog();
                    List<OrderTask> orderTasks = new ArrayList<>(8);
                    orderTasks.add(OrderTaskAssembler.setIBeaconEnable(1));
                    int major = Integer.parseInt(mBind.etMajor.getText().toString());
                    int minor = Integer.parseInt(mBind.etMinor.getText().toString());
                    String uuid = mBind.etUUid.getText().toString();
                    int interval = Integer.parseInt(mBind.etAdInterval.getText().toString());
                    orderTasks.add(OrderTaskAssembler.setIBeaconMajor(major));
                    orderTasks.add(OrderTaskAssembler.setIBeaconMinor(minor));
                    orderTasks.add(OrderTaskAssembler.setIBeaconUuid(uuid));
                    orderTasks.add(OrderTaskAssembler.setIBeaconAdInterval(interval));
                    orderTasks.add(OrderTaskAssembler.setIBeaconTxPower(mSelected));
                    MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
                } else {
                    ToastUtils.showToast(this, "Para Error");
                }
            }
        } else {
            //mqtt协议
            if (!mBind.cbIBeacon.isChecked()) {
                //不校验参数
                int major = (TextUtils.isEmpty(mBind.etMajor.getText()) || Integer.parseInt(mBind.etMajor.getText().toString()) > 65535) ? this.major : Integer.parseInt(mBind.etMajor.getText().toString());
                int minor = (TextUtils.isEmpty(mBind.etMinor.getText()) || Integer.parseInt(mBind.etMinor.getText().toString()) > 65535) ? this.minor : Integer.parseInt(mBind.etMinor.getText().toString());
                String uuid = (TextUtils.isEmpty(mBind.etUUid.getText()) || mBind.etUUid.getText().length() != 32) ? this.uuid : mBind.etUUid.getText().toString();
                int interval = (TextUtils.isEmpty(mBind.etAdInterval.getText()) || Integer.parseInt(mBind.etAdInterval.getText().toString()) < 1 ||
                        Integer.parseInt(mBind.etAdInterval.getText().toString()) > 100) ? this.advInterval : Integer.parseInt(mBind.etAdInterval.getText().toString());
                setBeaconParams(major, minor, uuid, interval);
            } else {
                if (!isValid()) {
                    ToastUtils.showToast(this, "Para Error");
                    return;
                }
                int major = Integer.parseInt(mBind.etMajor.getText().toString());
                int minor = Integer.parseInt(mBind.etMinor.getText().toString());
                String uuid = mBind.etUUid.getText().toString();
                int interval = Integer.parseInt(mBind.etAdInterval.getText().toString());
                setBeaconParams(major, minor, uuid, interval);
            }
        }
    }

    private void setBeaconParams(int major, int minor, String uuid, int advInterval) {
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Setup failed!");
        }, 30 * 1000);
        int msgId = MQTTConstants03.CONFIG_MSG_ID_BEACON_PARAMS;
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("switch_value", mBind.cbIBeacon.isChecked() ? 1 : 0);
        jsonObject.addProperty("major", major);
        jsonObject.addProperty("minor", minor);
        jsonObject.addProperty("uuid", uuid);
        jsonObject.addProperty("adv_interval", advInterval);
        jsonObject.addProperty("tx_power", mSelected);
        String message = assembleWriteCommonData(msgId, mMokoDevice.mac, jsonObject);
        try {
            MQTTSupport03.getInstance().publish(mAppTopic, message, msgId, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private boolean isValid() {
        if (TextUtils.isEmpty(mBind.etMajor.getText())) return false;
        int major = Integer.parseInt(mBind.etMajor.getText().toString());
        if (major > 65535) return false;
        if (TextUtils.isEmpty(mBind.etMinor.getText())) return false;
        int minor = Integer.parseInt(mBind.etMinor.getText().toString());
        if (minor > 65535) return false;
        if (TextUtils.isEmpty(mBind.etUUid.getText()) || mBind.etUUid.getText().length() != 32)
            return false;
        if (TextUtils.isEmpty(mBind.etAdInterval.getText())) return false;
        int interval = Integer.parseInt(mBind.etAdInterval.getText().toString());
        return interval >= 1 && interval <= 100;
    }

    public void onBack(View view) {
        finish();
    }
}
