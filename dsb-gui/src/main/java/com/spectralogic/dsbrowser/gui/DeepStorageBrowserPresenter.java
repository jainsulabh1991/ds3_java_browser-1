package com.spectralogic.dsbrowser.gui;

import com.spectralogic.dsbrowser.gui.components.about.AboutView;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3PanelView;
import com.spectralogic.dsbrowser.gui.components.interruptedjobwindow.EndpointInfo;
import com.spectralogic.dsbrowser.gui.components.interruptedjobwindow.JobInfoView;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.LocalFileTreeTableView;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionPopup;
import com.spectralogic.dsbrowser.gui.components.settings.SettingsView;
import com.spectralogic.dsbrowser.gui.services.JobWorkers;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.FilesAndFolderMap;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.JobInterruptionStore;
import com.spectralogic.dsbrowser.gui.services.jobprioritystore.SavedJobPrioritiesStore;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.settings.SettingsStore;
import com.spectralogic.dsbrowser.gui.services.tasks.Ds3JobTask;
import com.spectralogic.dsbrowser.gui.util.*;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.fxmisc.richtext.InlineCssTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;

import com.spectralogic.dsbrowser.gui.util.CancelJobsWorker;

public class DeepStorageBrowserPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(DeepStorageBrowserPresenter.class);
    private final ImageView INTERRUPTEDJOBIMAGEVIEW = new ImageView(ImageURLs.INTERRUPTED_JOB_IMAGE);
    private final ImageView CANCELALLJOBIMAGEVIEW = new ImageView(ImageURLs.CANCEL_ALL_JOBIMAGE);
    private final Alert CLOSECONFIRMATIONALERT = new Alert(
            Alert.AlertType.CONFIRMATION,
            "Are you sure you want to exit?"
    );

    private final Label count = new Label();
    private final Button jobButton = new Button();
    private final Circle circle = new Circle();

    @FXML
    private AnchorPane fileSystem, blackPearl;

    @FXML
    private SplitPane jobSplitter;

    @FXML
    private CheckMenuItem jobsMenuItem, logsMenuItem;

    @FXML
    private TabPane bottomTabPane;

    @FXML
    private Tab jobsTab, logsTab;

    @FXML
    private InlineCssTextArea inlineCssTextArea;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private MenuItem aboutMenuItem, closeMenuItem, sessionsMenuItem, settingsMenuItem, selectAllInFolderItem, selectAllInBucketItem;

    @FXML
    private Menu fileMenu, helpMenu, viewMenu, editMenu;

    @FXML
    private BorderPane borderPane;

    @Inject
    private JobWorkers jobWorkers;

    @Inject
    private ResourceBundle resourceBundle;

    @Inject
    private SavedJobPrioritiesStore savedJobPrioritiesStore;

    @Inject
    private Ds3Common ds3Common;

    @Inject
    private JobInterruptionStore jobInterruptionStore;

    @Inject
    private SettingsStore settingsStore;

    @Inject
    private SavedSessionStore savedSessionStore;

    @Inject
    private Workers workers;

    private MyTaskProgressView<Ds3JobTask> jobProgressView;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            setToolTilBehavior(200, 5000, 0); //To set the time interval of tooltip
            initGUIElement(); //Setting up labels from resource file

            LOG.info("Loading Main view");
            logText("Loading main view", LogType.INFO);

            final Stage stage = (Stage) CLOSECONFIRMATIONALERT.getDialogPane().getScene().getWindow();
            stage.getIcons().add(new Image(ImageURLs.DEEP_STORAGE_BROWSER));

            jobProgressView = new MyTaskProgressView<>();
            jobProgressView.setPrefHeight(1000);

            final VBox jobProgressVBox = new VBox();

            INTERRUPTEDJOBIMAGEVIEW.setFitHeight(15);
            INTERRUPTEDJOBIMAGEVIEW.setFitWidth(15);
            CANCELALLJOBIMAGEVIEW.setFitHeight(15);
            CANCELALLJOBIMAGEVIEW.setFitWidth(15);

            final Tooltip jobToolTip = new Tooltip();
            jobToolTip.setText("Interrupted Jobs");

            final Tooltip cancelAllToolTip = new Tooltip();
            cancelAllToolTip.setText("Cancel all running jobs");

            final Button cancelAll = new Button();
            cancelAll.setTranslateX(70);
            cancelAll.setTranslateY(4);
            cancelAll.setTooltip(cancelAllToolTip);
            cancelAll.setGraphic(CANCELALLJOBIMAGEVIEW);
            cancelAll.disableProperty().bind(Bindings.size(jobProgressView.getTasks()).lessThan(1));
            cancelAll.setOnAction(event -> {
                CancelJobsWorker.cancelAllRunningJobs(jobWorkers, jobInterruptionStore, LOG, workers, ds3Common);
            });

            jobButton.setTranslateX(20);
            jobButton.setTranslateY(4);
            jobButton.setTooltip(jobToolTip);
            jobButton.setGraphic(INTERRUPTEDJOBIMAGEVIEW);
            jobButton.setDisable(true);
            jobButton.setOnAction(event -> {
                if (ds3Common.getCurrentSession() != null) {
                    final Session session = ds3Common.getCurrentSession();
                    final String endpoint = session.getEndpoint() + ":" + session.getPortNo();
                    final ArrayList<Map<String, Map<String, FilesAndFolderMap>>> endpoints = jobInterruptionStore.getJobIdsModel().getEndpoints();

                    final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(endpoints, endpoint, jobProgressView, null);
                    if (jobIDMap != null && jobIDMap.size() > 0) {
                        jobButton.setDisable(false);
                        final JobInfoView jobView = new JobInfoView(new EndpointInfo(endpoint, session.getClient(), jobIDMap, DeepStorageBrowserPresenter.this, ds3Common));
                        Popup.show(jobView.getView(), "Interrupted Jobs of " + endpoint);
                    } else {
                        jobButton.setDisable(true);
                    }

                } else {
                    jobButton.setDisable(true);
                    logText("No interrupted jobs", LogType.INFO);
                }
            });

            final StackPane stackpane = new StackPane();
            stackpane.setLayoutX(45);
            stackpane.setLayoutY(1);
            count.setTextFill(Color.WHITE);
            circle.radiusProperty().setValue(8.0);
            circle.setStrokeType(StrokeType.INSIDE);
            circle.setVisible(false);
            circle.setFill(Color.RED);
            stackpane.getChildren().add(circle);
            stackpane.getChildren().add(count);

            final AnchorPane anchorPane = new AnchorPane();
            anchorPane.getChildren().addAll(jobButton, stackpane, cancelAll);
            anchorPane.setMinHeight(35);
            Bindings.bindContentBidirectional(jobWorkers.getTasks(), jobProgressView.getTasks());

            jobsMenuItem.selectedProperty().setValue(true);
            logsMenuItem.selectedProperty().setValue(true);

            jobProgressVBox.getChildren().add(anchorPane);
            jobProgressVBox.getChildren().add(jobProgressView);

            jobsTab.setContent(jobProgressVBox);
            logsTab.setContent(scrollPane);
            jobSplitter.getItems().add(bottomTabPane);
            jobSplitter.setDividerPositions(0.95);

            jobsMenuItem.setOnAction(event -> {
                if (jobsMenuItem.isSelected()) {
                    jobsTab.setContent(jobProgressVBox);
                    bottomTabPane.getTabs().add(0, jobsTab);
                    bottomTabPane.getSelectionModel().select(jobsTab);
                    if (!jobSplitter.getItems().stream().anyMatch(i -> i instanceof TabPane)) {
                        jobSplitter.getItems().add(bottomTabPane);
                        jobSplitter.setDividerPositions(0.75);
                    }
                } else {
                    if (!logsMenuItem.isSelected())
                        jobSplitter.getItems().remove(bottomTabPane);
                    bottomTabPane.getTabs().remove(jobsTab);
                }
            });

            logsMenuItem.setOnAction(event -> {
                if (logsMenuItem.isSelected()) {
                    logsTab.setContent(scrollPane);
                    bottomTabPane.getTabs().add(logsTab);
                    bottomTabPane.getSelectionModel().select(logsTab);
                    if (!jobSplitter.getItems().stream().anyMatch(i -> i instanceof TabPane)) {
                        jobSplitter.getItems().add(bottomTabPane);
                        jobSplitter.setDividerPositions(0.75);
                    }
                } else {
                    if (!jobsMenuItem.isSelected())
                        jobSplitter.getItems().remove(bottomTabPane);
                    bottomTabPane.getTabs().remove(logsTab);
                }
            });

            closeMenuItem.setOnAction(event -> {
                final CloseConfirmationHandler closeConfirmationHandler = new CloseConfirmationHandler(PrimaryStageModel.getInstance().getPrimaryStage(), savedSessionStore, savedJobPrioritiesStore, jobInterruptionStore, settingsStore, jobWorkers, workers);
                closeConfirmationHandler.closeConfirmationAlert(event);
            });

            final LocalFileTreeTableView localTreeView = new LocalFileTreeTableView(this);
            final Ds3PanelView ds3PanelView = new Ds3PanelView(this);
            localTreeView.getViewAsync(fileSystem.getChildren()::add);
            ds3PanelView.getViewAsync(blackPearl.getChildren()::add);

        } catch (final Throwable e) {
            LOG.error("Encountered an error when creating Main view", e);
            logText("Encountered an error when creating Main view", LogType.ERROR);
        }
    }

    private void initGUIElement() {
        fileMenu.setText(resourceBundle.getString("fileMenu"));
        sessionsMenuItem.setText(resourceBundle.getString("sessionsMenuItem"));
        settingsMenuItem.setText(resourceBundle.getString("settingsMenuItem"));
        closeMenuItem.setText(resourceBundle.getString("closeMenuItem"));
        viewMenu.setText(resourceBundle.getString("viewMenu"));
        editMenu.setText(resourceBundle.getString("editMenu"));
        selectAllInBucketItem.setText(resourceBundle.getString("selectAllInBucketItem"));
        selectAllInFolderItem.setText(resourceBundle.getString("selectAllInFolderItem"));
        jobsMenuItem.setText(resourceBundle.getString("jobsMenuItem"));
        logsMenuItem.setText(resourceBundle.getString("logsMenuItem"));
        helpMenu.setText(resourceBundle.getString("helpMenu"));
        aboutMenuItem.setText(resourceBundle.getString("aboutMenuItem"));
    }

    public Label getCount() {
        return count;
    }

    public Button getJobButton() {
        return jobButton;
    }

    public Circle getCircle() {
        return circle;
    }

    public AnchorPane getFileSystem() {
        return fileSystem;
    }

    public AnchorPane getBlackPearl() {
        return blackPearl;
    }

    public MyTaskProgressView<Ds3JobTask> getJobProgressView() {
        return jobProgressView;
    }

    /**
     * This function is to set the tooltip behaviour. You can set initial delay, duration of the tooltip and close delay.
     *
     * @param openDelay  delay in displaying toltip
     * @param duration   tooltip display time
     * @param closeDelay tooltip closing time
     */
    private void setToolTilBehavior(final int openDelay, final int duration, final int closeDelay) {
        try {
            final Field field = Tooltip.class.getDeclaredField("BEHAVIOR");
            field.setAccessible(true);
            final Class[] classes = Tooltip.class.getDeclaredClasses();
            for (final Class clazz : classes) {
                if (clazz.getName().equals("javafx.scene.control.Tooltip$TooltipBehavior")) {
                    @SuppressWarnings("unchecked")
                    final Constructor constructor = clazz.getDeclaredConstructor(Duration.class, Duration.class, Duration.class, boolean.class);
                    constructor.setAccessible(true);
                    final Object tooltipBehavior = constructor.newInstance(new Duration(openDelay), new Duration(duration), new Duration(closeDelay), false);
                    field.set(null, tooltipBehavior);
                    break;
                }
            }
        } catch (final Exception e) {
            LOG.error("Unable to set tooltip behaviour", e);
        }
    }

    public void showSettingsPopup() {
        final SettingsView settingsView = new SettingsView();
        Popup.show(settingsView.getView(), "Settings");
    }

    public void showAboutPopup() {
        final AboutView aboutView = new AboutView();
        Popup.show(aboutView.getView(), "About");
    }

    public void showSessionPopup() {
        NewSessionPopup.show();
    }

    public void logText(final String log, final LogType type) {
        if (inlineCssTextArea != null) {
            inlineCssTextArea.appendText(formattedString(log));
            final int size = inlineCssTextArea.getParagraphs().size() - 2;
            switch (type) {
                case SUCCESS:
                    inlineCssTextArea.setStyle(size, "-fx-fill: GREEN;");
                    break;
                case ERROR:
                    inlineCssTextArea.setStyle(size, "-fx-fill: RED;");
                    break;
                default:
                    inlineCssTextArea.setStyle(size, "-fx-fill: BLACK;");
            }
            scrollPane.setVvalue(1.0);
        }
    }

    //set the same color for all the lines of string log seprated by \n
    public void logTextForParagraph(final String log, final LogType type) {
        final int previousSize = inlineCssTextArea.getParagraphs().size() - 2;
        inlineCssTextArea.appendText(formattedString(log));
        final int size = inlineCssTextArea.getParagraphs().size() - 2;
        for (int i = previousSize + 1; i <= size; i++)
            switch (type) {
                case SUCCESS:
                    inlineCssTextArea.setStyle(i, "-fx-fill: GREEN;");
                    break;
                case ERROR:
                    inlineCssTextArea.setStyle(i, "-fx-fill: RED;");
                    break;
                default:
                    inlineCssTextArea.setStyle(i, "-fx-fill: BLACK;");
            }
        scrollPane.setVvalue(1.0);
    }

    private String formattedString(final String log) {
        return ">> " + log + "\n";
    }
}
