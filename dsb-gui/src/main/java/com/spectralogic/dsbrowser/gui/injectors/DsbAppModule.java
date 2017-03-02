package com.spectralogic.dsbrowser.gui.injectors;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.services.JobWorkers;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.JobInterruptionStore;
import com.spectralogic.dsbrowser.gui.services.jobprioritystore.SavedJobPrioritiesStore;
import com.spectralogic.dsbrowser.gui.services.logservice.LogService;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Ds3SessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.settings.SettingsStore;
import com.spectralogic.dsbrowser.gui.util.ResourceBundleProperties;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import javafx.scene.input.DataFormat;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.ResourceBundle;

public class DsbAppModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(LogService.class).toProvider(LogServiceProvider.class).in(Singleton.class);
        bind(Ds3SessionStore.class).in(Singleton.class);
        bind(Session.class).in(Singleton.class);
        bind(Ds3Common.class).in(Singleton.class);
        bind(ResourceBundle.class).toInstance(ResourceBundleProperties.getResourceBundle());
        loadPresenters(this::bind);
    }

    @Provides
    @Singleton
    private SettingsStore provideSettingsStore() throws IOException {
        return SettingsStore.loadSettingsStore();
    }

    @Provides
    @Singleton
    private Workers provideWorkers() {
        return new Workers();
    }

    @Provides
    @Singleton
    private JobWorkers provideJobWorkers() throws IOException {
        final SettingsStore settings = SettingsStore.loadSettingsStore();
        return new JobWorkers(settings.getProcessSettings().getMaximumNumberOfParallelThreads());
    }

    @Provides
    @Singleton
    private SavedSessionStore provideSavedSessionStore() throws IOException {
        return SavedSessionStore.loadSavedSessionStore();
    }

    @Provides
    @Singleton
    private SavedJobPrioritiesStore provideSavedJobPrioritiesStore() throws IOException {
        return SavedJobPrioritiesStore.loadSavedJobPriorties();

    }

    @Provides
    @Singleton
    private JobInterruptionStore provideJobInterruptionStore() throws IOException {
        return JobInterruptionStore.loadJobIds();
    }

    @Provides
    @Singleton
    private DataFormat provideDataFormat() {
        final ResourceBundle resourceBundle = ResourceBundleProperties.getResourceBundle();
        return new DataFormat(resourceBundle.getString("dataFormat"));
    }

    private void loadPresenters(final Binder binder) {
        new FastClasspathScanner("com.spectralogic.dsbrowser.gui")
                .matchClassesWithAnnotation(Presenter.class, binder::bind)
                .scan();
    }

    @FunctionalInterface
    private interface Binder {
        void bind(final Class<?> bind);
    }
}
