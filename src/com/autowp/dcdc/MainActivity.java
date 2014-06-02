package com.autowp.dcdc;

import java.util.HashMap;
import java.util.Iterator;

import com.autowp.dcdc.hidusb.HIDUSB;
import com.autowp.dcdc.hidusb.ResponseStatus;
import com.autowp.dcdc.hidusb.StatusEvent;
import com.autowp.dcdc.hidusb.StatusEventClassListener;
import com.autowp.hidusb.dcdc.R;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;

public class MainActivity extends Activity {
    
    public static final String TAG = "dcdc";
    
    HIDUSB dcdc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        Switch switchService = (Switch) findViewById(R.id.switchService);
        
        final EditText outputVoltageText = (EditText) findViewById(R.id.outputVoltageEditText);
        final EditText inputVoltageText = (EditText) findViewById(R.id.inputVoltageEditText);
        final EditText modeText = (EditText) findViewById(R.id.modeEditText);
        
        switchService.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                    boolean isChecked) {
                
                if (isChecked) {
                    if (dcdc == null) {
                        UsbManager mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                        UsbDevice device = findDevice();
                        
                        dcdc = new HIDUSB(mManager, device);
                        dcdc.addEventListener(new StatusEventClassListener() {
                            @Override
                            public void handleStatusEventClassEvent(StatusEvent e) {
                                final ResponseStatus status = e.getStatus();
                                
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        
                                        String outputVoltage = String.format("%.2f", status.getOutputVoltage());
                                        String inputVoltage = String.format("%.2f", status.getInputVoltage());
                                        
                                        if (!status.getOutputEnable()) {
                                            outputVoltage += " (disabled)";
                                        }
                                        
                                        outputVoltageText.setText(outputVoltage);
                                        inputVoltageText.setText(inputVoltage);
                                        
                                        String modeStr = "";
                                        switch(status.getMode()) {
                                            case ResponseStatus.MODE_DUMB: modeStr = "mode: 0 (dumb)"; break;
                                            case ResponseStatus.MODE_AUTOMOTIVE: modeStr = "mode: 1 (automotive)"; break;
                                            case ResponseStatus.MODE_SCRIPT: modeStr = "mode: 2 (script)"; break;
                                            case ResponseStatus.MODE_UPS: modeStr = "mode: 3 (ups)"; break;
                                            default:
                                                modeStr = "Unknown mode";
                                                
                                        }
                                        modeText.setText(modeStr);
                                    }
                                });
                                
                            }
                            
                        });
                    }
                    dcdc.enableMonitoring();
                } else {
                    if (dcdc != null) {
                        dcdc.disableMonitoring();
                    }
                }
                
            }
        });
    }
    
    public UsbDevice findDevice()
    {
        UsbManager mManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = mManager.getDeviceList();
        Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();

        while (deviceIterator.hasNext()) {
            
            UsbDevice device = deviceIterator.next();
            
            if (device.getVendorId() != HIDUSB.VID) {
                continue;
            }
            
            if (device.getProductId() != HIDUSB.PID) {
                continue;
            }
            
            Log.i(TAG, device.toString());
            
            return device;
        }
        Log.i(TAG, "No more devices connected.");
        
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

}
