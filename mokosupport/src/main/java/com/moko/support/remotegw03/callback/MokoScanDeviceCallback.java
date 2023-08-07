package com.moko.support.remotegw03.callback;

import com.moko.support.remotegw03.entity.DeviceInfo;

public interface MokoScanDeviceCallback {
    void onStartScan();

    void onScanDevice(DeviceInfo device);

    void onStopScan();
}
