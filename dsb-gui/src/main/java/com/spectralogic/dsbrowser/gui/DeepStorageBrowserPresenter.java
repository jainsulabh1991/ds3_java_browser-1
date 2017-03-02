package com.spectralogic.dsbrowser.gui;

import com.google.inject.Injector;
import com.spectralogic.ds3client.utils.Guard;
import com.spectralogic.dsbrowser.gui.components.about.AboutView;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3PanelView;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.Ds3TreeTableValue;
import com.spectralogic.dsbrowser.gui.components.interruptedjobwindow.EndpointInfo;
import com.spectralogic.dsbrowser.gui.components.interruptedjobwindow.JobInfoView;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.LocalFileTreeTableView;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionView;
import com.spectralogic.dsbrowser.gui.components.settings.SettingsView;
import com.spectralogic.dsbrowser.gui.injectors.GuicePresenterInjector;
import com.spectralogic.dsbrowser.gui.injectors.Presenter;
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
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeType;
import org.fxmisc.richtext.InlineCssTextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

@Presenter
public class DeepStorageBrowserPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(DeepStorageBrowserPresenter.class);
    private final ImageView INTERRUPTEDJOBIMAGEVIEW = new ImageView(ImageURLs.INTERRUPTED_JOB_IMAGE);
    private final ImageView CANCELALLJOBIMAGEVIEW = new ImageView(ImageURLs.CANCEL_ALL_JOB_IMAGE);

    private final Label lblCount = new Label();
    private final Button jobButton = new Button();
    private final Circle circle = new Circle();
    private Injector injector;

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
    private MenuItem aboutMenuItem, closeMenuItem, sessionsMenuItem, settingsMenuItem, selectAllItem;

    @FXML
    private Menu fileMenu, helpMenu, viewMenu, editMenu;

    @FXML
    private BorderPane borderPane;

    private JobWorkers jobWorkers;

    private ResourceBundle resourceBundle;

    private SavedJobPrioritiesStore savedJobPrioritiesStore;

    private Ds3Common ds3Common;

    private JobInterruptionStore jobInterruptionStore;

    private SettingsStore settingsStore;

    private SavedSessionStore savedSessionStore;

    private Workers workers;

    private DeepStorageBrowserTaskProgressView<Ds3JobTask> jobProgressView;

    @Inject
    public DeepStorageBrowserPresenter(final SavedSessionStore savedSessionStore) {
        this.savedSessionStore = savedSessionStore;
    }

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            initInjectors();
            initGUIElement(); //Setting up labels from resource file
            LOG.info("Loading Main view");
            ds3Common.setDeepStorageBrowserPresenter(this);
            logText(resourceBundle.getString("loadMainView"), LogType.INFO);
            final SetToolTipBehavior setToolTipBehavior = new SetToolTipBehavior();
            setToolTipBehavior.setToolTilBehaviors(Constants.OPEN_DELAY, Constants.DURATION, Constants.CLOSE_DELAY); //To set the time interval of tooltip
            final Tooltip jobToolTip = new Tooltip();
            jobToolTip.setText(resourceBundle.getString("interruptedJobs"));
            final Tooltip cancelAllToolTip = new Tooltip();
            cancelAllToolTip.setText(resourceBundle.getString("cancelAllRunningJobs"));
            jobProgressView = new DeepStorageBrowserTaskProgressView<>();
            jobProgressView.setPrefHeight(1000);
            final VBox jobProgressVBox = new VBox();
            //Implementation and design of Cancel button in bottom pane
            CANCELALLJOBIMAGEVIEW.setFitHeight(15);
            CANCELALLJOBIMAGEVIEW.setFitWidth(15);
            final Button cancelAll = new Button();
            cancelAll.setTranslateX(70);
            cancelAll.setTranslateY(4);
            cancelAll.setTooltip(cancelAllToolTip);
            cancelAll.setGraphic(CANCELALLJOBIMAGEVIEW);
            cancelAll.disableProperty().bind(Bindings.size(jobProgressView.getTasks()).lessThan(1));
            cancelAll.setOnAction(event -> {
                final Optional<ButtonType> closeResponse = Ds3Alert.showConfirmationAlert(
                        resourceBundle.getString("confirmation"), jobWorkers.getTasks().size()
                                + StringConstants.SPACE + resourceBundle.getString("jobsWillBeCancelled"),
                        Alert.AlertType.CONFIRMATION, resourceBundle.getString("reallyWantToCancel"),
                        resourceBundle.getString("exitBtnJobCancelConfirm"),
                        resourceBundle.getString("cancelBtnJobCancelConfirm"));
                if (closeResponse.get().equals(ButtonType.OK)) {
                    CancelJobsWorker.cancelAllRunningJobs(jobWorkers, jobInterruptionStore, LOG, workers, ds3Common);
                    event.consume();
                }
                if (closeResponse.get().equals(ButtonType.CANCEL)) {
                    event.consume();
                }
            });
            INTERRUPTEDJOBIMAGEVIEW.setFitHeight(15);
            INTERRUPTEDJOBIMAGEVIEW.setFitWidth(15);
            jobButton.setTranslateX(20);
            jobButton.setTranslateY(4);
            jobButton.setTooltip(jobToolTip);
            jobButton.setGraphic(INTERRUPTEDJOBIMAGEVIEW);
            jobButton.setDisable(true);
            jobButton.setOnAction(event -> {
                if (ds3Common.getCurrentSession() != null) {
                    final Session session = ds3Common.getCurrentSession();
                    final String endpoint = session.getEndpoint() + StringConstants.COLON + session.getPortNo();
                    final List<Map<String, Map<String, FilesAndFolderMap>>> endpoints = jobInterruptionStore.getJobIdsModel().getEndpoints();
                    final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(endpoints, endpoint, jobProgressView, null);
                    if (!Guard.isMapNullOrEmpty(jobIDMap)) {
                        jobButton.setDisable(false);
                        final JobInfoView jobView = new JobInfoView(new EndpointInfo(endpoint, session.getClient(), jobIDMap, DeepStorageBrowserPresenter.this, ds3Common));
                        Popup.show(jobView.getView(), resourceBundle.getString("interruptedJobsPopUp") +
                                StringConstants.SPACE + endpoint);
                    } else {
                        jobButton.setDisable(true);
                    }

                } else {
                    jobButton.setDisable(true);
                    logText(resourceBundle.getString("noInterruptedJobs"), LogType.INFO);
                }
            });
            final StackPane stackpane = new StackPane();
            stackpane.setLayoutX(45);
            stackpane.setLayoutY(1);
            lblCount.setTextFill(Color.WHITE);
            circle.radiusProperty().setValue(8.0);
            circle.setStrokeType(StrokeType.INSIDE);
            circle.setVisible(false);
            circle.setFill(Color.RED);
            stackpane.getChildren().add(circle);
            stackpane.getChildren().add(lblCount);
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
                logsORJobsMenuItemAction(jobsMenuItem, jobProgressVBox, null, jobsTab);
            });
            logsMenuItem.setOnAction(event -> {
                logsORJobsMenuItemAction(logsMenuItem, null, scrollPane, logsTab);
            });
            closeMenuItem.setOnAction(event -> {
                final CloseConfirmationHandler closeConfirmationHandler = new CloseConfirmationHandler(PrimaryStageModel.getInstance().getPrimaryStage(), savedSessionStore, savedJobPrioritiesStore, jobInterruptionStore, settingsStore, jobWorkers, workers);
                closeConfirmationHandler.closeConfirmationAlert(event);
            });

            final LocalFileTreeTableView localTreeView = new LocalFileTreeTableView();
            final Ds3PanelView ds3PanelView = new Ds3PanelView();
