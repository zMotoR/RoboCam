package ru.proghouse.robocam.drivers.EV3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Alexey Valuev on 24.02.2016.
 */
public class EV3ByteCodes {
    ByteArrayOutputStream s = null;
    private static Object sync = new Object();
    private static int messageNumber = 0;
    private boolean hasProgramHeader = false;


    public static final int BEGIN_DOWNLOAD = 0x92; // Begin file download
    public static final int CONTINUE_DOWNLOAD = 0x93; // Continue file download
    public static final int BEGIN_UPLOAD = 0x94; // Begin file upload
    public static final int CONTINUE_UPLOAD = 0x95; // Continue file upload
    public static final int BEGIN_GETFILE = 0x96; // Begin get bytes from a file (while writing to the file)
    public static final int CONTINUE_GETFILE = 0x97; // Continue get byte from a file (while writing to the file)
    public static final int CLOSE_FILEHANDLE = 0x98; // Close file handle
    public static final int LIST_FILES = 0x99; // List files
    public static final int CONTINUE_LIST_FILES = 0x9A; // Continue list files
    public static final int CREATE_DIR = 0x9B; // Create directory
    public static final int DELETE_FILE = 0x9C; // Delete
    public static final int LIST_OPEN_HANDLES = 0x9D; // List handles
    public static final int WRITEMAILBOX = 0x9E; // Write to mailbox
    public static final int BLUETOOTHPIN = 0x9F; // Transfer trusted pin code to brick
    public static final int ENTERFWUPDATE = 0xA0; // Restart the brick in Firmware update mode

    public static final byte SUCCESS = 0x00;
    public static final byte UNKNOWN_HANDLE = 0x01;
    public static final byte HANDLE_NOT_READY = 0x02;
    public static final byte CORRUPT_FILE = 0x03;
    public static final byte NO_HANDLES_AVAILABLE = 0x04;
    public static final byte NO_PERMISSION = 0x05;
    public static final byte ILLEGAL_PATH = 0x06;
    public static final byte FILE_EXITS = 0x07;
    public static final byte END_OF_FILE = 0x08;
    public static final byte SIZE_ERROR = 0x09;
    public static final byte UNKNOWN_ERROR = 0x0A;
    public static final byte ILLEGAL_FILENAME = 0x0B;
    public static final byte ILLEGAL_CONNECTION = 0x0C;

    public static final int LED_OFF = 0x00;
    public static final int LED_GREEN = 0x01;
    public static final int LED_RED = 0x02;
    public static final int LED_ORANGE = 0x03;
    public static final int LED_GREEN_FLASHING = 0x04;
    public static final int LED_RED_FLASHING = 0x05;
    public static final int LED_ORANGE_FLASHING = 0x06;
    public static final int LED_GREEN_PULSE = 0x07;
    public static final int LED_RED_PULSE = 0x08;
    public static final int LED_ORANGE_PULSE = 0x09;

    public static final int GET_TYPEMODE = 0x05; //CMD for opInput_Device
    public static final int READY_SI = 0x1D; //CMD for opInput_Device
    public static final int READY_RAW = 0x1C; //CMD for opInput_Device

    public static final int GET_OS_VERS = 0x03; //CMD for opUI_Read
    public static final int GET_HW_VERS = 0x09; //CMD for opUI_Read
    public static final int GET_FW_VERS = 0x0A; //CMD for opUI_Read
    public static final int GET_FW_BUILD = 0x0B; //CMD for opUI_Read
    public static final int GET_OS_BUILD = 0x0C; //CMD for opUI_Read
    public static final int GET_VERSION = 0x1A; //CMD for opUI_Read


    public static final int LOAD_IMAGE = 0x08; //CMD for opFile

    public static final int USER_SLOT = 0x01; //Slot used for executing user projects, apps and tools

    public static final int DEBUG_MODE_NORMAL = 0;
    public static final int DEBUG_MODE_DEBUG = 1;
    public static final int DEBUG_MODE_NO_EXECUTE = 2;

    public static final int TEXT = 0x05;
    public static final int FILLWINDOW = 0x13;

