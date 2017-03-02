package com.spectralogic.dsbrowser.gui.components.ds3panel;

import com.google.common.collect.ImmutableList;
import com.google.inject.Injector;
import com.spectralogic.ds3client.commands.spectrads3.CancelJobSpectraS3Request;
import com.spectralogic.ds3client.utils.Guard;
import com.spectralogic.dsbrowser.gui.DeepStorageBrowserPresenter;
import com.spectralogic.dsbrowser.gui.components.ds3panel.ds3treetable.*;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.FileTreeModel;
import com.spectralogic.dsbrowser.gui.components.localfiletreetable.FileTreeTableItem;
import com.spectralogic.dsbrowser.gui.components.modifyjobpriority.ModifyJobPriorityModel;
import com.spectralogic.dsbrowser.gui.components.modifyjobpriority.ModifyJobPriorityPopUp;
import com.spectralogic.dsbrowser.gui.components.newsession.NewSessionView;
import com.spectralogic.dsbrowser.gui.injectors.GuicePresenterInjector;
import com.spectralogic.dsbrowser.gui.injectors.Presenter;
import com.spectralogic.dsbrowser.gui.services.JobWorkers;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.ds3Panel.CreateService;
import com.spectralogic.dsbrowser.gui.services.ds3Panel.DeleteService;
import com.spectralogic.dsbrowser.gui.services.ds3Panel.Ds3PanelService;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.FilesAndFolderMap;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.JobInterruptionStore;
import com.spectralogic.dsbrowser.gui.services.jobprioritystore.SavedJobPrioritiesStore;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Ds3SessionStore;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.services.settings.SettingsStore;
import com.spectralogic.dsbrowser.gui.services.tasks.*;
import com.spectralogic.dsbrowser.gui.util.*;
import com.spectralogic.dsbrowser.util.GuavaCollectors;
import com.spectralogic.dsbrowser.util.Icon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.stream.Stream;

