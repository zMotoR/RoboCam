package ru.proghouse.robocam.drivers;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.HashSet;

/**
 * Created by Alexey Valuev on 14.06.2017.
 */

public class RoboCamKeyGroup {
    //Where the group is active.
    private boolean active = false;
    //Name of the key group.
    private String name = "";
    //There are key codes that will be send to a robot.
    private HashSet<Integer> keyCodes = new HashSet<Integer>();

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public HashSet<Integer> getKeyCodes() {
        return keyCodes;
    }

    static public void loadKeysFromXml(Element parentNode, HashSet<Integer> keyCodes, String nodeName) {
        NodeList keyCodeNodes = parentNode.getElementsByTagName(nodeName);
        for (int j = 0; j < keyCodeNodes.getLength(); j++) {
            Element keyCodeNode = (Element) keyCodeNodes.item(j);
            String strValue = keyCodeNode.getTextContent();
            Integer value = Integer.valueOf(strValue);
            if (value > 0 && value <= 255)
                keyCodes.add(value);
        }
    }

}
