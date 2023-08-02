package com.moko.mkremotegw03.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.moko.mkremotegw03.R;
import com.moko.mkremotegw03.entity.MokoDevice;

import androidx.core.content.ContextCompat;


public class DeviceAdapter extends BaseQuickAdapter<MokoDevice, BaseViewHolder> {

    public DeviceAdapter() {
        super(R.layout.device_item_remote);
    }

    @Override
    protected void convert(BaseViewHolder helper, MokoDevice item) {
        helper.setText(R.id.tv_device_name, item.name);
        helper.setText(R.id.tv_device_mac, item.mac.toUpperCase());
        if (!item.isOnline) {
            helper.setText(R.id.tv_device_status, mContext.getString(R.string.device_state_offline));
            helper.setTextColor(R.id.tv_device_status, ContextCompat.getColor(mContext, R.color.grey_b3b3b3));
            helper.setImageResource(R.id.iv_net_status, R.drawable.ic_net_offline);
        } else {
            helper.setText(R.id.tv_device_status, mContext.getString(R.string.device_state_online));
            helper.setTextColor(R.id.tv_device_status, ContextCompat.getColor(mContext, R.color.blue_0188cc));
            if (item.netStatus == 0)
                helper.setImageResource(R.id.iv_net_status, R.drawable.ic_net_good);
            else if (item.netStatus == 1)
                helper.setImageResource(R.id.iv_net_status, R.drawable.ic_net_medium);
            else if (item.netStatus == 2)
                helper.setImageResource(R.id.iv_net_status, R.drawable.ic_net_poor);
        }
    }
}
