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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static com.spectralogic.dsbrowser.gui.util.StringConstants.EMPTY_STRING;
import static org.junit.Assert.assertTrue;

/**
 * THERE MUST BE AN INTERRUPTED JOB IN LOCAL FILE SYSTEM TO SUCCESSFULLY RUN THIS TEST CASE
 */
public class RecoverInterruptedJobTest {

    private final static Logger LOG = LoggerFactory.getLogger(RecoverInterruptedJobTest.class);
    private static final JobWorkers jobWorkers = new JobWorkers(10);
    private static Session session;
    private static RecoverInterruptedJob recoverInterruptedJob;
    private final String fileName = SessionConstants.LOCAL_FILE;
    private boolean successFlag = false;

    @BeforeClass
    public static void setConnection() {
        new JFXPanel();
        Platform.runLater(() -> {
            try {
                final SavedSession savedSession = new SavedSession(SessionConstants.SESSION_NAME, SessionConstants.SESSION_PATH, SessionConstants.PORT_NO, null, new SavedCredentials(SessionConstants.ACCESS_ID, SessionConstants.SECRET_KEY), false);
                session = new CreateConnectionTask().createConnection(SessionModelService.setSessionModel(savedSession, false));
                final Ds3Client ds3Client = session.getClient();
                final DeepStorageBrowserPresenter deepStorageBrowserPresenter = Mockito.mock(DeepStorageBrowserPresenter.class);

                Mockito.when(deepStorageBrowserPresenter.getCircle()).thenReturn(Mockito.mock(Circle.class));
                Mockito.when(deepStorageBrowserPresenter.getLblCount()).thenReturn(Mockito.mock(Label.class));
                Mockito.when(deepStorageBrowserPresenter.getJobButton()).thenReturn(Mockito.mock(Button.class));
                final Ds3Common ds3Common = Mockito.mock(Ds3Common.class);
                Mockito.when(ds3Common.getCurrentSession()).thenReturn(session);
                Mockito.when(ds3Common.getDeepStorageBrowserPresenter()).thenReturn(deepStorageBrowserPresenter);

                final JobInterruptionStore jobInterruptionStore = JobInterruptionStore.loadJobIds();
                final Optional<Map<String, Map<String, FilesAndFolderMap>>> endPointsMapElement = jobInterruptionStore.getJobIdsModel()
                        .getEndpoints().stream().filter(endpoint -> endpoint.containsKey(session.getEndpoint() + StringConstants
                                .COLON + session.getPortNo())).findFirst();
                if (endPointsMapElement.isPresent()) {
                    final Map<String, Map<String, FilesAndFolderMap>> endPointsMap = endPointsMapElement.get();
                    final Map<String, FilesAndFolderMap> stringFilesAndFolderMapMap = endPointsMap.get(session.getEndpoint() + StringConstants.COLON + session.getPortNo());
                    final Optional<String> jobIdKeyElement = stringFilesAndFolderMapMap.entrySet().stream()
                            .map(Map.Entry::getKey)
                            .findFirst();
                    final EndpointInfo endPointInfo = new EndpointInfo(session.getEndpoint() + StringConstants.COLON + session.getPortNo(),
                            ds3Client,
                            stringFilesAndFolderMapMap,
                            deepStorageBrowserPresenter,
                            ds3Common
                    );
                    if (jobIdKeyElement.isPresent()) {
                        final String jobIdKey = jobIdKeyElement.get();
                        final DeepStorageBrowserTaskProgressView<Ds3JobTask> taskProgressView = new DeepStorageBrowserTaskProgressView<>();
                        Mockito.when(deepStorageBrowserPresenter.getJobProgressView()).thenReturn(taskProgressView);
                        Mockito.when(ds3Common.getDeepStorageBrowserPresenter().getJobProgressView()).thenReturn(taskProgressView);
                        recoverInterruptedJob = new RecoverInterruptedJob(UUID.fromString(jobIdKey), endPointInfo,
                                jobInterruptionStore, false, null, null);
                        taskProgressView.getTasks().add(recoverInterruptedJob);
                    } else {
                        LOG.info("No job available to recover");
                    }
                } else {
                    LOG.info("No job available to recover");
                }

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
                if (recoverInterruptedJob != null) {
                    final String skipPath = recoverInterruptedJob.getSkipPath(fileName, new HashMap<>());
                    if (skipPath.equals(EMPTY_STRING)) {
                        successFlag = true;
                        latch.countDown();
                    }
                } else {
                    LOG.info("No job found to recover");
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