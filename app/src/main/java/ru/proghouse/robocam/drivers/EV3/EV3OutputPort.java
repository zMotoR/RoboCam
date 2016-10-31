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
    private volatile int step = 0; //(0 - 200) Max step that increases or decreases the power or angle value (before using the coefficient).

    public EV3OutputPort(int layer, int number) {
        this.layer = layer;
        this.number = number;
    }

    public int getLayer() {
        return layer;
    }

    public float getAngle() {
        return angle;
    }

    public boolean setAngle(float angle, boolean useStep, boolean canGotoNextStep) {
        if (!useStep)
            this.angle = angle;
        else if (angle > this.angle) {
            if (step > 0) {
                if (canGotoNextStep) {
                    float newAngle = this.angle + (float) step;
                    if (newAngle > angle)
                        this.angle = angle;
                    else
                        this.angle = newAngle;
                }
            }
            else
                this.angle = angle;
        } else if (angle < this.angle) {
            if (step > 0) {
                if (canGotoNextStep) {
                    float newAngle = this.angle - (float) step;
                    if (newAngle < angle)
                        this.angle = angle;
                    else
                        this.angle = newAngle;
                }
            }
            else
                this.angle = angle;
        }
        return angle == this.angle;
    }

    public int getPower() {
        return power;
    }

    public boolean setPower(int power, boolean useStep, boolean canGotoNextStep) {
        if (!useStep)
            this.power = power;
        else if (power > this.power) {
            if (step > 0) {
                if (canGotoNextStep) {
                    int newPower = this.power + step;
                    if (newPower > power)
                        this.power = power;
                    else
                        this.power = newPower;
                }
            }
            else
                this.power = power;
        } else if (power < this.power) {
            if (step > 0) {
                if (canGotoNextStep) {
                    int newPower = this.power - step;
                    if (newPower < power)
                        this.power = power;
                    else
                        this.power = newPower;
                }
            }
            else
                this.power = power;
        }
        return power == this.power;
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

    public String getId() {
        //Using input port numbering for id generating
        return new Integer(layer).toString() + ":"
                + new Integer(EV3Driver.getInputPortNumberByOutputPortNumber(number)).toString();
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

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

}
