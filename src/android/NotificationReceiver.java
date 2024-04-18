package land.cookie.cordova.plugin.zebrascanner;

import android.util.Log;

import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.FirmwareUpdateEvent;
import com.zebra.scannercontrol.IDcsSdkApiDelegate;
// import com.zebra.barcode.sdk.sms.ConfigurationUpdateEvent;

import com.zebra.rfid.api3.*;

import land.cookie.cordova.plugin.zebrascanner.barcode.BarcodeTypes;

import org.json.JSONException;
import org.json.JSONArray;

public class NotificationReceiver implements IDcsSdkApiDelegate, RfidEventsListener {
    private static final String TAG = "CL_ZebraScanner";
    private ZebraScanner mScanner;
    ReaderDevice readerDevice;
    int connectedDeviceId;

    NotificationReceiver(ZebraScanner scanner, ReaderDevice readerDevice, int connectedDeviceId) {
        mScanner = scanner;
        this.readerDevice = readerDevice;
        this.connectedDeviceId = connectedDeviceId;
    }

    @Override
    public void eventStatusNotify(RfidStatusEvents rfidStatusEvents) {
        Log.d(TAG, "NotificationReceiver eventStatusNotify!!!!");
        try {
            if (rfidStatusEvents != null) {

                STATUS_EVENT_TYPE statusEventType = rfidStatusEvents.StatusEventData.getStatusEventType();
                // if (statusEventType != null && statusEventType.toString().equalsIgnoreCase("BATTERY_EVENT")) {
                //     getDeviceInfo(rfidStatusEvents);

                // }
                RfidReadEvents rfidReadEvents = new RfidReadEvents(readerDevice.getRFIDReader().Actions.Inventory);
                HANDHELD_TRIGGER_EVENT_TYPE handheldEvent = rfidStatusEvents.StatusEventData.HandheldTriggerEventData.getHandheldEvent();
                try {
                    if (handheldEvent != null && handheldEvent.toString().equalsIgnoreCase("HANDHELD_TRIGGER_PRESSED")) {

                        /*
                         * after perform(), stop() has to be called to save tags in memory. then we
                         * can read the tags from memory when eventReadNotify() is called after
                         * stop()
                         */
                        readerDevice.getRFIDReader().Actions.Inventory.perform();

                    }
                    if (handheldEvent != null && handheldEvent.toString().equalsIgnoreCase("HANDHELD_TRIGGER_RELEASED")) {
                        readerDevice.getRFIDReader().Actions.Inventory.stop();
                    }
                } catch (InvalidUsageException e) {
                    Log.e(TAG, "Error: InvalidUsageException");
                    e.printStackTrace();
                } catch (OperationFailureException e) {
                    Log.e(TAG, "Error: OperationFailureException");
                    e.printStackTrace();
                }

                if (rfidReadEvents != null) {
                    Log.d(TAG, "rfidReadEvents!!!!");
                    eventReadNotify(rfidReadEvents);
                }

            }

        } catch (Exception ex) {
            Log.e(TAG, "Error: " + ex.getMessage());
        }

    }

    @Override
    public void eventReadNotify(RfidReadEvents rfidReadEvents) {
        Log.d(TAG, "NotificationReceiver eventReadNotify!!!!");
        String epc;
        if (readerDevice != null) {
            try{
                if (myTags != null) {
                    JSONArray data = new JSONArray();
                    TagData[] myTags = readerDevice.getRFIDReader().Actions.getReadTags(100);
                    for (TagData tag : myTags) {
                        putTagID(data, tag);
                        // epc = tag.getTagID();
                        // Log.d(TAG, "eventReadNotify epc" + epc);

                    }
                    mScanner.notifyBarcodeReceived(data, "rfid" , connectedDeviceId); 
                    // try { 
                    //     mScanner.notifyBarcodeReceived(epc, "rfid", 1); 
                    // } catch(JSONException err) {
                    //     Log.e(TAG, "ERROR notifying barcode.");
                    // }
                }
            } catch (JSONException ex) {
                Log.e(TAG, "Error: " + ex.getMessage());
            }
         } else {
            Log.e(TAG, "ERROR missing readerDevice.");
         }
    }

