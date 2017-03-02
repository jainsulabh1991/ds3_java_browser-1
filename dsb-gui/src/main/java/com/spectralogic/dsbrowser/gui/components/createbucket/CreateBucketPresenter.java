package com.spectralogic.dsbrowser.gui.components.createbucket;

import com.google.inject.Injector;
import com.spectralogic.dsbrowser.gui.DeepStorageBrowserPresenter;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.injectors.GuicePresenterInjector;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.tasks.CreateBucketTask;
import com.spectralogic.dsbrowser.gui.util.Ds3Alert;
import com.spectralogic.dsbrowser.gui.util.LogType;
import com.spectralogic.dsbrowser.gui.util.RefreshCompleteViewWorker;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class CreateBucketPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(CreateBucketPresenter.class);

    @FXML
    private TextField bucketNameField;

    @FXML
    private ComboBox dataPolicyCombo;

    @FXML
    private Label dataPolicyComboLabel, bucketNameFieldLabel;

    @FXML
    private Button createBucketButton;

    @com.google.inject.Inject
    private Workers workers;

    @Inject
    private CreateBucketWithDataPoliciesModel createBucketWithDataPoliciesModel;

    @com.google.inject.Inject
    private ResourceBundle resourceBundle;


    private DeepStorageBrowserPresenter deepStorageBrowserPresenter;


    @com.google.inject.Inject
    private Ds3Common ds3Common;

    private Injector injector;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        LOG.info("Initializing Create Bucket form");
        initInjectors();
        initGUIElements();
        //noinspection unchecked
        dataPolicyCombo.getItems().addAll(createBucketWithDataPoliciesModel.getDataPolicies().stream().map(CreateBucketModel::getDataPolicy).collect(Collectors.toList()));
        bucketNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty() && (dataPolicyCombo.getValue()) != null) {
                createBucketButton.setDisable(false);
            } else {
                createBucketButton.setDisable(true);
            }
        });
        dataPolicyCombo.setOnAction(event -> {
            if (!bucketNameField.textProperty().getValue().isEmpty() && ((String) dataPolicyCombo.getValue()) != null) {
                createBucketButton.setDisable(false);
            } else {
                createBucketButton.setDisable(true);
            }
        });
    }

    private void initGUIElements() {
        bucketNameFieldLabel.setText(resourceBundle.getString("bucketNameFieldLabel"));
        dataPolicyComboLabel.setText(resourceBundle.getString("dataPolicyComboLabel"));
    }

    /**
     * Method to create bucket on blackpearl
     */
    public void createBucket() {
        LOG.info("Create Bucket called");
        try {
            final CreateBucketModel dataPolicy = createBucketWithDataPoliciesModel.getDataPolicies().stream()
                    .filter(i -> i.getDataPolicy().equals(dataPolicyCombo.getValue())).findFirst().orElse(null);
            if (null != dataPolicy) {
                final CreateBucketTask createBucketTask = new CreateBucketTask(dataPolicy,
                        createBucketWithDataPoliciesModel.getSession().getClient(), bucketNameField.getText().trim(),
                        deepStorageBrowserPresenter);
                workers.execute(createBucketTask);
                createBucketTask.setOnSucceeded(event -> Platform.runLater(() -> {
                    LOG.info("Bucket is created");
                    deepStorageBrowserPresenter.logText(resourceBundle.getString("bucketCreated"), LogType.SUCCESS);
                    ds3Common.getDs3TreeTableView().setRoot(new TreeItem<>());
                    RefreshCompleteViewWorker.refreshCompleteTreeTableView(ds3Common, workers);
                    closeDialog();
                }));
                createBucketTask.setOnFailed(event -> {
                    Ds3Alert.show(resourceBundle.getString("createBucketError"), resourceBundle.getString("createBucketErrorAlert"), Alert.AlertType.ERROR);
                });
            } else {
                LOG.info("Data policy not found");
                deepStorageBrowserPresenter.logText(resourceBundle.getString("dataPolicyNotFoundErr"), LogType.INFO);
                Ds3Alert.show(resourceBundle.getString("createBucketError"), resourceBundle.getString("dataPolicyNotFoundErr"), Alert.AlertType.ERROR);
            }

        } catch (final Exception e) {
            LOG.error("Failed to create bucket", e);
            deepStorageBrowserPresenter.logText(resourceBundle.getString("createBucketFailedErr") + e, LogType.ERROR);
            Ds3Alert.show(resourceBundle.getString("createBucketError"), resourceBundle.getString("createBucketErrorAlert"), Alert.AlertType.ERROR);
        }
    }

    public void cancelCreateBucket() {
        LOG.info("Cancel create bucket called");
        closeDialog();
    }

    private void closeDialog() {
        final Stage popupStage = (Stage) bucketNameField.getScene().getWindow();
        popupStage.close();
    }

    private void initInjectors() {
        injector = GuicePresenterInjector.injector;
        workers = injector.getInstance(Workers.class);

        resourceBundle = injector.getInstance(ResourceBundle.class);

        ds3Common = injector.getInstance(Ds3Common.class);

        deepStorageBrowserPresenter = ds3Common.getDeepStorageBrowserPresenter();

    }
}