    public EV3ByteCodes() {
        s = new ByteArrayOutputStream();
    }

    public EV3ByteCodes(ByteArrayOutputStream s) {
        this.s = s;
    }

    public int getSize() {
        return s.size();
    }

    public byte[] createMessage() {
        byte[] bytes = new byte[2 + s.size()];
        setUShortToByteArray(bytes, 0, s.size());
        System.arraycopy(s.toByteArray(), 0, bytes, 2, s.size());
        return bytes;
    }

    private static int getNextMessageNumber() {
        synchronized (sync) {
            messageNumber++;
            if (messageNumber > 0xFFFF)
                messageNumber = 0;
            return messageNumber;
        }
    }

    public int messageHeader(int commandType, int globalSize, int localSize) throws IOException {
        int messageNumber = getNextMessageNumber();
        writeUShort(messageNumber);
        writeUByte(commandType);
        writeVariablesAllocation(globalSize, localSize);
        return messageNumber;
    }

    public int messageHeader(int commandType, int systemCommand) throws IOException {
        int messageNumber = getNextMessageNumber();
        writeUShort(messageNumber);
        writeUByte(commandType);
        writeUByte(systemCommand);
        return messageNumber;
    }

    //Writes unsigned byte.
    public void writeUByte(int _ubyte) throws IOException {
        s.write(_ubyte > Byte.MAX_VALUE ? _ubyte - 256 : _ubyte);
    }

    private void setUByteToByteArray(byte[] bytes, int index, int _ubyte) {
        bytes[index] = (byte) (_ubyte > Byte.MAX_VALUE ? _ubyte - 256 : _ubyte);
    }

    private void setUShortToByteArray(byte[] bytes, int index, int _ushort) {
        setUByteToByteArray(bytes, index, _ushort & 0xFF);
        setUByteToByteArray(bytes, index + 1, (_ushort >> 8) & 0xFF);
    }

    private void setUIntegerToByteArray(byte[] bytes, int index, long _uint) {
        setUByteToByteArray(bytes, index, (int) (_uint & 0xFF));
        setUByteToByteArray(bytes, index + 1, (int)((_uint >> 8) & 0xFF));
        setUByteToByteArray(bytes, index + 2, (int) ((_uint >> 16) & 0xFF));
        setUByteToByteArray(bytes, index + 3, (int)((_uint >> 24) & 0xFF));
    }

    private void setIntegerToByteArray(byte[] bytes, int index, int _int) {
        setUByteToByteArray(bytes, index, _int & 0xFF);
        setUByteToByteArray(bytes, index + 1, (_int >> 8) & 0xFF);
        setUByteToByteArray(bytes, index + 2, (_int >> 16) & 0xFF);
        setUByteToByteArray(bytes, index + 3, (_int >> 24) & 0xFF);
    }

    //Write signed byte.
    public void writeByte(byte _byte) throws IOException {
        s.write(_byte);
    }

    //Writes unsigned short.
    public void writeUShort(int _ushort) throws IOException {
        writeUByte(_ushort & 0xFF);
        writeUByte((_ushort >> 8) & 0xFF);
    }

    //Writes signed short.
    public void writeShort(short _short) throws IOException {
        byte bytes[] = new byte[2];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putShort(_short);
        s.write(bytes);
    }

    public void writeUInteger(long _uint) throws IOException {
        writeUByte((int) (_uint & 0xFF));
        writeUByte((int) ((_uint >> 8) & 0xFF));
        writeUByte((int) ((_uint >> 16) & 0xFF));
        writeUByte((int) ((_uint >> 24) & 0xFF));
    }

    public void writeInteger(int _int) throws IOException {
        writeUByte(_int & 0xFF);
        writeUByte((_int >> 8) & 0xFF);
        writeUByte((_int >> 16) & 0xFF);
        writeUByte((_int >> 24) & 0xFF);
    }

    public void writeFloat(float _float) throws IOException {
        writeInteger(Float.floatToIntBits(_float));
        /*byte bytes[] = new byte[4];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putFloat(_float);
        s.write(bytes);*/
    }

    /*public void writeDouble(double _double) throws IOException {
        writeUInteger(Double.doubleToLongBits(_double));
    }*/

