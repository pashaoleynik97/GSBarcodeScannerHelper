package com.github.pashaoleynik97.gsbarcodescannerhelper;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.generalscan.scannersdk.core.basic.interfaces.CommunicateListener;
import com.generalscan.scannersdk.core.basic.interfaces.IConnectSession;
import com.generalscan.scannersdk.core.basic.interfaces.SessionListener;
import com.generalscan.scannersdk.core.scannercommand.command.functional.SendWakeUp;
import com.generalscan.scannersdk.core.scannercommand.command.read.ReadBattery;
import com.generalscan.scannersdk.core.session.bluetooth.connect.BluetoothConnectSession;

import java.util.HashMap;

import static android.content.Context.MODE_PRIVATE;

public class BarcodeScannerHelper {

    private final static String LOG_TAG = "ScannerHelper";
    private static boolean logsEnabled = false;

    private static String SP_NAME;
    public static final String SCN_NAME_KEY = "scn_name";
    public static final String SCN_ADDRESS_KEY = "scn_address";

    @SuppressLint("StaticFieldLeak")
    private static Context context;
    @SuppressLint("StaticFieldLeak")
    private static BarcodeScannerHelper instance = null;
    @SuppressLint("StaticFieldLeak")
    private static BluetoothConnectSession mBluetoothConnectSession = null;
    private static BluetoothDevice device;

    private Handler handler;
    private Runnable batteryHighRequest;
    private Runnable batteryLowRequest;
    private boolean batteryRequestsScheduled = false;
    public static final long DEFAULT_BATTERY_UPD_INTERVAL = 15 * 1000;
    private long batteryRequestInterval = DEFAULT_BATTERY_UPD_INTERVAL;

    private BarcodeScannerHelper(Context context, String appName) {
        BarcodeScannerHelper.context = context;
        SP_NAME = appName + "_" + "BSH";
        BluetoothDevice d = preloadDevice();
        if (d != null) BarcodeScannerHelper.device = d;
        handler = new Handler();
    }

    /**
     * Call this method in your {@link android.app.Application} class
     *
     * @param context application context
     * @param appName application name
     * @throws BarcodeScannerHelperException possible exception (avoiding of instance duplicating)
     */
    public static void newInstance(Context context, String appName) throws BarcodeScannerHelperException {
        if (instance == null) {
            instance = new BarcodeScannerHelper(context, appName);
        } else {
            throw new BarcodeScannerHelperException(BarcodeScannerHelperException.Err.DUPLICATING_INSTANCE);
        }
    }

    /**
     * This method checks if instance of {@link BarcodeScannerHelper} exists
     *
     * @throws BarcodeScannerHelperException exception (if instance is null)
     */
    private static void checkInstance() throws BarcodeScannerHelperException {
        if (instance == null)
            throw new BarcodeScannerHelperException(BarcodeScannerHelperException.Err.NOT_INITIALIZED);
    }

    /**
     * Returns instance of {@link BarcodeScannerHelper}.
     *
     * @return {@link BarcodeScannerHelper} instance
     * @throws BarcodeScannerHelperException exception (if instance is null)
     */
    public static BarcodeScannerHelper getInstance() throws BarcodeScannerHelperException {
        checkInstance();
        return instance;
    }

