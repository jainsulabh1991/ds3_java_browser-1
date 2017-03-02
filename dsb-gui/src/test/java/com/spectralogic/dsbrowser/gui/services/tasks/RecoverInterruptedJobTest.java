package com.spectralogic.dsbrowser.gui.services.tasks;

import com.spectralogic.ds3client.Ds3Client;
import com.spectralogic.dsbrowser.gui.DeepStorageBrowserPresenter;
import com.spectralogic.dsbrowser.gui.components.ds3panel.Ds3Common;
import com.spectralogic.dsbrowser.gui.components.interruptedjobwindow.EndpointInfo;
import com.spectralogic.dsbrowser.gui.services.JobWorkers;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.FilesAndFolderMap;
import com.spectralogic.dsbrowser.gui.services.jobinterruption.JobInterruptionStore;
import com.spectralogic.dsbrowser.gui.services.newSessionService.SessionModelService;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedCredentials;
import com.spectralogic.dsbrowser.gui.services.savedSessionStore.SavedSession;
import com.spectralogic.dsbrowser.gui.services.sessionStore.Session;
import com.spectralogic.dsbrowser.gui.util.DeepStorageBrowserTaskProgressView;
import com.spectralogic.dsbrowser.gui.util.SessionConstants;
import com.spectralogic.dsbrowser.gui.util.StringConstants;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.shape.Circle;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.spectralogic.dsbrowser.gui.util.StringConstants.EMPTY_STRING;
import static org.junit.Assert.assertTrue;

/**
 * THERE MUST BE AN INTERRUPTED JOB IN LOCAL FILE SYSTEM TO SUCCESSFULLY RUN THIS TEST CASE
 */
public class RecoverInterruptedJobTest {

    private static final JobWorkers jobWorkers = new JobWorkers(10);
    private static Session session;
    private static RecoverInterruptedJob recoverInterruptedJob;
    private boolean successFlag = false;
    private final String fileName = SessionConstants.LOCAL_FILE;


    @BeforeClass
    public static void setConnection() {
        new JFXPanel();
        Platform.runLater(() -> {
            try {
                final SavedSession savedSession = new SavedSession(SessionConstants.SESSION_NAME_BPLAB, SessionConstants.SESSION_PATH_BPLAB, SessionConstants.PORT_NO_BPLAB, null, new SavedCredentials(SessionConstants.ACCESS_ID_BPLAB, SessionConstants.SECRET_KEY_BPLAB), false);
                session = new CreateConnectionTask().createConnection(SessionModelService.setSessionModel(savedSession, false));
                final Ds3Client ds3Client = session.getClient();
                final DeepStorageBrowserPresenter deepStorageBrowserPresenter = Mockito.mock(DeepStorageBrowserPresenter.class);
                Mockito.when(deepStorageBrowserPresenter.getCircle()).thenReturn(Mockito.mock(Circle.class));
                Mockito.when(deepStorageBrowserPresenter.getLblCount()).thenReturn(Mockito.mock(Label.class));
                Mockito.when(deepStorageBrowserPresenter.getJobButton()).thenReturn(Mockito.mock(Button.class));
                final Ds3Common ds3Common = Mockito.mock(Ds3Common.class);
                Mockito.when(ds3Common.getCurrentSession()).thenReturn(session);
                final JobInterruptionStore jobInterruptionStore = JobInterruptionStore.loadJobIds();
                final Map<String, Map<String, FilesAndFolderMap>> endPointsMap = jobInterruptionStore.getJobIdsModel()
                        .getEndpoints().stream().filter(endpoint -> endpoint.containsKey(session.getEndpoint() + StringConstants
                                .COLON + session.getPortNo())).findFirst().orElse(null);
                final Map<String, FilesAndFolderMap> stringFilesAndFolderMapMap = endPointsMap.get(session.getEndpoint() + StringConstants.COLON + session.getPortNo());
                final String jobIdKey = stringFilesAndFolderMapMap.entrySet().stream()
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                final EndpointInfo endPointInfo = new EndpointInfo(session.getEndpoint() + StringConstants.COLON + session.getPortNo(),
                        ds3Client,
                        stringFilesAndFolderMapMap,
                        deepStorageBrowserPresenter,
                        ds3Common
                );
                final DeepStorageBrowserTaskProgressView<Ds3JobTask> taskProgressView = new DeepStorageBrowserTaskProgressView<>();
                Mockito.when(deepStorageBrowserPresenter.getJobProgressView()).thenReturn(taskProgressView);
                recoverInterruptedJob = new RecoverInterruptedJob(UUID.fromString(jobIdKey), endPointInfo, jobInterruptionStore, false);
                taskProgressView.getTasks().add(recoverInterruptedJob);
            } catch (final Exception e) {
                e.printStackTrace();

            }
        });

    }

    @Test
    public void executeJob() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                jobWorkers.execute(recoverInterruptedJob);
                recoverInterruptedJob.setOnSucceeded(event -> {
                    if (!recoverInterruptedJob.isJobFailed()) {
                        successFlag = true;
                    }
                    latch.countDown();
                });
                recoverInterruptedJob.setOnFailed(event -> latch.countDown());
                recoverInterruptedJob.setOnCancelled(event -> latch.countDown());

            } catch (final Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();
        assertTrue(successFlag);
    }

    @Test
    public void getSkipPath() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                final String skipPath = recoverInterruptedJob.getSkipPath(fileName, new HashMap<>());
                if (skipPath.equals(EMPTY_STRING)) {
                    successFlag = true;
                    latch.countDown();
                }
            } catch (final Exception e) {
                e.printStackTrace();
                latch.countDown();
            }
        });
        latch.await();
        assertTrue(successFlag);
    }
}