    public String getStringFromByteArray(byte[] bytes, int index, int maxLength) throws UnsupportedEncodingException {
        for (int i = index; i < Math.min(bytes.length, i + maxLength); i++)
            if (bytes[i] == 0) {
                maxLength = i - index;
                break;
            }
        return new String(bytes, index, maxLength, "US-ASCII");
    }

    public void writeStringWithout0(String _string) throws IOException {
        byte[] bytes = _string.getBytes("US-ASCII");
        s.write(bytes);
    }

    public void writeString(String _string) throws IOException {
        writeStringWithout0(_string);
        s.write(0);
    }

    //Writes a number of bytes reserved for the global and local variables.
    private void writeVariablesAllocation(int globalSize, int localSize) throws IOException {
        writeUByte(globalSize & 0xFF);
        writeUByte(((globalSize >> 8) & 0x3) | ((localSize << 2) & 0xFC));
    }

    //Writes an index of a global variable.
    public void GV(int index) throws IOException {
        if (index <= 31)
            GV0(index);
        else if (index <= 255)
            GV1(index);
        else if (index <= 65535)
            GV2(index);
        else
            GV4(index);
    }

    public void GV0(int index) throws IOException {
        writeUByte(index | 0x60);
    }

    public void GV1(int index) throws IOException {
        writeUByte(0xE1);
        writeUByte(index);
    }

    public void GV2(int index) throws IOException {
        writeUByte(0xE2);
        writeUShort(index);
    }

    public void GV4(long index) throws IOException {
        writeUByte(0xE3);
        writeUInteger(index);
        /*writeUByte(index & 0xFF);
        writeUByte((index >> 8) & 0xFF);
        writeUByte((index >> 16) & 0xFF);
        writeUByte((index >> 24) & 0xFF);*/
    }

    public void LV(int index) throws IOException {
        if (index <= 31)
            LV0(index);
        else if (index <= 255)
            LV1(index);
        else if (index <= 65535)
            LV2(index);
        else
            LV4(index);
    }

    public void LV0(int index) throws IOException {
        writeUByte(index | 0x40);
    }

    public void LV1(int index) throws IOException {
        writeUByte(0xC1);
        writeUByte(index);
    }

    public void LV2(int index) throws IOException {
        writeUByte(0xC2);
        writeUShort(index);
    }

    public void LV4(long index) throws IOException {
        writeUByte(0xC3);
        writeUInteger(index);
        /*writeUByte(index & 0xFF);
        writeUByte((index >> 8) & 0xFF);
        writeUByte((index >> 16) & 0xFF);
        writeUByte((index >> 24) & 0xFF);*/
    }

    public void LCS(String _string) throws IOException {
        writeUByte(0x84);
        writeString(_string);
    }

    public void LC0(int value) throws IOException {
        if (value < 0 && value > 31)
            throw new IllegalArgumentException("The value should be in the range of 0 to 31.");
        writeUByte(value);
    }

    /*//Writes unsigned byte.
    private void writeParameterAsUByte(int value) throws IOException {
        if (value < 0 && value > 255)
            throw new IllegalArgumentException("The value should be in the range of 0 to 255.");
        writeUByte(0x81);
        writeUByte(value);
    }*/

    //Writes signed byte.
    public void LC1(int value) throws IOException {
        if (value < Byte.MIN_VALUE && value > Byte.MAX_VALUE)
            throw new IllegalArgumentException("The value should be in the range of "
                    + Byte.MIN_VALUE + " to " + Byte.MAX_VALUE + ".");
        writeUByte(0x81);
        writeByte((byte) value);
    }

    /*//Writes unsigned short.
    private void LC2(OutputStream _stream, int value) throws IOException {
        if (value < 0 && value > 65535)
            throw new IllegalArgumentException("Value must be in the range of 0 to 65535.");
        writeUByte(0x82);
        writeUShort(value);
    }*/

    //Writes signed short.
    public void LC2(int value) throws IOException {
        if (value < Short.MIN_VALUE && value > Short.MAX_VALUE)
            throw new IllegalArgumentException("The value should be in the range of "
                    + Short.MIN_VALUE + " to " + Short.MAX_VALUE + ".");
        writeUByte(0x82);
        writeShort((short) value);
    }

