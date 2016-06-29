package ru.proghouse.robocam.drivers.EV3;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Phaser;

import ru.proghouse.robocam.DefaultValue;
import ru.proghouse.robocam.HttpServer;
import ru.proghouse.robocam.R;
import ru.proghouse.robocam.StringHelper;
import ru.proghouse.robocam.drivers.RoboCamDriver;

/**
 * Created by Alexey Valuev on 02.02.2016.
 */
public class EV3Driver extends RoboCamDriver {
    public static final int DIRECT_COMMAND_REPLY = 0x00;
    public static final int DIRECT_COMMAND_NO_REPLY = 0x80;
    public static final int DIRECT_REPLY = 0x02;
    public static final int DIRECT_REPLY_ERROR = 0x04;

    public static final int SYSTEM_COMMAND_REPLY = 0x01;
    public static final int SYSTEM_COMMAND_NO_REPLY = 0x81;
    public static final int SYSTEM_REPLY = 0x03;
    public static final int SYSTEM_REPLY_ERROR = 0x05;

    //Opcodes
    private static final int OP_SUB_32 = 0x16;

    private static final int OP_JR_GTEQ_32 = 0x7A;

    private static final int OP_MOVE_32_32 = 0x3A;
    private static final int OP_MUL_32 = 0x1A;

    private static final int OP_UI_WRITE = 0x82;
    private static final int LED = 0x1B;
    private static final int PUT_STRING = 0x08;

    private static final int OP_TIMER_WAIT = 0x85;
    private static final int OP_TIMER_READY = 0x86;

    private static final int OP_INPUT_DEVICE_LIST = 0x98;

    private static final int OP_OUTPUT_STOP = 0xA3;
    private static final int OP_OUTPUT_POWER = 0xA4;
    private static final int OP_OUTPUT_START = 0xA6;
    private static final int OP_OUTPUT_READY = 0xAA;
    private static final int OP_OUTPUT_STEP_SPEED = 0xAE;
    private static final int OP_OUTPUT_PRG_STOP = 0xB4;
    private static final int OP_OUTPUT_RESET = 0xA2;
    private static final int OP_OUTPUT_CLR_COUNT = 0xB2;

    private static final int OP_MAILBOX_OPEN = 0xD8;
    private static final int OP_MAILBOX_CLOSE = 0xDD;

    public static final int OUTPUT_PORT_A = 0x01;
    public static final int OUTPUT_PORT_B = 0x02;
    public static final int OUTPUT_PORT_C = 0x04;
    public static final int OUTPUT_PORT_D = 0x08;

    public static final int INPUT_PORT_1 = 0x00;
    public static final int INPUT_PORT_2 = 0x01;
    public static final int INPUT_PORT_3 = 0x02;
    public static final int INPUT_PORT_4 = 0x03;
    public static final int INPUT_PORT_A = 0x10;
    public static final int INPUT_PORT_B = 0x11;
    public static final int INPUT_PORT_C = 0x12;
    public static final int INPUT_PORT_D = 0x13;

    public static final int FLOAT = 0;
    public static final int BRAKE = 1;

    public static final int INPUT_DEVICE_TYPE_EV3_LARGE_MOTOR = 7;
    public static final int INPUT_DEVICE_TYPE_EV3_MEDIUM_MOTOR = 8;
    public static final int MOTOR_MODE_DEGREE = 0;
    public static final int MOTOR_MODE_ROTATION = 1;
    public static final int MOTOR_MODE_POWER = 2;
    public static final int INPUT_DEVICE_TYPE_UNKNOWN = 0xff;
    public static final int INPUT_DEVICE_TYPE_EMPTY = 0x7e;
    public static final int INPUT_DEVICE_TYPE_INITIALIZING = 0x7d;
    public static final int INPUT_DEVICE_TYPE_WRONG = 0x7f;

    private static final int SOCKET_DISCONNECTED = 0;
    private static final int SOCKET_CONNECTING = 1;
    private static final int SOCKET_ABORTED = 2;
    private static final int SOCKET_CONNECTED = 3;

    public static int JOYSTICK_TYPE_POWER = 0;
    public static int JOYSTICK_TYPE_ANGLE = 1;

    public static final String MAILBOX_NAME = "RoboCam";
    public static final int MAILBOX_ID = 0;

    static final String[] axisNames = new String[]{"x", "y", "w", "z", "a", "b", "c", "d"};
    static int[] joystickCoordinates = new int[4 * 2];

    private static List<EV3Joystick> joysticks = new ArrayList<EV3Joystick>();

    static {
        joysticks.add(new EV3Joystick());
        joysticks.add(new EV3Joystick());
        joysticks.add(new EV3Joystick());
        joysticks.add(new EV3Joystick());
        storeJoystickCoordinates();
    }

    private static void storeJoystickCoordinates() {
        for (int i = 0; i < joysticks.size(); i++) {
            joystickCoordinates[i * 2] = joysticks.get(i).getX();
            joystickCoordinates[i * 2 + 1] = joysticks.get(i).getY();
        }
    }

    static {RoboCamDriver.registerDriver(DefaultValue.EV3, EV3Driver.class);};

    private Object socketSyncObject = new Object();
    private Object portSyncObject = new Object();

    private volatile BluetoothSocket socket = null;
    private volatile BluetoothDevice ev3device = null;
    //private volatile int layerCount = 0;
    private HashSet<Integer> usedLayers = new HashSet<Integer>();
    private Hashtable<String, EV3InputPort> inputPorts = new Hashtable<String, EV3InputPort>();
    private Hashtable<String, EV3OutputPort> outputPorts = new Hashtable<String, EV3OutputPort>();
    private List<EV3OutputPort> angleOutputPorts = new ArrayList<EV3OutputPort>();
    private volatile int inputReaderPeriod = 100;
    private volatile int socketState = SOCKET_DISCONNECTED;
    private volatile boolean hasMovingMotors = false;
    private final Object joystickMonitor = new Object();
    private final Object joystickSyncObject = new Object();
    private Hashtable<String, Integer> joystickValues = new Hashtable<String, Integer>();

    private volatile String settingsName = "";
    private volatile String settingsFileName = "";
    private volatile long lastModified = 0;

    private EV3ByteCodes createAPIAndWriteMessageHeader(EV3ByteCodes c, int commandType, int globalSize,
            int localSize) throws IOException {
        if (c == null) {
            c = new EV3ByteCodes();
            c.messageHeader(commandType, globalSize, localSize);
        }
        return c;
    }

    private EV3ByteCodes createAPIAndWriteMessageHeader(EV3ByteCodes c, int commandType,
                                                        int systemCommand) throws IOException {
        if (c == null) {
            c = new EV3ByteCodes();
            c.messageHeader(commandType, systemCommand);
        }
        return c;
    }

    /*private void writeOutputSetPowerCommand(OutputStream _stream, int layer,
                                           int port, int power) throws IOException {
        //Set the power
        writeCommand(_stream, OP_OUTPUT_POWER); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
        writeParameterAsByte(_stream, power); //POWER
    }

    private void writeOutputStartCommand(OutputStream _stream, int layer,
                                        int port) throws IOException {
        //Start
        writeCommand(_stream, OP_OUTPUT_START); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
    }

    private void writeOutputStopCommand(OutputStream _stream, int layer,
                                        int port, int _brake) throws IOException {
        //Stop
        writeCommand(_stream, OP_OUTPUT_STOP); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
        writeParameterAsSmallByte(_stream, _brake); //BRAKE
    }

    private void writeOutputPrgStop(OutputStream _stream) throws IOException {
        writeCommand(_stream, OP_OUTPUT_PRG_STOP); //Opcode
    }

    private void writeOutputStepSpeed(OutputStream _stream, int layer, int port, int power,
                                      int accelerationAngle, int rotationAngle,
                                      int brakingAngle, int brake) throws IOException {
        writeCommand(_stream, OP_OUTPUT_STEP_SPEED); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
        writeParameterAsByte(_stream, power); //SPEED
        writeParameterAsInteger(_stream, accelerationAngle); //STEP1
        writeParameterAsInteger(_stream, rotationAngle); //STEP2
        writeParameterAsInteger(_stream, brakingAngle); //STEP3
        writeParameterAsUByte(_stream, brake); //BRAKE
    }*/

    //private void writeOutputReady(OutputStream _stream, int layer, int port) throws IOException {
    //    writeCommand(_stream, OP_OUTPUT_READY); //Opcode
    //    writeParameterAsSmallByte(_stream, layer); //LAYER
    //    writeParameterAsSmallByte(_stream, port); //NOS
    //}

    /*private void writeOutputReset(OutputStream _stream, int layer, int port) throws IOException {
        writeCommand(_stream, OP_OUTPUT_RESET); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
    }

    private void writeOutputClrCount(OutputStream _stream, int layer, int port) throws IOException {
        writeCommand(_stream, OP_OUTPUT_CLR_COUNT); //Opcode
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NOS
    }

    private void writeInputDeviceList(OutputStream _stream, int length, int outVarArray,
                                      int outVarChanged) throws IOException {
        writeCommand(_stream, OP_INPUT_DEVICE_LIST); //Opcode
        writeParameterAsSmallByte(_stream, length); //LENGTH
        //Return
        writeGlobalIndex(_stream, outVarArray); //ARRAY
        writeGlobalIndex(_stream, outVarChanged); //CHANGED
    }

    private void writeInputGetTypeMode(OutputStream _stream, int layer, int port,
                                       int outVarDeviceType, int outVarMode) throws IOException {
        writeCommand(_stream, OP_INPUT_DEVICE, GET_TYPEMODE); //Opcode and CMD
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NO
        //Return
        writeGlobalIndex(_stream, outVarDeviceType); //TYPE
        writeGlobalIndex(_stream, outVarMode); //MODE
    }

    private void writeInputReadSI(OutputStream _stream, int layer,
                                  int port, int type, int mode, int varCount,
                                  int outVarValue) throws IOException {
        writeCommand(_stream, OP_INPUT_DEVICE, READY_SI);
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NO
        writeParameterAsSmallByte(_stream, type); //TYPE
        writeParameterAsSmallByte(_stream, mode); //MODE
        writeParameterAsSmallByte(_stream, varCount); //VALUES
        writeGlobalIndex(_stream, outVarValue);
    }

    private void writeInputReadRAW(OutputStream _stream, int layer,
                                  int port, int type, int mode, int varCount,
                                  int outGlobalVarValue, int outLocalVarValue) throws IOException {
        writeCommand(_stream, OP_INPUT_DEVICE, READY_RAW);
        writeParameterAsSmallByte(_stream, layer); //LAYER
        writeParameterAsSmallByte(_stream, port); //NO
        writeParameterAsSmallByte(_stream, type); //TYPE
        writeParameterAsSmallByte(_stream, mode); //MODE
        writeParameterAsSmallByte(_stream, varCount); //VALUES
        if (outGlobalVarValue >= 0)
            writeGlobalIndex(_stream, outGlobalVarValue);
        else
            writeLocalIndex(_stream, outLocalVarValue);
    }*/

