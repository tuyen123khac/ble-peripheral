package com.tuyenvo.bleperipheral;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "MainActivity";
    public static final String BLE_ADVERTISE_MESSAGE = "BLE";
    public static final String CHARACTERISTIC_VALUE = "Hello";
    final private int REQUEST_CODE_ASK_PERMISSIONS = 123;
    private int REQUEST_ENABLE_BT = 1;
    private BluetoothLeScanner bluetoothLeScanner;
    BluetoothGattServer bluetoothGattServer;
    BluetoothGattService bluetoothGattService;
    BluetoothGattCharacteristic bluetoothGattCharacteristic;
    BluetoothManager bluetoothManager;
    private Handler handler;
    ImageView statusButton;
    Button advertiseButton;
    Button discoverButton;
    TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusButton = findViewById(R.id.statusButton);
        statusText = findViewById(R.id.statusText);
        advertiseButton = findViewById(R.id.advertiseButton);

        handler = new Handler();
        bluetoothLeScanner = BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
        advertiseButton.setOnClickListener(this);

        if (BluetoothAdapter.getDefaultAdapter() == null || !BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        checkLocationPermission();
        checkMultipleAdvertiseSupported();
        setBluetoothGattServer();
        setBluetoothService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void checkMultipleAdvertiseSupported() {
        if(!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()){
            Log.e(TAG, "checkMultipleAdvertiseSupported: Multiple advertisement not supported");
            Toast.makeText(this, "Multiple advertisement not supported", Toast.LENGTH_SHORT).show();
            advertiseButton.setEnabled(false);
            discoverButton.setEnabled(false);
        }
    }

    private void checkLocationPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION},
                        REQUEST_CODE_ASK_PERMISSIONS);
                return;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("MainActivity ", "onRequestPermissionsResult: Location Permission granted");
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT)
                            .show();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.advertiseButton:
                advertise();
                break;
            default:
                break; 
        }
    }

    private void advertise() {
        BluetoothLeAdvertiser advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        ParcelUuid parcelUuid = new ParcelUuid(UUID.fromString(getString(R.string.ble_uuid)));

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(parcelUuid)
                //.addServiceData(parcelUuid, BLE_ADVERTISE_MESSAGE.getBytes(Charset.forName("UTF-8")))
                // Not able to add service data because the byte range is limited
                .build();

        AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.d(TAG, "onStartSuccess: Advertise successfully");
                Toast.makeText(MainActivity.this, "Advertised", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "onStartFailure: Advertise failed: " + errorCode);
                super.onStartFailure(errorCode);
                Toast.makeText(MainActivity.this, "Advertised failed", Toast.LENGTH_SHORT).show();
            }

        };

        advertiser.startAdvertising(settings, data, advertiseCallback);
    }


    private void setBluetoothGattServer(){
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if(bluetoothManager != null){
            bluetoothGattServer = bluetoothManager.openGattServer(this, bluetoothGattServerCallback);
        }
    }

    private void setBluetoothService() {
        // create the Service
        bluetoothGattService = new BluetoothGattService(UUID.fromString(getString(R.string.ble_uuid)), BluetoothGattService.SERVICE_TYPE_PRIMARY);

        //create the Characteristic.
        bluetoothGattCharacteristic = new BluetoothGattCharacteristic(UUID.fromString(getString(R.string.ble_uuid)),
                BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE | BluetoothGattCharacteristic.PERMISSION_READ );

        bluetoothGattCharacteristic.addDescriptor(new BluetoothGattDescriptor(UUID.fromString(getString(R.string.ble_uuid)), BluetoothGattCharacteristic.PERMISSION_WRITE));
        bluetoothGattCharacteristic.setValue(CHARACTERISTIC_VALUE);

        // add the Characteristic to the Service
        bluetoothGattService.addCharacteristic(bluetoothGattCharacteristic);

        // add the Service to the Server/Peripheral
        if (bluetoothGattServer != null) {
            bluetoothGattServer.addService(bluetoothGattService);
        }
    }

    private final BluetoothGattServerCallback bluetoothGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.d(TAG, "onConnectionStateChange: ");
            Log.d(TAG, "onCharacteristicWriteRequest: a connected device: " + device.getName() + ", Address: " + device.getAddress() + ", UUID: " + device.getUuids());

        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if(bluetoothGattServer == null){
                return;
            }
            Log.d(MainActivity.TAG, "Device tried to read characteristic: " + characteristic.getUuid());
            Log.d(MainActivity.TAG, "Value: " + Arrays.toString(characteristic.getValue()));
            Toast.makeText(MainActivity.this, "Value: " + Arrays.toString(characteristic.getValue()), Toast.LENGTH_SHORT).show();

            bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.d(TAG, "onCharacteristicWriteRequest: ");

            if (characteristic.getUuid().equals(UUID.fromString(getString(R.string.ble_uuid)))) {
                bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                int length = value.length;
                byte[] reversed = new byte[length];
                for (int i = 0; i < length; i++) {
                    reversed[i] = value[length - (i + 1)];
                }
                characteristic.setValue(reversed);
                bluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);

                String status = new String(value, StandardCharsets.UTF_8);
                switch (status){
                    case "GREEN":
                        Log.d(TAG, "onCharacteristicWriteRequest: Message from client: " + status);
                        statusButton.setBackground(getResources().getDrawable(R.drawable.background_green));
                        statusText.setText(status);
                        break;
                    case "RED":
                        Log.d(TAG, "onCharacteristicWriteRequest: Message from client: " + status);
                        statusButton.setBackground(getResources().getDrawable(R.drawable.background_red));
                        statusText.setText(status);
                        break;
                    default:
                        Log.d(TAG, "onCharacteristicWriteRequest: Message from client: " + status);
                        statusButton.setBackground(getResources().getDrawable(R.drawable.background_grey));
                        statusText.setText(status);
                        break;
                }
            }

        }

    };
}