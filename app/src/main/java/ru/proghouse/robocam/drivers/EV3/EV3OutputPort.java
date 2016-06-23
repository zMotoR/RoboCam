package ru.proghouse.robocam.drivers.EV3;

/**
 * Created by Alexey Valuev on 13.02.2016.
 */
public class EV3OutputPort {
    private int layer = 0;
    private int number = 0;
    private volatile boolean invert = false;
    private volatile float coefficient = 1;
    private volatile int joystickType = EV3Driver.JOYSTICK_TYPE_POWER;
    private volatile int power = 0;
    private volatile float angle = 0;
    private volatile int preparedCount = 0;
    private volatile int brake = EV3Driver.BRAKE;
    private volatile int motorDirection = 0;
    private volatile float stopAngle = 0;
    private volatile float oldSentAngle = 0;
    private volatile int mailboxId = -1;

    public float getOldSentAngle() {
        return oldSentAngle;
    }

    public void setOldSentAngle(float oldSentAngle) {
        this.oldSentAngle = oldSentAngle;
    }

    public EV3OutputPort(int layer, int number) {
        this.layer = layer;
        this.number = number;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public float getAngle() {
        return angle;
    }

    public void setAngle(float angle) {
        this.angle = angle;
    }

    public int getPower() {
        return power;
    }

    public void setPower(int power) {
        this.power = power;
    }

    public int getJoystickType() {
        return joystickType;
    }

    public void setJoystickType(int type) {
        this.joystickType = type;
    }

    public boolean isInvert() {
        return invert;
    }

    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    public float getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(float coefficient) {
        this.coefficient = coefficient;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public int getBrake() {
        return brake;
    }

    public void setBrake(int brake) {
        this.brake = brake;
    }

    public EV3OutputPort getPreparedPort(){
        EV3OutputPort port = new EV3OutputPort(layer, number);
        port.joystickType = joystickType;
        port.brake = brake;
        if (joystickType == EV3Driver.JOYSTICK_TYPE_POWER) {
            port.power = Math.round((float) power * coefficient);
            if (invert)
                port.power *= -1;
        } else {
            port.angle = angle * coefficient;
            if (invert)
                port.angle *= -1.0;
            port.power = Math.abs(power);
        }
        port.preparedCount = 1;
        return port;
    }

    public void addPort(EV3OutputPort port) {
        if (preparedCount > 0 && port.number == number && port.joystickType == joystickType
                && port.layer == layer){
            EV3OutputPort preparedPort = port.getPreparedPort();
            if (joystickType == EV3Driver.JOYSTICK_TYPE_POWER)
                power += preparedPort.power;
            else {
                angle += preparedPort.angle;
                power += preparedPort.power;
            }
            if (port.brake == EV3Driver.FLOAT && brake == EV3Driver.FLOAT)
                brake = EV3Driver.FLOAT;
            else
                brake = EV3Driver.BRAKE;
            preparedCount++;
        }
    }

    public boolean portEquals(EV3OutputPort port) {
        return port.layer == layer && port.power == power && port.angle == angle
                && port.joystickType == joystickType && port.invert == invert && port.number == number
                && port.coefficient == coefficient && brake == brake;
    }

    public int getPreparedCount() {
        return preparedCount;
    }

    public String getId() {
        //Using input port numering for id generating
        return new Integer(layer).toString() + ":"
                + new Integer(EV3Driver.getInputPortNumberByOutputPortNumber(number)).toString();
    }

    public int getMotorDirection() {
        return motorDirection;
    }

    public void setMotorDirection(int motorDirection) {
        this.motorDirection = motorDirection;
    }

    public float getStopAngle() {
        return stopAngle;
    }

    public void setStopAngle(float stopAngle) {
        this.stopAngle = stopAngle;
    }

    public int getPreparedPower() {
        if (power > 100)
            return 100;
        if (power < -100)
            return -100;
        return power;
    }

    public Object getNumberDesc() {
        switch (number) {
            case EV3Driver.OUTPUT_PORT_A: return "A";
            case EV3Driver.OUTPUT_PORT_B: return "B";
            case EV3Driver.OUTPUT_PORT_C: return "C";
            case EV3Driver.OUTPUT_PORT_D: return "D";
        }
        return number;
    }

    public void setMailboxId(int mailboxId) {
        this.mailboxId = mailboxId;
    }

    public int getMailboxId() {
        return mailboxId;
    }

    public String getMailboxName() {
        return new Integer(getLayer()).toString() + ":" + getNumberDesc();
    }
}
