package land.cookie.cordova.plugin.zebrascanner;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;

import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.Manifest;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.util.Xml;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.zebra.scannercontrol.SDKHandler;
import com.zebra.scannercontrol.DCSSDKDefs;
import com.zebra.scannercontrol.DCSScannerInfo;
import com.zebra.scannercontrol.BarCodeView;

import org.xmlpull.v1.XmlPullParser;

import static com.zebra.scannercontrol.RMDAttributes.*;

public class ZebraScanner extends CordovaPlugin {
    private static final String TAG = "CL_ZebraScanner";

    private SDKHandler sdkHandler; // Zebra SDK
    private NotificationReceiver notificationReceiver;
    private CallbackContext scanCallBack;
    // SDK does not support multiple connected devices.
    private CallbackContext connectionCallBack;
    private CallbackContext subscriptionCallback;
    private CallbackContext callbackContext; // Callback context for permissions

    // Permissions found in example Scanner Control app (version 2.6.16.0)
    private static final String[] ANDROID_13_PERMISSIONS = new String[]{
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    };

    private static final String[] ANDROID_12_PERMISSIONS = new String[]{
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    };

    private static final String[] ANDROID_PERMISSIONS = new String[]{
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
    };

    private static BatteryStatsAsyncTask cmdExecTask=null;

