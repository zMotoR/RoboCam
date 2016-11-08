package ru.proghouse.robocam.drivers.EV3;

import android.app.Activity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import ru.proghouse.robocam.KeyDescription;
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

    private static List<KeyDescription> keyDescriptions = null;

    private static void addKeyDescriptions(int code1, int code2) {
        byte[] buf = new byte[1];
        for (byte i = (byte)code1; i <= (byte)code2; i++) {
            buf[0] = i;
            keyDescriptions.add(new KeyDescription(i, new String(buf)));
        }
    }

    public static int[] toArray(HashSet<Integer> keys) {
        int[] buffer = new int[keys == null ? 0 : keys.size()];
        if (keys != null) {
            int i = 0;
            for (Integer code : keys)
                buffer[i++] = code;
        }
        return buffer;
    }

    public static void fromArray(int[] buffer, HashSet<Integer> keys) {
        keys.clear();
        for (int i = 0; i < buffer.length; i++)
            keys.add(buffer[i]);
    }

    public static String getKeyString(Activity activity, HashSet<Integer> keys) {
        String strKeys = "";
        if (keys != null)
            for (KeyDescription desc : EV3KeyGroup.getKeyDescriptions())
                if (keys.contains(desc.getCode()))
                    if (strKeys.isEmpty())
                        strKeys += desc.getDesc();
                    else
                        strKeys += ", " + desc.getDesc();
        if (strKeys.isEmpty())
            strKeys = activity.getString(R.string.nothing_selected);
        return strKeys;
    }

    public static List<KeyDescription> getKeyDescriptions() {
        if (keyDescriptions == null) {
            keyDescriptions = new ArrayList<KeyDescription>();
            addKeyDescriptions(0x30, 0x39);
            addKeyDescriptions(0x41, 0x5a);
            keyDescriptions.addAll(Arrays.asList(new KeyDescription[]{
                    new KeyDescription(192, "(`)"),
                    new KeyDescription(189, "(-)"),
                    new KeyDescription(219, "([)"),
                    new KeyDescription(221, "(])"),
                    new KeyDescription(220, "(\\)"),
                    new KeyDescription(186, "(;)"),
                    new KeyDescription(222, "(')"),
                    new KeyDescription(188, "(,)"),
                    new KeyDescription(190, "(.)"),
                    new KeyDescription(191, "(/)"),
                    new KeyDescription(27, "Esc"),
                    new KeyDescription(113, "F2"),
                    new KeyDescription(115, "F4"),
                    new KeyDescription(118, "F7"),
                    new KeyDescription(119, "F8"),
                    new KeyDescription(120, "F9"),
                    new KeyDescription(121, "F10"),
                    new KeyDescription(33, "Page Up"),
                    new KeyDescription(34, "Page Down"),
                    new KeyDescription(36, "Home"),
                    new KeyDescription(35, "End"),
                    new KeyDescription(45, "Ins"),
                    new KeyDescription(46, "Del"),
                    new KeyDescription(8, "Backspace"),
                    new KeyDescription(9, "Tab"),
                    new KeyDescription(20, "Caps Lock"),
                    new KeyDescription(13, "Enter"),
                    new KeyDescription(16, "Shift"),
                    new KeyDescription(17, "Ctrl"),
                    new KeyDescription(18, "Alt"),
                    new KeyDescription(32, "Space"),
                    new KeyDescription(37, "Left"),
                    new KeyDescription(38, "Up"),
                    new KeyDescription(39, "Right"),
                    new KeyDescription(40, "Down"),
                    new KeyDescription(144, "Num Lock"),
                    new KeyDescription(96, "0 (Numpad)"),
                    new KeyDescription(97, "1 (Numpad)"),
                    new KeyDescription(98, "2 (Numpad)"),
                    new KeyDescription(99, "3 (Numpad)"),
                    new KeyDescription(100, "4 (Numpad)"),
                    new KeyDescription(101, "5 (Numpad)"),
                    new KeyDescription(102, "6 (Numpad)"),
                    new KeyDescription(103, "7 (Numpad)"),
                    new KeyDescription(104, "8 (Numpad)"),
                    new KeyDescription(105, "9 (Numpad)"),
                    new KeyDescription(110, "(.) (Numpad)"),
                    new KeyDescription(107, "(+) (Numpad)"),
                    new KeyDescription(109, "(-) (Numpad)"),
                    new KeyDescription(106, "(*) (Numpad)"),
                    new KeyDescription(12, "Clear"),
                    new KeyDescription(145, "Scroll Lock"),
                    new KeyDescription(19, "Pause")
            }));
        }
        return keyDescriptions;
    }

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
