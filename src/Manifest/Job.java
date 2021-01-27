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
import org.json.simple.JSONArray;
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

    boolean verbose;    // true if verbose output
    boolean debug;      // true if debugging output

    /**
     * Constructor
     */
    public Job() {
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
        if (actor == null) {
            return false;
        }
        if (manifest == null) {
            return false;
        }
        if (task == Task.CREATE && identifier == null) {
            return false;
        }
        if (task == Task.UPDATE && comment == null) {
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
        JSONObject j1, j2;
        JSONArray ja1;
        int i;

        FileWriter fw;
        BufferedWriter bw;

        try {
            fw = new FileWriter(file.toFile());
            bw = new BufferedWriter(fw);
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.getMessage());
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
        if (hashAlg != null) {
            j1.put("hashAlgorithm", hashAlg);
        }
        j1.put("verboseReporting", verbose);
        j1.put("debugReporting", debug);

        try {
            bw.write(prettyPrintJSON(j1.toString()));
        } catch (IOException ioe) {
            throw new AppError("Failed trying to write Job file: " + ioe.getMessage());
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
        JSONObject j1, j2;
        JSONArray ja1;
        int i;
        FileReader fr;
        BufferedReader br;
        String s;

        try {
            fr = new FileReader(file.toFile());
            br = new BufferedReader(fr);
            j1 = (JSONObject) parser.parse(br);
        } catch (ParseException pe) {
            throw new AppError("Failed parsing Job file: " + pe.toString());
        } catch (IOException ioe) {
            throw new AppError("Failed reading Job file: " + ioe.getMessage());
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
        verbose = ((Boolean) j1.get("verboseReporting"));
        debug = ((Boolean) j1.get("debugReporting"));
        directory = Paths.get((String) j1.get("directory"));
        if ((s = (String) j1.get("manifest")) != null) {
            manifest = Paths.get(s);
        }
        hashAlg = (String) j1.get("hashAlgorithm");

        try {
            br.close();
            fr.close();
        } catch (IOException ioe) {
            /* ignore */ }
    }

    @Override
    public String toString() {
        int i;
        StringBuilder sb = new StringBuilder();

        sb.append("Task: ");
        switch (task) {
            case CREATE:
                sb.append("create\n");
                break;
            case UPDATE:
                sb.append("update\n");
                break;
            case VERIFY:
                sb.append("verify\n");
                break;
            case NOTSET:
                sb.append("not set\n");
                break;
        }
        sb.append("Actor: '");
        if (actor != null) {
            sb.append(actor);
        } else {
            sb.append("not set");
        }
        sb.append("'\n");
        sb.append("Identifier: '");
        if (identifier != null) {
            sb.append(identifier);
        } else {
            sb.append("not set");
        }
        sb.append("'\n");
        sb.append("Manifest: '");
        if (manifest != null) {
            sb.append(manifest);
        } else {
            sb.append("not set");
        }
        sb.append("'\n");
        sb.append("Directory: ");
        if (directory != null) {
            sb.append(directory.toString());
        } else {
            sb.append("None specified");
        }
        sb.append("\n");
        sb.append("Comment: '");
        if (comment != null) {
            sb.append(comment);
        } else {
            sb.append("not set");
        }
        sb.append("'\n");
        if (hashAlg != null) {
            sb.append("Hash Algorithm: '");
            sb.append(hashAlg);
            sb.append("'\n");
        }
        sb.append("Verbose : ");
        sb.append(verbose);
        sb.append("'\n");
        sb.append("Debug: '");
        sb.append(debug);
        sb.append("'\n");
        return sb.toString();
    }
}
