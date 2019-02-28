package com.github.pashaoleynik97.gsbarcodescannerhelper;

public class BarcodeScannerHelperException extends RuntimeException {

    public enum Err {

        DUPLICATING_INSTANCE(
                "TRYING TO DUPLICATE INSTANCE",
                "You are trying to duplicate instance of "
                        + BarcodeScannerHelper.class.getSimpleName()
                        + "."
        ),
        NOT_INITIALIZED(
                "INSTANCE NOT INITIALIZED",
                "You must first initialize instance using "
                        + BarcodeScannerHelper.class.getSimpleName()
                        + ".newInstance()"
        ),
        CAN_NOT_START_SESSION(
                "CAN NOT START SESSION",
                "BluetoothConnectSession can not start session. Session start timed out."
        ),
        NULL_LISTENER_PASSED(
                "NULL LISTENER PASSED",
                "Can not initiate scanner connection, because null was passed instead of "
                        + BarcodeScannerListener.class.getSimpleName()
                        + " object"
        ),
        BLUETOOTH_DEVICE_NOT_SELECTED(
                "BLUETOOTH DEVICE NOT SELECTED",
                "Before initiate connection, you must select bluetooth device. You can do it calling " +
                        BarcodeScannerHelper.class.getSimpleName() + ".selectBluetoothDevice()"
        )
        ;

        String reason;
        String message;

        Err(String reason, String message) {
            this.reason = reason;
            this.message = message;
        }

        public String getReason() {
            return reason;
        }

        public String getMessage() {
            return message;
        }

    }

    BarcodeScannerHelperException(Err e) {
        super(e.getReason() + " --> " + e.getMessage());
    }

}
