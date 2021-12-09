/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package Manifest;

import VERSCommon.AppError;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * This class encapsulates the VEO generation
 *
 * @author Andrew
 */
public class Job {

    public enum Task {
        NOTSET, // not set yet
        CREATE, // create a new manifest
        VERIFY, // verify an existing manifest
        UPDATE  // update some items in a manifest     
    }
    Task task;          // what we want done
    Path manifest;      // manifest file to manipulate
    String actor;       // who is creating or updating the manifest
    Path directory;     // list of directories to process
    String comment;     // comment about creation or update
    String identifier;  // identifier of this uplift (series/consignment/set)
    String hashAlg;     // hash algorithm to use
    String dateTimeCreated; // date and time the manifest was created
    Path logFile;       // user requested a log file to be produced
    boolean verifyHash; // if false do *NOT* verify the hash, only check that the file exists

    boolean verbose;    // true if verbose output
    boolean debug;      // true if debugging output

    /**
     * Constructor
     */
    public Job() {
        setDefault();
    }

    /**
     * Set up the default job
     */
    final protected void setDefault() {
        verbose = AppConfig.getCreateVerboseOutputDefault();
        debug = AppConfig.getCreateDebugModeDefault();
        task = Task.CREATE;
        manifest = null;
        actor = null;
        directory = null;
        comment = null;
        identifier = null;
        dateTimeCreated = null;
        hashAlg = "SHA-1";
        logFile = null;
        verifyHash = true;
    }

    /**
     * Deconstructor
     */
    public void free() {
        manifest = null;
        actor = null;
        directory = null;
        comment = null;
        identifier = null;
        dateTimeCreated = null;
        logFile = null;
    }

    /**
     * Check to see if sufficient information has been entered to perform the
     * requested task
     *
     * @return
     */
    public boolean validate() {
        if (task == Task.NOTSET) {
            return false;
        }
        if (directory == null) {
            return false;
        }
        if (manifest == null) {
            return false;
        }
        if (task == Task.CREATE && actor == null) {
            return false;
        }
        if (task == Task.CREATE && identifier == null) {
            return false;
        }
        return true;
    }

    /**
     * Create a JSON file capturing the Job
     *
     * @param file
     * @throws AppError
     */
    public void saveJob(Path file) throws AppError {
        JSONObject j1;

        FileWriter fw;
        BufferedWriter bw;

        try {
            fw = new FileWriter(file.toFile());
            bw = new BufferedWriter(fw);
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.toString());
        }

        j1 = new JSONObject();
        switch (task) {
            case NOTSET:
                j1.put("task", "notSet");
                break;
            case CREATE:
                j1.put("task", "create");
                break;
            case VERIFY:
                j1.put("task", "verify");
                break;
            case UPDATE:
                j1.put("task", "update");
                break;
        }
        if (actor != null) {
            j1.put("actor", actor);
        }
        if (identifier != null) {
            j1.put("identifier", identifier);
        }
        if (comment != null) {
            j1.put("comment", comment);
        }
        if (directory != null) {
            j1.put("directory", directory.toString());
        }
        if (manifest != null) {
            j1.put("manifest", manifest.toString());
        }
        if (logFile != null) {
            j1.put("logfile", logFile.toString());
        }
        if (hashAlg != null) {
            j1.put("hashAlgorithm", hashAlg);
        }
        j1.put("verifyHash", verifyHash);
        j1.put("verboseReporting", verbose);
        j1.put("debugReporting", debug);

