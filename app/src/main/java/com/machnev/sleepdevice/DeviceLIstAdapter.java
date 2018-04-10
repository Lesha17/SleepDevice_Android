package com.machnev.sleepdevice;

import android.bluetooth.BluetoothDevice;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DeviceLIstAdapter extends BaseAdapter {

    private final Context context;
    private final List<BluetoothDevice> devices;

    public DeviceLIstAdapter(@NonNull Context context)
    {
        this(context, Collections.<BluetoothDevice>emptyList());
    }

    public DeviceLIstAdapter(@NonNull Context context, Collection<BluetoothDevice> devices)
    {
        this.context = context;
        this.devices = new ArrayList<>(devices);
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

        BluetoothDevice device = devices.get(position);

        nameView.setText(device.getName());
        addrView.setText(device.getAddress());

        return  rootView;
    }

    public boolean addDevice(BluetoothDevice device) {
        if(!this.devices.contains(device)) {
            this.devices.add(device);
            notifyDataSetChanged();
            return true;
        } else {
            return false;
        }
    }
}
