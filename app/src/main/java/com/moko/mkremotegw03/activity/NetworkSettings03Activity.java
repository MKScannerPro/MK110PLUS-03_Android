package com.moko.mkremotegw03.activity;

import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.mkremotegw03.base.BaseActivity;
import com.moko.mkremotegw03.databinding.ActivityNetworkSettings03Binding;
import com.moko.mkremotegw03.utils.ToastUtils;
import com.moko.support.remotegw03.MokoSupport03;
import com.moko.support.remotegw03.OrderTaskAssembler;
import com.moko.support.remotegw03.entity.OrderCHAR;
import com.moko.support.remotegw03.entity.ParamsKeyEnum;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NetworkSettings03Activity extends BaseActivity<ActivityNetworkSettings03Binding> {
    private boolean mSavedParamsError;

    @Override
    protected ActivityNetworkSettings03Binding getViewBinding() {
        return ActivityNetworkSettings03Binding.inflate(getLayoutInflater());
    }

    @Override
    protected void onCreate() {
        mBind.cbDhcp.setOnCheckedChangeListener((buttonView, isChecked) -> mBind.clIp.setVisibility(isChecked ? View.GONE : View.VISIBLE));
        showLoadingProgressDialog();
        mBind.tvTitle.postDelayed(() -> {
            List<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.getNetworkDHCP());
            orderTasks.add(OrderTaskAssembler.getNetworkIPInfo());
            MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        }, 500);
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
                    int header = value[0] & 0xFF;// 0xED
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
                                case KEY_NETWORK_IP_INFO:
                                    if (result != 1) {
                                        mSavedParamsError = true;
                                    }
                                    break;
                                case KEY_NETWORK_DHCP:
                                    if (result != 1) {
                                        mSavedParamsError = true;
                                    }
                                    if (mSavedParamsError) {
                                        ToastUtils.showToast(this, "Setup failed！");
                                    } else {
                                        ToastUtils.showToast(this, "Setup succeed！");
                                    }
                                    break;
                            }
                        }
                        if (flag == 0x00) {
                            if (length == 0) return;
                            // read
                            switch (configKeyEnum) {
                                case KEY_NETWORK_DHCP:
                                    int enable = value[4];
                                    mBind.cbDhcp.setChecked(enable == 1);
                                    mBind.clIp.setVisibility(enable == 1 ? View.GONE : View.VISIBLE);
                                    break;
                                case KEY_NETWORK_IP_INFO:
                                    if (length == 16) {
                                        String ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                                value[4] & 0xFF, value[5] & 0xFF, value[6] & 0xFF, value[7] & 0xFF);
                                        String mask = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                                value[8] & 0xFF, value[9] & 0xFF, value[10] & 0xFF, value[11] & 0xFF);
                                        String gateway = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                                value[12] & 0xFF, value[13] & 0xFF, value[14] & 0xFF, value[15] & 0xFF);
                                        String dns = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                                value[16] & 0xFF, value[17] & 0xFF, value[18] & 0xFF, value[19] & 0xFF);
                                        mBind.etIp.setText(ip);
                                        mBind.etMask.setText(mask);
                                        mBind.etGateway.setText(gateway);
                                        mBind.etDns.setText(dns);
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
        if (!isParaError()) {
            saveParams();
        } else {
            ToastUtils.showToast(this, "Para Error");
        }
    }

    private boolean isParaError() {
        if (!mBind.cbDhcp.isChecked()) {
            if (TextUtils.isEmpty(mBind.etIp.getText()) || TextUtils.isEmpty(mBind.etMask.getText()) ||
                    TextUtils.isEmpty(mBind.etGateway.getText()) || TextUtils.isEmpty(mBind.etDns.getText())) {
                return true;
            }
            String ip = mBind.etIp.getText().toString();
            String mask = mBind.etMask.getText().toString();
            String gateway = mBind.etGateway.getText().toString();
            String dns = mBind.etDns.getText().toString();
            int[] ipArray = getIp(ip);
            int[] maskArray = getIp(mask);
            int[] gatewayArray = getIp(gateway);
            int[] dnsArray = getIp(dns);
            if (null == ipArray || null == gatewayArray || null == maskArray || null == dnsArray)
                return true;
            if (isIpError(ipArray)) return true;
            if (isIpError(maskArray)) return true;
            if (isIpError(gatewayArray)) return true;
            if (isIpError(dnsArray)) return true;
        }
        return false;
    }

    private boolean isIpError(@NonNull int[] array) {
        for (int arr : array) {
            if (arr > 255) return true;
        }
        return false;
    }

    private int[] getIp(String ipInfo) {
        if (TextUtils.isEmpty(ipInfo)) return null;
        String[] split = ipInfo.split("\\.");
        if (split.length != 4) return null;
        for (String str : split) {
            if (TextUtils.isEmpty(str)) return null;
        }
        return new int[]{Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]), Integer.parseInt(split[3])};
    }

    private void saveParams() {
        showLoadingProgressDialog();
        List<OrderTask> orderTasks = new ArrayList<>();
        if (!mBind.cbDhcp.isChecked()) {
            String ip = mBind.etIp.getText().toString();
            String mask = mBind.etMask.getText().toString();
            String gateway = mBind.etGateway.getText().toString();
            String dns = mBind.etDns.getText().toString();
            String[] ipArray = ip.split("\\.");
            String ipHex = String.format("%s%s%s%s",
                    MokoUtils.int2HexString(Integer.parseInt(ipArray[0])),
                    MokoUtils.int2HexString(Integer.parseInt(ipArray[1])),
                    MokoUtils.int2HexString(Integer.parseInt(ipArray[2])),
                    MokoUtils.int2HexString(Integer.parseInt(ipArray[3])));
            String[] maskArray = mask.split("\\.");
            String maskHex = String.format("%s%s%s%s",
                    MokoUtils.int2HexString(Integer.parseInt(maskArray[0])),
                    MokoUtils.int2HexString(Integer.parseInt(maskArray[1])),
                    MokoUtils.int2HexString(Integer.parseInt(maskArray[2])),
                    MokoUtils.int2HexString(Integer.parseInt(maskArray[3])));
            String[] gatewayArray = gateway.split("\\.");
            String gatewayHex = String.format("%s%s%s%s",
                    MokoUtils.int2HexString(Integer.parseInt(gatewayArray[0])),
                    MokoUtils.int2HexString(Integer.parseInt(gatewayArray[1])),
                    MokoUtils.int2HexString(Integer.parseInt(gatewayArray[2])),
                    MokoUtils.int2HexString(Integer.parseInt(gatewayArray[3])));
            String[] dnsArray = dns.split("\\.");
            String dnsHex = String.format("%s%s%s%s",
                    MokoUtils.int2HexString(Integer.parseInt(dnsArray[0])),
                    MokoUtils.int2HexString(Integer.parseInt(dnsArray[1])),
                    MokoUtils.int2HexString(Integer.parseInt(dnsArray[2])),
                    MokoUtils.int2HexString(Integer.parseInt(dnsArray[3])));
            orderTasks.add(OrderTaskAssembler.setNetworkIPInfo(ipHex, maskHex, gatewayHex, dnsHex));
        }
        orderTasks.add(OrderTaskAssembler.setNetworkDHCP(mBind.cbDhcp.isChecked() ? 1 : 0));
        MokoSupport03.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));

    }

    public void onBack(View view) {
        finish();
    }
}
