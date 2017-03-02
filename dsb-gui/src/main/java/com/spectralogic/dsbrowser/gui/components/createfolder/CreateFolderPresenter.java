package com.spectralogic.dsbrowser.gui.components.createfolder;

import com.google.inject.Injector;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.injectors.GuicePresenterInjector;
import com.spectralogic.dsbrowser.gui.services.Workers;
import com.spectralogic.dsbrowser.gui.services.tasks.CreateFolderTask;
import com.spectralogic.dsbrowser.gui.util.Ds3Alert;
import com.spectralogic.dsbrowser.gui.util.LogType;
import com.spectralogic.dsbrowser.gui.util.PathUtil;
import com.spectralogic.dsbrowser.gui.util.StringConstants;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.URL;
import java.util.ResourceBundle;

public class CreateFolderPresenter implements Initializable {

    private final static Logger LOG = LoggerFactory.getLogger(CreateFolderPresenter.class);

    @FXML
    private TextField folderNameField;

    @FXML
    private Label labelText;

    @FXML
    private Button createFolderButton, cancelCreateFolderButton;

    @com.google.inject.Inject
    private Workers workers;

    @Inject
    private CreateFolderModel createFolderModel;

    @com.google.inject.Inject
    private ResourceBundle resourceBundle;

    @com.google.inject.Inject
    private Ds3Common ds3Common;

    private Injector injector;

    @Override
    public void initialize(final URL location, final ResourceBundle resources) {
        try {
            initInjectors();
            initGUIElements();
            folderNameField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.equals(StringConstants.EMPTY_STRING)) {
                    createFolderButton.setDisable(true);
                } else {
                    createFolderButton.setDisable(false);
                }
            });
            folderNameField.setOnKeyReleased(event -> {
                if (!createFolderButton.isDisabled() && event.getCode().equals(KeyCode.ENTER)) {
                    createFolder();
                }
            });

        } catch (final Exception e) {
            LOG.error("Encountered an error making the create folder presenter", e);
        }
    }

    private void initGUIElements() {
        labelText.setText(resourceBundle.getString("labelText"));
        createFolderButton.setText(resourceBundle.getString("createFolderButton"));
        cancelCreateFolderButton.setText(resourceBundle.getString("cancelCreateFolderButton"));
    }

    public void createFolder() {
        try {
            final String location = PathUtil.getFolderLocation(createFolderModel.getLocation(), createFolderModel
                    .getBucketName());
            //Instantiating create folder task
            final CreateFolderTask createFolderTask = new CreateFolderTask(createFolderModel.getClient(),
                    createFolderModel, folderNameField.textProperty().getValue(),
                    PathUtil.getDs3ObjectList(location, folderNameField.textProperty().getValue()),
                    ds3Common.getDeepStorageBrowserPresenter());
            workers.execute(createFolderTask);
            //Handling task actions
            createFolderTask.setOnSucceeded(event -> {
                this.closeDialog();
                ds3Common.getDeepStorageBrowserPresenter().logText(folderNameField.textProperty().getValue() + StringConstants.SPACE
                        + resourceBundle.getString("folderCreated"), LogType.SUCCESS);
            });
            createFolderTask.setOnCancelled(event -> this.closeDialog());
            createFolderTask.setOnFailed(event -> {
                this.closeDialog();
                Ds3Alert.show(resourceBundle.getString("createFolderErrAlert"), resourceBundle.getString("createFolderErrLogs"), Alert.AlertType.ERROR);
            });
        } catch (final Exception e) {
            LOG.error("Failed to create folder", e);
            ds3Common.getDeepStorageBrowserPresenter().logText(resourceBundle.getString("createFolderErr") + StringConstants.SPACE
                    + folderNameField.textProperty().getValue().trim() + StringConstants.SPACE
                    + resourceBundle.getString("txtReason") + StringConstants.SPACE + e, LogType.ERROR);
            Ds3Alert.show(resourceBundle.getString("createFolderErrAlert"), resourceBundle.getString("createFolderErrLogs"), Alert.AlertType.ERROR);
        }
    }

    public void cancel() {
        LOG.info("Cancelling create folder");
        closeDialog();
    }

    private void closeDialog() {
        final Stage popupStage = (Stage) folderNameField.getScene().getWindow();
        popupStage.close();
    }

    private void initInjectors() {
        injector = GuicePresenterInjector.injector;
        workers = injector.getInstance(Workers.class);

        resourceBundle = injector.getInstance(ResourceBundle.class);

        ds3Common = injector.getInstance(Ds3Common.class);



    }
}
