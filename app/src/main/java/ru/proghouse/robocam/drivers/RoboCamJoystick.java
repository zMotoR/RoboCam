package ru.proghouse.robocam.drivers;

/**
 * Created by Alexey Valuev on 14.06.2017.
 */

public class RoboCamJoystick {

    private boolean visible = false;
    private String shape = RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR;
    private int behavior0 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;
    private int behavior1 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setShape(String shape) {
        this.shape = shape;
    }

    public String getShape() {
        if (!visible)
            return RoboCamDriver.JOYSTICK_SHAPE_INVISIBLE;
        return shape;
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

}
