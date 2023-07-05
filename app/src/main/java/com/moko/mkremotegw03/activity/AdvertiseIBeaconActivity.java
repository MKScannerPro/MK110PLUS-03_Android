package com.moko.mkremotegw03.activity;

import android.text.TextUtils;
import android.view.View;
import android.widget.CompoundButton;

import androidx.activity.ViewTreeOnBackPressedDispatcherOwner;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityAdvertiseIbeaconBinding;
import com.moko.mkremotegw03.dialog.BottomDialog;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw.MokoSupport;
import com.moko.support.remotegw.OrderTaskAssembler;
import com.moko.support.remotegw.entity.OrderCHAR;
import com.moko.support.remotegw.entity.ParamsKeyEnum;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author: jun.liu
 * @date: 2023/7/3 11:24
 * @des:
 */
public class AdvertiseIBeaconActivity extends BaseActivity<ActivityAdvertiseIbeaconBinding> {
    private final String[] txPowerArr = {"-24dBm", "-21dBm", "-18dBm", "-15dBm", "-12dBm", "-9dBm", "-6dBm", "-3dBm", "0dBm", "3dBm", "6dBm",
            "9dBm", "12dBm", "15dBm", "18dBm", "21dBm"};
    private int mSelected;
    private boolean isIBeaconEnableSuc;
    private boolean isIBeaconMajorSuc;
    private boolean isIBeaconMinorSuc;
    private boolean isIBeaconUuidSuc;
    private boolean isIBeaconIntervalSuc;

    @Override
    protected ActivityAdvertiseIbeaconBinding getViewBinding() {
        return ActivityAdvertiseIbeaconBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        showLoadingProgressDialog();
        mBind.tvTitle.postDelayed(() -> {
            List<OrderTask> orderTasks = new ArrayList<>(8);
            orderTasks.add(OrderTaskAssembler.getIBeaconEnable());
            orderTasks.add(OrderTaskAssembler.getIBeaconMajor());
            orderTasks.add(OrderTaskAssembler.getIBeaconMinor());
            orderTasks.add(OrderTaskAssembler.getIBeaconUUid());
            orderTasks.add(OrderTaskAssembler.getIBeaconAdInterval());
            orderTasks.add(OrderTaskAssembler.getIBeaconTxPower());
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
        }, 500);
        mBind.tvTxPowerVal.setOnClickListener(v -> {
            if (isWindowLocked()) return;
            BottomDialog dialog = new BottomDialog();
            dialog.setDatas((ArrayList<String>) (Arrays.asList(txPowerArr)), mSelected);
            dialog.setListener(value -> {
                mSelected = value;
                mBind.tvTxPowerVal.setText(txPowerArr[value]);
            });
            dialog.show(getSupportFragmentManager());
        });
        mBind.cbIBeacon.setOnCheckedChangeListener((buttonView, isChecked) -> mBind.layoutAdvertise.setVisibility(isChecked ? View.VISIBLE : View.GONE));
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
            int responseType = response.responseType;
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
        showLoadingProgressDialog();
        if (!mBind.cbIBeacon.isChecked()) {
            MokoSupport.getInstance().sendOrder(OrderTaskAssembler.setIBeaconEnable(0));
        } else {
            if (isValid()) {
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
                MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
            } else {
                ToastUtils.showToast(this, "Para Error");
            }
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