    /**
     * This method is for establishing connection with scanner and initialization of scanner listener.
     * Notice, that you should check that {@link BluetoothDevice} device is specified in helper.
     *
     * @param barcodeScannerListener {@link BarcodeScannerListener} listener
     * @throws BarcodeScannerHelperException exception (if device not specified or GS SDK can not start session)
     */
    public void connectScanner(final BarcodeScannerListener barcodeScannerListener) throws BarcodeScannerHelperException {
        if (logsEnabled) Log.d(LOG_TAG, "Trying to connect scanner.");
        checkInstance();
        if (barcodeScannerListener == null)
            throw new BarcodeScannerHelperException(BarcodeScannerHelperException.Err.NULL_LISTENER_PASSED);
        barcodeScannerListener.onScannerConnecting();
        mBluetoothConnectSession = new BluetoothConnectSession(context);
        mBluetoothConnectSession.setSessionListener(new SessionListener() {
            @Override
            public void onSessionReady(IConnectSession iConnectSession) {
                if (logsEnabled)
                    Log.d(LOG_TAG, "Session ready. Connected: " + iConnectSession.isConnected());
                if (device == null)
                    throw new BarcodeScannerHelperException(BarcodeScannerHelperException.Err.BLUETOOTH_DEVICE_NOT_SELECTED);
                mBluetoothConnectSession.setBluetoothDeviceToConnect(device);
                mBluetoothConnectSession.connect();
            }

            @Override
            public void onSessionStartTimeOut(IConnectSession iConnectSession) {
                if (logsEnabled) Log.e(LOG_TAG, "Session start timed out!");
                throw new BarcodeScannerHelperException(BarcodeScannerHelperException.Err.CAN_NOT_START_SESSION);
            }
        });
        mBluetoothConnectSession.startSession();
        initListener(barcodeScannerListener);
    }

    private void initListener(final BarcodeScannerListener barcodeScannerListener) {
        mBluetoothConnectSession.setConnectListener(new CommunicateListener() {

            private final long delayForHandler = 10;
            private Handler handler = new Handler();
            private String completeData = "";
            private long stamp = 0;
            private boolean rProcessStarted = false;

            private void buildNext(String part) {
                completeData += part;
                stamp = System.currentTimeMillis();
            }

            private void checkData() {
                if (System.currentTimeMillis() - stamp > 100) {
                    String finalData = completeData.trim().replaceAll("\\n", "")
                            .trim().replaceAll("\\s", "")
                            .trim().replaceAll("\\t", "");
                    /*
                     * DUE TO BUGS IN GS SDK, WE NEED TO CHECK, IF DATA IS NOT A BATTERY STATE DATA
                     * */
                    boolean isBatteryData;
                    isBatteryData = finalData.startsWith("[G1066") // battery cmd code (e.g. for SDK 1.0.2)
                    ||
                    finalData.isEmpty(); // for SDK 1.0.6
                    if (isBatteryData) {
                        /* Format: '[G1066/0000mV/00%]' */
                        String[] parsed = finalData.split("/");
                        if (!finalData.isEmpty() && parsed.length >= 2) {
                            String voltage = parsed[1];
                            String percentage = parsed[2].substring(0, parsed[2].length() - 1);
                            this.onBatteryDataReceived(voltage, percentage);
                        }
                    } else {
                        // received barcode
                        barcodeScannerListener.onScannerReceivedData(finalData);
                    }
                    stopReceiving();
                } else {
                    if (rProcessStarted) handler.postDelayed(
                            dataCheck,
                            delayForHandler
                    );
                }
            }

            private Runnable dataCheck = new Runnable() {
                @Override
                public void run() {
                    checkData();
                }
            };

            private void startReceiving() {
                rProcessStarted = true;
                handler.postDelayed(
                        dataCheck,
                        delayForHandler
                );
            }

            private void stopReceiving() {
                handler.removeCallbacks(dataCheck);
                completeData = "";
                stamp = 0;
                rProcessStarted = false;
            }

            @Override
            public void onConnected() {
                barcodeScannerListener.onScannerConnected();
            }

            @Override
            public void onConnectFailure(String errorMessage) {
                barcodeScannerListener.onScannerFailedToConnect(errorMessage);
            }

            @Override
            public void onDisconnected() {
                barcodeScannerListener.onScannerDisconnected();
            }

            @Override
            public void onRawDataReceived(byte data) {
                if (logsEnabled) Log.d(LOG_TAG, "RAW: " + data);
            }

            @Override
            public void onRawDataReceiveError(String errorMessage, String source) {
                if (logsEnabled)
                    Log.d(LOG_TAG, "Data receive error. Msg: " + errorMessage + " Source: " + source);
            }

            @Override
            public void onDataReceived(String data) {
                if (!rProcessStarted) startReceiving();
                buildNext(data);
                if (logsEnabled) Log.d(LOG_TAG, "Data (string): " + data);
            }

            @Override
            public void onCommandCallback(String name, String data) {
                if (logsEnabled)
                    Log.d(LOG_TAG, "Cmd callback. Cmd name: " + name + " Data: " + data);
            }

            @Override
            public void onCommandNoResponse(String errorMessage) {
                if (logsEnabled) Log.d(LOG_TAG, "Cmd no response. Error: " + errorMessage);
            }

            @Override
            public void onBatteryDataReceived(String voltage, String percentage) {
                barcodeScannerListener.onScannerReceivedBatteryData(percentage);
            }
        });
    }

