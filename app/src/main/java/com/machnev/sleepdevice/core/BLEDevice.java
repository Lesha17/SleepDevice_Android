package com.machnev.sleepdevice.core;

import java.util.Objects;

public class BLEDevice {
    public final String name;
    public final String uuid;

    public BLEDevice(String name, String uuid) {
        this.name = name;
        this.uuid = uuid;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BLEDevice) {
            return Objects.equals(uuid, ((BLEDevice) obj).uuid);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }
}
