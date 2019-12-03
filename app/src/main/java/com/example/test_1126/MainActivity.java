package com.example.test_1126;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static com.example.test_1126.Functions.byte2HexStr;

public class MainActivity extends AppCompatActivity {

    static String Data = "yichien+nthu+0956883866+ssssss";

    private static MainActivity sInstance;

    public static final String TAG = "chien";
    public static UUID id;
    public static UUID UUIDs;
    static int j;
    BluetoothAdapter mBluetoothAdapter;
    AdvertiseCallback mAdvertiseCallback;
    BluetoothManager mmBluetoothManager;
    BluetoothLeScanner mBluetoothScanner;
    BluetoothLeAdvertiser mBluetoothLeAdvertiser;

    private Button btn_on;
    private Button btn_off;

    static Map<UUID, AdvertiseCallback> mAdvertiseCallbacks;
    static Map<UUID, Long> mAdvertiseStartTimestamp;
    static Map<UUID, PendingIntent> mScheduledPendingIntents;
    static AlarmManager mAlarmManager;

    static byte[][] adv_packet;
    static int x;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize();

        if(!BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported()){
            Toast.makeText(this,"Advertisement 不支援",Toast.LENGTH_SHORT).show();
        }

        btn_on=findViewById(R.id.button_on);
        btn_off=findViewById(R.id.button_off);
        btn_on.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startAdvertising();
            }
        });

        btn_off.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopAdvertising();
            }
        });

        mAdvertiseCallbacks = new TreeMap<>();
        mAdvertiseStartTimestamp = new HashMap<>();
        mScheduledPendingIntents = new HashMap<>();
        mAlarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);

        btn_off.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
        Log.d(TAG,"onDestroy() called");
