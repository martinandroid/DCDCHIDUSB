package com.autowp.dcdc.hidusb;

import java.io.IOException;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;

public abstract class HIDUSB {
    
    protected UsbManager mUsbManager;
    
    protected UsbDevice mDevice;
    
    public HIDUSB(UsbManager manager, UsbDevice device) throws HIDUSBException {
        mUsbManager = manager;
        setHIDDevice(device);
    }

    abstract public int sendCommand(byte cmd, byte val) throws IOException;
    
    abstract public int send(byte[] data) throws IOException;
    
    abstract public int recv(byte[] data, int timeout);
    
    abstract public ResponseStatus getStatus() throws HIDUSBException, IOException;
    
    abstract public void resetHIDDevice();
    
    abstract public boolean setHIDDevice(UsbDevice device) throws HIDUSBException;
    
    public UsbDevice getHIDDevice()
    {
        return mDevice;
    }
}
