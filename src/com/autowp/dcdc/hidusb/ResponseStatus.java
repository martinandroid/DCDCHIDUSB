package com.autowp.dcdc.hidusb;

import org.apache.commons.codec.binary.BinaryCodec;

public class ResponseStatus {
    
    protected int mode;
    
    protected int timeConfig;
    protected int voltageConfig;
    
    protected int state;
    
    protected double inputVoltage;
    protected double ignitionVoltage;
    protected double outputVoltage;
    
    protected boolean powerSwitch;
    protected boolean outputEnable;
    protected boolean auxVinEnable;
    
    protected byte statusFlag1;
    protected byte statusFlag2;
    
    protected byte voltageFlags;
    protected byte timerFlags;
    
    protected int flashPointer;
    
    protected int timerWait;
    protected int timerVout;
    protected int timerVaux;
    protected int timerPwSwitch;
    protected int timerOffDelay;
    protected int timerHardOff;
    
    protected int versionMajor;
    protected int versionMinor;
    
    public static final int MODE_DUMB = 0;
    public static final int MODE_AUTOMOTIVE = 1;
    public static final int MODE_SCRIPT = 2;
    public static final int MODE_UPS = 3;
    
    public ResponseStatus(byte[] data) throws HIDUSBException
    {
        byte firstByte = data[1];
        byte statusByte = data[6];
        
        mode = (firstByte & 0x03);
        timeConfig = ((firstByte >> 5) & 0x07);
        voltageConfig = ((firstByte >> 2) & 0x07);
        state = data[2];
        inputVoltage = (float) data[3] * 0.1558f;
        ignitionVoltage = (float) data[4] * 0.1558f;
        outputVoltage = (float) data[5] * 0.1170f;
        
        // Check
        switch(mode)
        {
            case MODE_DUMB: 
            case MODE_AUTOMOTIVE: 
            case MODE_SCRIPT: 
            case MODE_UPS: 
                break;
            default:
                throw new HIDUSBException("Unexpected mode");
        }
        
        powerSwitch = (statusByte & 0x04) != 0;
        outputEnable = (statusByte & 0x08) != 0;
        auxVinEnable = (statusByte & 0x10) != 0;
        
        statusFlag1 = data[6];
        statusFlag2 = data[7];
        
        voltageFlags = data[8];
        timerFlags = data[9];
        
        flashPointer = data[10];
        
        versionMajor = ((data[23] >> 5) & 0x07);
        versionMinor = (data[23] & 0x1F);
        
        timerWait = (data[11] << 8) | data[12];
        timerVout = (data[13] << 8) | data[14];
        timerVaux = (data[15] << 8) | data[16];
        timerPwSwitch = (data[17] << 8) | data[18];
        timerOffDelay = (data[19] << 8) | data[20];
        timerHardOff = (data[21] << 8) | data[22];
    }
    
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        
        switch(mode) {
            case MODE_DUMB: s.append("mode: 0 (dumb)"); break;
            case MODE_AUTOMOTIVE: s.append("mode: 1 (automotive)"); break;
            case MODE_SCRIPT: s.append("mode: 2 (script)"); break;
            case MODE_UPS: s.append("mode: 3 (ups)");break;
            default:
                s.append("Unknown mode");
                s.append(mode);
        }
        s.append("\n");
        
        s.append(String.format("time config: %d\n", timeConfig));
        s.append(String.format("voltage config: %d\n", voltageConfig));
        s.append(String.format("state: %d\n", state));
        
        s.append(String.format("input voltage: %.2f\n", inputVoltage));
        s.append(String.format("ignition voltage: %.2f\n", ignitionVoltage));
        s.append(String.format("output voltage: %2f\n", outputVoltage));
        
        s.append(String.format("power switch: %s\n", powerSwitch ? "On" : "Off"));
        s.append(String.format("output enable: %s\n", outputEnable ? "On" : "Off"));
        s.append(String.format("aux vin enable %s\n", auxVinEnable ? "On" : "Off"));
        
        s.append(String.format("status flags 1: %s\n", byteToStr(statusFlag1)));
        s.append(String.format("status flags 2: %s\n", byteToStr(statusFlag2)));
        
        s.append(String.format("voltage flags: %s\n", byteToStr(voltageFlags)));
        s.append(String.format("timer flags: %s\n", byteToStr(timerFlags)));
        
        s.append(String.format("flash pointer: %d\n", flashPointer));
        
        s.append(String.format("version: %d.%d\n", versionMajor, versionMinor));
        
        s.append(String.format("timer wait: %d\n", timerWait));
        s.append(String.format("timer vout: %d\n", timerVout));
        s.append(String.format("timer vaux: %d\n",timerVaux));
        s.append(String.format("timer pw switch: %d\n", timerPwSwitch));
        s.append(String.format("timer off delay: %d\n", timerOffDelay));
        s.append(String.format("timer hard off: %d", timerHardOff));
        
        return s.toString();
    }
    
    public String byteToStr(byte b)
    {
        byte[] c = new byte[1];
        c[0] = b;
        
        return BinaryCodec.toAsciiString(c);
    }
    
    public double getOutputVoltage()
    {
        return outputVoltage;
        
    }

    public double getInputVoltage() {
        return inputVoltage;
    }
    
    public boolean getOutputEnable()
    {
        return outputEnable;
    }
    
    public int getMode()
    {
        return mode;
    }
}
