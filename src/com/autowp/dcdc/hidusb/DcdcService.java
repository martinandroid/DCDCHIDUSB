package com.autowp.dcdc.hidusb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.autowp.dcdc.MainActivity;
import com.autowp.dcdc.R;

import android.app.Notification;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.IBinder;

public class DcdcService extends Service {
    
    private NotificationManager mNM;
    
    public static int NOTIFICATION = R.string.local_service_started;
    
    public static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    
    IBinder mBinder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public DcdcService getServerInstance() {
            return DcdcService.this;
        }
    }
    
    protected boolean monitoringEnabled = false;
    protected MonitoringThread monitoringThread;
    
    protected int interval = 500; // milliseconds
    
    private List<ConnectedEventListener> connectedListeners = new ArrayList<ConnectedEventListener>();
    
    private List<DisconnectedEventListener> disconnectedListeners = new ArrayList<DisconnectedEventListener>();
    
    private List<StatusEventListener> statusListeners = new ArrayList<StatusEventListener>();
    
    protected HIDUSB mDCDC;
    private UsbManager mUsbManager;

    private Builder mBuilder;
    
    public final static String EXTRA_DEVICE_ID = "deviceId";

    @Override
    public IBinder onBind(Intent intent) {
        System.out.println("onBind");
        return mBinder;
    }
    
    public void onCreate() {
        super.onCreate();
        System.out.println("Service.onCreate");
        
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        
        // The PendingIntent to launch our activity if the user selects this notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);
        
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbPermissionReceiver, filter);
        
        IntentFilter attachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbAttachedReceiver, attachedFilter);
        
        IntentFilter detachedFilter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(mUsbDetachedReceiver, detachedFilter);
        
        mBuilder = new Notification.Builder(this)
            .setContentTitle("DCDC")
            .setContentText("Service started")
            .setSmallIcon(R.drawable.ic_launcher) 
            .setContentIntent(contentIntent);
        
        showNotification();
        
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        
        try {
            UsbDevice device = findDevice();
            if (device != null) {
                setDevice(device);
            }
        } catch (HIDUSBException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void onDestroy() {
        super.onDestroy();
        System.out.println("Service.onDestroy");
        
        unregisterReceiver(mUsbPermissionReceiver);
        unregisterReceiver(mUsbAttachedReceiver);
        unregisterReceiver(mUsbDetachedReceiver);
        
        mNM.cancel(NOTIFICATION);
        
        disableMonitoring();
        
        if (mDCDC != null) {
            mDCDC.resetHIDDevice();
            mDCDC = null;
        }
    }
    
    public boolean isConnected()
    {
        return mDCDC != null;
    }
    
    private void showNotification() {
        mNM.notify(NOTIFICATION, mBuilder.build());
    }
    
    public void setDevice(UsbDevice device) throws HIDUSBException
    {
        if (!MiniBox.isMinibox(device)) {
            throw new HIDUSBException("Device is not MiniBox");
        }
        
        System.out.println("setDevice " + device.getDeviceName());
        
        if (!mUsbManager.hasPermission(device)) {
            System.out.println("requestPermission " + device.getDeviceName());
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            mUsbManager.requestPermission(device, mPermissionIntent);
        } else {
            System.out.println("hasPermission " + device.getDeviceName());
            mDCDC = new MiniBox(mUsbManager, device);
            
            mBuilder.setContentText("Connected to " + device.getDeviceName());
            
            fireConnectedEvent();
            
            enableMonitoring();
        }
    }
    
    public void setDeviceById(int deviceId) throws HIDUSBException
    {
        UsbDevice device = findDeviceById(deviceId);
        setDevice(device);
    }
    
    public UsbDevice getDevice()
    {
        return mDCDC == null ? null : mDCDC.getHIDDevice();
    }
    
    private UsbDevice findDevice()
    {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (MiniBox.isMinibox(device)) {
                return device;
            }
        }
        
        return null;
    }
    
    private UsbDevice findDeviceById(int deviceId)
    {
        HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            UsbDevice device = deviceIterator.next();
            if (MiniBox.isMinibox(device) && device.getDeviceId() == deviceId) {
                return device;
            }
        }
        
        return null;
    }
    
    public void resetDevice()
    {
        if (mDCDC != null) {
            disableMonitoring();
            mDCDC.resetHIDDevice();
            mDCDC = null;
        }
        
        mBuilder.setContentText("Disconnected");
        showNotification();
        
        fireDisconnectedEvent();
    }
    
    public boolean isMonitoringEnabled()
    {
        return monitoringEnabled;
    }
    
    private void enableMonitoring()
    {
        if (!monitoringEnabled && monitoringThread == null) {
            monitoringEnabled = true;
            monitoringThread = new MonitoringThread();
            monitoringThread.start();
        }
    }
    
    private void disableMonitoring()
    {
        if (monitoringEnabled && monitoringThread != null && !monitoringThread.isInterrupted()) {
            monitoringThread.interrupt();
            monitoringThread = null;
        }
    }
    
    private class MonitoringThread extends Thread 
    {
        @Override
        public void run() {
            
            boolean mInterruptedWithError = false;
            String errorMessage = null;
            
            while (!isInterrupted()) {
                
                try {
                    if (mDCDC == null) {
                        throw new HIDUSBException("Device is not connected");
                    }
                    
                    ResponseStatus status = mDCDC.getStatus();
                    fireStatusEvent(status);
                    
                    
                    mBuilder.setContentText(String.format(getString(R.string.notification_voltage), status.getInputVoltage(), status.getOutputVoltage()));
                    showNotification();
                    
                    Thread.sleep(interval);
                    
                } catch (HIDUSBException e) {
                    mInterruptedWithError = true;
                    errorMessage = e.getMessage();
                    break;
                    //e.printStackTrace();
                } catch (IOException e) {
                    mInterruptedWithError = true;
                    errorMessage = e.getMessage();
                    break;
                    //e.printStackTrace();
                } catch (InterruptedException e) {
                    break;
                }
                
            }
            
            monitoringEnabled = false;
            
            if (mInterruptedWithError) {
                mBuilder.setContentText("Error monitoring: " + errorMessage);
                showNotification();
            }
        }
    }
    
    private synchronized void fireStatusEvent(ResponseStatus status)
    {
        StatusEvent event = new StatusEvent(this, status);
        Iterator<StatusEventListener> i = statusListeners.iterator();
        while(i.hasNext())  {
            ((StatusEventListener) i.next()).handleStatusEvent(event);
        }
    }
    
    public void addEventListener(StatusEventListener listener) {
        statusListeners.add(listener);
    }
    
    public void removeEventListener(StatusEventListener listener)   
    {
        statusListeners.remove(listener);
    }
    
    private synchronized void fireConnectedEvent()
    {
        System.out.println("fireConnectedEvent for " + connectedListeners.size() + " handlers");
        
        ConnectedEvent event = new ConnectedEvent(this);
        Iterator<ConnectedEventListener> i = connectedListeners.iterator();
        while(i.hasNext())  {
            ((ConnectedEventListener) i.next()).handleConnectedEvent(event);
        }
    }
    
    public void addEventListener(ConnectedEventListener listener) {
        connectedListeners.add(listener);
    }
    
    public void removeEventListener(ConnectedEventListener listener)   
    {
        connectedListeners.remove(listener);
    }
    
    private synchronized void fireDisconnectedEvent()
    {
        System.out.println("fireDisconnectedEvent for " + disconnectedListeners.size() + " handlers");
        
        DisconnectedEvent event = new DisconnectedEvent(this);
        Iterator<DisconnectedEventListener> i = disconnectedListeners.iterator();
        while(i.hasNext())  {
            ((DisconnectedEventListener) i.next()).handleDisconnectedEvent(event);
        }
    }
    
    public void addEventListener(DisconnectedEventListener listener) {
        disconnectedListeners.add(listener);
    }
    
    public void removeEventListener(DisconnectedEventListener listener)   
    {
        disconnectedListeners.remove(listener);
    }

    public Notification getNotification() {
        return mBuilder.build();
    }
    
    private final BroadcastReceiver mUsbPermissionReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            System.out.println("Persmission braodcast");
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null){
                            System.out.println("Permissions granted for device " + device.getDeviceName());
                            
                            try {
                                setDevice(device);
                            } catch (HIDUSBException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    } else {
                        System.out.println("permission denied for device " + device.getDeviceName());
                    } 
                }
            }
        }
    };
    
    protected boolean isCurrentDevice(UsbDevice device)
    {
        UsbDevice currentDevice = getDevice();
        if (currentDevice != null) {
            return currentDevice.getDeviceId() == device.getDeviceId();
        }
        
        return false;
    }
    
    private final BroadcastReceiver mUsbAttachedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                
                System.out.println("BroadcastReceiver USB Connected");
                System.out.println(device);
                
                if (MiniBox.isMinibox(device) && !isCurrentDevice(device)) {
                    try {
                        setDevice(device);
                    } catch (HIDUSBException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

            }
        }
    };
    
    private final BroadcastReceiver mUsbDetachedReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                
                UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                
                if (isCurrentDevice(device)) {
                    System.out.println("BroadcastReceiver USB Disconnected");
                    System.out.println(device);
                    
                    resetDevice();
                }
            }
        }
    };
}
