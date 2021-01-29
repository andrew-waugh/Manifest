/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.json.simple.JSONObject;

/**
 * This class reports on the progress and results of creating or verifying a
 * manifest. It consists of three inter-related classes - the
 * FXMLProgressController itself which runs the GUI screen, and two internal
 * classes: ManifestService and DoManifestTask. These implement a thread that
 * manage the actual work. The separate thread allows the GUI to remain responsive
 * while the work is actually being carried out.
 * 
 * Note this class doesn't actually do the work - it handles communication
 * between the thread that manages the GUI and the thread that does the work.
 *
 * @author Andrew
 */
public class FXMLProgressController extends BaseManifestController {

    @FXML
    private AnchorPane rootAP;
    @FXML
    private ProgressBar processedPB;
    @FXML
    private Button finishB;
    @FXML
    private TextArea warningTA;
    @FXML
    private Label currentlyProcessingL;
    @FXML
    private Label countL;

    Job job;                    // information shared between scenes
    HostServices hostServices;
    ManifestService cms;        // The service to handle processing
    
    static String defaultText = "Stage 2 processing - no errors or warnings generated yet\n";

    /**
     * Initializes the controller class. This is called when the GUI screen
     * is created.
     */
    public void initialize() {
        processedPB.setProgress(0);

        try {
            initTooltips();
        } catch (AppFatal af) {
            System.err.println(af.getMessage());
        }
    }

    /**
     * Put a tool tip on each control
     */
    private void initTooltips() throws AppFatal {
        JSONObject json = openTooltips();
        createTooltip(warningTA, (String) json.get("report"));
        createTooltip(processedPB, (String) json.get("progress"));
        createTooltip(currentlyProcessingL, (String) json.get("processing"));
        createTooltip(finishB, (String) json.get("finish"));
    }

    /**
     * This is called by the SetupRunController when it is desired to start a
     * new processing run. It passes in the details of the job to be run and
     * starts the service
     *
     * @param job information about manifest to be created
     * @param baseDirectory the base directory
     */
    public void generate(Job job, File baseDirectory) {

        this.job = job;
        this.baseDirectory = baseDirectory;
        
        // start the service that will do the work (which can be reused for multiple tasks)
        cms = new ManifestService();
        cms.start(); // start the service
    }

    /**
     * Callback when user presses a close button or the close window button
     */
    @FXML
    private void handleCloseAction(ActionEvent event) throws Exception {
        shutdown();
    }

    /**
     * Called when it is necessary to close this window
     */
    public void shutdown() {
        cms.cancel();
        final Stage stage = (Stage) rootAP.getScene().getWindow();
        stage.close();
    }

    /**
     * Create a service that processes the manifest
     */
    private class ManifestService extends Service<ArrayList<String>> {