    //Writes signed int.
    public void LC4(int value) throws IOException {
        writeUByte(0x83);
        writeUByte(value & 0xFF);
        writeUByte((value >> 8) & 0xFF);
        writeUByte((value >> 16) & 0xFF);
        writeUByte((value >> 24) & 0xFF);
    }

    public void LCF(float value) throws IOException {
        LC4(Float.floatToIntBits(value));
    }

    //Writes the command.
    private void opcode(int opCode) throws IOException {
        writeUByte(opCode);
    }

    //Writes the command and its specific parameter.
    private void opcode(int opCode, int cmd) throws IOException {
        writeUByte(opCode);
        writeUByte(cmd);
    }

    public void opProgram_Stop() throws IOException {
        opcode(0x02);
    }

    public void opPROGRAM_START() throws IOException {
        opcode(0x03);
    }

    public void opObject_End() throws IOException {
        opcode(0x0A);
    }

    public void opSub32() throws IOException {
        opcode(0x16);
    }

    public void opMul8() throws IOException {
        opcode(0x18);
    }

    public void opMul32() throws IOException {
        opcode(0x1A);
    }

    public void opOr8() throws IOException {
        opcode(0x20);
    }

    public void opAnd8() throws IOException {
        opcode(0x24);
    }

    public void opMove8_8() throws IOException {
        opcode(0x30);
    }

    public void opMove32_32() throws IOException {
        opcode(0x3A);
    }

    public void opMoveF_F() throws IOException {
        opcode(0x3F);
    }

    public void opJr() throws IOException {
        opcode(0x40);
    }

    public void opJr_False() throws IOException {
        opcode(0x41);
    }

    public void opJr_True() throws IOException {
        opcode(0x42);
    }

    public void opCp_Lt8() throws IOException {
        opcode(0x44);
    }

    public void opCp_Lt32() throws IOException {
        opcode(0x46);
    }

    public void opCp_LtF() throws IOException {
        opcode(0x47);
    }

    public void opCp_Gt8() throws IOException {
        opcode(0x48);
    }

    public void opCp_Gt32() throws IOException {
        opcode(0x4A);
    }

    public void opCp_GtF() throws IOException {
        opcode(0x4B);
    }

    public void opCp_Lteq32() throws IOException {
        opcode(0x56);
    }

    public void opCp_LteqF() throws IOException {
        opcode(0x57);
    }

    public void opCp_Gteq32() throws IOException {
        opcode(0x5A);
    }

    public void opCp_GteqF() throws IOException {
        opcode(0x5B);
    }

    public void opJr_Lt8() throws IOException {
        opcode(0x64);
    }

    public void opJr_Lt32() throws IOException {
        opcode(0x66);
    }

    public void opJr_Gt32() throws IOException {
        opcode(0x6A);
    }

    public void opJr_GtF() throws IOException {
        opcode(0x6B);
    }

    public void opJr_Eq8() throws IOException {
        opcode(0x6C);
    }

    public void opJr_Eq32() throws IOException {
        opcode(0x6E);
    }

    public void opJr_Neq8() throws IOException {
        opcode(0x70);
    }

    public void opJr_Gteq32() throws IOException {
        opcode(0x7A);
    }

    public void opUI_Flush() throws IOException {
        opcode(0x80);
    }

    public void opUI_WRITE(int cmd) throws IOException {
        opcode(0x82, cmd);
    }

    public void opUI_DRAW(int cmd) throws IOException {
        opcode(0x84);
    }

    public void opTimer_Wait() throws IOException {
        opcode(0x85);
    }

    public void opTimer_Ready() throws IOException {
        opcode(0x86);
    }

    public void opInput_Device(int cmd) throws IOException {
        opcode(0x99, cmd);
    }

    public void opUI_Read(int cmd) throws IOException {
        opcode(0x81, cmd);
    }

    public void opOutput_Stop() throws IOException {
        opcode(0xA3);
    }

    public void opOutput_Power() throws IOException {
        opcode(0xA4);
    }

