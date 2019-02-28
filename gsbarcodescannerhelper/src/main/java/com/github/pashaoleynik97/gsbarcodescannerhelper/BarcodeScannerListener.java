package com.github.pashaoleynik97.gsbarcodescannerhelper;

public interface BarcodeScannerListener {
    void onScannerConnecting();
    void onScannerDisconnected();
    void onScannerConnected();
    void onScannerFailedToConnect(String errMsg);
    void onScannerReceivedData(String data);
    void onScannerReceivedBatteryData(String percentage);
}
