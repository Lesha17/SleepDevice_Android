package com.machnev.sleepdevice.core;

import android.os.Parcel;
import android.os.Parcelable;

public class BLEDeviceViewModel implements Parcelable {

    public final String address;
    public final String name;

    public BLEDeviceViewModel(String address, String name) {
        this.address = address;
        this.name = name;
    }

    protected BLEDeviceViewModel(Parcel in) {
        address = in.readString();
        name = in.readString();
    }

    public static final Creator<BLEDeviceViewModel> CREATOR = new Creator<BLEDeviceViewModel>() {
        @Override
        public BLEDeviceViewModel createFromParcel(Parcel in) {
            return new BLEDeviceViewModel(in);
        }

        @Override
        public BLEDeviceViewModel[] newArray(int size) {
            return new BLEDeviceViewModel[size];
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BLEDeviceViewModel) {
            return address.equals(((BLEDeviceViewModel) obj).address);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(address);
        dest.writeString(name);
    }
}
