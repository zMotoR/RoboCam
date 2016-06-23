package ru.proghouse.robocam.drivers.EV3;

/**
 * Created by Alexey Valuev on 14.02.2016.
 */
public class EV3InputPort {
    private int layer = 0;
    private int number = 0;
    private int type = EV3Driver.INPUT_DEVICE_TYPE_EMPTY;
    private int mode = 0;
    private float value = 0;
    private float initValue = 0;
    private boolean initialized = false;

    public EV3InputPort(int layer, int number, int type, int mode) {
        this.layer = layer;
        this.number = number;
        this.type = type;
        this.mode = mode;
    }

    public int getLayer() {
        return layer;
    }

    public int getNumber() {
        return number;
    }

    public String getId() {
        return new Integer(layer).toString() + ":" + new Integer(number).toString();
    }

    public int getType() {
        return type;
    }

    public int getMode() {
        return mode;
    }

    public void setValue(float value) {
        this.value = value;
        if (!initialized) {
            initValue = value;
            initialized = true;
        }
    }

    public float getValue() {
        return value;
    }

    public float getInitValue() {
        return initValue;
    }
}
