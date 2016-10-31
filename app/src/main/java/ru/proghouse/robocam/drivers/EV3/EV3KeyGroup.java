package ru.proghouse.robocam.drivers.EV3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import ru.proghouse.robocam.R;
import ru.proghouse.robocam.drivers.RoboCamDriver;

/**
 * Created by Alexey Valuev on 26.10.2016.
 */
public class EV3KeyGroup extends EV3Controller {
    private int x = 0;
    private int y = 0;
    private int oldX = 0;
    private int oldY = 0;
    private String oldKeysHash = "";
    private boolean isFinished = true;
    private HashSet<Integer> oldPressedKeys = null;
    //Shows how to control motors.
    private int type = RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS;
    //Where the group is active.
    private boolean active = false;
    //The mailbox name that uses when group type is JOYSTICK_TYPE_MAILBOX.
    //Has to contain 2 and more valid symbols.
    private String mailbox = "";
    //For JOYSTICK_TYPE_INDEPENDENT_MOTORS and JOYSTICK_TYPE_STEERING
    // there is key codes for 1-4 directions like arrows.
    private HashSet<Integer> upKeyCodes = new HashSet<Integer>();
    private HashSet<Integer> leftKeyCodes = new HashSet<Integer>();
    private HashSet<Integer> downKeyCodes = new HashSet<Integer>();
    private HashSet<Integer> rightKeyCodes = new HashSet<Integer>();
    //For JOYSTICK_TYPE_MAILBOX there are key codes that will be send to EV3.
    private HashSet<Integer> keyCodes = new HashSet<Integer>();
    //Output ports either for a horizontal axis or for a left motor depending on the key group type.
    private List<EV3OutputPort> outputPorts0 = new ArrayList<EV3OutputPort>();
    //Output ports either for a vertical axis or for a right motor depending on the key group type.
    private List<EV3OutputPort> outputPorts1 = new ArrayList<EV3OutputPort>();
    private int behavior0 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;
    private int behavior1 = RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO;

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getMailbox() {
        return mailbox;
    }

    public void setMailbox(String mailbox) {
        if (mailbox == null)
            mailbox = "";
        /*else if (mailbox.toLowerCase().equals("x")
                || mailbox.toLowerCase().equals("y")
                || mailbox.toLowerCase().equals("w")
                || mailbox.toLowerCase().equals("z")
                || mailbox.toLowerCase().equals("a")
                || mailbox.toLowerCase().equals("b")
                || mailbox.toLowerCase().equals("c")
                || mailbox.toLowerCase().equals("d"))
            throw new Exception(R.string.invalid_mailbox_name);*/
        this.mailbox = mailbox;
    }

    public HashSet<Integer> getUpKeyCodes() {
        return upKeyCodes;
    }

    public HashSet<Integer> getLeftKeyCodes() {
        return leftKeyCodes;
    }

    public HashSet<Integer> getDownKeyCodes() {
        return downKeyCodes;
    }

    public HashSet<Integer> getRightKeyCodes() {
        return rightKeyCodes;
    }

    public HashSet<Integer> getKeyCodes() {
        return keyCodes;
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

    public void setBehavior(int group, int behavior){
        if (group == 0)
            behavior0 = behavior;
        else
            behavior1 = behavior;
    }

    @Override
    public boolean gotoNextStep(boolean canGotoNextStep) {
        if (!isFinished)
            return _setPressedKeys(oldPressedKeys, canGotoNextStep);
        return true;
    }

    public boolean setPressedKeys(HashSet<Integer> pressedKeys, boolean canGotoNextStep) {
        String keysHash = "";
        for (Integer pressedKey : pressedKeys)
            keysHash += pressedKey.toString() + ":";
        if (oldKeysHash.equals(keysHash) && isFinished)
            return true;
        oldPressedKeys = (HashSet<Integer>)pressedKeys.clone();
        boolean finished = _setPressedKeys(pressedKeys, canGotoNextStep);
        oldKeysHash = keysHash;
        return finished;
    }

    private boolean _setPressedKeys(HashSet<Integer> pressedKeys, boolean canGotoNextStep) {
        boolean finished = true;
        if (active) {
            int newX = 0;
            int newY = 0;
            boolean vertPressed = false;
            boolean horzPressed = false;
            if (type == EV3Driver.JOYSTICK_TYPE_INDEPENDENT_MOTORS
                    || type == EV3Driver.JOYSTICK_TYPE_STEERING
                    || type == EV3Driver.JOYSTICK_TYPE_STEERING_PROGRESSIVE) {
                for (Integer pressedKey : pressedKeys) {
                    if (upKeyCodes.contains(pressedKey)) {
                        newY += 100;
                        vertPressed = true;
                    }
                    if (leftKeyCodes.contains(pressedKey)) {
                        newX -= 100;
                        horzPressed = true;
                    }
                    if (downKeyCodes.contains(pressedKey)) {
                        newY -= 100;
                        vertPressed = true;
                    }
                    if (rightKeyCodes.contains(pressedKey)) {
                        newX += 100;
                        horzPressed = true;
                    }
                }
            }
            if (type == EV3Driver.JOYSTICK_TYPE_INDEPENDENT_MOTORS) {
                if (behavior0 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !horzPressed)
                    newX = x;
                if (behavior1 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !vertPressed)
                    newY = y;
                for (EV3OutputPort outputPortX : outputPorts0)
                    if (outputPortX.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER) {
                        finished = outputPortX.setPower(newX, true, canGotoNextStep) && finished;
                        x = outputPortX.getPower();
                    }
                    else {
                        finished = outputPortX.setAngle(newX, true, canGotoNextStep) && finished;
                        x = (int)outputPortX.getAngle();
                    }
                for (EV3OutputPort outputPortY : outputPorts1)
                    if (outputPortY.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER) {
                        finished = outputPortY.setPower(newY, true, canGotoNextStep) && finished;
                        y = outputPortY.getPower();
                    }
                    else {
                        finished = outputPortY.setAngle(newY, true, canGotoNextStep) && finished;
                        y = (int)outputPortY.getAngle();
                    }
            } else if (type == EV3Driver.JOYSTICK_TYPE_STEERING
                    || type == EV3Driver.JOYSTICK_TYPE_STEERING_PROGRESSIVE) {
                int powerL = newY;
                int powerR = newY;
                if (newY == 0) {
                    powerL = newX;
                    powerR = -newX;
                } else if (newX != 0) {
                    if (newX < 0)
                        powerL = 0;
                    else
                        powerR = 0;
                }
                if (behavior0 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !horzPressed)
                    powerL = oldPowerL;
                if (behavior1 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !vertPressed)
                    powerR = oldPowerR;
                for (EV3OutputPort outputPortL : outputPorts0) {
                    finished = outputPortL.setPower(powerL, true, canGotoNextStep) && finished;
                    oldPowerL = outputPortL.getPower();
                }
                for (EV3OutputPort outputPortR : outputPorts1) {
                    finished = outputPortR.setPower(powerR, true, canGotoNextStep) && finished;
                    oldPowerR = outputPortR.getPower();
                }
            }
            oldX = x; oldY = y;
        }
        isFinished = finished;
        return finished;
    }

}