    /**
     * @param data
     * @param tag
     */
    private void putTagID(JSONArray data, TagData tag) {
        Boolean isNew = true;
        String epc;
        // JSONObject tagInfo = null;
        // int rssi;
        try {
            // if (useASCII) {
                epc = hexToAscii(tag.getTagID());

            // } else {
            //     epc = tag.getTagID();
            // }

            for (int i = 0; i < data.length(); i++) {
                // JSONObject availableData = data.getJSONObject(i);
                // String availableID = (String) availableData.get(EPC);
                String availableID = (String) data.getJSONObject(i);

                if (epc.equalsIgnoreCase(availableID)) {
                    isNew = false;
                }

            }

            if (isNew) {
                // rssi = tag.getPeakRSSI();
                // tagInfo = JSONUtil.createRFIDJSONObject(epc, rssi);
                data.put(epc);
            }

        } catch (JSONException ex) {
            callbackContext.error("Error: " + ex.getMessage());
        }
    }

    private String hexToAscii(String hexStr) {
        StringBuilder output = new StringBuilder();

        for (int i = 0; i < hexStr.length(); i += 2) {
            String str = hexStr.substring(i, i + 2);
            output.append((char) Integer.parseInt(str, 16));
        }

        return output.toString();
    }

    @Override
    public void dcssdkEventScannerAppeared(DCSScannerInfo scanner) {
        Log.d(TAG, "Scanner Appeared");
        try {
            mScanner.notifyDeviceFound(scanner);
        } catch(JSONException err) {
            Log.e(TAG, "ERROR notifying appeared event.");
        }
    }

    @Override
    public void dcssdkEventScannerDisappeared(int scannerId) {
        Log.d(TAG, "Scanner Disappeared");
        try {
            mScanner.notifyDeviceLost(scannerId);
        } catch(JSONException err) {
            Log.e(TAG, "ERROR notifying disappeared event.");
        }
    }

    @Override
    public void dcssdkEventCommunicationSessionEstablished(DCSScannerInfo scanner) {
        Log.d(TAG, "Communication Session Established");
        // try {
        //     mScanner.notifyDeviceConnected(scanner);
        // } catch(JSONException err) {
        //     Log.e(TAG, "ERROR notifying connected event.");
        // }
    }

    @Override
    public void dcssdkEventCommunicationSessionTerminated(int scannerId) {
        Log.d(TAG, "Communication Session Terminated");
        try {
            mScanner.notifyDeviceDisconnected(scannerId);
        } catch(JSONException err) {
            Log.e(TAG, "ERROR notifying disconnected event.");
        }
    }

    @Override
    public void dcssdkEventBarcode(byte[] barcodeData, int barcodeType, int fromScannerId) {
        Log.d(TAG, "Got Barcode");
        Log.d(TAG, "\nType: " + BarcodeTypes.getBarcodeTypeName(barcodeType) + ".\n From scanner: " + fromScannerId + ".\n Data: " + new String(barcodeData));
        try {
            mScanner.notifyBarcodeReceived(new String(barcodeData), BarcodeTypes.getBarcodeTypeName(barcodeType), fromScannerId);
        } catch(JSONException err) {
            Log.e(TAG, "ERROR notifying barcode.");
        }
    }

    @Override
    public void dcssdkEventImage(byte[] var1, int var2) {
        Log.d(TAG, "Got Image?");
    }

    @Override
    public void dcssdkEventVideo(byte[] var1, int var2) {
        Log.d(TAG, "Got Video?");
    }

    @Override
    public void dcssdkEventFirmwareUpdate(FirmwareUpdateEvent var1) {
        Log.d(TAG, "Firmware Update Event");
    }

    @Override
    public void dcssdkEventAuxScannerAppeared(DCSScannerInfo dcsScannerInfo, DCSScannerInfo dcsScannerInfo1) {
        Log.d(TAG, "Aux Scanner Appeared");
    }

    // @Override
    // public void dcssdkEventConfigurationUpdate(ConfigurationUpdateEvent configEvent) {
    //     Log.d(TAG, "dcssdkEventConfigurationUpdate appeared");
    // }

    @Override
    public void dcssdkEventBinaryData(byte[] var1, int var2) {
        Log.d(TAG, "Got dcssdkEventBinaryData");
    }
}
