package com.autowp.dcdc.hidusb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.binary.Hex;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;

public class HIDUSB {
    
    protected boolean monitoringEnabled = false;
    protected Thread monitoringThread;
    
    protected UsbManager mUsbManager;
    protected UsbDeviceConnection mConnection;
    protected UsbEndpoint mEndpointRead;
    protected UsbEndpoint mEndpointWrite;
    
    protected final String ManufacturerString = "MINI-BOX.COM";
    protected final String ProductString = "DCDC-USB";
    
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
    
    public static final int VID = 0x04d8;
    public static final int PID = 0xd003;
    
    protected final int USB_ENDPOINT_IN  = 0x80;
    protected final int USB_ENDPOINT_OUT = 0x00;
    
    public static final String TAG = "dcdc";
    
    private List<StatusEventClassListener> statusListeners = new ArrayList<StatusEventClassListener>();
                 
    public HIDUSB(UsbManager manager, UsbDevice device) {
        mUsbManager = manager;
        setHIDDevice(device);
    }

    public int sendCommand(byte cmd, byte val) throws IOException
    {
        byte[] c = new byte[3];
        c[0] = CMD_OUT;
        c[1] = cmd;
        c[2] = val;
        
        return send(c);
    }
    
    public int send(byte[] data) throws IOException
    {
        return mConnection.bulkTransfer(mEndpointWrite, data, data.length, 1000);
    }
    
    public int recv(byte[] data, int timeout)
    {
        return mConnection.bulkTransfer(mEndpointRead, data, data.length, 1000);
    }
    
    public ResponseStatus getStatus() throws HIDUSBException, IOException
    {
        byte[] c = new byte[2];
        
        c[0] = GET_ALL_VALUES;
        c[1] = 0;
        
        if (send(c) < 0) {
            throw new HIDUSBException("Cannot send command to device");
        }
        
        byte[] buf = new byte[MAX_TRANSFER_SIZE];
        int bytesRcvd = recv(buf, 1000);
        
        if (bytesRcvd < 0) {
            throw new HIDUSBException("Cannot get device status");
        }
        
        byte[] data = Arrays.copyOfRange(buf, 0, bytesRcvd);
        
        Log.i(TAG, new String(Hex.encodeHex( data )));
        
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
    
    private boolean setHIDDevice(UsbDevice device)
    {    
        UsbInterface usbInterface = null;

        //This HID device is using a single interface
    
        usbInterface = device.getInterface(0);
        
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint ep = usbInterface.getEndpoint(i);
            if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_INT) {
                if (ep.getDirection() == UsbConstants.USB_DIR_IN) {
                    mEndpointRead = ep;
                } else if (ep.getDirection() == UsbConstants.USB_DIR_OUT) {
                    mEndpointWrite = ep;
                }
            }
        }

        //check that we should be able to read and write
        if ((mEndpointRead == null) || (mEndpointWrite == null)) {
            Log.e(TAG, "One EP is null");
            return false;
        }
        
        if (device != null) {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection != null && connection.claimInterface(usbInterface, true)) {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;

            } else {
                Log.d(TAG, "open FAIL");
                mConnection = null;
            }
        }
        mConnection.claimInterface(usbInterface, true);
        
        return true;
    }
    
    public void enableMonitoring()
    {
        monitoringEnabled = true;
        
        monitoringThread = new Thread(new MonitoringThread());
        monitoringThread.start();
    }
    
    public void disableMonitoring()
    {
        monitoringEnabled = false;
        monitoringThread.interrupt();
    }
    
    private class MonitoringThread extends Thread 
    {
        @Override
        public void run() {
            
            while (!this.isInterrupted()) {
                
                try {
                    fireStatusEvent(getStatus());
                    
                    Thread.sleep(1000);
                    
                } catch (HIDUSBException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    break;
                }
                
            }
        }
        
    }
    
    private synchronized void fireStatusEvent(ResponseStatus status)
    {
        StatusEvent event = new StatusEvent(this, status);
        Iterator<StatusEventClassListener> i = statusListeners.iterator();
        while(i.hasNext())  {
            ((StatusEventClassListener) i.next()).handleStatusEventClassEvent(event);
        }
    }
    
    public void addEventListener(StatusEventClassListener listener) {
        statusListeners.add(listener);
    }
    
    public void removeEventListener(StatusEventClassListener listener)   
    {
        statusListeners.remove(listener);
    }
}
