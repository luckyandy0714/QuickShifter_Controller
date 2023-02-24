package com.example.quickshifter_controller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class BluetoothConnect {
    private final String TAG = "BluetoothConnect_Error";
    private final int REQUEST_CONNECT_DEVICE_INSECURE = 3;
    private final UUID UUID_ = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private Context context;

    private Thread connect_thread;
    private String Bluetooth_Address;
    private BluetoothSocket mSocket;
    private BluetoothDevice mDevice;

    private InputStream InStream;
    private OutputStream OutStream;

    public ConnectCallBack ConnectCallBack;
    public ReadCallBack ReadCallBack;
    public Read_buffer_CallBack Read_buffer_CallBack;
    public WriteCallBack WriteCallBack;
    public SelectMacCallBack SelectMacCallBack;

    public boolean bluetooth_connect = false;

    public void set_Bluetooth_Address(String Mac) {
        if (Mac == null)
            return;
        Bluetooth_Address = Mac;
    }


    void registerReceiver() {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            ArrayAdapter<String> unpair_arrayAdapter = new ArrayAdapter(context, android.R.layout.simple_list_item_activated_1);
            List<BluetoothDevice> unpair_array = new ArrayList<>();

            @Override
            public void onReceive(Context context, Intent intent) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    System.out.println("..");
                String action = intent.getAction();
                switch (action) {
                    case BluetoothAdapter.ACTION_DISCOVERY_STARTED:
                        unpair_array.clear();
                        unpair_arrayAdapter.clear();
                        AlertDialog.Builder Dialog;
                        (Dialog = new AlertDialog.Builder(context))
                                .setAdapter(unpair_arrayAdapter, (dialog, which) -> {
                                    BluetoothDevice unpair_device = unpair_array.get(which);
                                    unpair_device.createBond();
                                })
                                .setPositiveButton("取消", (dialog, which) -> {
                                    unpair_array.clear();
                                    unpair_arrayAdapter.clear();
                                    bluetoothAdapter.cancelDiscovery();
                                }).setTitle("選擇配對裝置").show();
                        Toast.makeText(context, "---開始搜尋裝置---", Toast.LENGTH_SHORT).show();
                        break;
                    case BluetoothDevice.ACTION_PAIRING_REQUEST:
                    case BluetoothDevice.ACTION_FOUND: {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        if (device.getName() == null)
                            break;
                        unpair_array.add(device);
                        unpair_arrayAdapter.add(unpair_arrayAdapter.getCount() + 1 + ".  " + device.getName() + " [" + device + "]");
                        Toast.makeText(context, "---搜尋裝置->" + device.getName() + "---", Toast.LENGTH_SHORT).show();
                    }
                    break;
                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        bluetoothAdapter.cancelDiscovery();
                        Toast.makeText(context, "---搜尋完畢---", Toast.LENGTH_SHORT).show();
                        break;

                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED: {
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        switch (device.getBondState()) {
                            case BluetoothDevice.BOND_BONDING:
                                Toast.makeText(context, "---配對中---", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothDevice.BOND_BONDED:
                                Bluetooth_Address = device.getAddress();
                                SelectMacCallBack.onSelectChanged(Bluetooth_Address);
                                Start_Connect();
                                Toast.makeText(context, "---配對成功---", Toast.LENGTH_SHORT).show();
                                break;
                            case BluetoothDevice.BOND_NONE:
                                Toast.makeText(context, "---配對失敗---", Toast.LENGTH_SHORT).show();
                                bluetoothAdapter.cancelDiscovery();
                                break;
                        }
                    }
                    break;
                    case BluetoothDevice.ACTION_ACL_CONNECTED:
                        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        String device_name = "裝置名稱: " + device.getName();
                        int device_name_length = device_name.length();
                        device_name_length = (device_name_length / 2) + 4;
                        device_name_length = Math.max(device_name_length, 0);
                        StringBuilder pad = new StringBuilder();
                        for (int i = 0; i < device_name_length; i++)
                            pad.append(" ");
                        Toast.makeText(context, pad + "---藍芽已連線---" + "\n" + device_name, Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            try {
                                Thread.sleep(500);
                                ConnectCallBack.onConnectSucceed(Bluetooth_Address);
                            } catch (Exception ex) {
                            }
                        }).start();
                        break;
                    case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                        Toast.makeText(context, "---藍芽連線中斷---", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        };
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        context.registerReceiver(receiver, intentFilter);
    }

    public BluetoothConnect(Context context) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null)
            return;
        this.context = context;
        registerReceiver();
    }

    boolean bluetooth_enabled_flag = false;

    void bluetooth_Enabled_check() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_CONNECT_DEVICE_INSECURE);

        if (!(bluetooth_enabled_flag = bluetoothAdapter.isEnabled())) {
            ((Activity) context).runOnUiThread(() -> {
                new AlertDialog.Builder(context)
                        .setTitle("開啟藍芽")
                        .setMessage("未開啟藍芽了\n請問是否要開啟")
                        .setPositiveButton("開啟！", (dialog, which) -> {
                            bluetoothAdapter.enable();
                            bluetooth_enabled_flag = true;
                        })
                        //.setNegativeButton("沒有", null)
                        .setNeutralButton("這顆按鈕也是開啟", (dialog, which) -> {
                            bluetoothAdapter.enable();
                            bluetooth_enabled_flag = true;
                        }).setCancelable(false).show();
            });
            try {
                while (!bluetooth_enabled_flag)
                    Thread.sleep(10);
            } catch (Exception e) {
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
            }
        }
    }

    boolean bluetooth_mac_flag = false;

    void bluetooth_Mac_check(boolean cancel) {
        if (bluetooth_mac_flag)
            return;
        bluetooth_mac_flag = true;

        List<String> bluetooth_name = new ArrayList<>();
        List<String> bluetooth_address = new ArrayList<>();

        ((Activity) context).runOnUiThread(() -> {
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter(context, android.R.layout.simple_list_item_activated_1, bluetooth_name);
            new Thread(() -> {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_CONNECT_DEVICE_INSECURE);
                while (bluetooth_mac_flag) {
                    try {
                        bluetooth_name.clear();
                        bluetooth_address.clear();
                        for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                            bluetooth_name.add((bluetooth_name.size() + 1) + ". " + device.getName());
                            bluetooth_address.add(device.getAddress());
                        }
                        ((Activity) context).runOnUiThread(arrayAdapter::notifyDataSetChanged);
                    } catch (Exception e) {
                        bluetooth_mac_flag = false;
                        return;
                    }
                    try {
                        if (bluetooth_name.size() == 0)
                            Thread.sleep(100);
                        else
                            Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
            AlertDialog.Builder Dialog;
            (Dialog = new AlertDialog.Builder(context))
                    .setAdapter(arrayAdapter, (dialog, which) -> {
                        boolean different = !bluetooth_address.get(which).equals(Bluetooth_Address);
                        if (different)
                            Cancel();
                        if (!bluetooth_connect)
                            different = true;
                        Bluetooth_Address = bluetooth_address.get(which);
                        SelectMacCallBack.onSelectChanged(Bluetooth_Address);
                        if (different)
                            Start_Connect();
                        else {

                        }
                        Toast.makeText(context, "MAC:" + Bluetooth_Address, Toast.LENGTH_SHORT).show();
                        bluetooth_mac_flag = false;
                    })
                    .setPositiveButton("中斷連線", (dialog, which) -> {
                        //WriteLine("Disconnect");
                        Cancel();
                        bluetooth_mac_flag = false;
                        Dialog.show();
                    })
                    .setNegativeButton("搜尋", (dialog, which) -> {
                        bluetooth_mac_flag = false;

                        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
                        boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
                        if (!isGpsEnabled) {
                            ((Activity) context).startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), 0);
                            Dialog.show();
                        }

                        if (bluetoothAdapter.isDiscovering())
                            bluetoothAdapter.cancelDiscovery();
                        bluetoothAdapter.startDiscovery();
                    })
                    .setNeutralButton("設定頁面", (dialog, which) -> {
                        bluetooth_mac_flag = false;
                        Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                        context.startActivity(intent);
                    }).setOnCancelListener(dialogInterface -> {
                        bluetooth_mac_flag = false;
                    }).setTitle("選擇藍芽裝置").setCancelable(cancel).show();
        });
        try {
            while (bluetooth_mac_flag)
                Thread.sleep(10);
        } catch (Exception e) {
        }
        try {
            Thread.sleep(500);
        } catch (Exception e) {
        }
    }

    public void get_bluetooth_Mac_menu(boolean cancel) {
        new Thread(() -> {
            bluetooth_Enabled_check();
            bluetooth_Mac_check(cancel);
        }).start();
    }

    public void Start_Connect() {
        Cancel();
        connect_thread = new Thread(this::connect);
        try {
            connect_thread.start();
        } catch (Exception ex) {
        }
    }

    void connect() {
        bluetooth_Enabled_check();
        if (Bluetooth_Address == null)
            return;
        try {
            Thread.sleep(10);
        } catch (Exception e) {
        }
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_CONNECT_DEVICE_INSECURE);
        }
        try {
            mDevice = bluetoothAdapter.getRemoteDevice(Bluetooth_Address);
            mSocket = mDevice.createRfcommSocketToServiceRecord(UUID_);
            bluetoothAdapter.cancelDiscovery();
        } catch (Exception e) {
            return;
        }
        try {
            mSocket.connect();
        } catch (IOException e) {
            Log.e(TAG, "Error Socket connect", e);
            return;
        }

        try {
            InStream = mSocket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        try {
            OutStream = mSocket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }

        try {
            Thread.sleep(10);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }

        bluetooth_connect = true;

        Read();
        //Read_buffer();
    }
