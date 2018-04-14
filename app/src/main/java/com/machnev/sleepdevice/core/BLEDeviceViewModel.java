package com.machnev.sleepdevice.core;

public class BLEDeviceViewModel {

    public final String address;
    public final String name;

    public BLEDeviceViewModel(String address, String name) {
        this.address = address;
        this.name = name;
    }

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
}
