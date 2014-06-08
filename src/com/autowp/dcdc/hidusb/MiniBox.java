package com.autowp.dcdc.hidusb;

import java.io.IOException;
import java.util.Arrays;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;

public class MiniBox extends HIDUSB {
    
    protected final byte GET_ALL_VALUES  = (byte) 0x81;
    protected final byte RECV_ALL_VALUES = (byte) 0x82;
    protected final byte CMD_OUT         = (byte) 0xB1;
    protected final byte CMD_IN          = (byte) 0xB2;
    protected final byte MEM_READ_OUT    = (byte) 0xA1;
    protected final byte MEM_READ_IN     = (byte) 0xA2;
    protected final byte MEM_WRITE_OUT   = (byte) 0xA3;
    protected final byte MEM_WRITE_IN    = (byte) 0xA4;
    protected final byte MEM_ERASE       = (byte) 0xA5;
    
    protected final byte CMD_SET_AUX_WIN         = (byte) 0x01;
    protected final byte CMD_SET_PW_SWITCH       = (byte) 0x02;
    protected final byte CMD_SET_OUTPUT          = (byte) 0x03;
    protected final byte CMD_WRITE_VOUT          = (byte) 0x06;
    protected final byte CMD_READ_VOUT           = (byte) 0x07;
    protected final byte CMD_INC_VOUT            = (byte) 0x0C;
    protected final byte CMD_DEC_VOUT            = (byte) 0x0D;
    protected final byte CMD_LOAD_DEFAULTS       = (byte) 0x0E;
    protected final byte CMD_SCRIPT_START        = (byte) 0x10;
    protected final byte CMD_SCRIPT_STOP         = (byte) 0x11;
    protected final byte CMD_SLEEP               = (byte) 0x12;
    protected final byte CMD_READ_REGULATOR_STEP = (byte) 0x13;
    
    protected final int MAX_TRANSFER_SIZE = 24;
    
    protected UsbInterface mUsbInterface;
    protected UsbDeviceConnection mConnection;
    
    protected UsbEndpoint mEndpointRead;
    protected UsbEndpoint mEndpointWrite;

    public MiniBox(UsbManager manager, UsbDevice device) throws HIDUSBException {
        super(manager, device);
    }

    public static final int VID = 0x04d8;
    public static final int PID = 0xd003;
    
    public synchronized int send(byte[] data) throws IOException
    {
        return mConnection.bulkTransfer(mEndpointWrite, data, data.length, 1000);
    }
    
    public synchronized int recv(byte[] data, int timeout)
    {
        return mConnection.bulkTransfer(mEndpointRead, data, data.length, 1000);
    }
    
    public synchronized int sendCommand(byte cmd, byte val) throws IOException
    {
        byte[] c = new byte[3];
        c[0] = CMD_OUT;
        c[1] = cmd;
        c[2] = val;
        
        return send(c);
    }
    
    public ResponseStatus getStatus() throws HIDUSBException, IOException
    {
        byte[] c = new byte[2];
        
        c[0] = GET_ALL_VALUES;
        c[1] = 0;
        
        if (send(c) < 0) {
            throw new HIDUSBException("Cannot send data to device");
        }
        
        byte[] buf = new byte[MAX_TRANSFER_SIZE];
        int bytesRcvd = recv(buf, 1000);
        
        if (bytesRcvd < 0) {
            throw new HIDUSBException("Cannot get device status");
        }
        
        byte[] data = Arrays.copyOfRange(buf, 0, bytesRcvd);
        
        return new ResponseStatus(data);
    }
    
    /*public double getVOut() throws Exception
    {
        System.out.println("getVOut");
        if (sendCommand(CMD_READ_VOUT, (byte) 0) < 0) {
            throw new Exception("Cannot send command");
        }
     
        byte[] buf = new byte[MAX_TRANSFER_SIZE];
        recv(buf, 1000);
     
        return 0.0;
    }*/
    
    public void resetHIDDevice()
    {
        mDevice = null;
        
        if (mConnection != null) {
            if (mUsbInterface != null) {
                mConnection.releaseInterface(mUsbInterface);
                mUsbInterface = null;
            }
            mConnection.close();
            mConnection = null;
        }
    }
    
    public boolean setHIDDevice(UsbDevice device) throws HIDUSBException
    {   
        if (!isMinibox(device)) {
            throw new HIDUSBException("device is not minibox");
        }
        
        System.out.println("setHIDDevice");
        System.out.println(device.toString());
        resetHIDDevice();
        
        UsbInterface usbInterface = device.getInterface(0);
        
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                System.out.println("Endpoint");
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    System.out.println("Endpoint read");
                    mEndpointRead = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    System.out.println("Endpoint write");
                    mEndpointWrite = ep;
                }
            }
        }

        //check that we should be able to read and write
        if (mEndpointRead == null) {
            throw new HIDUSBException("Read EP not found");
        }
        
        if (mEndpointWrite == null) {
            throw new HIDUSBException("Write EP not found");
        }
        
        UsbDeviceConnection connection = mUsbManager.openDevice(device);
        if (connection != null) {
            
            if (connection.claimInterface(usbInterface, true)) {
                mConnection = connection;
                mUsbInterface = usbInterface;
            } else {
                connection.close();
                connection = null;
            }

        } else {
            mConnection = null;
        }
        
        mDevice = device;
        
        return true;
    }
    
    public static boolean isMinibox(UsbDevice device)
    {
        return (device.getVendorId() == VID) && 
               (device.getProductId() == PID) &&
               (device.getInterfaceCount() == 1);
    }

}
