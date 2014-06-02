package com.autowp.dcdc.hidusb;

@SuppressWarnings("serial")
public class StatusEvent extends java.util.EventObject {

    protected ResponseStatus status;
    
    public StatusEvent(Object source, ResponseStatus status) {
        super(source);
        this.status = status;
    }
    
    public ResponseStatus getStatus()
    {
        return status;
    }

}