        // create the task (i.e. thread) that actually processes the manifest
        @Override
        protected Task<ArrayList<String>> createTask() {
            DoManifestTask manifestTask;

            manifestTask = new DoManifestTask(job, warningTA, processedPB, countL, finishB);

            // this event handler is called when the thread completes, and it
            // puts the response into the report list view and scrolls to the
            // end
            manifestTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent event) {
                    ArrayList<String> results;

                    results = getValue();
                    if (results.size() > 0) {
                        if (warningTA.getText().equals(defaultText)) {
                            warningTA.clear();
                        }
                        for (int j = 0; j < results.size(); j++) {
                            warningTA.appendText(results.get(j));
                            warningTA.appendText("\n");
                        }
                        results.clear();
                    }
                }
            });
            return manifestTask;
        }
        
        // the user requested to cancel the job
        /*
        @Override
        protected void cancelled() {
            System.out.println("We've been cancelled!");
        }
        */
    }

    /**
     * Create a task (i.e. a thread) that actually creates the Manifest in order
     * to ensure that the GUI remains responsive
     */
    public class DoManifestTask extends Task<ArrayList<String>> {

        final Job job;
        final TextArea ta;      // the list view that will display the logging results
        final ProgressBar pb;   // the progress bar
        final Label count;
        final Button fb;        // the finish button
        int objectsProcessed;     // files processed
        Manifest manifest;      // Encapsulation of the file harvest itself
        int totalObjects;
        ArrayList<String> results; // list of results generated

        public DoManifestTask(Job job, TextArea ta, ProgressBar pb, Label count, Button fb) {
            this.job = job;
            this.ta = ta;
            this.pb = pb;
            this.count = count;
            this.fb = fb;
            results = new ArrayList<>();
            objectsProcessed = 0;
        }

        /**
         * Actually process files. The parameters of the work to be performed
         * were provided when the task was created; the GUI fields to be updated
         * during the processing were also passed when the job was created.
         * 
         * Updates to the calling GUI are scheduled via 'runLater()' as these
         * are executed in a different thread. Note that the 'runLater()' code
         * copies all of the current results and then clears the results.
         *
         * @return a list of strings containing the results of the processing
         */
        @Override
        protected ArrayList<String> call() {
            LogHandler lh;

            // create a list in which to put the results of processing 
            updateValue(results);

            try {
                lh = new LogHandler(results);
                manifest = new Manifest(job, lh, this);
            } catch (AppFatal af) {
                results.add("FAILED: " + af.getMessage());
                return results;
            }

            // set up defaults
            Platform.runLater(() -> {
                ta.insertText(0, "Stage 1: counting objects");
                count.setText("0/unknown");
                pb.setProgress(0.0);
                fb.setText("Cancel processing");
            });

            // go through list of directories, counting number of files to hash
            totalObjects = count(job.directory);
            if (isCancelled()) {
                return results;
            }
            Platform.runLater(() -> {
                ta.clear();
                ta.insertText(0, defaultText);
                count.setText("0/" + totalObjects);
                pb.setProgress(0.0);
            });

            // process the directory
            try {
                if (job.task == Job.Task.CREATE) {
                    manifest.createManifest();
                } else if (job.task == Job.Task.VERIFY) {
                    manifest.checkManifest();
                }
            } catch (AppFatal | AppError af) {
                System.out.println("Processing manifest error: " + af.getMessage());
            }

            Platform.runLater(() -> {
                if (results.size() > 0) {
                    if (ta.getText().equals(defaultText)) {
                        ta.clear();
                    }
                    for (int j = 0; j < results.size(); j++) {
                        ta.appendText(results.get(j));
                        ta.appendText("\n");
                    }
                    results.clear();
                }
                countL.setText(objectsProcessed + "/" + totalObjects);
                pb.setProgress(1.0);
                ta.appendText("Finished\n");
                fb.setText("Close");
            });
            return results;
        }

        /**
         * The code actually doing the work calls this method when it has
         * completed a unit of work. This allows the status of the GUI to be
         * updated, and for the work to check if it has been cancelled.
         *
         * @param id a String used to indicate when the processing is up to
         * @param messages a set of Strings giving any error etc to be displayed
         * @return true if processing should continue
         */
        public boolean updateStatus(String id, String[] messages) {
            double k;

            // increment the number of files processed
            objectsProcessed++;
            k = ((double) objectsProcessed + 1) / totalObjects;

            // schedule an update on the GUI
            Platform.runLater(() -> {
                currentlyProcessingL.setText(id);
                if (results.size() > 0) {
                    if (ta.getText().equals(defaultText)) {
                        ta.clear();
                    }
                    for (int j = 0; j < results.size(); j++) {
                        ta.appendText(results.get(j));
                        ta.appendText("\n");
                    }
                    results.clear();
                }
                countL.setText(objectsProcessed + "/" + totalObjects);
                pb.setProgress(k);
            });
            return isCancelled();
        }
    }

    /**
     * Recursively count the number of normal files that would be hashed
     * contained within a given file. This is used to display the progress when
     * the hashes are calculated...
     *
     * @param f file (which could be a directory)
     * @return the number of normal files
     */
    private int count(Path f) {
        DirectoryStream<Path> ds;
        int c;

        c = 0;

        // check that file or directory exists
        if (!Files.exists(f)) {
            return 0;
        }

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(f)) {
            try {
                ds = Files.newDirectoryStream(f);
                for (Path p : ds) {
                    c += count(p);
                }
                ds.close();
            } catch (IOException e) {
                // ignore - it's only a status
            }
        } else if (Files.isRegularFile(f)) {
            Platform.runLater(() -> {
                    currentlyProcessingL.setText(f.getFileName().toString());
                });
            c++;
        }
        return c;
    }

    /**
     * Log Handler used to ensure any call to Log() when processing things
     * writes the log entry to the ArrayList for eventual inclusion in the
     * status update
     */
    private class LogHandler extends Handler {

        final SimpleFormatter sf;
        ArrayList<String> responses; // list of results generated

        public LogHandler(ArrayList<String> responses) {
            this.responses = responses;
            sf = new SimpleFormatter();
        }

        @Override
        public void publish(LogRecord record) {
            String s;

            s = sf.format(record);
            responses.add(s);
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }
    }
}
