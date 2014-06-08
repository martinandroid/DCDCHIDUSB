package com.autowp.dcdc.hidusb;

import java.util.EventObject;

@SuppressWarnings("serial")
public class ConnectedEvent extends EventObject {
    
    public ConnectedEvent(Object source) {
        super(source);
    }
}
