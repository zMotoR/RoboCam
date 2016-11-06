package ru.proghouse.robocam;

/**
 * Created by Alexey Valuev on 06.11.2016.
 */
public class KeyDescription {
    private int code;
    private String desc;

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public KeyDescription(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