//            fileSystem.getChildren().add(localTreeView.getView());
//            blackPearl.getChildren().add(ds3PanelView.getView());
            localTreeView.getViewAsync(fileSystem.getChildren()::add);
            ds3PanelView.getViewAsync(blackPearl.getChildren()::add);

        } catch (final Exception e) {
            LOG.error("Encountered an error when creating Main view", e);
            logText(resourceBundle.getString("errorWhileCreatingMainView"), LogType.ERROR);
        }
    }

    private void initInjectors() {
        injector = GuicePresenterInjector.injector;
        workers = injector.getInstance(Workers.class);
        jobWorkers = injector.getInstance(JobWorkers.class);
        resourceBundle = injector.getInstance(ResourceBundle.class);
        savedJobPrioritiesStore = injector.getInstance(SavedJobPrioritiesStore.class);
        savedSessionStore = injector.getInstance(SavedSessionStore.class);
        jobInterruptionStore = injector.getInstance(JobInterruptionStore.class);
        ds3Common = injector.getInstance(Ds3Common.class);
        settingsStore = injector.getInstance(SettingsStore.class);
    }

    private void initGUIElement() {
        fileMenu.setText(resourceBundle.getString("fileMenu"));
        sessionsMenuItem.setText(resourceBundle.getString("sessionsMenuItem"));
        settingsMenuItem.setText(resourceBundle.getString("settingsMenuItem"));
        closeMenuItem.setText(resourceBundle.getString("closeMenuItem"));
        viewMenu.setText(resourceBundle.getString("viewMenu"));
        editMenu.setText(resourceBundle.getString("editMenu"));
        selectAllItem.setText(resourceBundle.getString("selectAllItem"));
        jobsMenuItem.setText(resourceBundle.getString("jobsMenuItem"));
        logsMenuItem.setText(resourceBundle.getString("logsMenuItem"));
        helpMenu.setText(resourceBundle.getString("helpMenu"));
        aboutMenuItem.setText(resourceBundle.getString("aboutMenuItem"));
        selectAllItem.setDisable(true);
    }

    public void showSettingsPopup() {
        final SettingsView settingsView = new SettingsView();
        Popup.show(settingsView.getView(), resourceBundle.getString("settingsMenuItem"));
    }

    public void showAboutPopup() {
        final AboutView aboutView = new AboutView();
        Popup.show(aboutView.getView(), resourceBundle.getString("aboutMenuItem"));
    }

    public void showSessionPopup() {
        Popup.show(new NewSessionView().getView(), resourceBundle.getString("sessionsMenuItem"));
    }

    public MenuItem getSelectAllMenuItem() {
        return selectAllItem;
    }

    public void selectAllItemsInPane() {
        final TreeTableView<Ds3TreeTableValue> ds3TreeTable = ds3Common.getDs3TreeTableView();
        if (null != ds3TreeTable && null != ds3TreeTable.getRoot().getParent()) {

            final ObservableList<String> rowNameList = FXCollections.observableArrayList();

            ds3TreeTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            //Selecting all the items of selected root(All visible items)
            ds3TreeTable.getRoot().getChildren().forEach((child) -> {
                if (!rowNameList.contains(child.getValue().getName())) {
                    rowNameList.add(child.getValue().getName());
                    ds3TreeTable.getSelectionModel().select(child);
                }
            });
        }
    }

    private void logsORJobsMenuItemAction(final CheckMenuItem menuItem, final VBox vBox, final ScrollPane pane, final Tab tab) {
        if (menuItem.isSelected()) {
            if (menuItem.getId().contains("jobsMenuItem")) {
                tab.setContent(vBox);
                bottomTabPane.getTabs().add(0, tab);
            } else {
                tab.setContent(pane);
                bottomTabPane.getTabs().add(tab);
            }
            bottomTabPane.getSelectionModel().select(tab);
            if (!jobSplitter.getItems().stream().anyMatch(i -> i instanceof TabPane)) {
                jobSplitter.getItems().add(bottomTabPane);
                jobSplitter.setDividerPositions(0.75);
            }
        } else {
            if (!menuItem.isSelected())
                bottomTabPane.getTabs().remove(tab);
        }
        if (bottomTabPane.getTabs().size() == 0) {
            jobSplitter.getItems().remove(bottomTabPane);
        }
    }

    public void logText(final String log, final LogType type) {
        Platform.runLater(() -> {
            if (inlineCssTextArea != null) {
                final int previousSize = inlineCssTextArea.getParagraphs().size() - 2;
                inlineCssTextArea.appendText(formattedString(log));
                final int size = inlineCssTextArea.getParagraphs().size() - 2;
                for (int i = previousSize + 1; i <= size; i++)
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
        });
    }

    private String formattedString(final String log) {
        return StringConstants.FORWARD_OPR + StringConstants.SPACE + log + "\n";
    }

    public Label getLblCount() {
        return lblCount;
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

    public DeepStorageBrowserTaskProgressView<Ds3JobTask> getJobProgressView() {
        return jobProgressView;
    }
}
