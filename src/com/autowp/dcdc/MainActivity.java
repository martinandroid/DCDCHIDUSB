package com.autowp.dcdc;

import com.autowp.dcdc.R;
import com.autowp.dcdc.hidusb.*;
import com.autowp.dcdc.hidusb.DcdcService.LocalBinder;

import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;

public class MainActivity extends Activity {
    
    private EditText mOutputVoltageText;

    private EditText mInputVoltageText;

    private EditText mModeText;

    private Switch mSwitchServiceEnabled;
    
    private boolean mBounded;
    
    private DcdcService mService;
    
    private Intent mServiceIntent;
    
    ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName name, IBinder service) {
            mBounded = true;
            LocalBinder mLocalBinder = (LocalBinder)service;
            mService = mLocalBinder.getServerInstance();
            try {
                dcdcServiceStarted();
            } catch (HIDUSBException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        public void onServiceDisconnected(ComponentName name) {
            toast("Service is disconnected");
            mBounded = false;
            mService = null;
        }
        
    };

    private boolean isServiceRunning(Class<?> serviceClass) 
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    
    private void bindDcdcService()
    {
        System.out.println("Bind to running service");
        
        bindService(mServiceIntent, mConnection, BIND_AUTO_CREATE);
    }
    
    private void startDcdcService()
    {
        System.out.println("Start service");
        
        startService(mServiceIntent);
        bindService(mServiceIntent, mConnection, BIND_AUTO_CREATE);
    }
    
    private void dcdcServiceStarted() throws HIDUSBException
    {
        mService.startForeground(DcdcService.NOTIFICATION, mService.getNotification());
        
        System.out.println("Service is connected");
        
        mService.addEventListener(mStatusEventListener);
        mService.addEventListener(mConnectedEventListener);
        mService.addEventListener(mDisconnectedEventListener);
        
        mOutputVoltageText.setEnabled(true);
        mInputVoltageText.setEnabled(true);
        mModeText.setEnabled(true);

    }
    
    private void stopDcdcService()
    {
        System.out.println("Stop service");
        
        mService.stopForeground(false);
        stopService(mServiceIntent);
        
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
            mService = null;
        }
    }
    
    private void dcdcServiceStopping()
    {
        mOutputVoltageText.setEnabled(false);
        mInputVoltageText.setEnabled(false);
        mModeText.setEnabled(false);
        
        if (mService != null) {
            mService.resetDevice();
            mService.removeEventListener(mStatusEventListener);
            mService.removeEventListener(mConnectedEventListener);
            mService.removeEventListener(mDisconnectedEventListener);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        boolean isServiceRunning = isServiceRunning(DcdcService.class);
        
        if (isServiceRunning) {
            Intent intent = getIntent();
            if (intent != null) {
                System.out.println(intent);
                String action = intent.getAction(); 
                if (action != null && action.equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    finish(); // just close activity. running service handle attached device by self
                    return;
                }
            }
        }
        
        mServiceIntent = new Intent(this, DcdcService.class);
        
        mSwitchServiceEnabled = (Switch) findViewById(R.id.switchServiceEnabled);
        mSwitchServiceEnabled.setChecked(isServiceRunning);
        
        mSwitchServiceEnabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    startDcdcService();
                } else {
                    dcdcServiceStopping();
                    stopDcdcService();
                }
            }
        });
        
        mOutputVoltageText = (EditText) findViewById(R.id.outputVoltageEditText);
        mInputVoltageText = (EditText) findViewById(R.id.inputVoltageEditText);
        mModeText = (EditText) findViewById(R.id.modeEditText);
        
        if (isServiceRunning) {
            bindDcdcService();
        }
    }
    
    protected void onDestroy()
    {
        System.out.println("onDestroy");
        
        if (mBounded) {
            unbindService(mConnection);
            mBounded = false;
            mService = null;
        }
        
        super.onDestroy();
    }
    
    protected void toast(String text)
    {
        Context context = getApplicationContext();
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        toast.show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }
    
    private final StatusEventListener mStatusEventListener = new StatusEventListener() {
        @Override
        public void handleStatusEvent(StatusEvent e) {
            final ResponseStatus status = e.getStatus();
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    
                    String outputVoltage = String.format("%.2f", status.getOutputVoltage());
                    String inputVoltage = String.format("%.2f", status.getInputVoltage());
                    
                    if (!status.getOutputEnable()) {
                        outputVoltage += " (disabled)";
                    }
                    
                    mOutputVoltageText.setText(outputVoltage);
                    mInputVoltageText.setText(inputVoltage);
                    
                    String modeStr = "";
                    switch(status.getMode()) {
                        case ResponseStatus.MODE_DUMB: modeStr = "mode: 0 (dumb)"; break;
                        case ResponseStatus.MODE_AUTOMOTIVE: modeStr = "mode: 1 (automotive)"; break;
                        case ResponseStatus.MODE_SCRIPT: modeStr = "mode: 2 (script)"; break;
                        case ResponseStatus.MODE_UPS: modeStr = "mode: 3 (ups)"; break;
                        default:
                            modeStr = "Unknown mode";
                            
                    } 
                    mModeText.setText(modeStr);
                }
            });
            
        }
    };
    
    private final ConnectedEventListener mConnectedEventListener = new ConnectedEventListener() {
        @Override
        public void handleConnectedEvent(
                final ConnectedEvent event) {
            
            System.out.println("ConnectedEvent");
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    
                }
            });
        }
    };
    
    private final DisconnectedEventListener mDisconnectedEventListener = new DisconnectedEventListener() {
        @Override
        public void handleDisconnectedEvent(
                final DisconnectedEvent event) {
            
            System.out.println("DisconnectedEvent");
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    
                }
            });
        }
    };
}