    @Override
    public boolean execute (
            String action, final JSONArray args, final CallbackContext callbackContext
    ) throws JSONException {

        if ("startScan".equals(action))
            startScanAction(callbackContext);
        else if ("stopScan".equals(action))
            stopScanAction(callbackContext);
        else if ("getAvailableDevices".equals(action))
            getAvailableDevicesAction(callbackContext);
        else if ("getActiveDevices".equals(action))
            getActiveDevicesAction(callbackContext);
        else if ("getPairingBarcode".equals(action))
            getPairingBarcodeAction(callbackContext);
        else if ("connect".equals(action))
            connectAction(args, callbackContext);
        else if ("disconnect".equals(action))
            disconnectAction(args, callbackContext);
        else if ("subscribe".equals(action))
            subscribeAction(callbackContext);
        else if ("unsubscribe".equals(action))
            unsubscribeAction(callbackContext);
        else if ("init".equals(action))
            initSdk();
        // else if ("enableAutomaticSession".equals(action))
        //     enableAutomaticSessionAction(args, callbackContext);
        else if ("getBatteryStats".equals(action))
            getBatteryStatsAction(args, callbackContext);
        else
            return false;

        this.callbackContext = callbackContext; // Used for permissions
        return true;
    }
    /**
     * Init SDK & check/request permissions
     */
    private void initSdk() {
        // Set permissions based on Android version
        String[] permissions = {};
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = ANDROID_13_PERMISSIONS;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = ANDROID_12_PERMISSIONS;
        } else {
            permissions = ANDROID_PERMISSIONS;
        }
        if (hasPermission(permissions)) {
            if (sdkHandler == null) {
                this.cordova.getActivity().runOnUiThread(new Runnable() {
                    // It must be called on the main thread
                    public void run() {
                        SDKHandler tempsdkHandler = new SDKHandler(cordova.getContext(), true);
                        setSdk(tempsdkHandler);
                    }
                });
            }
        } else {
            PermissionHelper.requestPermissions(this, 0, permissions);
        }
    }
    /**
     * Otherwise, sdkHandler was null
     */
    private void setSdk(SDKHandler sdk) {
        sdkHandler = sdk;
        init();
    }
    private void init() {
        Log.d(TAG, "Setting up Zebra SDK.");
        notificationReceiver = new NotificationReceiver(this);

        // Subscribe to barcode events
        int notifications_mask = DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_BARCODE.value
        // Subscribe to scanner available/unavailable events
                | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_APPEARANCE.value
                | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SCANNER_DISAPPEARANCE.value
        // Subscribe to scanner connection/disconnection events
                | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_ESTABLISHMENT.value
                | DCSSDKDefs.DCSSDK_EVENT.DCSSDK_EVENT_SESSION_TERMINATION.value;

        sdkHandler.dcssdkSubsribeForEvents(notifications_mask);
        sdkHandler.dcssdkSetDelegate(notificationReceiver);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_SNAPI);
        sdkHandler.dcssdkSetOperationalMode(DCSSDKDefs.DCSSDK_MODE.DCSSDK_OPMODE_BT_NORMAL);
    }

    private void startScanAction(CallbackContext callbackContext) throws JSONException {
        if (scanCallBack != null) {
            callbackContext.error("Scanning is already in a progress.");
            return;
        }

        Log.d(TAG, "Starting scan.");
        sdkHandler.dcssdkStartScanForAvailableDevices();

        scanCallBack = callbackContext;
        PluginResult message = createStatusMessage("scanStart", true);
        scanCallBack.sendPluginResult(message);

        List<DCSScannerInfo> deviceInfos = new ArrayList<DCSScannerInfo>();
        sdkHandler.dcssdkGetAvailableScannersList(deviceInfos);

        for (DCSScannerInfo deviceInfo : deviceInfos) {
            notifyDeviceFound(deviceInfo);
        }
    }

    private void stopScanAction(CallbackContext callbackContext) throws JSONException {
        if (scanCallBack == null) {
            callbackContext.error("Scanning was not in a progress.");
            return;
        }

        Log.d(TAG, "Stopping scan.");
        sdkHandler.dcssdkStopScanningDevices();

        callbackContext.success("ok");
        PluginResult message = createStatusMessage("scanStop", false);
        scanCallBack.sendPluginResult(message);
        scanCallBack = null;
    }

    private void getAvailableDevicesAction(CallbackContext callbackContext) throws JSONException {
        List<DCSScannerInfo> deviceInfos = new ArrayList<DCSScannerInfo>();
        sdkHandler.dcssdkGetAvailableScannersList(deviceInfos);

        JSONArray devices = new JSONArray();
        for (DCSScannerInfo deviceInfo : deviceInfos) {
            devices.put(createScannerDevice(deviceInfo));
        }
        callbackContext.success(devices);
    }

    private void getActiveDevicesAction(CallbackContext callbackContext) throws JSONException {
        List<DCSScannerInfo> deviceInfos = new ArrayList<DCSScannerInfo>();
        sdkHandler.dcssdkGetActiveScannersList(deviceInfos);

        JSONArray devices = new JSONArray();
        for (DCSScannerInfo deviceInfo : deviceInfos) {
            devices.put(createScannerDevice(deviceInfo));
        }
        callbackContext.success(devices);
    }

    private void getPairingBarcodeAction(CallbackContext callbackContext) {
        String barcode = getSnapiBarcode();
        callbackContext.success(barcode);
    }

    private void connectAction(JSONArray params, CallbackContext callbackContext) throws JSONException {
        JSONObject param = params.optJSONObject(0);
        if (param == null) {
            callbackContext.error("Missing parameters");
            return;
        }
        int deviceId = param.optInt("deviceId");
        if (deviceId == 0) {
            callbackContext.error("Invalid parameter - deviceId");
            return;
        }

        DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkEstablishCommunicationSession(deviceId);

        if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) {
            connectionCallBack = callbackContext;

            JSONObject device = new JSONObject();
            device.put("id", deviceId);

            PluginResult message = createStatusMessage("connected", "device", device, true);
            connectionCallBack.sendPluginResult(message);
        }
        else {
            if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SCANNER_NOT_AVAILABLE)
                callbackContext.error("Scanner is not available.");
            else if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SCANNER_ALREADY_ACTIVE)
                callbackContext.error("Already connected to a scanner.");
            else
                callbackContext.error("Unable to connect to a scanner.");
            Log.d(TAG, "Connection to scanner " + deviceId + " failed.");
        }
    }

    private void disconnectAction(JSONArray params, CallbackContext callbackContext) {
        JSONObject param = params.optJSONObject(0);
        if (param == null) {
            callbackContext.error("Missing parameter");
            return;
        }
        int deviceId = param.optInt("deviceId");
        if (deviceId == 0) {
            callbackContext.error("Invalid parameter - deviceId");
            return;
        }

        DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkTerminateCommunicationSession(deviceId);

        if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) {
            callbackContext.success("ok");
        }
        else {
            if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SCANNER_NOT_AVAILABLE)
                callbackContext.error("Scanner is not available.");
            else if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SCANNER_NOT_ACTIVE)
                callbackContext.error("Never connected to a scanner.");
            else
                callbackContext.error("Unable to disconnect from a scanner.");
            Log.d(TAG, "Connection to scanner " + deviceId + " failed.");
        }
    }

    private void subscribeAction(CallbackContext callbackContext) {
        if (connectionCallBack == null) {
            callbackContext.error("No connected scanner");
            return;
        }

        subscriptionCallback = callbackContext;
    }

    private void unsubscribeAction(CallbackContext callbackContext) {
        if (subscriptionCallback == null) {
            callbackContext.error("No active subscription");
            return;
        }

        subscriptionCallback = null;
        callbackContext.success("ok");
    }

    private void getBatteryStatsAction(JSONArray params, CallbackContext callbackContext) {
        JSONObject param = params.optJSONObject(0);
        if (param == null) {
            callbackContext.error("Missing parameters");
            return;
        }
        int deviceId = param.optInt("deviceId");
        if (deviceId == 0) {
            callbackContext.error("Invalid parameter - deviceId");
            return;
        }
        String in_xml = "<inArgs><scannerID>" + deviceId + " </scannerID><cmdArgs><arg-xml><attrib_list>";
            in_xml+=RMD_ATTR_BAT_MANUFACTURE_DATE;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_SERIAL_NUMBER;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_MODEL_NUMBER;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_FIRMWARE_VERSION;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_DESIGN_CAPACITY;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_STATE_OF_HEALTH_METER;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_CHARGE_CYCLES_CONSUMED;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_FULL_CHARGE_CAP;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_STATE_OF_CHARGE;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_REMAINING_CAP;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_CHARGE_STATUS;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_REMAINING_TIME_TO_COMPLETE_CHARGING;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_VOLTAGE;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_CURRENT;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_TEMP_PRESENT;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_TEMP_HIGHEST;
            in_xml+=",";
            in_xml+=RMD_ATTR_BAT_TEMP_LOWEST;

            in_xml += "</attrib_list></arg-xml></cmdArgs></inArgs>";

        new BatteryStatsAsyncTask(deviceId, DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_RSM_ATTR_GET, callbackContext).execute(new String[]{in_xml});
    }

    /**
     * Avoid blocking UI thread by using an async task for requesting battery statistics
     * 
     * Based on Zebra ScannerControl app
     */
    private class BatteryStatsAsyncTask extends AsyncTask<String,Integer,Boolean> {
        int scannerId;
        DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode;
        CallbackContext callbackContext;
        public BatteryStatsAsyncTask(int scannerId,  DCSSDKDefs.DCSSDK_COMMAND_OPCODE opcode, CallbackContext callbackContext){
            this.scannerId=scannerId;
            this.opcode=opcode;
            this.callbackContext=callbackContext;
        }
        @Override
        protected Boolean doInBackground(String... strings) {
            StringBuilder sb = new StringBuilder();
            DCSSDKDefs.DCSSDK_RESULT result = sdkHandler.dcssdkExecuteCommandOpCodeInXMLForScanner(opcode, strings[0], sb);
            if (opcode == DCSSDKDefs.DCSSDK_COMMAND_OPCODE.DCSSDK_RSM_ATTR_GET) {
                if (result == DCSSDKDefs.DCSSDK_RESULT.DCSSDK_RESULT_SUCCESS) {
                    try {
                        Log.i(TAG, sb.toString());
                        int i = 0;
                        int attrId = -1;
                        XmlPullParser parser = Xml.newPullParser();
                        
                        JSONObject device = new JSONObject();
                        parser.setInput(new StringReader(sb.toString()));
                        int event = parser.getEventType();
                        String text = null;
                        while (event != XmlPullParser.END_DOCUMENT) {
                            String name = parser.getName();
                            switch (event) {
                                case XmlPullParser.START_TAG:
                                    break;
                                case XmlPullParser.TEXT:
                                    text = parser.getText();
                                    break;
                                case XmlPullParser.END_TAG:
                                    // Log.i(TAG, "Name of the end tag: " + name);
                                    if (name.equals("id")) {
                                        if (text != null) {
                                            attrId = Integer.parseInt(text.trim());
                                        }
                                        // Log.i(TAG, "ID tag found: ID: " + attrId);
                                    } else if (name.equals("value")) {
                                        if (text != null) {
                                            final String attrVal = text.trim();
                                            Log.i(TAG, "ID tag : " + attrId + ", Value tag found: Value: " + attrVal);
                                            if (RMD_ATTR_BAT_MANUFACTURE_DATE == attrId) {
                                                device.put("batteryManufactureDate", attrVal);
                                            } else if (RMD_ATTR_BAT_SERIAL_NUMBER == attrId) {
                                                device.put("batterySerialNumber", attrVal);
                                            } else if (RMD_ATTR_BAT_MODEL_NUMBER == attrId) {
                                                device.put("batteryModelNumber", attrVal);
                                            } else if (RMD_ATTR_BAT_DESIGN_CAPACITY == attrId) {
                                                device.put("batteryDesignCapacity", attrVal + " mAh");
                                            } else if (RMD_ATTR_BAT_STATE_OF_HEALTH_METER == attrId) {
                                                device.put("batteryStateOfHealthMeter", Double.parseDouble(attrVal) / 100);
                                            } else if (RMD_ATTR_BAT_CHARGE_CYCLES_CONSUMED == attrId) {
                                                device.put("batteryChargeCyclesConsumed", Integer.parseInt(attrVal));
                                            } else if (RMD_ATTR_BAT_FULL_CHARGE_CAP == attrId) {
                                                device.put("batteryFullChargeCapacity", attrVal + " mAh");
                                            } else if (RMD_ATTR_BAT_STATE_OF_CHARGE == attrId) {
                                                device.put("batteryStateOfCharge", Double.parseDouble(attrVal) / 100);
                                            } else if (RMD_ATTR_BAT_REMAINING_CAP == attrId) {
                                                device.put("batteryRemainingCapacity", attrVal + " mAh");
                                            } else if (RMD_ATTR_BAT_TEMP_PRESENT == attrId) {
                                                device.put("batteryTemperaturePresent", Integer.parseInt(attrVal));
                                            } else if (RMD_ATTR_BAT_TEMP_HIGHEST == attrId) {
                                                device.put("batteryTemperatureHighest", Integer.parseInt(attrVal));
                                            } else if (RMD_ATTR_BAT_TEMP_LOWEST == attrId) {
                                                device.put("batteryTemperatureLowest", Integer.parseInt(attrVal));
                                            }
                                        }
                                    }
                                    break;
                            }
                            event = parser.next();
                        }
                        PluginResult message = new PluginResult(PluginResult.Status.OK, device);
                        this.callbackContext.sendPluginResult(message);
                    } catch (Exception e) {
                        Log.e(TAG, e.toString());
                        this.callbackContext.error(e.toString());
                    }
                    return true;
                }
                return false;
            }
            return false;
        }
    }

    public void notifyDeviceFound(DCSScannerInfo deviceInfo) throws JSONException {
        if (scanCallBack == null)
            return;

        JSONObject device = createScannerDevice(deviceInfo);

        PluginResult message = createStatusMessage("deviceFound","device", device, true);
        scanCallBack.sendPluginResult(message);
    }

    public void notifyDeviceLost(int deviceId) throws JSONException {
        if (scanCallBack == null)
            return;

        JSONObject device = new JSONObject();
        device.put("id", deviceId);

        PluginResult message = createStatusMessage("deviceLost","device", device, true);
        scanCallBack.sendPluginResult(message);
    }

    // public void notifyDeviceConnected(DCSScannerInfo deviceInfo) throws JSONException {
    //     if (connectionCallBack == null)
    //         return;
    // }

    public void notifyDeviceDisconnected(int deviceId) throws JSONException {
        if (connectionCallBack == null)
            return;

        // JSONObject device = new JSONObject();
        // device.put("id", deviceId);

        connectionCallBack.error("Disconnected");
        connectionCallBack = null;
        subscriptionCallback = null;
    }

    public void notifyBarcodeReceived(String barcodeData, String barcodeType, int fromScannerId) throws JSONException {
        if (subscriptionCallback == null)
            return;

        JSONObject data = new JSONObject();
        data.put("deviceId", fromScannerId);

        JSONObject barcode = new JSONObject();
        barcode.put("type", barcodeType);
        barcode.put("data", barcodeData);
        data.put("barcode", barcode);

        PluginResult message = new PluginResult(PluginResult.Status.OK, data);
        message.setKeepCallback(true);
        subscriptionCallback.sendPluginResult(message);
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
		if(callbackContext != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    Log.d(TAG, "Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
					callbackContext.sendPluginResult(result);
                    return;
                }
            }
            result = new PluginResult(PluginResult.Status.OK);
            // Init SDK when permissions have been given
            initSdk();
			callbackContext.sendPluginResult(result);
        }
    }
    public boolean hasPermission(String[] permissions) {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
                return false;
            }
        }
        return true;
    }

    private PluginResult createStatusMessage(String status, boolean keepCallback) throws JSONException {
        return createStatusMessage(status, null, null, keepCallback);
    }

    private PluginResult createStatusMessage(String status, String dataKey, JSONObject data, boolean keepCallback)
            throws JSONException {
        JSONObject msgData = new JSONObject();
        msgData.put("status", status);

        if (dataKey != null && !dataKey.isEmpty() && data != null) {
            msgData.put(dataKey, data);
        }

        PluginResult message = new PluginResult(PluginResult.Status.OK, msgData);
        message.setKeepCallback(keepCallback);
        return message;
    }

    private JSONObject createScannerDevice(DCSScannerInfo scanner) throws JSONException {
        JSONObject device = new JSONObject();
        device.put("id", scanner.getScannerID());
        device.put("name", scanner.getScannerName());
        device.put("model", scanner.getScannerModel());
        device.put("serialNumber", scanner.getScannerHWSerialNumber());
        device.put("connectionType", scanner.getConnectionType());
        return device;
    }

    private String getSnapiBarcode() {
        BarCodeView barCodeView = sdkHandler.dcssdkGetUSBSNAPIWithImagingBarcode();
        barCodeView.setSize(500, 100);
        return base64Encode(getBitmapFromView(barCodeView), Bitmap.CompressFormat.JPEG, 100);
    }

    // Convert native view to bitmap
    private Bitmap getBitmapFromView(BarCodeView view) {
        Bitmap converted = Bitmap.createBitmap(500, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(converted);
        Drawable bgDrawable = view.getBackground();

        if (bgDrawable != null) {
            bgDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }

        view.draw(canvas);

        return converted;
    }

    private String base64Encode(Bitmap image, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        image.compress(format, quality, byteArrayStream);
        return Base64.encodeToString(byteArrayStream.toByteArray(), Base64.DEFAULT);
    }
}