    public void selectBluetoothDevice(BluetoothDevice device) {
        BarcodeScannerHelper.device = device;
        if (device != null) {
            saveScannerDevicePreference(device.getName(), device.getAddress());
        }
    }

    public void stopSession() {
        checkInstance();
        if (mBluetoothConnectSession != null) mBluetoothConnectSession.endSession();
        batteryRequestsScheduled = false;
        if (batteryHighRequest != null) {
            try {
                handler.removeCallbacks(batteryHighRequest);
                handler.removeCallbacks(batteryLowRequest);
            } catch (Exception ex) {
                if (logsEnabled) Log.e(LOG_TAG, "Error", ex);
            }
        }
    }

    private void saveScannerDevicePreference(String name, String address) {
        SharedPreferences.Editor editor = context.getSharedPreferences(SP_NAME, MODE_PRIVATE).edit();
        editor.putString(SCN_NAME_KEY, name);
        editor.putString(SCN_ADDRESS_KEY, address);
        editor.apply();
    }

    private HashMap<String, String> readScannerDevicePreference() {
        HashMap<String, String> res = new HashMap<>();
        SharedPreferences prefs = context.getSharedPreferences(SP_NAME, MODE_PRIVATE);
        String name = prefs.getString(SCN_NAME_KEY, null);
        String address = prefs.getString(SCN_ADDRESS_KEY, null);
        res.put(SCN_NAME_KEY, name);
        res.put(SCN_ADDRESS_KEY, address);
        return res;
    }

    public HashMap<String, String> getSavedBluetoothDevice() {
        return readScannerDevicePreference();
    }

    private BluetoothDevice preloadDevice() {
        HashMap<String, String> devOptions = readScannerDevicePreference();
        if (devOptions.get(SCN_ADDRESS_KEY) == null) return null;

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice d = null;
        try {
            d = mBluetoothAdapter.getRemoteDevice(devOptions.get(SCN_ADDRESS_KEY));
        } catch (Exception ex) {
            if (logsEnabled) Log.e(LOG_TAG, "Can not preload saved bl-device.", ex);
        }
        return d;
    }

    public void scheduleBatteryStateRequests(final long interval) {
        this.batteryRequestInterval = interval >= 1000 ? interval : 1000;
        batteryRequestsScheduled = true;
        checkInstance();
        batteryHighRequest = new Runnable() {
            @Override
            public void run() {
                requestBatteryState();
                if (batteryRequestsScheduled)
                    handler.postDelayed(batteryHighRequest, batteryRequestInterval);
            }
        };
        batteryLowRequest = new Runnable() {
            @Override
            public void run() {
                ReadBattery rState = new ReadBattery(context);
                if (mBluetoothConnectSession != null)
                    mBluetoothConnectSession.sendData(rState.getCommandText());
            }
        };
        handler.post(batteryHighRequest);
    }

    public void requestBatteryState() {
        checkInstance();
        SendWakeUp wake = new SendWakeUp(context);
        if (mBluetoothConnectSession != null)
            mBluetoothConnectSession.sendData(wake.getCommandText());
        handler.postDelayed(batteryLowRequest, 250);
    }

    public boolean isBluetoothDeviceSpecified() {
        return device != null;
    }

    public static void setLogsEnabled(boolean logsEnabled) {
        BarcodeScannerHelper.logsEnabled = logsEnabled;
    }

    public static boolean isLogsEnabled() {
        return logsEnabled;
    }

}
