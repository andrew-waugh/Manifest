/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import org.json.simple.JSONObject;

/**
 * FXML Controller class
 *
 * @author Andrew
 */
public class FXMLProgressController extends BaseManifestController implements Initializable {

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
    private ArrayList<String> results; // list of results generated
    int objectsToProcess;       // totalObjects objects to process
    manifestService cms;        // Created to handle processing

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        results = new ArrayList<String>();
        objectsToProcess = 1;
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
     * Carry out the instructions from the setup screen
     *
     * @param job information about manifest to be created
     * @param baseDirectory the base directory
     */
    public void generate(Job job, File baseDirectory) {

        this.job = job;
        this.baseDirectory = baseDirectory;
        cms = new manifestService();
        cms.start();
    }

    /**
     * Callback when user presses 'Finish' button
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
    private class manifestService extends Service<ArrayList<String>> {

        // create the task (i.e. thread) that actually processes the manifest
        @Override
        protected Task<ArrayList<String>> createTask() {
            DoManifestTask manifestTask;

            warningTA.insertText(0, "No errors or warnings yet\n");
            manifestTask = new DoManifestTask(job, warningTA, processedPB, countL);

            // this event handler is called when the thread completes, and it
            // puts the response into the report list view and scrolls to the
            // end
            manifestTask.addEventHandler(WorkerStateEvent.WORKER_STATE_SUCCEEDED, new EventHandler<WorkerStateEvent>() {
                @Override
                public void handle(WorkerStateEvent event) {
                    warningTA.appendText(getValue().toString());
                    warningTA.positionCaret(warningTA.getLength() - 1);
                    processedPB.setProgress(1);
                }
            });
            return manifestTask;
        }

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
        int objectsProcessed;     // files processed
        Manifest manifest;      // Encapsulation of the file harvest itself
        int totalObjects;

        public DoManifestTask(Job job, TextArea ta, ProgressBar pb, Label count) {
            this.job = job;
            this.ta = ta;
            this.pb = pb;
            this.count = count;
            objectsProcessed = 0;
        }

        /**
         * Actually process files. The parameters of the work were provided when
         * the task was created in the Job argument. The
         *
         * @return a list of strings containing the results of the processing
         */
        @Override
        protected ArrayList<String> call() {
            ArrayList<String> results; // list of results generated
            LogHandler lh;
            int i;

            // create a list in which to put the results of processing 
            results = new ArrayList<>();
            updateValue(results);

            try {
                lh = new LogHandler(results);
                manifest = new Manifest(job, lh, this);
            } catch (AppFatal af) {
                results.add("FAILED: " + af.getMessage());
                return results;
            }

            // go through list of directories, counting number of files to hash
            totalObjects = 0;
            for (i = 0; i < job.directories.size() && !isCancelled(); i++) {
                totalObjects += count((Paths.get(job.directories.get(i))));
            }
            if (isCancelled()) {
                return results;
            }
            Platform.runLater(() -> {
                countL.setText("0/" + totalObjects);
            });

            // say we are starting...
            /*
            results.add("Starting " + job.directories.get(i) + "\n");
            Platform.runLater(() -> {
                for (int j = 0; j < results.size(); j++) {
                    ta.appendText(results.get(j));
                    ta.appendText("\n");
                }
                results.clear();
                // lv.scrollTo(lv.getItems().size() - 1);
            });
             */
            // go through list of directories, pausing after each to deal with
            // results and check if we've been cancelled
            // System.out.println("Processing size: " + job.items.size());
            try {
                if (job.task == Job.Task.CREATE) {
                    manifest.createManifest();
                } else if (job.task == Job.Task.VERIFY) {
                    manifest.checkManifest();
                }
            } catch (AppFatal | AppError af) {
                System.out.println("creating manifest error: "+af.getMessage());
            }

            Platform.runLater(() -> {
                // statusL.setText("Finished");
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
                for (int j = 0; j < results.size(); j++) {
                    ta.appendText(results.get(j));
                    ta.appendText("\n");
                }
                results.clear();
                // lv.scrollTo(lv.getItems().size() - 1);
                countL.setText(objectsProcessed + "/" + totalObjects);
                pb.setProgress(k);
            });
            return !isCancelled();
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
            responses.add(s+"\n");
        }

        @Override
        public void flush() {

        }

        @Override
        public void close() {

        }
    }
}
