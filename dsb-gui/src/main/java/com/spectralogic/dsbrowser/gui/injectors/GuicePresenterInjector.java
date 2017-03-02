package com.spectralogic.dsbrowser.gui.injectors;

import com.airhacks.afterburner.injection.PresenterFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import java.util.function.Function;

public class GuicePresenterInjector implements PresenterFactory {
    public static final Injector injector;

    static {
        injector = Guice.createInjector(new DsbAppModule());
    }

    @Override
    public <T> T instantiatePresenter(final Class<T> clazz, final Function<String, Object> injectionContext) {

        return injector.getInstance(clazz);
    }

    private class PresenterModule extends AbstractModule {

        @Override
        protected void configure() {

        }
    }
}