package ru.proghouse.robocam.drivers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by Alexey Valuev on 14.06.2017.
 */

public class StreamHelper {
    static public int getUByteFromByteArray(byte[] bytes, int index) {
        int _byte = bytes[index];
        return _byte < 0 ? _byte + 256 : _byte;
    }

    static public int getUShortFromByteArray(byte[] bytes, int index) {
        return getUByteFromByteArray(bytes, index) | (getUByteFromByteArray(bytes, index + 1) << 8);
    }

    static public String getStringFromByteArray(byte[] bytes, int index, int maxLength) throws UnsupportedEncodingException {
        for (int i = index; i < Math.min(bytes.length, i + maxLength); i++)
            if (bytes[i] == 0) {
                maxLength = i - index;
                break;
            }
        return new String(bytes, index, maxLength, "US-ASCII");
    }

    static public String getStringFromByteArray(byte[] bytes, int index, int maxLength, String charsetName) throws UnsupportedEncodingException {
        for (int i = index; i < Math.min(bytes.length, i + maxLength); i++)
            if (bytes[i] == 0) {
                maxLength = i - index;
                break;
            }
        return new String(bytes, index, maxLength, charsetName);
    }

    static public void setUByteToByteArray(byte[] bytes, int index, int _ubyte) {
        bytes[index] = (byte) (_ubyte > Byte.MAX_VALUE ? _ubyte - 256 : _ubyte);
    }

    static public void setUShortToByteArray(byte[] bytes, int index, int _ushort) {
        setUByteToByteArray(bytes, index, _ushort & 0xFF);
        setUByteToByteArray(bytes, index + 1, (_ushort >> 8) & 0xFF);
    }

    static public void setUIntegerToByteArray(byte[] bytes, int index, long _uint) {
        setUByteToByteArray(bytes, index, (int) (_uint & 0xFF));
        setUByteToByteArray(bytes, index + 1, (int)((_uint >> 8) & 0xFF));
        setUByteToByteArray(bytes, index + 2, (int) ((_uint >> 16) & 0xFF));
        setUByteToByteArray(bytes, index + 3, (int)((_uint >> 24) & 0xFF));
    }

    static public void setIntegerToByteArray(byte[] bytes, int index, int _int) {
        setUByteToByteArray(bytes, index, _int & 0xFF);
        setUByteToByteArray(bytes, index + 1, (_int >> 8) & 0xFF);
        setUByteToByteArray(bytes, index + 2, (_int >> 16) & 0xFF);
        setUByteToByteArray(bytes, index + 3, (_int >> 24) & 0xFF);
    }

    //Reads unsigned byte.
    static public int readUByte(InputStream _stream) throws IOException {
        byte bytes[] = new byte[1];
        _stream.read(bytes);
        return bytes[0] < 0 ? (int)bytes[0] + 256 : (int)bytes[0];
    }

    //Reads unsigned short.
    static public int readUShort(InputStream _stream) throws IOException {
        return readUByte(_stream) | (readUByte(_stream) << 8);
    }

    //Writes unsigned byte.
    static public void writeUByte(OutputStream s, int _ubyte) throws IOException {
        s.write(_ubyte > Byte.MAX_VALUE ? _ubyte - 256 : _ubyte);
    }

    //Write signed byte.
    static public void writeByte(OutputStream s, byte _byte) throws IOException {
        s.write(_byte);
    }

    //Writes unsigned short.
    static public void writeUShort(OutputStream s, int _ushort) throws IOException {
        writeUByte(s, _ushort & 0xFF);
        writeUByte(s, (_ushort >> 8) & 0xFF);
    }

    //Writes signed short.
    static public void writeShort(OutputStream s, short _short) throws IOException {
        byte bytes[] = new byte[2];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putShort(_short);
        s.write(bytes);
    }

    static public void writeUInteger(OutputStream s, long _uint) throws IOException {
        writeUByte(s, (int) (_uint & 0xFF));
        writeUByte(s, (int) ((_uint >> 8) & 0xFF));
        writeUByte(s, (int) ((_uint >> 16) & 0xFF));
        writeUByte(s, (int) ((_uint >> 24) & 0xFF));
    }

    static public void writeInteger(OutputStream s, int _int) throws IOException {
        writeUByte(s, _int & 0xFF);
        writeUByte(s, (_int >> 8) & 0xFF);
        writeUByte(s, (_int >> 16) & 0xFF);
        writeUByte(s, (_int >> 24) & 0xFF);
    }

    static public void writeFloat(OutputStream s, float _float) throws IOException {
        writeInteger(s, Float.floatToIntBits(_float));
        /*byte bytes[] = new byte[4];
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.putFloat(_float);
        s.write(bytes);*/
    }

    /*public void writeDouble(double _double) throws IOException {
        writeUInteger(Double.doubleToLongBits(_double));
    }*/

    static public void writeStringWithout0(OutputStream s, String _string) throws IOException {
        byte[] bytes = _string.getBytes("US-ASCII");
        s.write(bytes);
    }

    static public void writeString(OutputStream s, String _string) throws IOException {
        writeStringWithout0(s, _string);
        s.write(0);
    }

    static public void writeStringWithout0(OutputStream s, String _string, String charsetName) throws IOException {
        byte[] bytes = _string.getBytes(charsetName);
        s.write(bytes);
    }

    static public void writeString(OutputStream s, String _string, String charsetName) throws IOException {
        writeStringWithout0(s, _string, charsetName);
        s.write(0);
    }

}
