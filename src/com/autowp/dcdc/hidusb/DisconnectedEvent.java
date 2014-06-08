package com.autowp.dcdc.hidusb;

import java.util.EventObject;

@SuppressWarnings("serial")
public class DisconnectedEvent extends EventObject {

    public DisconnectedEvent(Object source) {
        super(source);
    }

}
