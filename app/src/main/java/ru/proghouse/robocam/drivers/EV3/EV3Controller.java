package ru.proghouse.robocam.drivers.EV3;

import java.util.List;

/**
 * Created by Alexey Valuev on 29.10.2016.
 */
public interface EV3Controller {
    List<EV3OutputPort> getOutputPorts(int group);
}
