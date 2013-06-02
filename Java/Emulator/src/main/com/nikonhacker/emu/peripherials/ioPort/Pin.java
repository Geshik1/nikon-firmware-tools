package com.nikonhacker.emu.peripherials.ioPort;

/** Plain pin */
public class Pin {
    /**
     * This is the pin name, not its function
     */
    private String name;

    /**
     * This is the pin connected to this pin, if any
     */
    private Pin connectedPin;

    /**
     * This is the value set by the component this pin belongs to,
     * that can be read by the component whose pin is connected to this one.
     */
    protected int outputValue;

    private boolean isInput        = true;
    private boolean isInputEnabled = true;

    /**
     * Static method to connect two pins "both ways"
     */
    public static void interconnect(Pin pinA, Pin pinB) {
        if (pinA != null) pinA.setConnectedPin(pinB);
        if (pinB != null) pinB.setConnectedPin(pinA);
    }

    public Pin(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Pin getConnectedPin() {
        return connectedPin;
    }

    public void setConnectedPin(Pin connectedPin) {
        this.connectedPin = connectedPin;
        if (connectedPin != null) connectedPin.setInputValue(outputValue);
    }

    /**
     * To be called by CPU code
     * Else returns the output value of the connected pin, if any
     * Otherwise, returns null and logs the problem.
     */
    public Integer getInputValue() {
        if (connectedPin != null) {
            return connectedPin.getOutputValue();
        }
        // System.out.println("No pin is connected to " + name);
        return null;
    }

    /**
     * To be called by external component
     */
    public void setInputValue(int value) {
        // Default implementation does nothing
    }

    /**
     * To be called by external component
     */
    public Integer getOutputValue() {
        return outputValue;
    }

    /**
     * To be called by CPU code
     * This pin forwards the value to to the attached pin, if any
     */
    public void setOutputValue(int value) {
        // remember the output value
        this.outputValue = value;
        if (connectedPin == null) {
            System.out.println("No pin is connected to " + name);
        }
        else {
            connectedPin.setInputValue(value);
        }
    }

    public boolean isInput() {
        return isInput;
    }

    public void setIsInput(boolean isInput) {
        this.isInput = isInput;
    }

    public boolean isInputEnabled() {
        return isInputEnabled;
    }

    public void setIsInputEnabled(boolean isInputEnabled) {
        this.isInputEnabled = isInputEnabled;
    }

    @Override
    public String toString() {
        return getName();
    }
}
