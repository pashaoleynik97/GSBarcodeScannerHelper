# GSBarcodeScannerHelper
Simple library that helps to use GeneralScan SDK (painless callbacks and initialization).

[![](https://jitpack.io/v/pashaoleynik97/GSBarcodeScannerHelper.svg)](https://jitpack.io/#pashaoleynik97/GSBarcodeScannerHelper)

## Summary
GSBarcodeScannerHelper library will help you to create connecion with scanner device and create listener to receive data from scanner.
This library fixes some terrible bugs from original GeneralScan SDK and allows you to use GS devices in your android applications much more easier.

## Adding to project

**Add it in your root build.gradle at the end of repositories:**

```
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

**Add the dependency (for app module):**

```
dependencies {
	implementation 'com.github.pashaoleynik97:GSBarcodeScannerHelper:Tag'
}
```
*Note: raplace **Tag** with actual library version.*

## Using library

First of all, you should add service in `Manifest.xml`:

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.github.pashaoleynik97.gsbarcodescannerhelperdemo">

    <application
        ...

        <service
            android:name="com.generalscan.scannersdk.core.session.bluetooth.service.BluetoothConnectService"
            android:enabled="true"
            android:exported="true" />
    </application>

</manifest>
```

Then you need to initialize `BarcodeScannerHelper` in your `Application` class.

```
public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        BarcodeScannerHelper.newInstance(this, "HelperDemo");
        BarcodeScannerHelper.setLogsEnabled(true);
    }

}
```

You can optionally check permissions for Bluetooth (with this sample method):

```
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
```

Then you need to select scanner device from bluetooth devices list. Library will automatically save device address. But you need to create UI to allow user to choose device. There is an example (but you can do it in another way):

```
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
```
*Note: if you do not select device it will lead your app to crash when you will try to use method for scanner connection.*

Next step is connecting your scanner device to library and adding listener:

```
private void connectScanner() {
        // avoiding crash if bluetooth device is not specified
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
                Log.d(LOG_TAG, "Scanner disconnected");
            }

            @Override
            public void onScannerConnected() {
                Log.d(LOG_TAG, "Scanner connected");
            }

            @Override
            public void onScannerFailedToConnect(String errMsg) {
                Log.d(LOG_TAG, "Failed to connect scanner. Msg: " + errMsg);
            }

            @Override
            public void onScannerReceivedData(String data) {
                Log.d(LOG_TAG, data);
            }

            @Override
            public void onScannerReceivedBatteryData(String percentage) {
                Log.d(LOG_TAG, "Battery: " + percentage);
            }
        });

    }
```

***Important note: you should to stop session in `onDestroy()` method of your activity!***

```
 @Override
    protected void onDestroy() {
        BarcodeScannerHelper.getInstance().stopSession();
        super.onDestroy();
    }
 ```
 
 If you want to know battery level of your scanner device, you can simply use `BarcodeScannerHelper.getInstance().requestBatteryState();` and you will receive result in `onScannerReceivedBatteryData(String percentage)` of your `BarcodeScannerListener` implementation. But if you want regulary receive battery data, you can use `BarcodeScannerHelper.getInstance().scheduleBatteryStateRequests(30 * 1000);` and you will get results every 30 seconds.
