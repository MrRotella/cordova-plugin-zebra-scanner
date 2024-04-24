const channel = require('cordova/channel');

const serviceName = 'ZebraScanner'

const zebraScanner = {
  // NOTE: Zebra has multiple bugs in their SDK so these 2 methods does not need to be used.

  // SDK will start scanning for device immediately after sdkHandler has been created.
  // That means the scanning will start after calling any of these methods.
  startScan(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  // The scan will be restarted by itself even after stopping it.
  // There is no way to stop scanning for devices unless a device is connected or the plugin is destroyed.
  stopScan(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  getAvailableDevices(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  getActiveDevices(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  getPairingBarcode(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  connect(successCallback, errorCallback, params) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, [params]);
  },
  disconnect(successCallback, errorCallback, params) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, [params]);
  },
  subscribe(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  unsubscribe(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  getBatteryStats(successCallback, errorCallback, params) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, [params]);
  },
  isConnected(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
  getAntennaInfo(successCallback, errorCallback) {
    cordova.exec(successCallback, errorCallback, serviceName, arguments.callee.name, []);
  },
}

// Init Zebra SDK when Cordova is ready
channel.onCordovaReady.subscribe(function () {
  cordova.exec((f) => console.log('zebraScanner: init Zebra Scanner SDK', f), (g) => console.warn('zebraScanner: init: error ', g), 'ZebraScanner', 'init', []);
});
module.exports = zebraScanner
