package ru.proghouse.robocam.drivers.EV3;

import java.util.List;

/**
 * Created by Alexey Valuev on 29.10.2016.
 */
public abstract class EV3Controller {
    public abstract List<EV3OutputPort> getOutputPorts(int group);
    public abstract boolean gotoNextStep(boolean canGotoNextStep);
}
