package com.moko.mkremotegw03.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.mkremotegw03.R;
import com.moko.support.remotegw03.entity.DeviceInfo;

public class DeviceInfo03Adapter extends BaseQuickAdapter<DeviceInfo, BaseViewHolder> {
    public DeviceInfo03Adapter() {
        super(R.layout.item_devices);
    }

    @Override
    protected void convert(BaseViewHolder helper, DeviceInfo item) {
        helper.setText(R.id.tv_device_name, item.name);
        helper.setText(R.id.tv_device_rssi, String.valueOf(item.rssi));
    }
}
