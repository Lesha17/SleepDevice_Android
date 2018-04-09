package com.machnev.sleepdevice;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.machnev.sleepdevice.core.BLEDevice;

import java.util.ArrayList;
import java.util.List;

public class DeviceLIstAdapter extends BaseAdapter {

    private final Context context;
    private final List<BLEDevice> devices = new ArrayList<>();

    public DeviceLIstAdapter(@NonNull Context context) {
        this.context = context;
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View rootView = inflater.inflate(R.layout.device_list_item, parent, false);

        TextView nameView = rootView.findViewById(R.id.deviceName);
        TextView addrView = rootView.findViewById(R.id.deviceAddr);

        BLEDevice device = devices.get(position);

        nameView.setText(device.name);
        addrView.setText(device.uuid);

        return  rootView;
    }

    public boolean addDevice(BLEDevice device) {
        if(!this.devices.contains(device)) {
            this.devices.add(device);
            notifyDataSetChanged();
            return true;
        } else {
            return false;
        }
    }
}
