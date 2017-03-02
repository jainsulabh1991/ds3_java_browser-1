package com.spectralogic.dsbrowser.gui.injectors;

import com.google.inject.Provider;
import com.spectralogic.dsbrowser.gui.services.logservice.LogService;
import com.spectralogic.dsbrowser.gui.services.settings.SettingsStore;

import java.io.IOException;

public class LogServiceProvider implements Provider<LogService>{
    @Override
    public LogService get() {
        final LogService logService;
        try {
            final SettingsStore settings = SettingsStore.loadSettingsStore();
            logService = new LogService(settings.getLogSettings());
            return logService;
        } catch (final IOException e) {
            return null;
        }

    }
}
