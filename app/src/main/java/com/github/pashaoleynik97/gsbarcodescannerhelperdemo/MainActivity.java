package com.github.pashaoleynik97.gsbarcodescannerhelperdemo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pashaoleynik97.gsbarcodescannerhelper.BarcodeScannerHelper;
import com.github.pashaoleynik97.gsbarcodescannerhelper.BarcodeScannerListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String LOG_TAG = "DEMO_ACTIVITY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btnCheckPermissions).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkSystemPermissions();
            }
        });

        findViewById(R.id.btnSelectScn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showScannerSelectDialog();
            }
        });

        findViewById(R.id.btnConnect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectScanner();
            }
        });

    }

    private void checkSystemPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN)
                    != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.BLUETOOTH)
                    != PackageManager.PERMISSION_GRANTED)
            {
                List<String> listPermissionsNeeded = new ArrayList<>();
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                        != PackageManager.PERMISSION_GRANTED)
                {
                    listPermissionsNeeded.add(Manifest.permission.BLUETOOTH);
                }
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                        != PackageManager.PERMISSION_GRANTED)
                {
                    listPermissionsNeeded.add(Manifest.permission.BLUETOOTH_ADMIN);
                }
                if (!listPermissionsNeeded.isEmpty()) {
                    ActivityCompat.requestPermissions(
                            this,
                            listPermissionsNeeded.toArray(new String[listPermissionsNeeded.size()]),
                            10
                    );
                }
            }
        }
    }

    private void showScannerSelectDialog() {
        final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        final Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        final String NAME = "n";
        final String ADDRESS = "a";

        final List<String> s = new ArrayList<>();
        final List<HashMap<String, String>> devices = new ArrayList<>();
        for(BluetoothDevice bt : pairedDevices) {
            HashMap<String, String> d = new HashMap<>();
            d.put(NAME, bt.getName());
            d.put(ADDRESS, bt.getAddress());
            devices.add(d);
            s.add(bt.getName());
        }

        String btSavedAddress = BarcodeScannerHelper.getInstance()
                .getSavedBluetoothDevice().get(BarcodeScannerHelper.SCN_ADDRESS_KEY);

        int indexSelected = -1;
        for (int i = 0; i < devices.size(); i ++) {
            if (devices.get(i).get(ADDRESS).equals(btSavedAddress)) {
                indexSelected = i;
                break;
            }
        }

        final AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle("Select scanner");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.select_dialog_singlechoice, s);
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int btnType) {
                ListView lv = ((AlertDialog) dialogInterface).getListView();
                if (btnType == Dialog.BUTTON_POSITIVE) {
                    for(BluetoothDevice bt : pairedDevices) {
                        if (bt.getAddress().equals(devices.get(lv.getCheckedItemPosition()).get(ADDRESS))) {
                            BarcodeScannerHelper.getInstance().selectBluetoothDevice(mBluetoothAdapter.getRemoteDevice(bt.getAddress()));
                            return;
                        }
                    }
                }
            }
        };
        adb.setSingleChoiceItems(adapter, indexSelected, clickListener);
        adb.setPositiveButton(android.R.string.ok, clickListener);
        adb.create();
        adb.show();
    }

    private void connectScanner() {
        if (!BarcodeScannerHelper.getInstance().isBluetoothDeviceSpecified()) {
            Toast.makeText(MainActivity.this, "Scanner not selected!", Toast.LENGTH_SHORT).show();
            return;
        }

        BarcodeScannerHelper.getInstance().connectScanner(new BarcodeScannerListener() {
            @Override
            public void onScannerConnecting() {
                Log.d(LOG_TAG, "Scanner connecting...");
            }

            @Override
            public void onScannerDisconnected() {
                Toast.makeText(MainActivity.this, "Scanner disconnected", Toast.LENGTH_SHORT).show();
                Log.d(LOG_TAG, "Scanner disconnected");
            }

            @Override
            public void onScannerConnected() {
                Toast.makeText(MainActivity.this, "Scanner connected", Toast.LENGTH_SHORT).show();
                Log.d(LOG_TAG, "Scanner connected");
            }

            @Override
            public void onScannerFailedToConnect(String errMsg) {
                Toast.makeText(MainActivity.this, "Failed to connect scanner. Msg: " + errMsg, Toast.LENGTH_SHORT).show();
                Log.d(LOG_TAG, "Failed to connect scanner. Msg: " + errMsg);
            }

            @Override
            public void onScannerReceivedData(String data) {
                ((TextView) findViewById(R.id.tvResult)).setText(
                        String.valueOf(data)
                );
                Log.d(LOG_TAG, data);
            }

            @Override
            public void onScannerReceivedBatteryData(String percentage) {
                Log.d(LOG_TAG, "Battery: " + percentage);
            }
        });

    }

    @Override
    protected void onDestroy() {
        BarcodeScannerHelper.getInstance().stopSession();
        super.onDestroy();
    }

}
