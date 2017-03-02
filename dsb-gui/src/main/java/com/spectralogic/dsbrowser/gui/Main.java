package com.spectralogic.dsbrowser.gui;

import com.spectralogic.dsbrowser.gui.injectors.GuicePresenterInjector;
import com.spectralogic.dsbrowser.gui.util.*;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class Main extends Application {
    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {
        PrimaryStageModel.getInstance().setPrimaryStage(primaryStage);
        primaryStage.setMinHeight(Constants.MIN_HEIGHT);
        primaryStage.setMinWidth(Constants.MIN_WIDTH);

        final com.google.inject.Injector injector = GuicePresenterInjector.injector;
        final DeepStorageBrowserView mainView = injector.getInstance(DeepStorageBrowserView.class);

        final Scene mainScene = new Scene(mainView.getView());
        primaryStage.getIcons().add(new Image(ImageURLs.DEEP_STORAGE_BROWSER));
        primaryStage.setScene(mainScene);

        if (ApplicationPreferences.getInstance().isWindowMaximized()) {
            primaryStage.setMaximized(true);
        } else {
            primaryStage.setX(ApplicationPreferences.getInstance().getX());
            primaryStage.setY(ApplicationPreferences.getInstance().getY());
            primaryStage.setWidth(ApplicationPreferences.getInstance().getWidth());
            primaryStage.setHeight(ApplicationPreferences.getInstance().getHeight());
        }
        primaryStage.setTitle(ResourceBundleProperties.getResourceBundle().getString("title"));
        primaryStage.show();

        // commented out for now
        // final CloseConfirmationHandler closeConfirmationHandler = new CloseConfirmationHandler(primaryStage, savedSessionStore, savedJobPrioritiesStore, jobInterruptionStore, settings, jobWorkers, workers);
        // primaryStage.setOnCloseRequest(closeConfirmationHandler.confirmCloseEventHandler);
    }
}
