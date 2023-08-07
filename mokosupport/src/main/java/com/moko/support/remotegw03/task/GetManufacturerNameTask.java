package com.moko.support.remotegw03.task;

import com.moko.ble.lib.task.OrderTask;
import com.moko.support.remotegw03.entity.OrderCHAR;

public class GetManufacturerNameTask extends OrderTask {

    public byte[] data;

    public GetManufacturerNameTask() {
        super(OrderCHAR.CHAR_MANUFACTURER_NAME, OrderTask.RESPONSE_TYPE_READ);
    }

    @Override
    public byte[] assemble() {
        return data;
    }
}
