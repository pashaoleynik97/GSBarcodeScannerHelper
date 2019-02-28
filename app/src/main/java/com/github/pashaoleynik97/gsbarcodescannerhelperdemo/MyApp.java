package com.github.pashaoleynik97.gsbarcodescannerhelperdemo;

import android.app.Application;

import com.github.pashaoleynik97.gsbarcodescannerhelper.BarcodeScannerHelper;

public class MyApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        BarcodeScannerHelper.newInstance(this, "HelperDemo");
        BarcodeScannerHelper.setLogsEnabled(true);
    }

}