/*
    void Read_buffer() {
        //StringBuilder read_data = new StringBuilder();
        char[] buffer = new char[64];
        char check_val = 0;
        int butter_add = 0;
        while (mSocket.isConnected()) {
            try {
                char val = 0;
                int buffer_length = InStream.available();
                if (buffer_length > 0) {
                    val = (char) InStream.read();
                    buffer[butter_add++] = val;
                } else {
                    SystemClock.sleep(1);
                }
                if (val == '\n'&& check_val=='\r') {
                    Read_buffer_CallBack.onRead_buffer_NewData(buffer, butter_add);
                    buffer = new char[64];
                    butter_add = 0;
                    check_val=0;
                }
                check_val=val;
            } catch (Exception e) {
                Log.e(TAG, "Error read stream", e);
                Cancel();
            }
        }
    }*/

    void Read() {
        StringBuilder read_data = new StringBuilder();
        while (mSocket.isConnected()) {
            try {
                char val = 0;
                int buffer_length = InStream.available();
                if (buffer_length > 0) {
                    val = (char) InStream.read();
                    read_data.append(val);
                } else {
                    SystemClock.sleep(1);
                }
                if (val == '\n') {
                    ReadCallBack.onReadNewData(read_data.toString());
                    read_data = new StringBuilder();
                }

            } catch (Exception e) {
                Log.e(TAG, "Error read stream", e);
                Cancel();
            }
        }
    }

    public void WriteLine(String write_data) {
        Write(write_data);
        Write("\n");
    }

    public void Write(String write_data) {
        byte[] bytes_data = write_data.getBytes();
        try {
            OutStream.write(bytes_data);
            WriteCallBack.onWriteNewData(write_data);
        } catch (Exception e) {
            Log.e(TAG, "NULL", e);
            Toast.makeText(context, "寫入失敗", Toast.LENGTH_SHORT).show();
            Cancel();
        }
    }

    public void Cancel() {
        try {
            mSocket.close();
        } catch (Exception e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
        bluetooth_connect = false;
    }

    public interface ConnectCallBack {
        public void onConnectSucceed(String Mac);
    }

    public interface SelectMacCallBack {
        public void onSelectChanged(String Mac);
    }

    public interface ReadCallBack {
        public void onReadNewData(String read_data);
    }

    public interface Read_buffer_CallBack {
        public void onRead_buffer_NewData(char[] read_data, int buffer_length);
    }

    public interface WriteCallBack {
        public void onWriteNewData(String Write_data);
    }
}