/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
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
    
    public static final AppConfig CONFIG = AppConfig.getInstance();
    Stage setupStage;
    
    @Override
    public void start(Stage stage) throws Exception {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s");
        
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
        setupStage.setOnHidden(e->controller.shutdown());
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
