/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
import VERSCommon.AppFatal;
import com.sun.javafx.scene.control.skin.TextFieldSkin;
import com.sun.javafx.scene.control.skin.TextInputControlSkin;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ResourceBundle;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.InputMethodEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.json.simple.JSONObject;

/**
 * FXML Controller class
 *
 * @author Andrew
 */
public class FXMLSetupRunController extends BaseManifestController implements Initializable {

    private HostServices hostServices;

    public static final String TITLE_ABOUT = "About";
    public static final String TITLE_HELP = "Help";
    Job job;    // encapsulation of the VEO creation tool
    Stage progressStage;

    @FXML
    private AnchorPane mainAP;
    @FXML
    private ComboBox<String> hashAlgorithmCB; // select the hash algorithm
    @FXML
    private CheckBox verboseCB;
    @FXML
    private CheckBox debugCB;
    @FXML
    private TextField createActorTF;
    @FXML
    private TextField createIdentifierTF;
    @FXML
    private TextArea createCommentTA;
    @FXML
    private TextField createManifestTF;
    @FXML
    private Button createManifestBrowseB;
    @FXML
    private TextField verifyManifestTF;
    @FXML
    private Button verifyManifestBrowseB;
    @FXML
    private TextField verifyActorTF;
    @FXML
    private TextField verifyIdentifierTF;
    @FXML
    private TextArea verifyCommentTA;
    @FXML
    private Button goB;
    @FXML
    private TabPane selectTP;
    @FXML
    private Tab createT;
    @FXML
    private Tab verifyT;
    @FXML
    private Tab advT;
    @FXML
    private TextField createDirectoryTF;
    @FXML
    private Button createDirBrowseB;
    @FXML
    private TextField verifyDirectoryTF;
    @FXML
    private Button verifyDirBrowseB;
    @FXML
    private CheckBox updateManifestCB;

    //private FXMLCreateSummaryController summaryController;
    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        progressStage = null;
        baseDirectory = new File(System.getProperty("user.dir", "/"));

        helpHashTag = AppConfig.getCreateHelpHashTag();

        // set up listener to react to changes in the tab selected
        selectTP.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends Tab> ov, Tab oldTab, Tab newTab) -> {
            switch (newTab.getId()) {
                case "createT":
                    job.task = Job.Task.CREATE;
                    break;
                case "verifyT":
                    job.task = Job.Task.VERIFY;
                    break;
                case "updateT":
                    job.task = Job.Task.UPDATE;
                    break;
                case "advT":
                    job.task = Job.Task.NOTSET;
                    break;
            }
            updateCreateButtonState();
        });

        // set up behavoir of create GUI elements
        createActorTF.focusedProperty().addListener(new FocusLostListener("createActorTF"));
        createActorTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        createIdentifierTF.focusedProperty().addListener(new FocusLostListener("createIdentifierTF"));
        createIdentifierTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        createCommentTA.focusedProperty().addListener(new FocusLostListener("createCommentTA"));
        createCommentTA.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.TAB);
        });createDirectoryTF.focusedProperty().addListener(new FocusLostListener("createDirectoryTF"));
        createDirectoryTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        createManifestTF.focusedProperty().addListener(new FocusLostListener("createManifestTF"));
        createManifestTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });

        // set up behavoir of verify GUI elements
        verifyActorTF.focusedProperty().addListener(new FocusLostListener("verifyActorTF"));
        verifyActorTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        verifyIdentifierTF.focusedProperty().addListener(new FocusLostListener("verifyIdentifierTF"));
        verifyIdentifierTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        verifyCommentTA.focusedProperty().addListener(new FocusLostListener("verifyCommentTA"));
        verifyCommentTA.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.TAB);
        });
        verifyDirectoryTF.focusedProperty().addListener(new FocusLostListener("verifyDirectoryTF"));
        verifyDirectoryTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });
        verifyManifestTF.focusedProperty().addListener(new FocusLostListener("verifyManifestTF"));
        verifyManifestTF.setOnKeyPressed((KeyEvent event) -> {
            traverseToNext(event, KeyCode.ENTER);
        });

        hashAlgorithmCB.getItems().addAll("SHA-1", "SHA-256", "SHA-384", "SHA-512");
        hashAlgorithmCB.valueProperty().addListener((ObservableValue<? extends String> observable, String oldValue, String newValue) -> {
            job.hashAlg = newValue;
        });
        verboseCB.setIndeterminate(false);
        verboseCB.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            job.verbose = newValue;
        });
        debugCB.setIndeterminate(false);
        debugCB.selectedProperty().addListener((ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) -> {
            job.debug = newValue;
        });

        // synchronise GUI with values in job
        job = new Job();
        sync(job);

        // set up logging
        String logPath = AppConfig.getCreateLogFilePathDefault();
        String s = AppConfig.getCreateOutputFolderDefault();
        logPath = logPath.replace("%output_folder%", s);

        try {
            initTooltips();
        } catch (AppFatal af) {
            System.err.println(af.getMessage());
        }

        // initTextFieldList();
        // can we create manifests?
        updateCreateButtonState();
    }

    /**
     * Traverse to the next node in the traversal tree.
     * WARNING. It appears that skin.getBehavior() is not supported long term...
     * @param event
     * @param key 
     */
    private void traverseToNext(KeyEvent event, KeyCode key) {
        if (event.getCode().equals(key)) {
            Node node = (Node) event.getSource();
            TextInputControlSkin skin = (TextInputControlSkin) ((Control) node).getSkin();
            skin.getBehavior().traverseNext();
        }
    }

