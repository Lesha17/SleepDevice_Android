package com.machnev.sleepdevice.core;

import android.os.Parcel;
import android.os.Parcelable;

public class StatusSettingsData implements Parcelable {
    public final String deviceAddress;
    public final float onBedValue;
    public final float notBedValue;

    public StatusSettingsData(String deviceAddress, float onBedValue, float notBedValue) {
        this.deviceAddress = deviceAddress;
        this.onBedValue = onBedValue;
        this.notBedValue = notBedValue;
    }

    protected StatusSettingsData(Parcel in) {
        deviceAddress = in.readString();
        onBedValue = in.readFloat();
        notBedValue = in.readFloat();
    }

    public static final Creator<StatusSettingsData> CREATOR = new Creator<StatusSettingsData>() {
        @Override
        public StatusSettingsData createFromParcel(Parcel in) {
            return new StatusSettingsData(in);
        }

        @Override
        public StatusSettingsData[] newArray(int size) {
            return new StatusSettingsData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(deviceAddress);
        dest.writeFloat(onBedValue);
        dest.writeFloat(notBedValue);
    }
}
