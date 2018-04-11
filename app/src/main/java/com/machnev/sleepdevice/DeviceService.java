package com.machnev.sleepdevice;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.machnev.sleepdevice.core.BLEController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceService extends Service {

    public static final int REQUEST_SENSORS_NOTIFICATION = 0;
    public static final int STOP_LISTEN_NOTIFICATIONS = 1;
    public static final int SENSOR_VALUE = 2;

    private final Handler handler = new DeviceServiceHandler();
    private final Messenger messenger = new Messenger(handler);
    private final Map<String, BLEController> contollers = new HashMap<>();
    private final Map<String, List<Messenger>> requestSensorsClients = new HashMap<>();

    private BluetoothAdapter adapter;

    @Override
    public void onCreate() {

        BluetoothManager manager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        adapter = manager.getAdapter();

        // start sending notifications
        // ..

        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    private class DeviceServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REQUEST_SENSORS_NOTIFICATION:
                    String deviceAddress = (String) msg.obj;

                    List<Messenger> clientsForDevice = requestSensorsClients.get(deviceAddress);
                    if(clientsForDevice == null)
                    {
                        clientsForDevice = new ArrayList<>();
                        requestSensorsClients.put(deviceAddress, clientsForDevice);
                    }

                    if(!clientsForDevice.contains(msg.replyTo)) {
                        clientsForDevice.add(msg.replyTo);
                    }

                    BLEController controller = contollers.get(deviceAddress);
                    if(controller == null) {
                        controller = new BLEController(adapter, deviceAddress);
                        controller.connect(DeviceService.this, new GattCallback(clientsForDevice));
                        contollers.put(deviceAddress, controller);
                    }
                    break;
                case STOP_LISTEN_NOTIFICATIONS:
                    deviceAddress = (String) msg.obj;
                    clientsForDevice = requestSensorsClients.get(deviceAddress);
                    if(clientsForDevice != null)
                    {
                        clientsForDevice.remove(msg.replyTo);
                        if(clientsForDevice.isEmpty())
                        {
                            controller = contollers.remove(deviceAddress);
                            if(controller != null) {
                                controller.disconnect();
                            }
                        }
                    }
                    break;
                    default:
                        break;
            }
            super.handleMessage(msg);
        }
    }

    private class GattCallback extends BluetoothGattCallback {
        private final List<Messenger> toSendMessage;

        private GattCallback(List<Messenger> toSendMessage) {
            this.toSendMessage = toSendMessage;
        }


        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String value = Arrays.toString(characteristic.getValue());
            Message message = Message.obtain(null, SENSOR_VALUE, value);
            message.replyTo = messenger;
            for(Messenger receiver : toSendMessage) {
                try {
                    receiver.send(message);
                } catch (RemoteException e) {
                    Log.e(DeviceService.class.getName(), e.getMessage(), e);
                }
            }

            super.onCharacteristicRead(gatt, characteristic, status);
        }
    }
}
