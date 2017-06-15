package ru.proghouse.robocam.drivers.Custom;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import ru.proghouse.robocam.DefaultValue;
import ru.proghouse.robocam.HttpServer;
import ru.proghouse.robocam.R;
import ru.proghouse.robocam.StringHelper;
import ru.proghouse.robocam.Utils;
import ru.proghouse.robocam.drivers.RoboCamDriver;
import ru.proghouse.robocam.drivers.RoboCamJoystick;
import ru.proghouse.robocam.drivers.RoboCamKeyGroup;
import ru.proghouse.robocam.drivers.StreamHelper;

/**
 * Created by Alexey Valuev on 13.06.2017.
 */

public class CustomDriver extends RoboCamDriver {

    private static final int CMD_STOP = 255;
    private static final int CMD_CALLSIGN = 0;

    static {RoboCamDriver.registerDriver(DefaultValue.Custom, CustomDriver.class);};

    private static final int SOCKET_DISCONNECTED = 0;
    private static final int SOCKET_CONNECTING = 1;
    private static final int SOCKET_ABORTED = 2;
    private static final int SOCKET_CONNECTED = 3;

    private volatile String settingsName = "";
    private volatile String settingsFileName = "";
    private volatile String callsign = "";
    private volatile String response = "";
    private List<RoboCamJoystick> joysticks = new ArrayList<RoboCamJoystick>();
    private List<RoboCamKeyGroup> keyGroups = new ArrayList<RoboCamKeyGroup>();
    private volatile long lastModified = 0;
    private volatile boolean hideJoysticks = true;
    private volatile int socketState = SOCKET_DISCONNECTED;

    private volatile BluetoothSocket socket = null;
    private volatile BluetoothDevice device = null;

    private Object socketSyncObject = new Object();

    public CustomDriver() {
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
        joysticks.add(new RoboCamJoystick());
    }

    private void clearJoysticks() {
        for (int i = 0; i < joysticks.size(); i++)
            joysticks.get(i).setVisible(false);
    }

    public static void createTestSettings(Context context) throws Exception {
        File robotDir = Utils.getRobotDir(context);
        File testSettingsFile = new File(robotDir, "test_custom.xml");
        //if (!testSettingsFile.exists()) {
            FileOutputStream fileOutputStream = new FileOutputStream(testSettingsFile);
            try {
                fileOutputStream.write(new String(
                        "<Custom Name=\"Test\""
                                + " Description=\"Custom robot\""
                                + " Callsign=\"RoboCam\""
                                + " Response=\"Researcher\""
                                + ">"
                                + "<Joystick Index=\"0\" Visible=\"1\" Shape=\"c\" />"
                                + "<Joystick Index=\"1\" Visible=\"1\" Shape=\"v\" Behavior1=\"1\" />"
                                + "<KeyGroup Active=\"1\" Name=\"wheels\">"
                                + "<UpKey>87</UpKey>"
                                + "<UpKey>38</UpKey>"
                                + "<LeftKey>65</LeftKey>"
                                + "<LeftKey>37</LeftKey>"
                                + "<DownKey>83</DownKey>"
                                + "<DownKey>40</DownKey>"
                                + "<RightKey>68</RightKey>"
                                + "<RightKey>39</RightKey>"
                                + "</KeyGroup>"
                                + "<KeyGroup Active=\"1\" Name=\"holder\">"
                                + "<UpKey>89</UpKey>"
                                + "<DownKey>72</DownKey>"
                                + "</KeyGroup>"
                                + "</Custom>"
                ).getBytes("UTF-8"));
            } finally {
                fileOutputStream.close();
            }
        //}
    }

    @Override
    public String getRobotName() {
        if (device != null)
            return device.getName();
        return null;
    }

    @Override
    public String getJoystickBehaviors() {
        return null;
    }

    @Override
    public String getJoystickShapes() {
        return null;
    }

    @Override
    public String getUsedKeys() {
        return null;
    }

    @Override
    public boolean isHideJoysticks() {
        return hideJoysticks;
    }

    @Override
    public void setJoystickValues(Hashtable<String, Integer> joystickValues) {

    }

    @Override
    public void setPressedKeys(HashSet<Integer> pressedKeys) {

    }

