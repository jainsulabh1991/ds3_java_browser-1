package com.spectralogic.dsbrowser.gui.components.modifyjobpriority;

import com.airhacks.afterburner.views.FXMLView;
import com.spectralogic.dsbrowser.gui.util.StringConstants;

public class ModifyJobPriorityView extends FXMLView {

    public ModifyJobPriorityView(final ModifyJobPriorityModel value) {
        super(name -> {
            switch (name) {
                case StringConstants.VALUE:
                    return value;
                default:
                    return null;
            }
        });
    }
}