@Presenter
public class Ds3PanelPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(Ds3PanelPresenter.class);

    private final Image LENS_ICON = new Image(ImageURLs.LENS_ICON);
    private final Image CROSS_ICON = new Image(ImageURLs.CROSS_ICON);

    @FXML
    private Label ds3PathIndicator;

    @FXML
    private Label infoLabel;

    @FXML
    private Label capacityLabel;

    @FXML
    private Label paneItems;

    @FXML
    private Tooltip ds3PathIndicatorTooltip;

    @FXML
    private Button ds3ParentDir, ds3Refresh, ds3NewFolder, ds3NewBucket, ds3DeleteButton, newSessionButton, ds3TransferLeft;

    @FXML
    private Tooltip ds3ParentDirToolTip, ds3RefreshToolTip, ds3NewFolderToolTip, ds3NewBucketToolTip, ds3DeleteButtonToolTip, ds3TransferLeftToolTip;

    @FXML
    private TextField ds3PanelSearch;

    @FXML
    private Tab addNewTab;

    @FXML
    private TabPane ds3SessionTabPane;

    @FXML
    private ImageView imageView, imageViewForTooltip;

    @FXML
    private Ds3TreeTablePresenter ds3TreeTablePresenter;

    
    private Ds3SessionStore store;

    
    private Workers workers;

    
    private JobWorkers jobWorkers;

    
    private ResourceBundle resourceBundle;

    
    private SavedJobPrioritiesStore savedJobPrioritiesStore;

    
    private JobInterruptionStore jobInterruptionStore;

    
    private SettingsStore settingsStore;

    private DeepStorageBrowserPresenter deepStorageBrowserPresenter;

    
    private FileTreeTableProvider provider;

    
    private DataFormat dataFormat;

    
    private Ds3Common ds3Common;

    
    private SavedSessionStore savedSessionStore;

    private GetNoOfItemsTask itemsTask;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            LOG.info("Loading Ds3PanelPresenter");
            ds3PathIndicator = makeSelectable(ds3PathIndicator);
            ds3PathIndicator.setTooltip(null);
            initInjectors();
            deepStorageBrowserPresenter = ds3Common.getDeepStorageBrowserPresenter();
            initMenuItems();
            initButtons();
            initTab();
            initTabPane();
            initListeners();

            ds3Common.setDs3PanelPresenter(this);
            final BackgroundTask backgroundTask = new BackgroundTask(ds3Common, workers);
            workers.execute(backgroundTask);
            try {
                //open default session when DSB launched
                savedSessionStore.openDefaultSession(store);
            } catch (final Exception e) {
                LOG.error("Encountered error fetching default session", e);
            }

        } catch (final Exception e) {
            LOG.error("Encountered error when creating Ds3PanelPresenter", e);
        }
    }

    private Label makeSelectable(final Label label) {
        final StackPane textStack = new StackPane();
        final TextField textField = new TextField(label.getText());
        textField.setEditable(false);
        textField.getStyleClass().add("selectableClass");

        // the invisible label is a hack to get the textField to size like a label.
        final Label invisibleLabel = new Label();
        invisibleLabel.textProperty().bind(label.textProperty());
        invisibleLabel.setVisible(false);
        textStack.getChildren().addAll(invisibleLabel, textField);
        label.textProperty().bindBidirectional(textField.textProperty());
        label.setGraphic(textStack);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        return label;
    }

    /**
     * To move to parent directory.
     */
    private void goToParentDirectory() {
        //if root is null back button will not work
        if (null != ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getValue() &&
                null != ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent()) {
            if (null == ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent().getValue()) {
                getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
                getDs3PathIndicator().setTooltip(null);
                capacityLabel.setText(StringConstants.EMPTY_STRING);
                capacityLabel.setVisible(false);
                infoLabel.setText(StringConstants.EMPTY_STRING);
                infoLabel.setVisible(false);
            } else {
                getDs3PathIndicator().setTooltip(getDs3PathIndicatorTooltip());
            }
            ds3Common.getDs3PanelPresenter().getTreeTableView()
                    .setRoot(ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getParent());
            ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot().getChildren().forEach(treeItem -> treeItem.setExpanded(false)
            );
            try {
                final ProgressIndicator progress = new ProgressIndicator();
                progress.setMaxSize(90, 90);
                ds3Common.getDs3PanelPresenter().getTreeTableView().setPlaceholder(new StackPane(progress));
                ((Ds3TreeTableItem) ds3Common.getDs3PanelPresenter().getTreeTableView().getRoot()).refresh();
            } catch (final Exception e) {
                LOG.error("Unable to change root", e);
            }
        } else {
            getDs3PathIndicator().setText(StringConstants.EMPTY_STRING);
            getDs3PathIndicator().setTooltip(null);

        }
    }

    private void initListeners() {
        ds3DeleteButton.setOnAction(event -> ds3DeleteObject());
        ds3Refresh.setOnAction(event -> RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers));
        ds3ParentDir.setOnAction(event -> goToParentDirectory());
        ds3NewFolder.setOnAction(event -> CreateService.createFolderPrompt(ds3Common));
        ds3TransferLeft.setOnAction(event -> ds3TransferToLocal());
        ds3NewBucket.setOnAction(event -> CreateService.createBucketPrompt(ds3Common, workers));

        store.getObservableList().addListener((ListChangeListener<Session>) c -> {
            if (c.next() && c.wasAdded()) {
                final List<? extends Session> newItems = c.getAddedSubList();
                newItems.forEach(newSession -> {
                    createTabAndSetBehaviour(newSession);
                    deepStorageBrowserPresenter.logText(resourceBundle.getString("starting") + StringConstants.SPACE +
                            newSession.getSessionName() + StringConstants.SESSION_SEPARATOR + newSession.getEndpoint()
                            + StringConstants.SPACE + resourceBundle.getString("session"), LogType.SUCCESS);
                });
            }
        });

        ds3SessionTabPane.getSelectionModel().selectedItemProperty().addListener((ov, oldTab, newTab) -> {
                    try {
                        if (newTab.getContent() instanceof VBox) {
                            final VBox vbox = (VBox) newTab.getContent();
                            @SuppressWarnings("unchecked")
                            final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = (TreeTableView<Ds3TreeTableValue>) vbox.getChildren().stream().filter(i -> i instanceof TreeTableView).findFirst().orElse(null);
                            final ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView.getSelectionModel().getSelectedItems()
                                    .stream().collect(GuavaCollectors.immutableList());
                            ds3Common.setDs3TreeTableView(ds3TreeTableView);
                            ds3Common.setCurrentTabPane(ds3SessionTabPane);

                            final String info = StringBuilderUtil.getPaneItemsString(ds3TreeTableView.getExpandedItemCount(), ds3TreeTableView.getSelectionModel().getSelectedItems().size()).toString();
                            if (Guard.isNullOrEmpty(values)) {
                                setBlank(true);
                            } else {
                                setBlank(false);
                                final TreeItem<Ds3TreeTableValue> ds3TreeTableValueTreeItem = values.stream().findFirst().orElse(null);
                                if (ds3TreeTableValueTreeItem != null) {
                                    final Ds3TreeTableValue value = ds3TreeTableValueTreeItem.getValue();
                                    if (!value.getType().equals(Ds3TreeTableValue.Type.Bucket)) {
                                        ds3PathIndicator.setText(value.getBucketName() + StringConstants.FORWARD_SLASH + value.getFullName());
                                        ds3PathIndicatorTooltip.setText(value.getBucketName() + StringConstants.FORWARD_SLASH + value.getFullName());
                                    } else {
                                        ds3PathIndicator.setText(value.getBucketName());
                                        ds3PathIndicatorTooltip.setText(value.getBucketName());
                                    }
                                    calculateFiles(ds3TreeTableView);
                                }
                            }
                            getPaneItems().setVisible(true);
                            getPaneItems().setText(info);

                        } else {
                            ds3Common.setCurrentSession(null);
                            setBlank(true);
                            disableSearch(true);
                        }
                    } catch (final Exception e) {
                        LOG.error("Not able to parse: {}", e);
                    }
                }
        );

        ds3SessionTabPane.getTabs().addListener((ListChangeListener<Tab>) c -> {
            if (c.next() && c.wasRemoved()) {
                if (ds3SessionTabPane.getTabs().size() == 1) {
                    disableMenu(true);
                }
                ds3Common.getDs3PanelPresenter().getInfoLabel().setVisible(false);
            } else if (c.wasAdded()) {
                disableMenu(false);
            }
        });

        deepStorageBrowserPresenter.getJobProgressView().setGraphicFactory(task -> {
            final ImageView imageView = new ImageView();
            imageView.setImage(new Image(ImageURLs.SETTINGS_ICON));
            final Button button = new Button();
            button.setGraphic(imageView);
            button.setTooltip(new Tooltip(resourceBundle.getString("viewOrModifyJobPriority")));
            button.setOnAction(event -> modifyJobPriority(task));
            return button;
        });

    }

    private void createTabAndSetBehaviour(final Session newSession) {
        ds3Common.setCurrentSession(newSession);
        addNewTab.setTooltip(new Tooltip(resourceBundle.getString("newSessionToolTip")));
        final Ds3TreeTableView newTreeView = new Ds3TreeTableView(newSession, deepStorageBrowserPresenter, this, ds3Common);
        final Tab treeTab = new Tab(newSession.getSessionName() + StringConstants.SESSION_SEPARATOR
                + newSession.getEndpoint(), newTreeView.getView());
        treeTab.setOnSelectionChanged(event -> {
            ds3Common.setCurrentSession(newSession);
            final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(
                    jobInterruptionStore.getJobIdsModel().getEndpoints(), newSession.getEndpoint()
                            + StringConstants.COLON + newSession.getPortNo(),
                    deepStorageBrowserPresenter.getJobProgressView(), null);
            ParseJobInterruptionMap.setButtonAndCountNumber(jobIDMap, deepStorageBrowserPresenter);

        });
        treeTab.setOnCloseRequest(event -> ds3Common.setSessionOfClosedTab(getSession()));
        treeTab.setOnClosed(event -> closeTab((Tab) event.getSource()));
        treeTab.setTooltip(new Tooltip(newSession.getSessionName() + StringConstants.SESSION_SEPARATOR + newSession.getEndpoint()));
        final int totalTabs = ds3SessionTabPane.getTabs().size();
        ds3SessionTabPane.getTabs().add(totalTabs - 1, treeTab);
        ds3SessionTabPane.getSelectionModel().select(treeTab);
    }

    private void modifyJobPriority(final Ds3JobTask task) {
        {
            UUID jobId = null;
            if (task instanceof Ds3PutJob) {
                final Ds3PutJob ds3PutJob = (Ds3PutJob) task;
                jobId = ds3PutJob.getJobId();
            } else if (task instanceof Ds3GetJob) {
                final Ds3GetJob ds3GetJob = (Ds3GetJob) task;
                jobId = ds3GetJob.getJobId();
            } else if (task instanceof RecoverInterruptedJob) {
                final RecoverInterruptedJob recoverInterruptedJob = (RecoverInterruptedJob) task;
                jobId = recoverInterruptedJob.getUuid();
            }
            if (getSession() != null) {
                if (jobId != null) {

                    final GetJobPriorityTask jobPriorityTask = new GetJobPriorityTask(getSession(), jobId);

                    workers.execute(jobPriorityTask);
                    jobPriorityTask.setOnSucceeded(eventPriority -> Platform.runLater(() -> {
                        LOG.info("Launching metadata popup");
                        ModifyJobPriorityPopUp.show((ModifyJobPriorityModel) jobPriorityTask.getValue(), resourceBundle);
                    }));
                } else {
                    LOG.info("Job is not started yet");
                }
            }
        }
    }

    private void closeTab(final Tab closedTab) {
        {
            try {
                if (closedTab != null) {
                    final Session closedSession = ds3Common.getSessionOfClosedTab();
                    if (closedSession != null) {
                        CancelJobsWorker.cancelAllRunningJobsBySession(jobWorkers, jobInterruptionStore, LOG, workers, closedSession);
                        store.removeSession(closedSession);
                        ds3Common.getExpandedNodesInfo().remove(closedSession.getSessionName() +
                                StringConstants.SESSION_SEPARATOR + closedSession.getEndpoint());
                        ds3Common.setSessionOfClosedTab(null);
                        deepStorageBrowserPresenter.logText(closedSession.getSessionName() +
                                StringConstants.SESSION_SEPARATOR + closedSession.getEndpoint() + StringConstants
                                .SPACE + resourceBundle.getString("closed"), LogType.ERROR);
                    }
                }
                final Session currentSession = getSession();
                if (currentSession != null) {
                    final Map<String, FilesAndFolderMap> jobIDMap = ParseJobInterruptionMap.getJobIDMap(jobInterruptionStore.getJobIdsModel().getEndpoints(),
                            currentSession.getEndpoint() + StringConstants.COLON + currentSession.getPortNo(),
                            deepStorageBrowserPresenter.getJobProgressView(), null);
                    ParseJobInterruptionMap.setButtonAndCountNumber(jobIDMap, deepStorageBrowserPresenter);
                }
            } catch (final Exception e) {
                LOG.error("Failed to remove session: {}", e);
            }
            if (store.size() == 0) {
                ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
                addNewTab.setTooltip(null);
            }
        }
    }

    public void setBlank(final boolean isSetBlank) {
        if (isSetBlank) {
            ds3PathIndicator.setText(StringConstants.EMPTY_STRING);
            ds3PathIndicator.setTooltip(null);
            paneItems.setVisible(false);
            capacityLabel.setVisible(false);
            infoLabel.setVisible(false);
        } else {
            ds3PathIndicator.setTooltip(ds3PathIndicatorTooltip);
            paneItems.setVisible(true);
            capacityLabel.setVisible(true);
            infoLabel.setVisible(true);
            capacityLabel.setText(resourceBundle.getString("infoLabel"));
            infoLabel.setText(resourceBundle.getString("infoLabel"));
        }
    }

    private Session getSession() {
        return ds3Common.getCurrentSession();
    }

    private void ds3TransferToLocal() {
        final Session session = getSession();
        if (null != session && null != ds3Common) {
            try {

                final TreeTableView<Ds3TreeTableValue> ds3TreeTableView = getTreeTableView();
                ImmutableList<TreeItem<Ds3TreeTableValue>> selectedItemsAtSourceLocation = ds3TreeTableView.getSelectionModel().getSelectedItems()
                        .stream().collect(GuavaCollectors.immutableList());
                final TreeItem<Ds3TreeTableValue> root = ds3TreeTableView.getRoot();
                if (Guard.isNullOrEmpty(selectedItemsAtSourceLocation) && (null == root || null == root.getValue())) {
                    LOG.info("Files not selected");
                    Ds3Alert.show(resourceBundle.getString("error"), resourceBundle.getString("fileSelect"), Alert.AlertType.ERROR);
                    return;
                } else if (Guard.isNullOrEmpty(selectedItemsAtSourceLocation)) {
                    final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
                    selectedItemsAtSourceLocation = builder.add(root).build().asList();
                }
                final TreeTableView<FileTreeModel> treeTable = ds3Common.getLocalTreeTableView();
                final Label localFilePathIndicator = ds3Common.getLocalFilePathIndicator();
                final String fileRootItem = localFilePathIndicator.getText();
                final ObservableList<TreeItem<FileTreeModel>> selectedItemsAtDestination = treeTable.getSelectionModel().getSelectedItems();
                if (fileRootItem.equals(resourceBundle.getString("myComputer"))) {
                    if (Guard.isNullOrEmpty(selectedItemsAtDestination)) {
                        LOG.info("Location not selected");
                        Ds3Alert.show(resourceBundle.getString("information"), resourceBundle.getString("sourceFileSelectError"), Alert.AlertType.INFORMATION);
                        return;
                    }
                }
                if (selectedItemsAtDestination.size() > 1) {
                    Ds3Alert.show(resourceBundle.getString("error"), resourceBundle.getString("multipleDestError"), Alert.AlertType.ERROR);
                    return;
                }
                final ImmutableList<FileTreeModel> selectedItemsAtDestinationList = selectedItemsAtDestination.stream()
                        .map(TreeItem::getValue).collect(GuavaCollectors.immutableList());

                //Getting selected item at source location
                final ImmutableList<Ds3TreeTableValue> selectedItemsAtSourceLocationList = selectedItemsAtSourceLocation.stream()
                        .map(TreeItem::getValue).collect(GuavaCollectors.immutableList());
                final ImmutableList<Ds3TreeTableValueCustom> selectedItemsAtSourceLocationListCustom =
                        selectedItemsAtSourceLocationList.stream()
                                .map(v -> new Ds3TreeTableValueCustom(v.getBucketName(),
                                        v.getFullName(), v.getType(), v.getSize(), v.getLastModified(),
                                        v.getOwner(), v.isSearchOn())).collect(GuavaCollectors.immutableList());
                final Path localPath;
                //Getting selected item at destination location
                final FileTreeModel selectedAtDest = selectedItemsAtDestinationList.stream().findFirst().orElse(null);
                if (selectedAtDest == null) {
                    localPath = Paths.get(fileRootItem);
                } else if (selectedAtDest.getType().equals(FileTreeModel.Type.File)) {
                    localPath = selectedAtDest.getPath().getParent();
                } else {
                    localPath = selectedAtDest.getPath();
                }
                final String priority = (!savedJobPrioritiesStore.getJobSettings().getGetJobPriority()
                        .equals(resourceBundle.getString("defaultPolicyText"))) ?
                        savedJobPrioritiesStore.getJobSettings().getGetJobPriority() : null;
                final Ds3GetJob getJob = new Ds3GetJob(selectedItemsAtSourceLocationListCustom, localPath, session.getClient(),
                        priority, settingsStore.getProcessSettings().getMaximumNumberOfParallelThreads(),
                        jobInterruptionStore, ds3Common);
                jobWorkers.execute(getJob);
                getJob.setOnSucceeded(event -> {
                    LOG.info("Succeed");
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });
                getJob.setOnFailed(e -> {
                    LOG.info("Get Job failed");
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });
                getJob.setOnCancelled(e -> {
                    LOG.info("Get Job cancelled");
                    if (getJob.getJobId() != null) {
                        try {
                            session.getClient().cancelJobSpectraS3(new CancelJobSpectraS3Request(getJob.getJobId()));
                            ParseJobInterruptionMap.removeJobID(jobInterruptionStore, getJob.getJobId().toString(), getJob.getDs3Client().getConnectionDetails().getEndpoint(), deepStorageBrowserPresenter);
                            deepStorageBrowserPresenter.logText(resourceBundle.getString("getJobCancelled"), LogType
                                    .ERROR);
                        } catch (final Exception e1) {
                            LOG.error("Failed to cancel job", e1);
                        }
                    }
                    refreshLocalSideView(selectedItemsAtDestination, treeTable, localFilePathIndicator, fileRootItem);
                });

            } catch (final Exception e) {
                LOG.error("Failed to get data from black pearl: {}", e);
                deepStorageBrowserPresenter.logText(resourceBundle.getString("somethingWentWrong"), LogType.ERROR);
                Ds3Alert.show(resourceBundle.getString("error"), resourceBundle.getString("somethingWentWrong"), Alert.AlertType.ERROR);
            }
        } else {
            Ds3Alert.show(resourceBundle.getString("error"), resourceBundle.getString("invalidSession"), Alert.AlertType.ERROR);
        }
    }

    private void refreshLocalSideView(final ObservableList<TreeItem<FileTreeModel>> selectedItemsAtDestination,
                                      final TreeTableView<FileTreeModel> treeTable, final Label fileRootItemLabel, final String fileRootItem) {
        if (selectedItemsAtDestination.stream().findFirst().isPresent()) {
            final TreeItem<FileTreeModel> selectedItem = selectedItemsAtDestination.stream().findFirst().orElse(null);
            if (selectedItem != null) {
                if (selectedItem instanceof FileTreeTableItem) {
                    final FileTreeTableItem fileTreeTableItem = (FileTreeTableItem) selectedItem;
                    fileTreeTableItem.refresh();
                    treeTable.getSelectionModel().clearSelection();
                    treeTable.getSelectionModel().select(selectedItem);
                }
            }
        } else {
            final TreeItem<FileTreeModel> rootTreeItem = new TreeItem<>();
            rootTreeItem.setExpanded(true);
            treeTable.setShowRoot(false);
            final Stream<FileTreeModel> rootItems = provider.getRoot(fileRootItem);
            fileRootItemLabel.setText(fileRootItem);
            rootItems.forEach(ftm -> {
                final TreeItem<FileTreeModel> newRootTreeItem = new FileTreeTableItem(provider, ftm, workers);
                rootTreeItem.getChildren().add(newRootTreeItem);
            });

            treeTable.setRoot(rootTreeItem);
        }
    }

    public void ds3DeleteObject() {
        LOG.info("Got delete bucket event");
        final TreeTableView<Ds3TreeTableValue> ds3TreeTable = ds3Common.getDs3TreeTableView();
        ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTable.getSelectionModel().getSelectedItems().stream().collect(GuavaCollectors.immutableList());
        final TreeItem<Ds3TreeTableValue> root = ds3TreeTable.getRoot();
        if (Guard.isNullOrEmpty(values)) {
            if (root.getValue() == null) {
                LOG.info("No files selected");
                Ds3Alert.show(resourceBundle.getString("error"), resourceBundle.getString("noFiles"), Alert.AlertType.ERROR);
                return;
            } else {
                final ImmutableList.Builder<TreeItem<Ds3TreeTableValue>> builder = ImmutableList.builder();
                values = builder.add(root).build().asList();
            }
        } else if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.Directory)) {
            LOG.info("Going delete the folder");
            final TreeItem<Ds3TreeTableValue> treeItem = values.stream().findFirst().orElse(null);
            if (treeItem != null) {
                DeleteService.deleteFolder(ds3Common, values);
            }
        } else if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.Bucket)) {
            LOG.info("Going delete the bucket");
            final TreeItem<Ds3TreeTableValue> treeItem = values.stream().findFirst().orElse(null);
            if (treeItem != null) {
                DeleteService.deleteBucket(ds3Common, values, workers);
            }
        } else if (values.stream().map(TreeItem::getValue).anyMatch(value -> value.getType() == Ds3TreeTableValue.Type.File)) {
            LOG.info("Going delete the file(s)");
            DeleteService.deleteFiles(ds3Common, values, workers);
        }
    }

    private void initTabPane() {
        ds3SessionTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (ds3SessionTabPane.getTabs().size() > 1 && newValue == addNewTab) {
                // popup new session dialog box
                final int sessionCount = store.size();
                newSessionDialog();
                if (sessionCount == store.size()) {
                    // Do not select the new value if NewSessionDialog fails
                    ds3SessionTabPane.getSelectionModel().select(oldValue);
                }
            }
        });
        ds3SessionTabPane.getTabs().addListener((ListChangeListener<? super Tab>) c -> {
            if (c.next() && c.wasRemoved()) {
                // TODO prompt the user to save each session that was closed,
                // if it is not already in the saved session store
            }
        });

    }


    private TreeTableView<Ds3TreeTableValue> getTreeTableView() {
        final VBox vbox = (VBox) ds3SessionTabPane.getSelectionModel().getSelectedItem().getContent();
        //noinspection unchecked
        return (TreeTableView<Ds3TreeTableValue>) vbox.getChildren().stream().filter(i ->
                i instanceof TreeTableView).findFirst().orElse(null);
    }

    public void newSessionDialog() {
        Popup.show(new NewSessionView().getView(), resourceBundle.getString("sessionsMenuItem"));
    }

    private void initTab() {
        addNewTab.setGraphic(Icon.getIcon(FontAwesomeIcon.PLUS));
    }

    private void initMenuItems() {
        ds3ParentDirToolTip.setText(resourceBundle.getString("ds3ParentDirToolTip"));
        ds3RefreshToolTip.setText(resourceBundle.getString("ds3RefreshToolTip"));
        ds3NewFolderToolTip.setText(resourceBundle.getString("ds3NewFolderToolTip"));
        ds3NewBucketToolTip.setText(resourceBundle.getString("ds3NewBucketToolTip"));
        ds3DeleteButtonToolTip.setText(resourceBundle.getString("ds3DeleteButtonToolTip"));
        ds3PanelSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            final Image icon = (Guard.isStringNullOrEmpty(newValue)) ? LENS_ICON : CROSS_ICON;
            imageView.setImage(icon);
            imageView.setMouseTransparent(icon == LENS_ICON);
            if (Guard.isStringNullOrEmpty(newValue)) {
                RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
            }
        });
        imageView.setOnMouseClicked(event -> ds3PanelSearch.setText(StringConstants.EMPTY_STRING));
        ds3PanelSearch.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                Ds3PanelService.filterChanged(ds3Common, workers);
            }
        });
        if (ds3SessionTabPane.getTabs().size() == 1) {
            disableMenu(true);
        }
    }

    public void disableSearch(final boolean disable) {
        ds3PanelSearch.setText(StringConstants.EMPTY_STRING);
        ds3PanelSearch.setDisable(disable);
    }

    private void initButtons() {
        newSessionButton.setText(resourceBundle.getString("newSessionButton"));
        ds3TransferLeft.setText(resourceBundle.getString("ds3TransferLeft"));
        ds3TransferLeftToolTip.setText(resourceBundle.getString("ds3TransferLeftToolTip"));
        final Tooltip imageToolTip = new Tooltip(resourceBundle.getString("imageViewForTooltip"));
        imageToolTip.setMaxWidth(150);
        imageToolTip.setWrapText(true);
        Tooltip.install(imageViewForTooltip, imageToolTip);
    }

    private void disableMenu(final boolean disable) {
        if (disable) {
            ds3PathIndicator.setTooltip(null);
        }
        imageViewForTooltip.setDisable(disable);
        ds3ParentDir.setDisable(disable);
        ds3Refresh.setDisable(disable);
        ds3NewFolder.setDisable(disable);
        ds3NewBucket.setDisable(disable);
        ds3DeleteButton.setDisable(disable);
        ds3PanelSearch.setDisable(disable);
        ds3TransferLeft.setDisable(disable);
    }

    public String getSearchedText() {
        return ds3PanelSearch.getText();
    }

    //Method for calculating no. of files and capacity of selected tree item
    public void calculateFiles(final TreeTableView<Ds3TreeTableValue> ds3TreeTableView) {
        //if a task for calculating of items is already running and cancel that task
        if (itemsTask != null) {
            itemsTask.cancel(true);
        }
        try {
            ObservableList<TreeItem<Ds3TreeTableValue>> selectedItems = ds3TreeTableView.getSelectionModel().getSelectedItems();
            final TreeItem<Ds3TreeTableValue> root = ds3TreeTableView.getRoot();
            if (Guard.isNullOrEmpty(selectedItems) && root != null && root.getValue() != null) {
                selectedItems = FXCollections.observableArrayList();
                selectedItems.add(root);
            }
            //start a new task for calculating
            itemsTask = new GetNoOfItemsTask(ds3TreeTableView, ds3Common, selectedItems);
            workers.execute(itemsTask);

            itemsTask.setOnSucceeded(event -> Platform.runLater(() -> {
                final ImmutableList<TreeItem<Ds3TreeTableValue>> values = ds3TreeTableView.getSelectionModel().getSelectedItems()
                        .stream().collect(GuavaCollectors.immutableList());
                TreeItem<Ds3TreeTableValue> selectedRoot = ds3TreeTableView.getRoot();
                if (!Guard.isNullOrEmpty(values)) {
                    selectedRoot = values.stream().findFirst().orElse(null);
                }
                //for number of files and folders
                final FilesCountModel filesCountModel = itemsTask.getValue();
                if (selectedRoot == null || selectedRoot.getValue() == null || getSession() == null || null == filesCountModel) {
                    setVisibilityOfItemsInfo(false);
                    return;
                } else {
                    setVisibilityOfItemsInfo(true);
                    setItemCountPanelInfo(filesCountModel, selectedRoot);
                }

            }));

        } catch (final Exception e) {
            LOG.error("Unable to calculate no. of items and capacity", e);
        }
    }

    private void setItemCountPanelInfo(final FilesCountModel filesCountModel, final TreeItem<Ds3TreeTableValue> selectedRoot) {
        //For no. of folder(s) and file(s)
        if (filesCountModel.getNoOfFiles() == 0 && filesCountModel.getNoOfFolders() == 0) {
            ds3Common.getDs3PanelPresenter().getInfoLabel().setText(resourceBundle.getString("containsNoItem"));
        } else {
            ds3Common.getDs3PanelPresenter().getInfoLabel()
                    .setText(StringBuilderUtil.getItemsCountInfoMessage(filesCountModel.getNoOfFolders(),
                            filesCountModel.getNoOfFiles()).toString());
        }
        //For capacity of bucket or folder
        ds3Common.getDs3PanelPresenter().getCapacityLabel()
                .setText(StringBuilderUtil.getCapacityMessage(filesCountModel.getTotalCapacity(),
                        selectedRoot.getValue().getType()).toString());
    }

    private void setVisibilityOfItemsInfo(final boolean visibility) {
        ds3Common.getDs3PanelPresenter().getInfoLabel().setVisible(visibility);
        ds3Common.getDs3PanelPresenter().getCapacityLabel().setVisible(visibility);
    }

    public Label getCapacityLabel() {
        return capacityLabel;
    }

    public void setDs3TreeTablePresenter(final Ds3TreeTablePresenter ds3TreeTablePresenter) {
        this.ds3TreeTablePresenter = ds3TreeTablePresenter;
    }

    public Label getDs3PathIndicator() {
        return ds3PathIndicator;
    }

    public Tooltip getDs3PathIndicatorTooltip() {
        return ds3PathIndicatorTooltip;
    }

    public Label getInfoLabel() {
        return infoLabel;
    }

    public Label getPaneItems() {
        return paneItems;
    }

    private void initInjectors() {
        Injector injector = GuicePresenterInjector.injector;
        workers = injector.getInstance(Workers.class);
        jobWorkers = injector.getInstance(JobWorkers.class);
        provider = injector.getInstance(FileTreeTableProvider.class);
        resourceBundle = injector.getInstance(ResourceBundle.class);
        savedJobPrioritiesStore = injector.getInstance(SavedJobPrioritiesStore.class);
        jobInterruptionStore = injector.getInstance(JobInterruptionStore.class);
        ds3Common = injector.getInstance(Ds3Common.class);
        settingsStore = injector.getInstance(SettingsStore.class);
        store = injector.getInstance(Ds3SessionStore.class);
        savedSessionStore = injector.getInstance(SavedSessionStore.class);
    }

}