        try {
            bw.write(prettyPrintJSON(j1.toString()));
        } catch (IOException ioe) {
            throw new AppError("Failed trying to write Job file: " + ioe.toString());
        }
        try {
            bw.close();
            fw.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    private String prettyPrintJSON(String in) {
        StringBuffer sb;
        int i, j, indent;
        char ch;

        sb = new StringBuffer();
        indent = 0;
        for (i = 0; i < in.length(); i++) {
            ch = in.charAt(i);
            switch (ch) {
                case '{':
                    indent++;
                    sb.append("{");
                    break;
                case '}':
                    indent--;
                    sb.append("}");
                    break;
                case '[':
                    indent++;
                    sb.append("[\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                case ']':
                    indent--;
                    sb.append("]");
                    break;
                case ',':
                    sb.append(",\n");
                    for (j = 0; j < indent; j++) {
                        sb.append(" ");
                    }
                    break;
                default:
                    sb.append(ch);
                    break;
            }
        }
        return sb.toString();
    }

    /**
     * Read a JSON file describing the Job to run
     *
     * @param file file containing the job file
     * @throws AppError
     */
    public void loadJob(Path file) throws AppError {
        JSONParser parser = new JSONParser();
        JSONObject j1;
        FileReader fr;
        BufferedReader br;
        String s;
        Boolean b;

        // set up the default job
        setDefault();

        // overwrite it with the saved job
        try {
            fr = new FileReader(file.toFile());
            br = new BufferedReader(fr);
            j1 = (JSONObject) parser.parse(br);
        } catch (ParseException pe) {
            throw new AppError("Failed parsing Job file: " + pe.toString());
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.toString());
        }

        if ((s = (String) j1.get("task")) != null) {
            switch (s) {
                case "create":
                    task = Task.CREATE;
                    break;
                case "verify":
                    task = Task.VERIFY;
                    break;
                case "update":
                    task = Task.UPDATE;
                    break;
                case "notset":
                default:
                    task = Task.NOTSET;
                    break;
            }
        }
        if ((s = (String) j1.get("actor")) != null) {
            actor = s;
        }
        if ((s = (String) j1.get("identifier")) != null) {
            identifier = s;
        }
        if ((s = (String) j1.get("comment")) != null) {
            comment = s;
        }
        if ((b = (Boolean) j1.get("verboseReporting")) != null) {
            verbose = b;
        }
        if ((b = (Boolean) j1.get("debugReporting")) != null) {
            debug = b;
        }
        if ((b = (Boolean) j1.get("verifyHash")) != null) {
            verifyHash = b;
        }
        if ((s = (String) j1.get("directory")) != null) {
            directory = Paths.get(s);
        }
        if ((s = (String) j1.get("manifest")) != null) {
            manifest = Paths.get(s);
        }
        if ((s = (String) j1.get("hashAlgorithm")) != null) {
            hashAlg = s;
        }
        if ((s = (String) j1.get("logfile")) != null) {
            logFile = Paths.get(s);
        }

        try {
            br.close();
            fr.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        if (task == Task.CREATE) {
            sb.append("Create manifest '" + manifest.toString() + "' from directory '" + directory.toString() + "'\n");
            sb.append("Hash algorithm (specified on command line or the default): " + hashAlg + "\n");
        } else if (task == Task.VERIFY) {
            sb.append("Checking manifest '" + manifest.toString() + "' against directory '" + directory.toString() + "' ");
            if (!verifyHash) {
                sb.append("(only verify existance of file, DO NOT verify hash)\n");
            } else {
                sb.append("(verify both existance and hash of file)\n");
            }
        }
        sb.append(" Person creating or checking manifest");
        if (actor != null) {
            sb.append(": '" + actor + "'\n");
        } else {
            sb.append(" is not set\n");
        }
        sb.append(" Comment on creating or checking manifest: ");
        if (comment != null) {
            sb.append(comment + "'\n");
        } else {
            sb.append("not set\n");
        }
        sb.append(" Manifest identifier: ");
        if (identifier != null) {
            sb.append("'" + identifier + "'\n");
        } else {
            sb.append("not set\n");
        }
        if (logFile != null) {
            sb.append(" Log File: '");
            sb.append(logFile.toString());
            sb.append("'\n");
        }
        if (debug) {
            sb.append(" Debug out selected\n");
        }
        if (verbose) {
            sb.append(" Verbose output is selected\n");
        }
        return sb.toString();
    }
}
