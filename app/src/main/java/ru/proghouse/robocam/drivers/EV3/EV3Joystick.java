package ru.proghouse.robocam.drivers.EV3;


import android.bluetooth.BluetoothSocket;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import ru.proghouse.robocam.drivers.RoboCamDriver;

/**
 * Created by Alexey Valuev on 13.02.2016.
 */
public class EV3Joystick extends EV3Controller {
    private int x = 0;
    private int y = 0;
    private int oldX = 0;
    private int oldY = 0;
    private boolean isFinished = true;
    private int behavior0 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;
    private int behavior1 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;
    private String shape = RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR;
    //Output ports either for a horizontal axis or for a left motor depending on the joystick type.
    private List<EV3OutputPort> outputPorts0 = new ArrayList<EV3OutputPort>();
    //Output ports either for a vertical axis or for a right motor depending on the joystick type.
    private List<EV3OutputPort> outputPorts1 = new ArrayList<EV3OutputPort>();
    private int type = RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS;
    private boolean visible = false;

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setBehavior(int group, int behavior){
        if (group == 0)
            behavior0 = behavior;
        else
            behavior1 = behavior;
    }

    public String getBehaviors(){
        return new Integer(behavior0).toString() + new Integer(behavior1).toString();
    }

    public void setShape(String shape){
        this.shape = shape;
    }

    public String getShape() {
        if (!visible)
            return RoboCamDriver.JOYSTICK_SHAPE_INVISIBLE;
        return shape;
    }

    @Override
    public List<EV3OutputPort> getOutputPorts(int group) {
        if (group == 0)
            return outputPorts0;
        else
            return outputPorts1;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getType() {
        return type;
    }

    public void setCoordinates(Integer newX, Integer newY) {
        if (newX != null || newY != null) {
            if (newX != null)
                x = newX;
            if (newY != null)
                y = newY;
            if (x != oldX || y != oldY) {
                if (EV3Driver.JOYSTICK_SHAPE_INVISIBLE.equals(shape)) {
                    //Nothing to do
                } else if (type == EV3Driver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                        || type == EV3Driver.JOYSTICK_TYPE_MAILBOX
                        || RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL.equals(shape)
                        || RoboCamDriver.JOYSTICK_SHAPE_VERTICAL.equals(shape)
                        || RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL_ARROWS.equals(shape)
                        || RoboCamDriver.JOYSTICK_SHAPE_VERTICAL_ARROWS.equals(shape)) {
                    if (RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL.equals(shape)
                            || RoboCamDriver.JOYSTICK_SHAPE_HORIZONTAL_ARROWS.equals(shape))
                        y = 0;
                    if (RoboCamDriver.JOYSTICK_SHAPE_VERTICAL.equals(shape)
                            || RoboCamDriver.JOYSTICK_SHAPE_VERTICAL_ARROWS.equals(shape))
                        x = 0;
                    for (EV3OutputPort outputPortX : outputPorts0)
                        if (outputPortX.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER)
                            outputPortX.setPower(x);
                        else
                            outputPortX.setAngle(x);
                    for (EV3OutputPort outputPortY : outputPorts1)
                        if (outputPortY.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER)
                            outputPortY.setPower(y);
                        else
                            outputPortY.setAngle(y);
                } else if (type == EV3Driver.JOYSTICK_TYPE_STEERING
                        || type == EV3Driver.JOYSTICK_TYPE_STEERING_PROGRESSIVE) {
                    int powerL = y;
                    int powerR = y;
                    if (RoboCamDriver.JOYSTICK_SHAPE_ARROWS.equals(shape)) {
                        if (y == 0) {
                            powerL = x;
                            powerR = -x;
                        }
                    } else {
                        //c- and q-shaped joystick
                        if (type == EV3Driver.JOYSTICK_TYPE_STEERING) {
                            //Method: Attenuation to zero
                            if (x < 0)
                                powerL = Math.round(powerL * (100 - Math.abs(x)) / 100);
                            else if (x > 0)
                                powerR = Math.round(powerR * (100 - x) / 100);
                        } else if (type == EV3Driver.JOYSTICK_TYPE_STEERING_PROGRESSIVE) {
                            //Method: Progressive (can spin on the spot)
                            double angle = Math.atan2(y, x) * 180.0 / Math.PI;
                            double d = Math.sqrt(x * x + y * y); //distance from center of circle to point
                            if (d > 100)
                                d = 100;
                            double L = 0, R = 0;
                            if (angle >= 0 && angle <= 90) {
                                R = angle / 90 * 201 - 100;
                                L = 100;
                            } else if (angle < 0 && angle >= -90) {
                                L = (angle / 90 * -201 - 100) * -1;
                                R = -100;
                            } else if (angle > 90 && angle <= 180) {
                                L = ((angle - 90) / 90 * 201 - 100) * -1;
                                R = 100;
                            } else if (angle < -90 && angle >= -180) {
                                R = (angle + 90) / 90 * -201 - 100;
                                L = -100;
                            }
                            L = L / 100 * d;
                            R = R / 100 * d;
                            powerR = (int) Math.round(R);
                            powerL = (int) Math.round(L);
                        }
                    }
                    for (EV3OutputPort outputPortL : outputPorts0)
                        outputPortL.setPower(powerL);
                    for (EV3OutputPort outputPortR : outputPorts1)
                        outputPortR.setPower(powerR);
                }
            }
            oldX = x; oldY = y;
        }
    }

}