    public void opOutput_Start() throws IOException {
        opcode(0xA6);
    }

    public void opOutput_Ready() throws IOException {
        opcode(0xAA);
    }

    public void opOutput_Step_Speed() throws IOException {
        opcode(0xAE);
    }

    public void opOutput_Clr_Count() throws IOException {
        opcode(0xB2);
    }

    public void opOutput_Prg_Stop() throws IOException {
        opcode(0xB4);
    }

    public void opFile(int cmd) throws IOException {
        opcode(0xC0, cmd);
    }

    public void opMailbox_Open() throws IOException {
        opcode(0xD8);
    }

    public void opMailbox_Write() throws IOException {
        opcode(0xD9);
    }

    public void opMailbox_Read() throws IOException {
        opcode(0xDA);
    }

    public void opMailbox_Test() throws IOException {
        opcode(0xDB);
    }

    public void opMailbox_Ready() throws IOException {
        opcode(0xDC);
    }

    public void opMailbox_Close() throws IOException {
        opcode(0xDD);
    }

    public void PROGRAMHeader(double versionInfo, int numberOfObjects, long globalBytes) throws IOException {
        //#define   BYTECODE_VERSION              1.07
        //#define   PROGRAMHeader(VersionInfo,NumberOfObjects,GlobalBytes)\
        //                  'L','E','G','O',LONGToBytes(0),WORDToBytes((UWORD)(BYTECODE_VERSION * 100.0)),WORDToBytes(NumberOfObjects),LONGToBytes(GlobalBytes)
        if (s.size() == 0)
            hasProgramHeader = true;
        writeStringWithout0("LEGO");
        writeUInteger(0);
        writeUShort((int) (versionInfo * 100.0));
        writeUShort(numberOfObjects);
        writeUInteger(globalBytes);
    }

    public void VMTHREADHeader(long offsetToInstructions, long localBytes) throws IOException {
        //#define   VMTHREADHeader(OffsetToInstructions,LocalBytes)\
        //                  LONGToBytes(OffsetToInstructions),0,0,0,0,LONGToBytes(LocalBytes)
        writeUInteger(offsetToInstructions);
        writeUInteger(0);
        writeUInteger(localBytes);
    }

    public void SUBCALLHeader(long offsetToInstructions, long localBytes) throws IOException {
        //#define   SUBCALLHeader(OffsetToInstructions,LocalBytes)\
        //                  LONGToBytes(OffsetToInstructions),0,0,1,0,LONGToBytes(LocalBytes)
        writeUInteger(offsetToInstructions);
        writeUByte(0);
        writeUByte(0);
        writeUByte(1);
        writeUByte(0);
        writeUInteger(localBytes);
    }

    public void BLOCKHeader(long offsetToInstructions, int ownerObjectId, int triggerCount) throws IOException {
        //#define   BLOCKHeader(OffsetToInstructions,OwnerObjectId,TriggerCount)\
        //                  LONGToBytes(OffsetToInstructions),WORDToBytes(OwnerObjectId),WORDToBytes(TriggerCount),LONGToBytes(0)
        writeUInteger(offsetToInstructions);
        writeUShort(ownerObjectId);
        writeUShort(triggerCount);
        writeUInteger(0);
    }

    public void writeByteCodes(EV3ByteCodes prg) throws IOException {
        prg.calcImageSize();
        prg.s.writeTo(s);
    }

    private void calcImageSize() throws IOException {
        if (hasProgramHeader) {
            byte[] bytes = s.toByteArray();
            setIntegerToByteArray(bytes, 4, s.size());
            s.reset();
            s.write(bytes);
        }
    }

    public void changeUInteger(int index, long _uint) throws IOException {
        byte[] bytes = s.toByteArray();
        setUIntegerToByteArray(bytes, index, _uint);
        s.reset();
        s.write(bytes);
    }

    public void changeInteger(int index, int _int) throws IOException {
        byte[] bytes = s.toByteArray();
        setIntegerToByteArray(bytes, index, _int);
        s.reset();
        s.write(bytes);
    }

    public void clear() throws IOException {
        s.reset();
    }

}