//        stopAll(0, true);
//        unregisterReceiver(mBroadcastReceiver);
//        EventBus.getDefault().unregister(this);
    }

    private void initialize() {
        if (mBluetoothLeAdvertiser == null) {
            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager != null) {
                BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter != null) {
                    mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
                }
            }
        }
    }

    public void startAdvertising(){
        Log.d(TAG, "Service: Starting Advertising");
        adv_packet=data_seg();
        if (mAdvertiseCallback == null) {
            if (mBluetoothLeAdvertiser != null) {
                for (int q=1;q<x;q++){
                    select_uuid(q);
                    startBroadcast(q,id,true);
                }
            }
        }
        btn_on.setVisibility(View.INVISIBLE);
        btn_off.setVisibility(View.VISIBLE);
    }

    public void stopAdvertising(){
        if (mBluetoothLeAdvertiser != null) {
            //mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
            for (int q=1;q<x;q++){
                select_uuid(q);
                stopBroadcast(q, id, true, false);
            }

            mAdvertiseCallback = null;
        }
        btn_off.setVisibility(View.INVISIBLE);
        btn_on.setVisibility(View.VISIBLE);
    }

    private void startBroadcast(int serviceStartId, UUID id, boolean isRestart) {
        final AdvertiseSettings settings =  buildAdvertiseSettings();
        final AdvertiseData advertiseData = buildAdvertiseData(serviceStartId,id);
        final String localName = (serviceStartId)+"ch" ;
        ChangeDeviceName(localName);
        mBluetoothLeAdvertiser.startAdvertising(settings,advertiseData,new MyAdvertiseCallback(serviceStartId, id, isRestart));
        //Log.d(TAG,"startBroadcast");
    }

    private void stopBroadcast(int serviceStartId, UUID id, boolean isRestart, boolean ignoreServiceStartId) {
        final AdvertiseCallback adCallback = mAdvertiseCallbacks.get(id);
        if (adCallback != null) {
            try {
                if (mBluetoothLeAdvertiser != null) {
                    mBluetoothLeAdvertiser.stopAdvertising(adCallback);
                }
                else {
                    Log.w(TAG,"Not able to stop broadcast; mBtAdvertiser is null");
                }
            }
            catch(RuntimeException e) { // Can happen if BT adapter is not in ON state
                Log.w(TAG,"Not able to stop broadcast; BT state: {}");
            }
            removeScheduledUpdate(id);
            mAdvertiseCallbacks.remove(id);
        }
        Log.d(TAG,serviceStartId+" Advertising successfully stopped");
    }

    private class MyAdvertiseCallback extends AdvertiseCallback {
        private final UUID _id;
        private final int _serviceStartId;
        private final boolean _isRestart;
        private MyAdvertiseCallback(int serviceStartId, UUID id, boolean isRestart) {
            _id = id;
            _serviceStartId = serviceStartId;
            _isRestart = isRestart;
        }
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            Log.d(TAG, "Advertising failed errorCode: "+errorCode);
            switch (errorCode) {
                case ADVERTISE_FAILED_ALREADY_STARTED:
                    Log.e(TAG,"ADVERTISE_FAILED_ALREADY_STARTED");
                    break;
                case ADVERTISE_FAILED_DATA_TOO_LARGE:
                    Log.e(TAG,"ADVERTISE_FAILED_DATA_TOO_LARGE");
                    break;
                case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                    Log.e(TAG,"ADVERTISE_FAILED_FEATURE_UNSUPPORTED");
                    break;
                case ADVERTISE_FAILED_INTERNAL_ERROR:
                    Log.e(TAG,"ADVERTISE_FAILED_INTERNAL_ERROR");
                    break;
                case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                    Log.e(TAG,"ADVERTISE_FAILED_TOO_MANY_ADVERTISERS");
                    break;
                default:
                    Log.e(TAG,"Unhandled error : "+errorCode);
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            Log.d(TAG, _serviceStartId+" Advertising successfully started");
            mAdvertiseCallbacks.put(_id, this);
        }
    }

    public static AdvertiseSettings buildAdvertiseSettings() {
        AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(false)
                .setTimeout(0);
        //settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER);
        //settingsBuilder.setTimeout(0);
        return settingsBuilder.build();
    }

    static AdvertiseData buildAdvertiseData(int serviceStartId,UUID id) {
        AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
        dataBuilder.setIncludeDeviceName(true);
        dataBuilder.addServiceUuid(ParcelUuid.fromString(id.toString())); //Add a service UUID to advertise data.
        dataBuilder.addServiceData(ParcelUuid.fromString(id.toString()),adv_packet[serviceStartId-1]);
        return dataBuilder.build();
    }

    static void removeScheduledUpdate(UUID uuid) {
        PendingIntent pendingIntent = mScheduledPendingIntents.remove(uuid);
        if (pendingIntent == null) {
            return;
        }
        mAlarmManager.cancel(pendingIntent);
    }

    static void ChangeDeviceName(String localName){
        BluetoothAdapter.getDefaultAdapter().setName(localName);
    }

    static void select_uuid(int serviceStartId){
        switch (serviceStartId){
            case 1:
                UUIDs = UUID.fromString("00002801-0000-1000-8000-00805f9b34fb");
                break;
            case 2:
                UUIDs = UUID.fromString("00002802-0000-1000-8000-00805f9b34fb");
                break;
            case 3:
                UUIDs = UUID.fromString("00002803-0000-1000-8000-00805f9b34fb");
                break;
            case 4:
                UUIDs = UUID.fromString("00002804-0000-1000-8000-00805f9b34fb");
                break;
            case 5:
                UUIDs = UUID.fromString("00002805-0000-1000-8000-00805f9b34fb");
                break;
            case 6:
                UUIDs = UUID.fromString("00002806-0000-1000-8000-00805f9b34fb");
                break;
            case 7:
                UUIDs = UUID.fromString("00002807-0000-1000-8000-00805f9b34fb");
                break;
            case 8:
                UUIDs = UUID.fromString("00002808-0000-1000-8000-00805f9b34fb");
                break;
            case 9:
                UUIDs = UUID.fromString("00002809-0000-1000-8000-00805f9b34fb");
                break;
            default:
                Log.d(TAG,"uuid select error");
        }
        id = ParcelUuid.fromString(UUIDs.toString()).getUuid();
    }

    public byte[][] data_seg() {
        byte[] byte_data = Data.getBytes();
        int pack_num=1;
        int coun = 0;
        int byte_len=15;
        x =(byte_data.length/15)+1;
        byte[][] adv_byte = new byte[x][byte_len];
        for (int counter = byte_data.length; counter >0; counter = counter-15) {
            if (counter>=15){
                System.arraycopy(byte_data,coun,adv_byte[pack_num],0,byte_len);
//                Log.d(TAG,"adv_byte: "+byte2HexStr(adv_byte[pack_num])+";  counter: "+counter + ";  length: "+adv_byte[pack_num].length);
//                Log.d(TAG,"coco"+byte_len+" pack_num: "+pack_num);
                pack_num++;
                coun=coun+byte_len;
            }else {
                System.arraycopy(byte_data,coun,adv_byte[pack_num],0,byte_len);
//                Log.d(TAG,"adv_byte: "+byte2HexStr(adv_byte[pack_num])+";  counter: "+counter + ";  length: "+adv_byte[pack_num].length);
//                Log.d(TAG,"coco"+byte_len+" pack_num: "+pack_num);
            }
        }
        return adv_byte;
    }
}