    private void sendMessage(EV3ByteCodes c) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] bytes = c.createMessage();
        synchronized (socketSyncObject) {
            outputStream.write(bytes);

            /*writeUShort(outputStream, byteArrayOutputStream.size());
            byteArrayOutputStream.writeTo(outputStream);*/
        }
    }

    private byte[] sendMessageAndReadReply(EV3ByteCodes c, int timeOut) throws IOException {
        OutputStream outputStream = socket.getOutputStream();
        byte[] outputBytes = c.createMessage();
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
                int replySize = readUShort(inputStream);
                replyBytes = new byte[replySize];
                inputStream.read(replyBytes, 0, replySize);
                if (outputBytes[2] != replyBytes[0] || outputBytes[3] != replyBytes[1])
                    replyBytes = null;
            }
        }
        return replyBytes;
    }

    //Reads unsigned byte.
    private int readUByte(InputStream _stream) throws IOException {
        byte bytes[] = new byte[1];
        _stream.read(bytes);
        return bytes[0] < 0 ? (int)bytes[0] + 256 : (int)bytes[0];
    }

    //Reads unsigned short.
    private int readUShort(InputStream _stream) throws IOException {
        return readUByte(_stream) | (readUByte(_stream) << 8);
    }

    @Override
    public String getRobotName() {
        if (ev3device != null)
            return ev3device.getName();
        return null;
    }

    @Override
    public String getJoystickBehaviors() {
        String result = "";
        for (EV3Joystick joystick : joysticks)
            result += joystick.getBehaviors();
        return result;
    }

    @Override
    public String getJoystickShapes() {
        String result = "";
        for (EV3Joystick joystick : joysticks)
            result += joystick.getShape();
        return result;
    }

    @Override
    public void setJoystickValues(Hashtable<String, Integer> newJoystickValues) {
        synchronized (joystickSyncObject) {
            for (Enumeration<String> enumerator = newJoystickValues.keys(); enumerator.hasMoreElements(); ) {
                String key = enumerator.nextElement();
                joystickValues.put(key, newJoystickValues.get(key));
            }
        }
        synchronized (joystickMonitor) {
            joystickMonitor.notifyAll();
        }
    }

    @Override
    protected void loadSettingsFromXml(Context context, File file, Document xml) throws Exception {
        if (!xml.getDocumentElement().getNodeName().equals(DefaultValue.EV3))
            throw new Exception(context.getString(R.string.unknown_driver_name,
                    xml.getDocumentElement().getNodeName()));
        settingsName = xml.getDocumentElement().getAttribute("Name");
        settingsFileName = file.getName();
        lastModified = file.lastModified();
        clearJoysticks();
        NodeList joystickNodes = xml.getElementsByTagName("Joystick");
        for (int i = 0; i < joystickNodes.getLength(); i++) {
            Element joystickNode = (Element)joystickNodes.item(i);
            int joystickIndex = StringHelper.intFromString(joystickNode.getAttribute("Index"), -1);
            if (joystickIndex >= 0 && joystickIndex < joysticks.size()) {
                EV3Joystick joystick = joysticks.get(joystickIndex);
                joystick.setVisible(StringHelper.booleanFromString(joystickNode.getAttribute("Visible"), false));
                joystick.setShape(StringHelper.stringFromString(joystickNode.getAttribute("Shape"),
                        RoboCamDriver.JOYSTICK_SHAPE_CIRCULAR));
                joystick.setType(StringHelper.intFromString(joystickNode.getAttribute("Type"),
                        RoboCamDriver.JOYSTICK_TYPE_INDEPENDENT_MOTORS));
                joystick.setBehavior(0, StringHelper.intFromString(joystickNode.getAttribute("Behavior0"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                joystick.setBehavior(1, StringHelper.intFromString(joystickNode.getAttribute("Behavior1"),
                        RoboCamDriver.JOYSTICK_BEHAVIOR_RETURN_TO_ZERO));
                if (joystick.getType() != JOYSTICK_TYPE_MAILBOX) {
                    NodeList outputPortNodes = joystickNode.getElementsByTagName("OutputPort");
                    for (int j = 0; j < outputPortNodes.getLength(); j++) {
                        Element outputPortNode = (Element) outputPortNodes.item(j);
                        int group = StringHelper.intFromString(outputPortNode.getAttribute("Group"), -1);
                        int layer = StringHelper.intFromString(outputPortNode.getAttribute("Layer"), -1);
                        String portName = StringHelper.stringFromString(outputPortNode.getAttribute("Number"), "");
                        int number = -1;
                        if (portName.equals("A"))
                            number = OUTPUT_PORT_A;
                        else if (portName.equals("B"))
                            number = OUTPUT_PORT_B;
                        else if (portName.equals("C"))
                            number = OUTPUT_PORT_C;
                        else if (portName.equals("D"))
                            number = OUTPUT_PORT_D;
                        if (group >= 0 && group <= 1 && layer >= 0 && layer < 4 && number >= 0) {
                            EV3OutputPort outputPort = new EV3OutputPort(layer, number);
                            joystick.getOutputPorts(group).add(outputPort);
                            outputPort.setJoystickType(StringHelper.intFromString(outputPortNode.getAttribute("JoystickType"),
                                    EV3Driver.JOYSTICK_TYPE_POWER));
                            outputPort.setPower(StringHelper.intFromString(outputPortNode.getAttribute("Power"), 0));
                            outputPort.setInvert(StringHelper.booleanFromString(outputPortNode.getAttribute("Invert"), false));
                            outputPort.setCoefficient(StringHelper.floatFromString(outputPortNode.getAttribute("Coefficient"), 1));
                            outputPort.setBrake(StringHelper.intFromString(outputPortNode.getAttribute("Brake"), EV3Driver.BRAKE));
                        }
                    }
                }
            }
        }
    }

    private void restoreDefaultSettings() {
        clearJoysticks();

        joysticks.get(0).setVisible(true);
        joysticks.get(0).setShape(JOYSTICK_SHAPE_CIRCULAR);
        joysticks.get(1).setVisible(true);
        joysticks.get(1).setShape(JOYSTICK_SHAPE_VERTICAL);

        joysticks.get(0).setType(JOYSTICK_TYPE_STEERING);
        joysticks.get(0).getOutputPorts(0).add(new EV3OutputPort(0, OUTPUT_PORT_B));
        joysticks.get(0).getOutputPorts(1).add(new EV3OutputPort(0, OUTPUT_PORT_C));

        joysticks.get(1).setType(JOYSTICK_TYPE_INDEPENDENT_MOTORS);
        joysticks.get(1).getOutputPorts(1).add(new EV3OutputPort(0, OUTPUT_PORT_A));
        joysticks.get(1).getOutputPorts(1).get(0).setJoystickType(JOYSTICK_TYPE_ANGLE);
        joysticks.get(1).getOutputPorts(1).get(0).setPower(50);
        joysticks.get(1).getOutputPorts(1).get(0).setInvert(true);
        joysticks.get(1).setBehavior(1, JOYSTICK_BEHAVIOR_HOLD_POSITION);
    }

    @Override
    public String getName() {
        return DefaultValue.EV3;
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
        return socketState == SOCKET_DISCONNECTED;
    }

    @Override
    public boolean isConnected() {
        return socketState == SOCKET_CONNECTED;
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
    public int getStringResId(int robotIsDisconnected) {
        switch (robotIsDisconnected){
            case RoboCamDriver.ROBOT_IS_DISCONNECTED:
                return R.string.ev3_is_disconnected;
            case RoboCamDriver.ROBOT_IS_CONNECTING:
                return R.string.ev3_is_connecting;
            case RoboCamDriver.ROBOT_IS_NOT_FOUND_VIA_BLUETOOTH:
                return R.string.ev3_bonded_devices_not_found;
        }
        return super.getStringResId(robotIsDisconnected);
    }

    @Override
    public void connect(BluetoothDevice device) {
        if (socketState == SOCKET_DISCONNECTED || socketState == SOCKET_ABORTED) {
            ev3device = device;
            socketState = SOCKET_CONNECTING;
            new Thread(new EV3Runnable(this)).start();
        }
    }

    @Override
    public void disconnect() {
        disconnect(0);
    }

    public void disconnect(int resId) {
        socketState = SOCKET_ABORTED;
        //clearJoysticks();
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
        if (socket != null) {
            if (outputPorts.size() > 0) {
                EV3ByteCodes c = new EV3ByteCodes();
                try {
                    int angleTypeCount = 0;
                    for (Enumeration<String> enumerator = outputPorts.keys(); enumerator.hasMoreElements(); ) {
                        EV3OutputPort outputPort = outputPorts.get(enumerator.nextElement());
                        if (outputPort.getJoystickType() == JOYSTICK_TYPE_ANGLE)
                            angleTypeCount++;
                    }
                    c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, angleTypeCount * 8);
                    //First we have to stop our brick supporting program
                    c.opProgram_Stop();
                    c.LC2(EV3ByteCodes.USER_SLOT);
                    //Closing mailbox
                    c.opMailbox_Close();
                    c.LC0(MAILBOX_ID);
                    //Stopping motors
                    for (Enumeration<String> enumerator = outputPorts.keys(); enumerator.hasMoreElements(); ) {
                        EV3OutputPort outputPort = outputPorts.get(enumerator.nextElement());
                        c.opOutput_Stop();
                        c.LC0(outputPort.getLayer());
                        c.LC0(outputPort.getNumber());
                        c.LC0(outputPort.getBrake());
                        //writeOutputStopCommand(byteArrayOutputStream, outputPort.getLayer(),
                        //        outputPort.getNumber(), outputPort.getBrake());
                    }
                    //Rotating angle motors to 0 degrees
                    int portIndex = 0;
                    for (Enumeration<String> enumerator = outputPorts.keys(); enumerator.hasMoreElements(); ) {
                        EV3OutputPort outputPort = outputPorts.get(enumerator.nextElement());
                        if (outputPort.getJoystickType() == JOYSTICK_TYPE_ANGLE
                                && outputPort.getPreparedPower() != 0) { //We have to return motors to angle 0.

                            c.opInput_Device(EV3ByteCodes.READY_RAW);
                            c.LC0(outputPort.getLayer()); //LAYER
                            c.LC0(getInputPortNumberByOutputPortNumber(outputPort.getNumber())); //NO
                            c.LC0(0); //TYPE
                            c.LC0(0); //MODE
                            c.LC0(1); //VALUES
                            c.LV1(portIndex * 8);

                            //writeCommand(byteArrayOutputStream, OP_INPUT_DEVICE, READY_RAW);
                            //writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getLayer()); //LAYER
                            //writeParameterAsSmallByte(byteArrayOutputStream,
                            //        getInputPortNumberByOutputPortNumber(outputPort.getNumber())); //NO
                            //writeParameterAsSmallByte(byteArrayOutputStream, 0); //TYPE
                            //writeParameterAsSmallByte(byteArrayOutputStream, 0); //MODE
                            //writeParameterAsSmallByte(byteArrayOutputStream, 1); //VALUES
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8);

                            c.opSub32();
                            c.LC4(0); //SOURCE1
                            c.LV1(portIndex * 8); //SOURCE2
                            c.LV1(portIndex * 8); //DESTINATION

                            //writeCommand(byteArrayOutputStream, OP_SUB_32); //opSub32
                            //writeParameterAsInteger(byteArrayOutputStream, 0); //SOURCE1
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8); //SOURCE2
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8); //DESTINATION

                            c.opMove32_32();
                            c.LC4(outputPort.getPreparedPower()); //SOURCE
                            c.LV1(portIndex * 8 + 4); //DESTINATION

                            //writeCommand(byteArrayOutputStream, OP_MOVE_32_32);
                            //writeParameterAsInteger(byteArrayOutputStream, outputPort.getPreparedPower()); //SOURCE
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION

                            c.opJr_Gteq32();
                            c.LV1(portIndex * 8); //LEFT
                            c.LC4(0); //RIGHT
                            c.LC4(10); //OFFSET                                                     - offset - 10 bytes

                            //writeCommand(byteArrayOutputStream, OP_JR_GTEQ_32); //opJr_Gteq32
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8); //LEFT
                            //writeParameterAsInteger(byteArrayOutputStream, 0); //RIGHT
                            //writeParameterAsInteger(byteArrayOutputStream, 10); //OFFSET                - offset - 10 bytes

                            c.opMul32();                //opCode                                    - 1 byte
                            c.LC4(-1);                  //SOURCE1                                   - 5 bytes
                            c.LV1(portIndex * 8 + 4);   //SOURCE2                                   - 2 bytes
                            c.LV1(portIndex * 8 + 4);   //DESTINATION                               - 2 bytes

                            //writeCommand(byteArrayOutputStream, OP_MUL_32); //opMul32                   - 1 byte
                            //writeParameterAsInteger(byteArrayOutputStream, -1); //SOURCE1               - 5 bytes
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SOURCE2      - 2 bytes
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION  - 2 bytes

                            c.opOutput_Step_Speed();        //Opcode
                            c.LC0(outputPort.getLayer());   //LAYER
                            c.LC0(outputPort.getNumber());  //NOS
                            c.LV1(portIndex * 8 + 4);       //SPEED
                            c.LC4(0);                       //STEP1
                            c.LV1(portIndex * 8);           //STEP2
                            c.LC4(0);                       //STEP3
                            c.LC1(outputPort.getBrake());   //BRAKE

                            //writeCommand(byteArrayOutputStream, OP_OUTPUT_STEP_SPEED); //Opcode
                            //writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getLayer()); //LAYER
                            //writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getNumber()); //NOS
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SPEED
                            //writeParameterAsInteger(byteArrayOutputStream, 0); //STEP1
                            //writeLocalIndex1(byteArrayOutputStream, portIndex * 8); //STEP2
                            //writeParameterAsInteger(byteArrayOutputStream, 0); //STEP3
                            //writeParameterAsUByte(byteArrayOutputStream, outputPort.getBrake()); //BRAKE

                            c.opOutput_Ready();
                            c.LC0(outputPort.getLayer()); //LAYER
                            c.LC0(outputPort.getNumber()); //NOS

                            //writeOutputReady(byteArrayOutputStream, outputPort.getLayer(),
                            //        outputPort.getNumber());

                            portIndex++;
                        }
                    }
                    //Turns the light to green
                    //c.opUI_WRITE(LED);
                    //c.LC0(EV3ByteCodes.LED_GREEN);
                    //Stopping output program
                    c.opOutput_Prg_Stop();
                    //writeOutputPrgStop(byteArrayOutputStream);
                    sendMessage(c);
                    /*byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                    ByteBuffer byteBuffer = ByteBuffer.wrap(replyBytes);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    int value1 = byteBuffer.getInt(3);
                    int value2 = byteBuffer.getInt(3 + 4);
                    if (value1 > value2) {

                    }*/

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void close() {
        if (socket != null)
            try {
                EV3ByteCodes c = new EV3ByteCodes();
                c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, 0);
                c.opUI_WRITE(LED);
                c.LC0(EV3ByteCodes.LED_GREEN);
                sendMessage(c);
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        socket = null;
        usedLayers.clear();
        inputPorts.clear();
        outputPorts.clear();
        angleOutputPorts.clear();
        socketState = SOCKET_DISCONNECTED;
        synchronized (joystickMonitor) {
            joystickMonitor.notifyAll();
        }
        synchronized (joystickSyncObject) {
            joystickValues.clear();
        }
    }

    public static void checkDefaultSettingsFile(Context context) throws Exception {
        File cacheDir = context.getCacheDir();
        File ev3Dir = new File(cacheDir, DefaultValue.ROBOT_SETTINGS_DIRECTORY);
        ev3Dir.mkdirs();
        File ev3DefaultSettingsFile = new File(ev3Dir, DefaultValue.EV3_SETTINGS_FILE);
        if (!ev3DefaultSettingsFile.exists()) {
            FileOutputStream fileOutputStream = new FileOutputStream(ev3DefaultSettingsFile);
            try {
                fileOutputStream.write(new String(
                        "<EV3 Name=\"" + context.getString(R.string.ev3_researcher) + "\""
                                + " Description=\"" + context.getString(R.string.ev3_researcher_desc) + "\""
                                + ">"
                            + "<Joystick Index=\"0\" Visible=\"1\" Shape=\"c\" Type=\"1\">"
                                + "<OutputPort Group=\"0\" Layer=\"0\" Number=\"B\"/>"
                                + "<OutputPort Group=\"1\" Layer=\"0\" Number=\"C\"/>"
                            + "</Joystick>"
                            + "<Joystick Index=\"1\" Visible=\"1\" Shape=\"v\" Type=\"0\" Behavior1=\"1\">"
                                + "<OutputPort Group=\"1\" Layer=\"0\" Number=\"A\" JoystickType=\"1\" Power=\"50\" Invert=\"1\"/>"
                            + "</Joystick>"
                        + "</EV3>"
                ).getBytes("UTF-8"));
            } finally {
                fileOutputStream.close();
            }
        }
        //A second file is created here for debugging purposes only.
        /*ev3DefaultSettingsFile = new File(ev3Dir, DefaultValue.EV3_SETTINGS_FILE + ".xml");
        FileOutputStream fileOutputStream = new FileOutputStream(ev3DefaultSettingsFile);
        try {
            fileOutputStream.write(new String(
                    "<EV3 Name=\"" + context.getString(R.string.ev3_researcher) + " 2" + "\""
                            + " Description=\"" + context.getString(R.string.ev3_researcher_desc) + " 2" + "\""
                            + ">"
                            + "<Joystick Index=\"0\" Visible=\"1\" Shape=\"c\" Type=\"1\">"
                            + "<OutputPort Group=\"0\" Layer=\"0\" Number=\"B\"/>"
                            + "<OutputPort Group=\"1\" Layer=\"0\" Number=\"C\"/>"
                            + "</Joystick>"
                            + "<Joystick Index=\"1\" Visible=\"1\" Shape=\"v\" Type=\"0\" Behavior1=\"1\">"
                            + "<OutputPort Group=\"1\" Layer=\"0\" Number=\"A\" JoystickType=\"1\" Power=\"50\"/>"
                            + "</Joystick>"
                            + "</EV3>"
            ).getBytes("UTF-8"));
        } finally {
            fileOutputStream.close();
        }*/
    }

    class EV3Runnable implements Runnable{
        private EV3Driver driver = null;
        private int errorId = 0;
        private Object arg1 = null;
        private Object arg2 = null;

        EV3Runnable(EV3Driver driver){
            this.driver = driver;
        }

        private void test() {
            EV3ByteCodes c = new EV3ByteCodes();
            try {
                //c.messageHeader(DIRECT_COMMAND_REPLY, 0, 0);
                //c.opMailbox_Close();
                //c.LC0(1);
                //byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                //if (replyBytes != null) {

                //}

                c = new EV3ByteCodes();
                c.messageHeader(SYSTEM_COMMAND_NO_REPLY, EV3ByteCodes.WRITEMAILBOX);
                c.writeUByte("TestMB".length() + 1);
                c.writeString("TestMB");
                c.writeUShort(4);
                c.writeInteger(17);
                sendMessage(c);

                c = new EV3ByteCodes();
                c.messageHeader(DIRECT_COMMAND_REPLY, 5, 0);

                /*c.opMailbox_Write();
                c.LCS("");
                c.LC0(2);
                c.LCS("TestMB");
                c.LC0(0x02);
                c.LC1(1);
                c.LC4(17);*/

                c.opMailbox_Open();
                c.LC0(1);
                c.LCS("TestMB");
                c.LC0(0x02);
                c.LC0(0);
                c.LC1(1);

                c.opMailbox_Test();
                c.LC0(1);
                c.GV0(4);

                c.opMailbox_Read();
                c.LC0(1);
                c.LC0(4);
                c.LC0(1);
                c.GV0(0);

                c.opMailbox_Close();
                c.LC0(1);

                byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                if (replyBytes != null) {

                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            /*try {

            } catch (IOException e) {
                e.printStackTrace();
            }*/

            /*ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                writeMessageHeader(byteArrayOutputStream, DIRECT_COMMAND_REPLY, 4, 0);
                writeInputReadRAW(byteArrayOutputStream, 0, INPUT_PORT_A, 0, 0, 1, 0, -1);
                byte[] replyBytes = sendMessageAndReadReply(byteArrayOutputStream, 100);
                if (replyBytes != null && replyBytes.length >= 3 && replyBytes[2] == DIRECT_REPLY) {
                    ByteBuffer byteBuffer = ByteBuffer.wrap(replyBytes);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    int raw = byteBuffer.getInt(3);
                    if (raw == 0)
                        sendMessageAndReadReply(byteArrayOutputStream, 100);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }

        private void initPorts() {
            EV3ByteCodes c = new EV3ByteCodes();
            //ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try {
                c.messageHeader(DIRECT_COMMAND_REPLY, 16 * 4, 0);
                //writeMessageHeader(byteArrayOutputStream, DIRECT_COMMAND_REPLY, 16 * 4, 0);
                for (int layer = 0; layer < 4; layer++)
                    for (int i = 0; i < 8; i++) {
                        c.opInput_Device(EV3ByteCodes.GET_TYPEMODE);
                        c.LC0(layer);
                        c.LC0(i < 4 ? i : (i + 12));
                        c.GV(i * 2 + layer * 16);
                        c.GV(i * 2 + layer * 16 + 1);

                        //writeInputGetTypeMode(byteArrayOutputStream, layer,
                        //        i < 4 ? i : (i + 12),
                        //        i * 2 + layer * 16, //Device type variable index
                        //        i * 2 + layer * 16 + 1 //Device mode variable index
                        //);
                    }
                byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                c = null;
                if (replyBytes != null && replyBytes.length >= 3 && replyBytes[2] == DIRECT_REPLY) {
                    for (int layer = 0; layer < 4; layer++)
                        for (int i = 0; i < 8; i++) {
                            int type = replyBytes[3 + i * 2 + layer * 16];
                            int mode = replyBytes[3 + i * 2 + layer * 16 + 1];
                            if (type != INPUT_DEVICE_TYPE_EMPTY
                                    && type != INPUT_DEVICE_TYPE_INITIALIZING
                                    && type != INPUT_DEVICE_TYPE_UNKNOWN
                                    && type != INPUT_DEVICE_TYPE_WRONG) {
                                if (!usedLayers.contains(layer))
                                    usedLayers.add(layer);
                                int inputPortNumber = i < 4 ? i : (i + 12);
                                EV3InputPort inputPort = new EV3InputPort(layer, inputPortNumber,
                                        type, mode);
                                inputPorts.put(inputPort.getId(), inputPort);
                                if (inputPortNumber >= INPUT_PORT_A) /*A - D is output ports also*/ {
                                    EV3OutputPort outputPort = new EV3OutputPort(layer,
                                            getOutputPortNumberByInputPortNumber(inputPortNumber));
                                    outputPorts.put(outputPort.getId(), outputPort);
                                    c = createAPIAndWriteMessageHeader(c, DIRECT_COMMAND_NO_REPLY, 0, 0);
                                    c.opOutput_Clr_Count();
                                    c.LC0(layer);
                                    c.LC0(outputPort.getNumber());
                                    //writeOutputClrCount(byteArrayOutputStream, layer, outputPort.getNumber());
                                }
                            }
                        }
                    if (c != null)
                        sendMessage(c);
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            errorId = R.string.ev3_error_cannot_read_input_port_list;
        }

        private void load() {

            if (settingsFileName.equals(""))
                restoreDefaultSettings();

            Hashtable<String, Integer> joystickTypes = new Hashtable<String, Integer>();
            for (int i = 0; i < joysticks.size(); i++)
                for (int group = 0; group < 2; group++)
                    for (int j = 0; j < joysticks.get(i).getOutputPorts(group).size(); j++) {
                        EV3OutputPort joystickPort = joysticks.get(i).getOutputPorts(group).get(j);
                        if (!joystickTypes.containsKey(joystickPort.getId()))
                            joystickTypes.put(joystickPort.getId(), joystickPort.getJoystickType());
                        else if (!joystickTypes.get(joystickPort.getId()).equals(joystickPort.getJoystickType())) {
                            errorId = R.string.ev3_error_joysticks_have_different_types_for_same_port;
                            arg1 = joystickPort.getNumberDesc();
                        }
                        if (!outputPorts.containsKey(joystickPort.getId())) {
                            errorId = R.string.ev3_error_motor_not_connected;
                            arg1 = joystickPort.getNumberDesc();
                            return;
                        } else {
                            outputPorts.get(joystickPort.getId()).setJoystickType(joystickPort.getJoystickType());
                            if (joystickPort.getJoystickType() == JOYSTICK_TYPE_ANGLE) {
                                outputPorts.get(joystickPort.getId()).setPower(joystickPort.getPower());
                                angleOutputPorts.add(outputPorts.get(joystickPort.getId()));
                            }
                        }
                    }
        }

        private void runBrickPrograms() {
            if (angleOutputPorts.size() > 0) {
                try {
                    //Supporting brick program
                    EV3ByteCodes prg = new EV3ByteCodes();
                    prg.PROGRAMHeader(1.08, 1, 0);
                    prg.VMTHREADHeader(0, angleOutputPorts.size() * (4 + 4 + 4 + 1) + 7); //Thread 1

                    int arrJoystickAngles = 0; //Index of the first entry in the data32 array
                    int arrCurrentAngles = angleOutputPorts.size() * 4; //Index of the first entry in the data32 array
                    int arrStopAngles = angleOutputPorts.size() * (4 + 4); //Index of the first entry in the data32 array
                    int arrDirections = angleOutputPorts.size() * (4 + 4 + 4); //Index of the first entry in the data8 array
                    int varMailboxTestResult = angleOutputPorts.size() * (4 + 4 + 4 + 1); //Index of data8 variable
                    int varTemp1 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 1; //Index of data8 variable
                    int varTemp2 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 2; //Index of data8 variable
                    int varTemp3 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 3; //Index of data8 variable
                    int varTemp4 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 4; //Index of data8 variable
                    int varTemp5 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 5; //Index of data8 variable
                    int varTemp6 = angleOutputPorts.size() * (4 + 4 + 4 + 1) + 6; //Index of data8 variable

                    prg.opMailbox_Open(); //Opens the mailbox to read from it
                    prg.LC0(MAILBOX_ID);
                    prg.LCS(MAILBOX_NAME);
                    prg.LC0(0x02); //Data32
                    prg.LC0(0);
                    prg.LC1(angleOutputPorts.size());

                    prg.opUI_WRITE(LED);
                    prg.LC0(EV3ByteCodes.LED_RED);

                    int addrMainLoop = prg.getSize();

                    prg.opMailbox_Test(); //Checks if the mailbox contains angles
                    prg.LC0(MAILBOX_ID);
                    prg.LV2(varMailboxTestResult);

                    prg.opJr_True(); //Skips reading if the mailbox is empty. Go to [addrReading]
                    prg.LV2(varMailboxTestResult);
                    prg.LC4(0);
                    int addrAfterReading = prg.getSize();

                    /*prg.opMailbox_Ready();
                    prg.LC0(MAILBOX_ID);*/

                    prg.opMailbox_Read(); //Reads angles from the mailbox
                    prg.LC0(MAILBOX_ID);
                    prg.LC1(angleOutputPorts.size() * 4);
                    prg.LC1(angleOutputPorts.size());
                    prg.LV2(arrJoystickAngles);

                    //------------------------------------------------------------------------------
                    prg.opJr_Gt32(); //If angle > 99, blink in red, otherwise in green
                    prg.LV2(arrJoystickAngles);
                    prg.LC4(10);
                    prg.LC4(9);

                    prg.opUI_WRITE(LED);
                    prg.LC0(EV3ByteCodes.LED_GREEN_PULSE);

                    prg.opJr();
                    prg.LC4(3);

                    prg.opUI_WRITE(LED);
                    prg.LC0(EV3ByteCodes.LED_RED_PULSE);
                    //------------------------------------------------------------------------------

                    prg.changeInteger(addrAfterReading - 4, prg.getSize() - addrAfterReading); //[addrReading]

                    for (int i = 0; i < angleOutputPorts.size(); i++) {
                        EV3OutputPort outputPort = angleOutputPorts.get(i);

                        /*prg.opInput_Device(EV3ByteCodes.READY_SI);
                        prg.LC0(outputPort.getLayer()); //LAYER
                        prg.LC0(getInputPortNumberByOutputPortNumber(outputPort.getNumber())); //NO
                        prg.LC0(0); //TYPE
                        prg.LC0(0); //MODE
                        prg.LC0(1); //VALUES
                        prg.LV2(arrCurrentAngles + i);*/

//                            if ((outputPort.getMotorDirection() > 0
//                                    && currentAngle >= joystickAngle)
//                                    || (outputPort.getMotorDirection() < 0
//                                    && currentAngle <= joystickAngle)) {
//                                motorsToStop.add(outputPort);
//                            }


                        /*prg.opCp_Gt8(); //If motor direction > 0 -> temp1
                        prg.LV2(arrDirections + i);
                        prg.LC0(0);
                        prg.LV2(varTemp1);

                        prg.opCp_GteqF(); //If current angle >= joystick angle -> temp2
                        prg.LV2(arrCurrentAngles + i);
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(varTemp2);

                        prg.opCp_Lt8(); //If motor direction < 0 -> temp3
                        prg.LV2(arrDirections + i);
                        prg.LC0(0);
                        prg.LV2(varTemp3);

                        prg.opCp_LteqF(); //If current angle <= joystick angle -> temp4
                        prg.LV2(arrCurrentAngles + i);
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(varTemp4);

                        prg.opAnd8(); //temp1 and temp2 -> temp5
                        prg.LV2(varTemp1);
                        prg.LV2(varTemp2);
                        prg.LV2(varTemp5);

                        prg.opAnd8(); //temp3 and temp4 -> temp6
                        prg.LV2(varTemp3);
                        prg.LV2(varTemp4);
                        prg.LV2(varTemp6);

                        prg.opOr8(); //temp5 or temp6 -> temp1
                        prg.LV2(varTemp5);
                        prg.LV2(varTemp6);
                        prg.LV2(varTemp1);

                        prg.opJr_False(); //Skips stopping the motor if false. Go to [afterMotorStop]
                        prg.LV2(varTemp1);
                        prg.LC4(0);
                        int afterMotorStop = prg.getSize();

                        prg.opOutput_Stop(); //Stops the motor
                        prg.LC0(outputPort.getLayer());
                        prg.LC0(outputPort.getNumber());
                        prg.LC0(outputPort.getBrake());

                        prg.opMove8_8(); //Set motor direction to 0
                        prg.LC0(0);
                        prg.LV2(arrDirections + i);

                        prg.changeInteger(afterMotorStop - 4, prg.getSize() - afterMotorStop); //[afterMotorStop]

                        prg.opJr_Neq8(); //If the motor direction is not 0. Go to [afterMotorStart]
                        prg.LV2(arrDirections + i);
                        prg.LC0(0);
                        prg.LC4(0);
                        int afterMotorStart = prg.getSize();

//                        if (outputPort.getMotorDirection() == 0
//                                && joystickAngle > currentAngle
//                                && joystickAngle > outputPort.getStopAngle()) {
//                            motorsToRotateRight.add(outputPort);
//                            outputPort.setStopAngle(joystickAngle);
//                            outputPort.setMotorDirection(1);
//                            hasMovingMotors = true;
//                        }

                        prg.opCp_GtF(); //If joystick angle > current angle -> temp1
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrCurrentAngles + i);
                        prg.LV2(varTemp1);

                        prg.opCp_GtF(); //If joystick angle > stop angle -> temp2
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrStopAngles + i);
                        prg.LV2(varTemp2);

                        prg.opAnd8(); //temp1 and temp2 -> temp3
                        prg.LV2(varTemp1);
                        prg.LV2(varTemp2);
                        prg.LV2(varTemp3);

                        prg.opJr_False(); //Skips starting the motor if false. Go to [afterMotorStartRight]
                        prg.LV2(varTemp3);
                        prg.LC4(0);
                        int afterMotorStartRight = prg.getSize();

                        prg.opMoveF_F(); //Sets joystick angle to stop angle
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrStopAngles + i);

                        prg.opMove8_8(); //Sets motor direction to 1
                        prg.LC0(1);
                        prg.LV2(arrDirections + i);

                        prg.opOutput_Power(); //Sets the motor power
                        prg.LC0(outputPort.getLayer());
                        prg.LC0(outputPort.getNumber());
                        prg.LC1(Math.abs(outputPort.getPreparedPower()));

                        prg.opOutput_Start(); //Starts the motor
                        prg.LC0(outputPort.getLayer());
                        prg.LC0(outputPort.getNumber());

                        prg.opJr();
                        prg.LC4(0);
                        int afterMotorStart2 = prg.getSize();

                        prg.changeInteger(afterMotorStartRight - 4, prg.getSize() - afterMotorStartRight); //[afterMotorStartRight]

//                        if (outputPort.getMotorDirection() == 0
//                                && joystickAngle < currentAngle
//                                && joystickAngle < outputPort.getStopAngle()) {
//                            motorsToRotateLeft.add(outputPort);
//                            outputPort.setStopAngle(joystickAngle);
//                            outputPort.setMotorDirection(-1);
//                            hasMovingMotors = true;
//                        }

                        prg.opCp_LtF(); //If joystick angle < current angle -> temp1
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrCurrentAngles + i);
                        prg.LV2(varTemp1);

                        prg.opCp_LtF(); //If joystick angle < stop angle -> temp2
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrStopAngles + i);
                        prg.LV2(varTemp2);

                        prg.opAnd8(); //temp1 and temp2 -> temp3
                        prg.LV2(varTemp1);
                        prg.LV2(varTemp2);
                        prg.LV2(varTemp3);

                        prg.opJr_False(); //Skips starting the motor if false. Go to [afterMotorStartLeft]
                        prg.LV2(varTemp3);
                        prg.LC4(0);
                        int afterMotorStartLeft = prg.getSize();

                        prg.opMoveF_F(); //Sets joystick angle to stop angle
                        prg.LV2(arrJoystickAngles + i);
                        prg.LV2(arrStopAngles + i);

                        prg.opMove8_8(); //Sets motor direction to -1
                        prg.LC1(-1);
                        prg.LV2(arrDirections + i);

                        prg.opOutput_Power(); //Sets the motor power
                        prg.LC0(outputPort.getLayer());
                        prg.LC0(outputPort.getNumber());
                        prg.LC1(Math.abs(outputPort.getPreparedPower()) * -1);

                        prg.opOutput_Start(); //Starts the motor
                        prg.LC0(outputPort.getLayer());
                        prg.LC0(outputPort.getNumber());

                        prg.changeInteger(afterMotorStartLeft - 4, prg.getSize() - afterMotorStartLeft); //[afterMotorStartLeft]
                        prg.changeInteger(afterMotorStart2 - 4, prg.getSize() - afterMotorStart2); //[afterMotorStart2]
                        prg.changeInteger(afterMotorStart - 4, prg.getSize() - afterMotorStart); //[afterMotorStart]
                        */

                    }

                    prg.opJr();
                    prg.LC4(addrMainLoop - prg.getSize() - 5);

                    prg.opObject_End();

                    //Writing the program into the brick
                    EV3ByteCodes c = new EV3ByteCodes();
                    c.messageHeader(SYSTEM_COMMAND_REPLY, EV3ByteCodes.BEGIN_DOWNLOAD);
                    c.writeInteger(prg.getSize());
                    c.writeString("../prjs/RoboCam/RoboCam.rpf");
                    byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                    if (replyBytes != null && replyBytes.length >= 6) {
                        byte returnStatus = replyBytes[4];
                        byte handleToFile = replyBytes[5];
                        if (returnStatus == EV3ByteCodes.SUCCESS) {
                            c.clear();
                            c.messageHeader(SYSTEM_COMMAND_REPLY, EV3ByteCodes.CONTINUE_DOWNLOAD);
                            c.writeByte(handleToFile);
                            c.writeByteCodes(prg);
                            replyBytes = sendMessageAndReadReply(c, 1000);
                            if (replyBytes != null && replyBytes.length >= 6) {
                                returnStatus = replyBytes[4];
                                if (returnStatus == EV3ByteCodes.END_OF_FILE) {
                                    c.clear();
                                    c.messageHeader(DIRECT_COMMAND_REPLY, 0, 8);
                                    c.opFile(EV3ByteCodes.LOAD_IMAGE);
                                    c.LC2(EV3ByteCodes.USER_SLOT);
                                    c.LCS("../prjs/RoboCam/RoboCam.rpf");
                                    c.LV0(0);
                                    c.LV0(4);
                                    c.opPROGRAM_START();
                                    c.LC2(EV3ByteCodes.USER_SLOT);
                                    c.LV0(0);
                                    c.LV0(4);
                                    c.LC0(EV3ByteCodes.DEBUG_MODE_NORMAL);
                                    replyBytes = sendMessageAndReadReply(c, 1000);
                                    if (replyBytes != null && replyBytes.length >= 3 && replyBytes[2] == DIRECT_REPLY)
                                        return;
                                    else {
                                        errorId = R.string.ev3_error_could_not_start_supporting_program;
                                        return;
                                    }
                                }
                            }
                        }
                    }
                    errorId = R.string.ev3_error_cannot_write_supporting_program;
                } catch (IOException e) {
                    e.printStackTrace();
                    errorId = R.string.ev3_error_cannot_write_supporting_program;
                }



                /*EV3ByteCodes c = new EV3ByteCodes();
                //ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                try {
                    int varCount = 5;
                    int usedMemory = varCount * 4;
                    c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, angleTypeCount * usedMemory);

                    //writeMessageHeader(byteArrayOutputStream, DIRECT_COMMAND_NO_REPLY, 0, angleTypeCount * usedMemory);

                    int portIndex = 0;
                    for (Enumeration<String> enumerator = outputPorts.keys(); enumerator.hasMoreElements(); ) {
                        EV3OutputPort outputPort = outputPorts.get(enumerator.nextElement());
                        outputPort.setMailboxId(portIndex);
                        if (outputPort.getJoystickType() == JOYSTICK_TYPE_ANGLE) {

                            int varIndexPower = portIndex * usedMemory;
                            int varIndexCurAngle = portIndex * usedMemory + 4;
                            int varIndexJoystickAngle = portIndex * usedMemory + 8;
                            int varIndexTerminated = portIndex * usedMemory + 12;
                            int varIndexTimer = portIndex * usedMemory + 16;*/

                        /*writeCommand(byteArrayOutputStream, OP_MOVE_32_32); //opMove32_32
                        writeParameterAsInteger(byteArrayOutputStream, outputPort.getPreparedPower()); //SOURCE
                        writeLocalIndex1(byteArrayOutputStream, varIndexPower); //DESTINATION

                        writeCommand(byteArrayOutputStream, OP_MAILBOX_OPEN); //opMailbox_Open
                        writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getMailboxId()); //NO
                        byte[] mailboxName = outputPort.getMailboxName().getBytes("US-ASCII");
                        byteArrayOutputStream.write(mailboxName); //BOXNAME
                        byteArrayOutputStream.write(0); //BOXNAME (Zero terminated string)
                        writeParameterAsSmallByte(byteArrayOutputStream, 0x02); //TYPE (0x02 : Data32 32-bit integer value)
                        writeParameterAsSmallByte(byteArrayOutputStream, 0); //FIFOSIZE
                        writeParameterAsSmallByte(byteArrayOutputStream, 1); //VALUES

                        writeCommand(byteArrayOutputStream, OP_INPUT_DEVICE, READY_RAW); //opInput_Device
                        writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getLayer()); //LAYER
                        writeParameterAsSmallByte(byteArrayOutputStream,
                                getInputPortNumberByOutputPortNumber(outputPort.getNumber())); //NO
                        writeParameterAsSmallByte(byteArrayOutputStream, 0); //TYPE
                        writeParameterAsSmallByte(byteArrayOutputStream, 0); //MODE
                        writeParameterAsSmallByte(byteArrayOutputStream, 1); //VALUES
                        writeLocalIndex1(byteArrayOutputStream, varIndexCurAngle);*/

                           // c.opUI_WRITE(LED);
                            //c.LC0(EV3ByteCodes.LED_RED);

                            //writeCommand(byteArrayOutputStream, OP_UI_WRITE, LED); //opUI_WRITE
                            //writeParameterAsSmallByte(byteArrayOutputStream, LED_RED); //PATTERN

                            //c.opTimer_Wait();
                            //c.LC4(1000);            //TIME
                            //c.LV1(varIndexTimer);   //TIMER

                            //writeCommand(byteArrayOutputStream, OP_TIMER_WAIT); //opTimer_Wait
                            //writeParameterAsInteger(byteArrayOutputStream, 1000); //TIME
                            //writeLocalIndex1(byteArrayOutputStream, varIndexTimer); //TIMER

                            //c.opTimer_Ready();
                            //c.LV1(varIndexTimer);   //TIMER

                            //writeCommand(byteArrayOutputStream, OP_TIMER_READY); //opTimer_Ready
                            //writeLocalIndex1(byteArrayOutputStream, varIndexTimer); //TIMER

                            //c.opUI_WRITE(LED);
                            //c.LC0(EV3ByteCodes.LED_GREEN);   //TIMER

                            //writeCommand(byteArrayOutputStream, OP_UI_WRITE, LED); //opUI_WRITE
                            //writeParameterAsSmallByte(byteArrayOutputStream, LED_GREEN); //PATTERN

                        /*writeCommand(byteArrayOutputStream, OP_MAILBOX_CLOSE); //opMailbox_Close
                        writeParameterAsSmallByte(byteArrayOutputStream, outputPort.getMailboxId()); //NO*/

                        //}
                    //}
                    //sendMessage(c);
                //} catch (IOException e) {
                //    e.printStackTrace();
                //}
            }
        }

        private void showConnected() {
            EV3ByteCodes c = new EV3ByteCodes();
            try {
                c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, 0);
                c.opUI_WRITE(LED);
                c.LC0(EV3ByteCodes.LED_RED_PULSE);
                sendMessage(c);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                driver.socket = driver.ev3device.createRfcommSocketToServiceRecord(
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                int counter = 0;
                while(true) {
                    try {
                        if (socketState == SOCKET_ABORTED)
                            break;
                        driver.socket.connect();
                        break;
                    } catch (IOException e) {
                        if (counter >= 7 || !e.getMessage().equals("Connection refused"))
                            throw e;
                        Thread.sleep(3000);
                        counter++;
                    }
                }
                if (socketState != SOCKET_ABORTED)
                    initPorts();
                //test();
                if (errorId == 0 && socketState != SOCKET_ABORTED)
                    load();
                //if (errorId == 0 && socketState != SOCKET_ABORTED)
                //    runBrickPrograms();
                if (socketState != SOCKET_ABORTED) {
                    if (errorId == 0) {
                        startReadingInputPorts();
                        startReadingJoystickValues();
                        showConnected();
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
            }
            catch (Exception e) {
                //java.io.IOException: read failed, socket might closed or timeout, read ret: -1
                if (socketState != SOCKET_ABORTED) {
                    driver.doOnConnectionError(R.string.robot_connection_error);
                    //driver.doOnConnectionError(e.getLocalizedMessage());
                    driver.close();
                }
            }
        }

    }

    private void clearJoysticks() {
        for (int i = 0; i < joysticks.size(); i++)
            for (int group = 0; group < 2; group++) {
                joysticks.get(i).getOutputPorts(group).clear();
                joysticks.get(i).setVisible(false);
            }
    }

    private void startReadingJoystickValues() {
        new Thread(new EV3JoystickValuesReader()).start();
    }

    class EV3JoystickValuesReader implements Runnable {
        private EV3Driver driver = null;

        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (joystickMonitor) {
                        if (socketState == SOCKET_ABORTED || socket == null)
                            break;
                        joystickMonitor.wait(100);
                        if (socketState == SOCKET_ABORTED || socket == null)
                            break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                setJoystickValues();
            }
        }

        public void setJoystickValues() {
            while (true) {
                if (socketState == SOCKET_ABORTED || socket == null)
                    break;
                Hashtable<String, Integer> newJoystickValues = null;
                synchronized (joystickSyncObject) {
                    if (joystickValues.size() > 0) { //We have the joystick message
                        newJoystickValues = (Hashtable<String, Integer>) joystickValues.clone();
                        joystickValues.clear();
                    }
                }
                if (newJoystickValues == null)
                    break;
                else
                    setJoystickValues(newJoystickValues);
            }
        }

        public void setJoystickValues(Hashtable<String, Integer> newJoystickValues) {
            if (socketState == SOCKET_ABORTED && newJoystickValues != null)
                return;
            try {
                if (socket != null) {
                    Hashtable<String, EV3OutputPort> ports = new Hashtable<String, EV3OutputPort>();
                    for (int i = 0; i < 4; i++) {
                        if (newJoystickValues != null) {
                            if (newJoystickValues.containsKey(axisNames[i * 2])
                                    || newJoystickValues.containsKey(axisNames[i * 2 + 1])) {
                                Integer newX = newJoystickValues.containsKey(axisNames[i * 2])
                                        ? newJoystickValues.get(axisNames[i * 2]) : null;
                                Integer newY = newJoystickValues.containsKey(axisNames[i * 2 + 1])
                                        ? newJoystickValues.get(axisNames[i * 2 + 1]) : null;
                                joysticks.get(i).setCoordinates(newX, newY);
                            }
                        } else {
                            joysticks.get(i).setCoordinates(0, 0);
                        }
                        //Calculating sum of port values
                        for (int j = 0; j < 2; j++)
                            for (EV3OutputPort port : joysticks.get(i).getOutputPorts(j)) {
                                String portId = port.getId();
                                if (!ports.containsKey(portId))
                                    ports.put(portId, port.getPreparedPort());
                                else
                                    ports.get(portId).addPort(port);
                            }
                    }
                    //Searching for changes
                    List<String> portsToDelete = new ArrayList<String>();
                    for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                        String key = enumerator.nextElement();
                        if (outputPorts.containsKey(key) && ports.get(key).portEquals(outputPorts.get(key)))
                            portsToDelete.add(key);
                    }
                    for (String key : portsToDelete)
                        ports.remove(key);
                    int localBytes = 0;
                    int globalBytes = angleOutputPorts.size() * (4 + 4 + 1);
                    int commandType = DIRECT_COMMAND_REPLY;
                    if (ports.size() > 0) {
                        EV3ByteCodes c = null; //Direct commands
                        //EV3ByteCodes s = null; //System commands
                        //ByteArrayOutputStream byteArrayOutputStream = null;
                        try {
                            //Starts motors
                            for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                                EV3OutputPort port = ports.get(enumerator.nextElement());
                                if (port.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER
                                        && port.getPower() != 0) {
                                    c = createAPIAndWriteMessageHeader(c, commandType, globalBytes, localBytes);
                                    c.opOutput_Power();
                                    c.LC0(port.getLayer());
                                    c.LC0(port.getNumber());
                                    c.LC1(port.getPreparedPower());

                                    //writeOutputSetPowerCommand(byteArrayOutputStream, port.getLayer(),
                                    //        port.getNumber(), port.getPreparedPower());

                                    c.opOutput_Start();
                                    c.LC0(port.getLayer());
                                    c.LC0(port.getNumber());

                                    //writeOutputStartCommand(byteArrayOutputStream, port.getLayer(),
                                    //        port.getNumber());
                                }
                            }
                            //Stops motors
                            for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                                EV3OutputPort port = ports.get(enumerator.nextElement());
                                if (port.getJoystickType() == EV3Driver.JOYSTICK_TYPE_POWER
                                        && port.getPower() == 0) {
                                    c = createAPIAndWriteMessageHeader(c, commandType, globalBytes, localBytes);
                                    c.opOutput_Stop();
                                    c.LC0(port.getLayer());
                                    c.LC0(port.getNumber());
                                    c.LC0(port.getBrake());

                                    //writeOutputStopCommand(byteArrayOutputStream, port.getLayer(),
                                    //        port.getNumber(), port.getBrake());
                                }
                            }
                            //Rotate motors
                        /*if (angleOutputPorts.size() > 0) {
                            boolean angleChanged = false;
                            for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                                String key = enumerator.nextElement();
                                EV3OutputPort port = ports.get(key);
                                if (port.getJoystickType() == EV3Driver.JOYSTICK_TYPE_ANGLE
                                        && outputPorts.containsKey(port.getId())) {
                                    EV3OutputPort outputPort = outputPorts.get(port.getId());
                                    if (port.getAngle() != outputPort.getAngle()) {
                                        angleChanged = true;
                                        break;
                                    }
                                }
                            }*/
                            //for (EV3OutputPort outputPort : ) {

                            for (int i = 0; i < angleOutputPorts.size(); i++) {
                                if (ports.containsKey(angleOutputPorts.get(i).getId())) {
                                    int arrAngles = 0;
                                    int arrPowers = angleOutputPorts.size() * (4 + 4);
                                    c = createAPIAndWriteMessageHeader(c, commandType, globalBytes, localBytes);
                                    EV3OutputPort port = ports.get(angleOutputPorts.get(i).getId());
                                    int varAngle1 = arrAngles + (i * 8);
                                    int varAngle2 = arrAngles + (i * 8) + 4;
                                    int varPower = arrPowers + i;

                                    c.opOutput_Stop();
                                    c.LC0(port.getLayer());
                                    c.LC0(port.getNumber());
                                    c.LC0(port.getBrake());

                                    c.opInput_Device(EV3ByteCodes.READY_RAW);
                                    c.LC0(port.getLayer());
                                    c.LC0(getInputPortNumberByOutputPortNumber(port.getNumber()));
                                    c.LC0(0);
                                    c.LC0(0);
                                    c.LC0(1);
                                    c.GV1(varAngle1);

                                    c.opSub32();
                                    c.LC4(Math.round(port.getAngle()));
                                    c.GV1(varAngle1);
                                    c.GV1(varAngle2);

                                    c.opMove8_8();
                                    c.LC1(port.getPreparedPower());
                                    c.GV1(varPower);

                                    c.opJr_Gteq32();
                                    c.GV1(varAngle2);
                                    c.LC4(0);
                                    c.LC4(0);
                                    int afterMul = c.getSize();

                                    c.opMul8();
                                    c.LC1(-1);
                                    c.GV1(varPower);
                                    c.GV1(varPower);

                                    c.opMul32();
                                    c.LC4(-1);
                                    c.GV1(varAngle2);
                                    c.GV1(varAngle2);

                                    c.changeInteger(afterMul - 4, c.getSize() - afterMul);

                                    c.opOutput_Step_Speed();
                                    c.LC0(port.getLayer());
                                    c.LC0(port.getNumber());
                                    c.GV1(varPower);
                                    c.LC4(0);
                                    c.GV1(varAngle2);
                                    c.LC4(0);
                                    c.LC0(port.getBrake());



                                    /*writeCommand(byteArrayOutputStream, OP_INPUT_DEVICE, READY_RAW);
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getLayer()); //LAYER
                                    writeParameterAsSmallByte(byteArrayOutputStream,
                                            getInputPortNumberByOutputPortNumber(port.getNumber())); //NO
                                    writeParameterAsSmallByte(byteArrayOutputStream, 0); //TYPE
                                    writeParameterAsSmallByte(byteArrayOutputStream, 0); //MODE
                                    writeParameterAsSmallByte(byteArrayOutputStream, 1); //VALUES
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8);

                                    writeCommand(byteArrayOutputStream, OP_SUB_32); //opSub32
                                    writeParameterAsInteger(byteArrayOutputStream, Math.round(port.getAngle())); //SOURCE1
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //SOURCE2
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //DESTINATION

                                    writeCommand(byteArrayOutputStream, OP_MOVE_32_32);
                                    writeParameterAsInteger(byteArrayOutputStream, port.getPreparedPower()); //SOURCE
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION

                                    writeCommand(byteArrayOutputStream, OP_JR_GTEQ_32); //opJr_Gteq32
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //LEFT
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //RIGHT
                                    writeParameterAsInteger(byteArrayOutputStream, 10); //OFFSET                - offset - 10 bytes

                                    writeCommand(byteArrayOutputStream, OP_MUL_32); //opMul32                   - 1 byte
                                    writeParameterAsInteger(byteArrayOutputStream, -1); //SOURCE1               - 5 bytes
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SOURCE2      - 2 bytes
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION  - 2 bytes

                                    writeCommand(byteArrayOutputStream, OP_OUTPUT_STEP_SPEED); //Opcode
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getLayer()); //LAYER
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getNumber()); //NOS
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SPEED
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //STEP1
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //STEP2
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //STEP3
                                    writeParameterAsUByte(byteArrayOutputStream, port.getBrake()); //BRAKE*/
                                }
                            }
                            //}
                            //}
                        /*if (angleOutputPorts.size() > 0) {
                            boolean angleChanged = false;
                            for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                                String key = enumerator.nextElement();
                                EV3OutputPort port = ports.get(key);
                                if (port.getJoystickType() == EV3Driver.JOYSTICK_TYPE_ANGLE
                                        && outputPorts.containsKey(port.getId())) {
                                    EV3OutputPort outputPort = outputPorts.get(port.getId());
                                    if (port.getAngle() != outputPort.getAngle()) {
                                        angleChanged = true;
                                        break;
                                    }
                                }
                            }
                            if (angleChanged) {
                                s = createAPIAndWriteMessageHeader(s, SYSTEM_COMMAND_NO_REPLY, EV3ByteCodes.WRITEMAILBOX);
                                s.writeUByte(MAILBOX_NAME.length());
                                s.writeString(MAILBOX_NAME);
                                s.writeUShort(angleOutputPorts.size() * 4);
                                for (int i = 0; i < angleOutputPorts.size(); i++) {
                                    EV3OutputPort port;
                                    if (ports.containsKey(angleOutputPorts.get(i).getId()))
                                        port = ports.get(angleOutputPorts.get(i).getId());
                                    else
                                        port = outputPorts.get(angleOutputPorts.get(i).getId());
                                    s.writeInteger(Math.round(port.getAngle()));
                                }
                            }
                        }*/
                        /*int portIndex = 0;
                        for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); portIndex++) {
                            String key = enumerator.nextElement();
                            EV3OutputPort port = ports.get(key);
                            //EV3InputPort inputPort = inputPorts.get(port.getId());
                            if (port.getJoystickType() == EV3Driver.JOYSTICK_TYPE_ANGLE
                                    && outputPorts.containsKey(port.getId())) {
                                EV3OutputPort outputPort = outputPorts.get(port.getId());
                                int newAngle = Math.round(port.getAngle() / 10) * 10;
                                if (newAngle != outputPort.getOldSentAngle()) {
                                    outputPort.setOldSentAngle(newAngle);
                                    c = createAPIAndWriteMessageHeader(c, DIRECT_COMMAND_NO_REPLY, 0, 0);


                                    byteArrayOutputStream = createByteArrayOutputStreamAndWriteMessageHeader(
                                            byteArrayOutputStream, DIRECT_COMMAND_REPLY, 32, 32);

                                    writeCommand(byteArrayOutputStream, OP_INPUT_DEVICE, READY_RAW);
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getLayer()); //LAYER
                                    writeParameterAsSmallByte(byteArrayOutputStream,
                                            getInputPortNumberByOutputPortNumber(port.getNumber())); //NO
                                    writeParameterAsSmallByte(byteArrayOutputStream, 0); //TYPE
                                    writeParameterAsSmallByte(byteArrayOutputStream, 0); //MODE
                                    writeParameterAsSmallByte(byteArrayOutputStream, 1); //VALUES
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8);

                                    writeCommand(byteArrayOutputStream, OP_SUB_32); //opSub32
                                    writeParameterAsInteger(byteArrayOutputStream, Math.round(port.getAngle())); //SOURCE1
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //SOURCE2
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //DESTINATION

                                    writeCommand(byteArrayOutputStream, OP_MOVE_32_32);
                                    writeParameterAsInteger(byteArrayOutputStream, port.getPreparedPower()); //SOURCE
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION

                                    writeCommand(byteArrayOutputStream, OP_JR_GTEQ_32); //opJr_Gteq32
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //LEFT
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //RIGHT
                                    writeParameterAsInteger(byteArrayOutputStream, 10); //OFFSET                - offset - 10 bytes

                                    writeCommand(byteArrayOutputStream, OP_MUL_32); //opMul32                   - 1 byte
                                    writeParameterAsInteger(byteArrayOutputStream, -1); //SOURCE1               - 5 bytes
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SOURCE2      - 2 bytes
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //DESTINATION  - 2 bytes

                                    writeCommand(byteArrayOutputStream, OP_OUTPUT_STEP_SPEED); //Opcode
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getLayer()); //LAYER
                                    writeParameterAsSmallByte(byteArrayOutputStream, port.getNumber()); //NOS
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8 + 4); //SPEED
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //STEP1
                                    writeGlobalIndex1(byteArrayOutputStream, portIndex * 8); //STEP2
                                    writeParameterAsInteger(byteArrayOutputStream, 0); //STEP3
                                    writeParameterAsUByte(byteArrayOutputStream, port.getBrake()); //BRAKE
                                }
                                //writeInputReadSI();
                            }
                        }*/

                    /*for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                        String key = enumerator.nextElement();
                        EV3OutputPort port = ports.get(key);
                        EV3InputPort inputPort = inputPorts.get(port.getId());
                        if (port.getType() == EV3Driver.OUTPUT_PORT_TYPE_ANGLE
                                && inputPorts != null
                                && (inputPort.getType() == INPUT_DEVICE_TYPE_EV3_LARGE_MOTOR
                                    || inputPort.getType() == INPUT_DEVICE_TYPE_EV3_MEDIUM_MOTOR)
                                && port.getAngle() != inputPort.getValue()) {
                            ;lk


                            int oldAngle = oldPorts.containsKey(key)
                                    ? Math.round(oldPorts.get(key).getAngle()
                                        / (float)oldPorts.get(key).getPreparedCount())
                                    : 0;
                            int angle = Math.round(port.getAngle() / (float)port.getPreparedCount());
                            int diff = angle - oldAngle;
                            int power = Math.round((float)port.getPower() / (float)port.getPreparedCount());
                            if (power > 100)
                                power = 100;
                            if (diff < 0) {
                                power *= -1;
                                diff *= -1;
                            }
                            if (diff != 0) {
                                writeOutputReady(byteArrayOutputStream, port.getLayer(),
                                        port.getNumber());
                                writeOutputStepSpeed(byteArrayOutputStream, port.getLayer(),
                                        port.getNumber(), power, 0, diff, 0,
                                        port.getBrake());
                            }
                        }
                    }*/
                            //EV3ByteCodes s = new EV3ByteCodes();
                            //s.messageHeader(SYSTEM_COMMAND_NO_REPLY, EV3ByteCodes.WRITEMAILBOX);
                            //s.writeString();
                            //bbbb = bytes in the message
                            //mmmm = message counter
                            //tt = type of message
                            //ss = system command
                            //ll = name Length
                            //aaa... = name
                            //LLLL = payload length
                            //ppp... = payload


                            //---
                        /*if (byteArrayOutputStream != null) {
                            byte[] replyBytes = sendMessageAndReadReply(byteArrayOutputStream, 100);
                            if (replyBytes != null && replyBytes.length >= 3 && replyBytes[2] == DIRECT_REPLY) {
                                ByteBuffer byteBuffer = ByteBuffer.wrap(replyBytes);
                                byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                                int value1 = byteBuffer.getInt(3);
                                int value2 = byteBuffer.getInt(3 + 4);
                                if (value1 > value2) {

                                }
                            }
                        }*/
                        /*if (c != null)
                            sendMessage(c);*/
                        /*if (s != null)
                            sendMessage(s);
                            */
                            if (c != null) {
                                byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                                if (replyBytes != null) {
                                    Log.d("RoboCam", "Mesage is sent");
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    for (Enumeration<String> enumerator = ports.keys(); enumerator.hasMoreElements(); ) {
                        String key = enumerator.nextElement();
                        EV3OutputPort outputPort = outputPorts.get(key);
                        EV3OutputPort joystickPort = ports.get(key);
                        outputPort.setInvert(joystickPort.isInvert());
                        outputPort.setCoefficient(joystickPort.getCoefficient());
                        outputPort.setPower(joystickPort.getPower());
                        float oldAngle = outputPort.getAngle();
                        float newAngle = joystickPort.getAngle();
                        outputPort.setAngle(newAngle);
                        outputPort.setBrake(joystickPort.getBrake());
                        outputPort.setJoystickType(joystickPort.getJoystickType());
                        if (newAngle != oldAngle)
                            hasMovingMotors = true;
                    }
                    //Mailbox
                    for (int i = 0; i < joysticks.size(); i++) {
                        if (joysticks.get(i).getType() == RoboCamDriver.JOYSTICK_TYPE_MAILBOX) {
                            if (joystickCoordinates[i * 2] != joysticks.get(i).getX()) {
                                //Have to send coordinate
                                SendJoystickCoordinateToMailbox(axisNames[i * 2], joysticks.get(i).getX());
                                joystickCoordinates[i * 2] = joysticks.get(i).getX();
                            }
                            if (joystickCoordinates[i * 2 + 1] != joysticks.get(i).getY()) {
                                //Have to send coordinate
                                SendJoystickCoordinateToMailbox(axisNames[i * 2 + 1], joysticks.get(i).getY());
                                joystickCoordinates[i * 2 + 1] = joysticks.get(i).getY();
                            }
                        }
                    }

                }
            }
            catch(Throwable e) {
                e.printStackTrace();
            }
        }

        private void SendJoystickCoordinateToMailbox(String axisName, int value) {
            try {
                EV3ByteCodes s = new EV3ByteCodes();
                s.messageHeader(SYSTEM_COMMAND_NO_REPLY, EV3ByteCodes.WRITEMAILBOX);
                //bbbb = bytes in the message
                //mmmm = message counter
                //tt = type of message
                //ss = system command
                //ll = name Length
                s.writeUByte(axisName.length() + 1);
                //aaa... = name
                s.writeString(axisName);
                //LLLL = payload length
                s.writeUShort(4);
                //ppp... = payload
                s.writeFloat(value);
                sendMessage(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startReadingInputPorts() {
        new Thread(new EV3InputPortReader(this)).start();
    }

    class EV3InputPortReader implements Runnable {
        private EV3Driver driver = null;

        EV3InputPortReader(EV3Driver driver){
            this.driver = driver;
        }

        @Override
        public void run() {
            while(true) {
                if (socketState == SOCKET_ABORTED || driver.socket == null || inputPorts.size() == 0)
                    break;
                EV3ByteCodes c = new EV3ByteCodes();
                try {
                    c.messageHeader(DIRECT_COMMAND_REPLY, 4 * inputPorts.size(), 0);
                    int portIndex = 0;
                    for (Enumeration<String> enumerator = inputPorts.keys(); enumerator.hasMoreElements();) {
                        EV3InputPort inputPort = inputPorts.get(enumerator.nextElement());

                        c.opInput_Device(EV3ByteCodes.READY_SI);
                        c.LC0(inputPort.getLayer());
                        c.LC0(inputPort.getNumber());
                        c.LC0(0);
                        c.LC0(0);
                        c.LC0(1);
                        c.GV1(portIndex * 4);

                        portIndex++;
                    }
                    byte[] replyBytes = sendMessageAndReadReply(c, 1000);
                    if (socketState == SOCKET_ABORTED || driver.socket == null)
                        break;
                    if (replyBytes == null || replyBytes.length < 3 || replyBytes[2] == DIRECT_REPLY_ERROR) {
                        disconnect(R.string.ev3_error_no_reply_while_reading_input_port_values);
                        break;
                    }
                    ByteBuffer byteBuffer = ByteBuffer.wrap(replyBytes);
                    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
                    portIndex = 0;
                    List<EV3OutputPort> motorsToStop = new ArrayList<EV3OutputPort>();
                    List<EV3OutputPort> motorsToRotateRight = new ArrayList<EV3OutputPort>();
                    List<EV3OutputPort> motorsToRotateLeft = new ArrayList<EV3OutputPort>();
                    hasMovingMotors = false;
                    for (Enumeration<String> enumerator = inputPorts.keys(); enumerator.hasMoreElements();) {
                        EV3InputPort inputPort = inputPorts.get(enumerator.nextElement());
                        inputPort.setValue(byteBuffer.getFloat(3 + portIndex * 4));
                        /*if ((inputPort.getType() == INPUT_DEVICE_TYPE_EV3_LARGE_MOTOR
                                || inputPort.getType() == INPUT_DEVICE_TYPE_EV3_MEDIUM_MOTOR)
                                && outputPorts.containsKey(inputPort.getId())) {
                            EV3OutputPort outputPort = outputPorts.get(inputPort.getId());
                            if (outputPort.getJoystickType() == EV3Driver.JOYSTICK_TYPE_ANGLE) {
                                float currentAngle = inputPort.getValue() - inputPort.getInitValue();
                                float joystickAngle = outputPort.getAngle();
                                if (outputPort.getMotorDirection() != 0) {
                                    if ((outputPort.getMotorDirection() > 0
                                            && currentAngle >= joystickAngle)
                                            || (outputPort.getMotorDirection() < 0
                                            && currentAngle <= joystickAngle)) {
                                        outputPort.setStopAngle(joystickAngle);
                                        motorsToStop.add(outputPort);
                                    }
                                    else
                                        hasMovingMotors = true;
                                } else {
                                    if ((outputPort.getMotorDirection() < 0
                                                && joystickAngle > currentAngle)
                                            || (outputPort.getMotorDirection() == 0
                                                && joystickAngle > currentAngle
                                                && joystickAngle > outputPort.getStopAngle())) {
                                        motorsToRotateRight.add(outputPort);
                                        outputPort.setMotorDirection(1);
                                        hasMovingMotors = true;
                                    }
                                    else if ((outputPort.getMotorDirection() > 0
                                                && joystickAngle < currentAngle)
                                            || (outputPort.getMotorDirection() == 0
                                                && joystickAngle < currentAngle
                                                && joystickAngle < outputPort.getStopAngle())) {
                                        motorsToRotateLeft.add(outputPort);
                                        outputPort.setMotorDirection(-1);
                                        hasMovingMotors = true;
                                    }
                                }
                            }
                        }*/
                        portIndex++;
                    }
                    /*if (motorsToStop.size() > 0) {
                        c.clear();
                        c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, 0);

                        for (EV3OutputPort outputPort : motorsToStop) {
                            c.opOutput_Stop();
                            c.LC0(outputPort.getLayer());
                            c.LC0(outputPort.getNumber());
                            c.LC0(outputPort.getBrake());

                            outputPort.setMotorDirection(0);
                        }
                        if (socketState == SOCKET_ABORTED || driver.socket == null)
                            break;
                        sendMessage(c);
                    }
                    if (motorsToRotateRight.size() > 0
                            || motorsToRotateLeft.size() > 0) {
                        c.clear();
                        c.messageHeader(DIRECT_COMMAND_NO_REPLY, 0, 0);
                        for (EV3OutputPort outputPort : motorsToRotateRight) {
                            c.opOutput_Power();
                            c.LC0(outputPort.getLayer());
                            c.LC0(outputPort.getNumber());
                            c.LC1(Math.abs(outputPort.getPreparedPower()));

                            c.opOutput_Start();
                            c.LC0(outputPort.getLayer());
                            c.LC0(outputPort.getNumber());
                        }
                        for (EV3OutputPort outputPort : motorsToRotateLeft) {
                            c.opOutput_Power();
                            c.LC0(outputPort.getLayer());
                            c.LC0(outputPort.getNumber());
                            c.LC1(Math.abs(outputPort.getPreparedPower()) * -1);

                            c.opOutput_Start();
                            c.LC0(outputPort.getLayer());
                            c.LC0(outputPort.getNumber());
                        }
                        if (socketState == SOCKET_ABORTED || driver.socket == null)
                            break;
                        sendMessage(c);
                    }*/
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    int counter = 0;
                    while (counter * 20 < (hasMovingMotors ? 0 : inputReaderPeriod)) {
                        Thread.sleep(20);
                        counter++;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static int getInputPortNumberByOutputPortNumber(int outputPortNumber) {
        int inputPortNumber = 0;
        switch(outputPortNumber) {
            case EV3Driver.OUTPUT_PORT_A:
                inputPortNumber = EV3Driver.INPUT_PORT_A;
                break;
            case EV3Driver.OUTPUT_PORT_B:
                inputPortNumber = EV3Driver.INPUT_PORT_B;
                break;
            case EV3Driver.OUTPUT_PORT_C:
                inputPortNumber = EV3Driver.INPUT_PORT_C;
                break;
            case EV3Driver.OUTPUT_PORT_D:
                inputPortNumber = EV3Driver.INPUT_PORT_D;
                break;
        }
        return inputPortNumber;
    }

    public static int getOutputPortNumberByInputPortNumber(int inputPortNumber) {
        int outPortNumber = 0;
        switch(inputPortNumber) {
            case EV3Driver.INPUT_PORT_A:
                inputPortNumber = EV3Driver.OUTPUT_PORT_A;
                break;
            case EV3Driver.INPUT_PORT_B:
                inputPortNumber = EV3Driver.OUTPUT_PORT_B;
                break;
            case EV3Driver.INPUT_PORT_C:
                inputPortNumber = EV3Driver.OUTPUT_PORT_C;
                break;
            case EV3Driver.INPUT_PORT_D:
                inputPortNumber = EV3Driver.OUTPUT_PORT_D;
                break;
        }
        return inputPortNumber;
    }
}
