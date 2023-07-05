package com.moko.mkremotegw03.activity;

import android.text.TextUtils;
import android.view.View;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityMeteringSettingsBinding;
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
 * @date: 2023/7/3 16:20
 * @des:
 */
public class MeteringSettingsActivity extends BaseActivity<ActivityMeteringSettingsBinding> {
    private boolean meteringReportSuc;
    private boolean loadDetectionNotifySuc;
    private boolean powerReportIntervalSuc;

    @Override
    protected ActivityMeteringSettingsBinding getViewBinding() {
        return ActivityMeteringSettingsBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        showLoadingProgressDialog();
        mBind.tvTitle.postDelayed(() -> {
            List<OrderTask> orderTasks = new ArrayList<>(4);
            orderTasks.add(OrderTaskAssembler.getMeteringReportEnable());
            orderTasks.add(OrderTaskAssembler.getPowerReportInterval());
            orderTasks.add(OrderTaskAssembler.getEnergyReportInterval());
            orderTasks.add(OrderTaskAssembler.getLoadDetectionNotifyEnable());
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
        }, 500);
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

    public void onSave(View view) {
        if (isWindowLocked()) return;
        showLoadingProgressDialog();
        if (!mBind.cbMetering.isChecked()) {
            MokoSupport.getInstance().sendOrder(OrderTaskAssembler.setMeteringReportEnable(0));
        } else {
            if (isValid()) {
                List<OrderTask> orderTasks = new ArrayList<>(4);
                orderTasks.add(OrderTaskAssembler.setMeteringReportEnable(1));
                int powerInterval = Integer.parseInt(mBind.etPowerInterval.getText().toString());
                int energyInterval = Integer.parseInt(mBind.etEnergyInterval.getText().toString());
                orderTasks.add(OrderTaskAssembler.setLoadDetectionNotifyEnable(mBind.cbDetectionNotify.isChecked() ? 1 : 0));
                orderTasks.add(OrderTaskAssembler.setPowerReportInterval(powerInterval));
                orderTasks.add(OrderTaskAssembler.setEnergyReportInterval(energyInterval));
                MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[0]));
            } else {
                ToastUtils.showToast(this, "Para Error");
            }
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
