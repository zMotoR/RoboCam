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
    private int incX = 0; //(0 - 200) Max step that increases the power or angle value (before using the coefficient).
    private int incY = 0;
    private int decX = 0; //(0 - 200) Max step that decreases the power or angle value (before using the coefficient).
    private int decY = 0;
    private int stepXPause = 100; //The pause between steps in milliseconds.
    private int stepYPause = 100;
    private long lastStepXTime = System.currentTimeMillis();
    private long lastStepYTime = System.currentTimeMillis();
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

    public int getIncX() {
        return incX;
    }

    public void setIncX(int incX) {
        this.incX = Math.min(200, Math.max(0, incX));
    }

    public int getDecX() {
        return decX;
    }

    public void setDecX(int decX) {
        this.decX = Math.min(200, Math.max(0, decX));
    }

    public int getIncY() {
        return incY;
    }

    public void setIncY(int incY) {
        this.incY = Math.min(200, Math.max(0, incY));
    }

    public int getDecY() {
        return decY;
    }

    public void setDecY(int decY) {
        this.decY = Math.min(200, Math.max(0, decY));
    }

    public int getStepXPause() {
        return stepXPause;
    }

    public void setStepXPause(int stepXPause) {
        this.stepXPause = Math.max(100, ((int)stepXPause / 100) * 100);
    }

    public int getStepYPause() {
        return stepYPause;
    }

    private boolean hasStepX() {
        return (incX > 0 && incX <= 200) || (decX > 0 && decX <= 200);
    }

    private boolean hasStepY() {
        return (incY > 0 && incY <= 200) || (decY > 0 && decY <= 200);
    }

    public void setStepYPause(int stepYPause) {
        this.stepYPause = Math.max(100, ((int)stepYPause / 100) * 100);
    }

    public boolean gotoNextStep() {
        if (!isFinished) {
            long curTime = System.currentTimeMillis();
            if (curTime - lastStepXTime >= stepXPause
                    || curTime - lastStepYTime >= stepYPause)
                _setPressedKeys(oldPressedKeys);
        }
        return isFinished;
    }

    public boolean setPressedKeys(HashSet<Integer> pressedKeys) {
        String keysHash = "";
        for (Integer pressedKey : pressedKeys)
            keysHash += pressedKey.toString() + ":";
        if (oldKeysHash.equals(keysHash) && isFinished)
            return true;
        oldPressedKeys = (HashSet<Integer>)pressedKeys.clone();
        long curTime = System.currentTimeMillis();
        if ((hasStepX() && curTime - lastStepXTime >= stepXPause)
                || (hasStepY() && curTime - lastStepYTime >= stepYPause))
            _setPressedKeys(pressedKeys);
        oldKeysHash = keysHash;
        return isFinished;
    }

    private static int sign(int x) {
        if (x > 0)
            return 1;
        else if (x < 0)
            return -1;
        return 0;
    }

    private void _setPressedKeys(HashSet<Integer> pressedKeys) {
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
            long curTime = System.currentTimeMillis();
            if (behavior0 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !horzPressed)
                newX = x;
            else if (((sign(newX) == sign(x) || (newX != 0 && x == 0)) && incX > 0 && incX <= 200)
                    || ((sign(newX) * sign(x) < 0 || (newX == 0 && x != 0)) && decX > 0 && decX <= 200)) {
                int tmp = newX;
                int step = (sign(newX) == sign(x) || (newX != 0 && x == 0)) ? incX : decX;
                if (curTime - lastStepXTime >= stepXPause) {
                    if (newX > x)
                        newX = Math.min(newX, x + step);
                    else
                        newX = Math.max(newX, x - step);
                    lastStepXTime = curTime;
                }
                else
                    newX = x;
                finished = tmp == newX && finished;
            }
            if (behavior1 != RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO && !vertPressed)
                newY = y;
            else if (((sign(newY) == sign(y) || (newY != 0 && y == 0)) && incY > 0 && incY <= 200)
                    || ((sign(newY) * sign(y) < 0 || (newY == 0 && y != 0)) && decY > 0 && decY <= 200)) {
                int tmp = newY;
                int step = (sign(newY) == sign(y) || (newY != 0 && y == 0)) ? incY : decY;
                if (curTime - lastStepYTime >= stepYPause) {
                    if (newY > y)
                        newY = Math.min(newY, y + step);
                    else
                        newY = Math.max(newY, y - step);
                    lastStepYTime = curTime;
                }
                else
                    newY = y;
                finished = tmp == newY && finished;
            }
            x = newX;
            y = newY;
            if (type == EV3Driver.JOYSTICK_TYPE_INDEPENDENT_MOTORS) {
                for (EV3OutputPort outputPortX : outputPorts0)
                    if (outputPortX.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER)
                        outputPortX.setPower(newX);
                    else
                        outputPortX.setAngle(newX);
                for (EV3OutputPort outputPortY : outputPorts1)
                    if (outputPortY.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER)
                        outputPortY.setPower(newY);
                    else
                        outputPortY.setAngle(newY);
            } else if (type == EV3Driver.JOYSTICK_TYPE_STEERING
                    || type == EV3Driver.JOYSTICK_TYPE_STEERING_PROGRESSIVE) {
                int powerL = newY;
                int powerR = newY;
                if (newY == 0) {
                    powerL = newX;
                    powerR = -newX;
                } else if (newX != 0) {
                    if (newX < 0)
                        powerL = (Math.abs(powerL) - Math.abs(newX)) * sign(powerL);
                    else
                        powerR = (Math.abs(powerR) - Math.abs(newX)) * sign(powerR);
                }
                for (EV3OutputPort outputPortL : outputPorts0)
                    outputPortL.setPower(powerL);
                for (EV3OutputPort outputPortR : outputPorts1)
                    outputPortR.setPower(powerR);
            }
        }
        isFinished = finished;
    }

}