public void shutdown() {
        if (progressStage != null) {
            progressStage.close();
        }
    }

    /**
     * Put a tool tip on each control
     */
    protected void initTooltips() throws AppFatal {
        JSONObject json;

        json = openTooltips();
        createTooltip(createActorTF, (String) json.get("actor"));
        createTooltip(createIdentifierTF, (String) json.get("identifier"));
        createTooltip(createCommentTA, (String) json.get("comment"));
        createTooltip(createDirectoryTF, (String) json.get("directory"));
        createTooltip(createDirBrowseB, (String) json.get("browse"));
        createTooltip(createManifestTF, (String) json.get("manifest"));
        createTooltip(createManifestBrowseB, (String) json.get("browse"));
        createTooltip(verifyActorTF, (String) json.get("actor"));
        createTooltip(verifyIdentifierTF, (String) json.get("identifier"));
        createTooltip(verifyCommentTA, (String) json.get("comment"));
        createTooltip(verifyDirectoryTF, (String) json.get("directory"));
        createTooltip(verifyDirBrowseB, (String) json.get("browse"));
        createTooltip(verifyManifestTF, (String) json.get("manifest"));
        createTooltip(verifyManifestBrowseB, (String) json.get("browse"));
        createTooltip(hashAlgorithmCB, (String) json.get("hashAlgorithm"));
        createTooltip(verboseCB, (String) json.get("verboseOutput"));
        createTooltip(debugCB, (String) json.get("debugOutput"));
        createTooltip(goB, (String) json.get("createManifests"));
    }

    /**
     * User has selected the File/close menu item
     *
     * @param event
     */
    @FXML
    public void handleMenuFileCloseAction(ActionEvent event) {
        final Stage stage = (Stage) mainAP.getScene().getWindow();
        stage.close();
    }

    /**
     * User has selected the File/Save Job menu item. This saves the current job
     * in a JSON file for subsequent reloading
     *
     * @param event
     */
    @FXML
    private void handleMenuSaveJobAction(ActionEvent event) {
        File f;

        f = browseForSaveFile("Select Job File to save", ".json", null);
        if (f == null) {
            return;
        }
        try {
            job.saveJob(f.toPath());
        } catch (AppError ae) {
            System.out.println(ae.toString());
        }
    }

    /**
     * User has selected the File/Load Job menu item. This loads a job
     * specification from the selected JSON file and syncs the state of the GUI
     * fields with the specification
     *
     * @param event
     */
    @FXML
    private void handleMenuLoadJobAction(ActionEvent event) {
        File f;

        f = browseForOpenFile("Select Job File to load", ".json", null);
        if (f == null) {
            return;
        }
        try {
            job.loadJob(f.toPath());
        } catch (AppError ae) {
            System.out.println(ae.toString());
            return;
        }
        sync(job);
        updateCreateButtonState();
    }

    /**
     * This method syncs the GUI state with the current state of the job
     *
     * @param job
     */
    private void sync(Job job) {
        String s;

        if (job.actor != null) {
            createActorTF.setText(job.actor);
            createActorTF.positionCaret(job.actor.length());
            verifyActorTF.setText(job.actor);
            verifyActorTF.positionCaret(job.actor.length());
        }
        if (job.identifier != null) {
            createIdentifierTF.setText(job.identifier);
            createIdentifierTF.positionCaret(job.identifier.length());
            verifyIdentifierTF.setText(job.identifier);
            verifyIdentifierTF.positionCaret(job.identifier.length());
        }
        if (job.comment != null) {
            createCommentTA.setText(job.comment);
            createCommentTA.positionCaret(job.comment.length());
            verifyCommentTA.setText(job.comment);
            verifyCommentTA.positionCaret(job.comment.length());
        }
        if (job.directory != null) {
            s = job.directory.toString();
            createDirectoryTF.setText(s);
            createDirectoryTF.positionCaret(s.length());
            verifyDirectoryTF.setText(s);
            verifyDirectoryTF.positionCaret(s.length());
        }
        if (job.manifest != null) {
            s = job.manifest.toString();
            createManifestTF.setText(s);
            createManifestTF.positionCaret(s.length());
            verifyManifestTF.setText(s);
            verifyManifestTF.positionCaret(s.length());
        }
        if (job.hashAlg != null) {
            hashAlgorithmCB.getSelectionModel().select(job.hashAlg);
        }
        verboseCB.setSelected(job.verbose);
        verboseCB.setSelected(job.debug);
    }

    /**
     * Callback to open online help
     *
     * @param event
     * @throws Exception
     */
    @FXML
    private void handleOnlineHelpAction(ActionEvent event) {
        String hashTag = "aj";
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("FXMLHelp.fxml"));
        Parent root;

        try {
            root = (Parent) fxmlLoader.load();
        } catch (IOException ioe) {
            System.err.println("Couldn't get the root window when opening help window: " + ioe.getMessage());
            return;
        }

        FXMLHelpController controller = fxmlLoader.<FXMLHelpController>getController();
        controller.setHashTag(hashTag);
        controller.loadContent();

        Stage stage = new Stage();
        stage.setTitle(AppConfig.getWindowTitle() + " - " + TITLE_HELP);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /**
     * User has changed the creator text
     */
    @FXML
    private void actorChange(ActionEvent event) {
        TextField tf;

        tf = (TextField) event.getSource();
        actorChange(tf.getId());
    }

    private void actorChange(String id) {
        switch (id) {
            case ("createActorTF"):
                job.actor = createActorTF.getText();
                break;
            case ("verifyActorTF"):
                job.actor = verifyActorTF.getText();
                break;
            default:
                break;
        }
        if (job.actor != null) {
            job.actor = job.actor.trim();
            if (job.actor.equals("")) {
                job.actor = null;
            }
        }
        createActorTF.setText(job.actor);
        verifyActorTF.setText(job.actor);
        updateCreateButtonState();
    }

    /**
     * User has changed the identifier text
     */
    @FXML
    private void identiferChange(ActionEvent event) {
        TextField tf;

        tf = (TextField) event.getSource();
        identifierChange(tf.getId());
    }

    private void identifierChange(String id) {
        switch (id) {
            case ("createIdentifierTF"):
                job.identifier = createIdentifierTF.getText();
                break;
            case ("verifyIdentifierTF"):
                job.identifier = verifyIdentifierTF.getText();
                break;
            default:
                break;
        }
        if (job.identifier != null) {
            job.identifier = job.identifier.trim();
            if (job.identifier.equals("")) {
                job.identifier = null;
            }
        }
        createIdentifierTF.setText(job.identifier);
        verifyIdentifierTF.setText(job.identifier);
        updateCreateButtonState();
    }

    /**
     * User has changed the comment text
     */
    @FXML
    private void commentChange(InputMethodEvent event) {
        TextArea ta;

        ta = (TextArea) event.getSource();
        commentChange(ta.getId());
    }

    private void commentChange(String id) {
        switch (id) {
            case ("createCommentTA"):
                job.comment = createCommentTA.getText();
                break;
            case ("verifyCommentTA"):
                job.comment = verifyCommentTA.getText();
                break;
            default:
                break;
        }
        if (job.comment != null) {
            job.comment = job.comment.trim();
            if (job.comment.equals("")) {
                job.comment = null;
            }
        }
        createCommentTA.setText(job.comment);
        verifyCommentTA.setText(job.comment);
        updateCreateButtonState();
    }

    /*
     * User has changed the manifest file
     */
    @FXML
    private void manifestChange(ActionEvent event) {
        TextField tf;

        tf = (TextField) event.getSource();
        manifestChange(tf.getId());
    }

    private void manifestChange(String id) {
        Path fn;
        String n;
        int i;
        Alert a;
        String s;

        // get path for manifest
        switch (id) {
            case ("createManifestTF"):
                s = createManifestTF.getText();
                verifyManifestTF.setText(s);
                break;
            case ("verifyManifestTF"):
                s = verifyManifestTF.getText();
                createManifestTF.setText(s);
                break;
            default:
                return;
        }

        // an empty text field?
        if (s == null || s.trim().equals("")) {
            job.manifest = null;
            return;
        }

        // turn the text field into a Path
        job.manifest = Paths.get(s);

        // sanity check
        if (job.manifest == null || (fn = job.manifest.getFileName()) == null) {
            a = new Alert(Alert.AlertType.ERROR, "Internal error (should not happen). The manifest file name was converted into a null path");
            a.showAndWait();
            return;
        }

        // check that the file name represents an XML file
        n = fn.toString();
        if ((i = n.lastIndexOf(".")) == -1) {
            a = new Alert(Alert.AlertType.ERROR, "The manifest must be an XML file (i.e. have an '.xml' extension)");
            a.showAndWait();
            return;
        }
        if (i == 0) {
            a = new Alert(Alert.AlertType.ERROR, "The manifest must have a file name (i.e. have something before the '.xml' extension)");
            a.showAndWait();
            return;
        }
        if (!n.substring(i).toLowerCase().equals(".xml")) {
            a = new Alert(Alert.AlertType.ERROR, "The manifest must be an XML file (i.e. have an '.xml' extension)");
            a.showAndWait();
            return;
        }
        updateCreateButtonState();
    }

    /*
     * User has opted to browse for a new manifest
     */
    @FXML
    private void manifestBrowse(ActionEvent event) {
        File f;
        String s;

        f = null;
        switch (((Button) event.getSource()).getId()) {
            case ("createManifestBrowseB"):
                f = browseForSaveFile("Select manifest", ".xml", null);
                break;
            case ("verifyManifestBrowseB"):
                f = browseForOpenFile("Select manifest", ".xml", null);
                break;
            default:
                break;
        }
        if (f == null) {
            return;
        }
        job.manifest = f.toPath();
        s = job.manifest.toString();
        createManifestTF.setText(s);
        createManifestTF.positionCaret(s.length());
        verifyManifestTF.setText(s);
        verifyManifestTF.positionCaret(s.length());
        updateCreateButtonState();
    }

    /*
     * User has directly entered a new file path in the directory text field
     */
    @FXML
    private void directoryChange(ActionEvent event) {
        TextField tf;

        System.out.println("Change");
        tf = (TextField) event.getSource();
        directoryChange(tf.getId());
    }

    private void directoryChange(String id) {
        String s;
        Alert a;

        s = "";
        switch (id) {
            case ("createDirectoryTF"):
                s = createDirectoryTF.getText();
                verifyDirectoryTF.setText(s);
                break;
            case ("verifyDirectoryTF"):
                s = verifyDirectoryTF.getText();
                createDirectoryTF.setText(s);
                break;
            default:
                break;
        }

        // an empty text field?
        if (s == null || s.trim().equals("")) {
            job.manifest = null;
            return;
        }

        // turn the text field into a Path
        job.directory = Paths.get(s);
        
        // sanity check
        if (job.directory == null) {
            a = new Alert(Alert.AlertType.ERROR, "Internal error (should not happen). The directory name was converted into a null path");
            a.showAndWait();
            return;
        }


        // check that the directory exists
        if (!Files.isDirectory(job.directory)) {
            a = new Alert(Alert.AlertType.ERROR, "The source directory must exist and be a directory");
            a.showAndWait();
            return;
        }
        updateCreateButtonState();
    }

    /**
     * User has pressed the 'Browse' button to select the source directory to
     * include in the manifest
     */
    @FXML
    private void dirsBrowse(ActionEvent event) {
        File f;
        String s;
        Button b;

        f = browseForDirectory("Select root directory", null);
        if (f == null) {
            return;
        }
        job.directory = f.toPath();
        s = job.directory.toString();
        createDirectoryTF.setText(s);
        createDirectoryTF.positionCaret(s.length());
        verifyDirectoryTF.setText(s);
        verifyDirectoryTF.positionCaret(s.length());
        updateCreateButtonState();
    }

    /**
     * Respond to focus lost events. A new instance of this class is created for
     * each object, with its name passed as a parameter. When focus is lost in
     * the object, the associated listener instance is called. If newValue is
     * false, this is a lost focus event.
     */
    private class FocusLostListener implements ChangeListener<Boolean> {

        String type;

        FocusLostListener(String type) {
            this.type = type;
        }

        @Override
        public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
            if (newValue == false) {
                switch (type) {
                    case "createActorTF":
                        actorChange("createActorTF");
                        break;
                    case "verifyActorTF":
                        actorChange("verifyActorTF");
                        break;
                    case "updateActorTF":
                        actorChange("updateActorTF");
                        break;
                    case "createIdentifierTF":
                        identifierChange("createIdentifierTF");
                        break;
                    case "verifyIdentifierTF":
                        identifierChange("verifyIdentifierTF");
                        break;
                    case "updateIdentifierTF":
                        identifierChange("updateIdentifierTF");
                        break;
                    case "createCommentTA":
                        commentChange("createCommentTA");
                        break;
                    case "verifyCommentTA":
                        commentChange("verifyCommentTA");
                        break;
                    case "updateCommentTA":
                        commentChange("updateCommentTA");
                        break;
                    case "createManifestTF":
                        manifestChange("createManifestTF");
                        break;
                    case "verifyManifestTF":
                        manifestChange("verifyManifestTF");
                        break;
                    case "updateManifestTF":
                        manifestChange("updateManifestTF");
                        break;
                    case "createDirectoryTF":
                        directoryChange("createDirectoryTF");
                        break;
                    case "verifyDirectoryTF":
                        directoryChange("verifyDirectoryTF");
                        break;
                    case "updateDirectoryTF":
                        directoryChange("updateDirectoryTF");
                        break;

                    default:
                        break;
                }
            }
            updateCreateButtonState();
        }
    }

    /**
     * Check to see if all the information necessary to create VEOs has been
     * entered. If so, make the createVEOs button active
     */
    private void updateCreateButtonState() {
        goB.setDisable(!job.validate());
    }

    /**
     * User has pressed the 'GO' button at the bottom of the window. This button
     * is only active if sufficient information has been entered.
     *
     * @param event
     */
    @FXML
    private void go(ActionEvent event) {
        URL url;            // reference for FXMLProgress stage template
        FXMLLoader loader;
        Parent root;
        FXMLProgressController controller;

        // fire up window
        url = getClass().getResource("FXMLProgress.fxml");
        loader = new FXMLLoader();
        loader.setLocation(url);
        try {
            root = loader.<Parent>load();
        } catch (IOException ioe) {
            System.out.println("Failed getting the root: " + ioe.toString());
            return;
        }
        controller = loader.getController();
        controller.setHostServices(hostServices);
        controller.generate(job, baseDirectory);

        progressStage = new Stage();
        progressStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                // System.exit(0);
            }
        });
        if (selectTP.getSelectionModel().getSelectedItem() == createT) {
            progressStage.setTitle("Creating manifest progress");
            job.task = Job.Task.CREATE;
        } else if (selectTP.getSelectionModel().getSelectedItem() == verifyT) {
            progressStage.setTitle("Verifying manifest progress");
            job.task = Job.Task.VERIFY;
        } else {
            progressStage.setTitle("Progress");
        }
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("styles.css").toExternalForm());
        progressStage.setScene(scene);
        progressStage.setOnHidden(e -> controller.shutdown());
        progressStage.showAndWait();
        progressStage = null;
    }

    /**
     * User has selected the Edit/Cut menu item. This cuts the selected text
     * from the focused TextField into a Clipboard
     *
     * @param event
     */
    @FXML
    private void handleCutAction(ActionEvent event) {
        handleCutAction();
    }

    private void handleCutAction() {
        Node n;
        String s;
        int i;
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();

        n = mainAP.getScene().getFocusOwner();
        if (n instanceof TextField) {
            TextField tf = (TextField) n;
            tf.cut();
        }
    }

    /**
     * User has selected the Edit/Copy menu item. This cuts the selected text
     * from the focused TextField into a Clipboard
     *
     * @param event
     */
    @FXML
    private void handleCopyAction(ActionEvent event) {
        handleCopyAction();
    }

    private void handleCopyAction() {
        Node n;
        String s;
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        int i;

        n = mainAP.getScene().getFocusOwner();
        if (n instanceof TextField) {
            TextField tf = (TextField) n;
            tf.copy();
        }
    }

    /**
     * User has selected the Edit/Paste action.
     *
     * @param event
     */
    @FXML
    private void handlePasteAction(ActionEvent event) {
        handlePasteAction();
    }

    private void handlePasteAction() {
        Node n;
        String s;
        int i;
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();

        n = mainAP.getScene().getFocusOwner();
        if (n instanceof TextField) {
            TextField tf = (TextField) n;
            tf.paste();
        }
    }

    /**
     * Pop up a window that explains what this program is
     *
     * @param event
     * @throws Exception
     */
    @FXML
    private void handleAboutAction(ActionEvent event) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("FXMLAbout.fxml"));
        Parent root = (Parent) fxmlLoader.load();
        Stage stage = new Stage();
        stage.setTitle(AppConfig.getWindowTitle() + " - " + FXMLSetupRunController.TITLE_ABOUT);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }
}