    @Override
    public void loadSettingsFromXml(Context context, File file, Document xml) throws Exception {
        if (!xml.getDocumentElement().getNodeName().equals(DefaultValue.Custom))
            throw new Exception(context.getString(R.string.unknown_driver_name,
                    xml.getDocumentElement().getNodeName()));
        settingsFileName = file.getName();
        lastModified = file.lastModified();
        settingsName = xml.getDocumentElement().getAttribute("Name");
        callsign = xml.getDocumentElement().getAttribute("Callsign");
        response = xml.getDocumentElement().getAttribute("Response");
        hideJoysticks = StringHelper.booleanFromString(xml.getDocumentElement().getAttribute("HideJoysticks"), true);
        setShowDebugInfo(StringHelper.booleanFromString(xml.getDocumentElement().getAttribute("ShowDebugInfo"), true));
        clearJoysticks();
        NodeList joystickNodes = xml.getElementsByTagName("Joystick");
        for (int i = 0; i < joystickNodes.getLength(); i++) {
            Element joystickNode = (Element)joystickNodes.item(i);
            int joystickIndex = StringHelper.intFromString(joystickNode.getAttribute("Index"), -1);
            if (joystickIndex >= 0 && joystickIndex < joysticks.size()) {
                RoboCamJoystick joystick = joysticks.get(joystickIndex);
                joystick.setVisible(StringHelper.booleanFromString(joystickNode.getAttribute("Visible"), false));
                joystick.setShape(StringHelper.stringFromString(joystickNode.getAttribute("Shape"),
                        RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR));
                joystick.setBehavior(0, StringHelper.intFromString(joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystick.setBehavior(1, StringHelper.intFromString(joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
            }
        }
        keyGroups.clear();
        NodeList keyGroupNodes = xml.getElementsByTagName("KeyGroup");
        for (int i = 0; i < keyGroupNodes.getLength(); i++) {
            Element keyGroupNode = (Element) keyGroupNodes.item(i);
            RoboCamKeyGroup keyGroup = new RoboCamKeyGroup();
            keyGroup.setActive(StringHelper.booleanFromString(keyGroupNode.getAttribute("Active"), false));
            keyGroup.setName(StringHelper.stringFromString(keyGroupNode.getAttribute("Name"), ""));
            RoboCamKeyGroup.loadKeysFromXml(keyGroupNode, keyGroup.getKeyCodes(), "Key");
            keyGroups.add(keyGroup);
        }
    }

    @Override
    public String getName() {
        return DefaultValue.Custom;
    }

    @Override
    public String getSettingsFileName() {
        return settingsFileName;
    }

    @Override
    public String getSettingsName() {
        return settingsName;
    }

    @Override
    public boolean isDisconnected() {
        return false;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public boolean needBluetooth() {
        return true;
    }

    @Override
    public void connect(BluetoothDevice device) {
        if (socketState == SOCKET_DISCONNECTED || socketState == SOCKET_ABORTED) {
            this.device = device;
            socketState = SOCKET_CONNECTING;
            new Thread(new CustomRunnable(this)).start();
        }
    }

    @Override
    public void disconnect() {
        disconnect(0);
    }

    public void disconnect(int resId) {
        socketState = SOCKET_ABORTED;
        HttpServer.updateJoysticks();
        if (socket != null) {
            stop();
            close();
        }
        if (resId == 0)
            doOnDisconnected();
        else
            doOnDisconnected(resId);
    }

    @Override
    public void stop() {

    }

    private void close() {
        if (socket != null)
            try {
                sendCommand(CMD_STOP);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        socket = null;
        socketState = SOCKET_DISCONNECTED;
    }

    private void sendCommand(int cmd) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] bytes = new byte[3];
        StreamHelper.setUShortToByteArray(bytes, 0, 1);
        StreamHelper.setUByteToByteArray(bytes, 2, cmd);
        synchronized (socketSyncObject) {
            outputStream.write(bytes);
        }
    }

    private void sendMessage(ByteArrayOutputStream s) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] bytes = new byte[2 + s.size()];
        StreamHelper.setUShortToByteArray(bytes, 0, s.size());
        System.arraycopy(s.toByteArray(), 0, bytes, 2, s.size());
        synchronized (socketSyncObject) {
            outputStream.write(bytes);
        }
    }

    private byte[] sendMessageAndReadReply(ByteArrayOutputStream s, int timeOut) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] outputBytes = new byte[2 + s.size()];
        StreamHelper.setUShortToByteArray(outputBytes, 0, s.size());
        System.arraycopy(s.toByteArray(), 0, outputBytes, 2, s.size());
        byte[] replyBytes = null;
        synchronized (socketSyncObject) {
            outputStream.write(outputBytes);
            InputStream inputStream = socket.getInputStream();
            int available = 0;
            int counter = 0;
            while (counter * 20 < timeOut) {
                available = inputStream.available();
                if (available > 0)
                    break;
                try {
                    Thread.sleep(20);
                    counter++;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if (available > 0) {
                counter = 0;
                int replySize = StreamHelper.readUShort(inputStream);
                if (replySize > 0) {
                    int readBytes = 0;
                    replyBytes = new byte[replySize];
                    while (true) {
                        readBytes = inputStream.read(replyBytes, readBytes, replySize);
                        if (readBytes == replySize)
                            break;
                        else if (readBytes <= 0) {
                            if (counter * 20 < timeOut) {
                                try {
                                    Thread.sleep(20);
                                    counter++;
                                    continue;
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                            else {
                                replyBytes = null;
                                break;
                            }
                        }
                        else
                            replySize -= readBytes;
                    }
                }
            }
        }
        return replyBytes;
    }

    private class CustomRunnable implements Runnable {
        CustomDriver driver = null;
        private int errorId = 0;
        private Object arg1 = null;

        public CustomRunnable(CustomDriver driver) {
            this.driver = driver;
        }

        @Override
        public void run() {
            int counter = 0;
            try {
                while(true) {
                    driver.socket = driver.device.createRfcommSocketToServiceRecord(
                            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                    boolean connected = false;
                    if (socketState == SOCKET_ABORTED)
                        break;
                    try {
                        driver.socket.connect();
                        connected = true;
                    } catch (IOException e) {
                        driver.socket.close();
                        if (counter >= 7/* || !e.getMessage().equals("Connection refused")*/)
                            throw e;
                    }
                    if (connected) {
                        errorId = 0;
                        checkIfOurRobotConnected();
                        if (counter >= 7
                                || errorId == R.string.robot_error_unknown_device
                                || errorId == R.string.robot_error_wrong_reply
                                || errorId == R.string.robot_error_wrong_response_on_callsign
                                || errorId == 0)
                            break;
                    }
                    Thread.sleep(3000);
                    counter++;
                }
                if (socketState != SOCKET_ABORTED) {
                    if (errorId == 0) {
                        driver.doOnConnected();
                        socketState = SOCKET_CONNECTED;
                        HttpServer.updateJoysticks();
                    } else {
                        socketState = SOCKET_DISCONNECTED;
                        if (arg1 == null)
                            driver.doOnConnectionError(errorId);
                        else
                            driver.doOnConnectionError(errorId, arg1);
                        driver.close();
                    }
                }
            } catch (Exception e) {
                    //java.io.IOException: read failed, socket might closed or timeout, read ret: -1
                    if (socketState != SOCKET_ABORTED) {
                        e.printStackTrace();
                        driver.doOnConnectionError(R.string.robot_connection_error);
                        //driver.doOnConnectionError(e.getLocalizedMessage());
                        driver.close();
                    }
                }
        }

        private void checkIfOurRobotConnected() throws IOException {
            ByteArrayOutputStream s = new ByteArrayOutputStream();
            StreamHelper.writeUByte(s, CMD_CALLSIGN);
            Random r = new Random();
            int testByte = r.nextInt(254);
            StreamHelper.writeUByte(s, testByte);
            StreamHelper.writeString(s, driver.callsign == null ? "" : driver.callsign);
            byte[] replyBytes = sendMessageAndReadReply(s, 1000);
            if (replyBytes == null || replyBytes.length < 2) {
                errorId = R.string.robot_error_unknown_device;
                return;
            }
            int replyByte = StreamHelper.getUByteFromByteArray(replyBytes, 0);
            if (replyByte != testByte + 1) {
                errorId = R.string.robot_error_wrong_reply;
                return;
            }
            String response = StreamHelper.getStringFromByteArray(replyBytes, 1, replyBytes.length - 1);
            if (response == null)
                response = "";
            String expectedResponse = driver.response;
            if (expectedResponse == null)
                expectedResponse = "";
            if (!response.equals(expectedResponse)) {
                errorId = R.string.robot_error_wrong_response_on_callsign;
                return;
            }
        }
    }
}
