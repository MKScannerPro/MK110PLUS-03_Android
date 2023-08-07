package com.moko.mkremotegw03.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.mkremotegw03.R;

public class ScanDevice03Adapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public ScanDevice03Adapter() {
        super(R.layout.item_scan_device);
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tv_scan_device_info, item);
    }
}
