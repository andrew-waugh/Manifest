/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 *
 * @author Andrew
 */
public class ManifestGUI extends Application {

    public static AppConfig config;
    Job job;
    Path assets;
    Stage setupStage;
    final String USAGE = "ManifestGUI -a <AssettFile> [-j <JobFile>]";

    /**
     * Configure the run
     *
     * @throws Exception
     */
    @Override
    public void init() throws Exception {
        List<String> l;

        l = getParameters().getRaw();
        for (int i = 1; i < l.size(); i++) {
            // System.out.println("Parameter " + i + " '" + l.get(i) + "'");
            switch (l.get(i)) {
                case "Manifest.jar":
                    i++;
                    break;

                // '-j' specifies a job file
                case "-j":
                    i++;
                    job.loadJob(Paths.get(l.get(i)));
                    i++;
                    break;

                // '-a' specifies the configuration
                case "-a":
                    i++;
                    assets = Paths.get(l.get(i));
                    i++;
                    break;

                default:
                    throw new AppFatal("Unrecognised argument '" + l.get(i) + "' Usage: " + USAGE);
            }
        }
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s");
        config = AppConfig.getInstance(assets);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("FXMLSetupRun.fxml"));
        Parent root = loader.load();

        FXMLSetupRunController controller = loader.getController();
        controller.setHostServices(getHostServices());

        setupStage = stage;
        setupStage.setTitle(AppConfig.getWindowTitle());
        Scene scene = new Scene(root);
        if (getClass().getResource("styles.css") == null) {
            throw new AppError("Cannot find styles.css");
        }
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        setupStage.setScene(scene);
        // This makes all stages close and the app exit when the main stage 
        // is closed.
        setupStage.setOnHidden(e -> controller.shutdown());
        setupStage.setOnCloseRequest(e -> Platform.exit());
        setupStage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

}
