/**
 * ***********************************************************
 *
 * M A N I F E S T
 *
 * This class a manifest of files in a directory (recursively)
 *
 * <li><b>[files|directories]+</b> a list of EML files, or directories
 * containing such files.</li>
 * </ul>
 * <p>
 * The following command line arguments are optional:
 * <li><b>-v</b> verbose output. By default off.</li>
 * <li><b>-d</b> debug mode. In this mode more logging will be generated. By
 * default off.</li>
 * <li><b>-o &lt;outputDir&gt;</b> the directory in which the VEOs are to be
 * created. If not present, the VEOs will be created in the current
 * directory.</li>
 * </ul>
 * <p>
 * A minimal example of usage is<br>
 * <pre>
 *     eml2vers veo1.veo
 * </pre>
 *
 * Copyright Public Record Office Victoria 2018 Licensed under the CC-BY license
 * http://creativecommons.org/licenses/by/3.0/au/ Author Andrew Waugh Version
 * 1.0 February 2018
 */
package Manifest;

import VERSCommon.AppFatal;
import VERSCommon.AppError;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;

public class Manifest {

    static String classname = "Manifest"; // for reporting
    ArrayList<String> files;// files or directories to hash
    Runtime r;

    Path manifestFile;      // file containing manifest
    String hashAlg;         // hash algorithm to use
    int fileCount;          // number of files processed
    boolean debug;          // true if in debug mode
    boolean verbose;        // true if in verbose output mode
    boolean createManifest; // true if we are creating the manifest file
    String userId;          // user performing the conversion
    FXMLProgressController.DoManifestTask reporter; // call back for reporting

    private final static Logger LOG = Logger.getLogger("Manifest.Manifest");

    /**
     * Default constructor
     *
     * @param args arguments passed to program
     * @throws AppFatal if a fatal error occurred
     */
    public Manifest(String args[]) throws AppFatal {

        // Set up logging
        System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %5$s%n");
        LOG.setLevel(Level.INFO);

        // set up default global variables
        files = new ArrayList<>();

        manifestFile = Paths.get(".");
        hashAlg = "SHA-1";
        fileCount = 0;
        debug = false;
        verbose = false;
        createManifest = true;
        userId = System.getProperty("user.name");
        if (userId == null) {
            userId = "Unknown user";
        }
        reporter = null;
        r = Runtime.getRuntime();

        // process command line arguments
        configure(args);
    }

    /**
     * Constructor used when called by a program (typically a GUI).
     * The parameters of the execution are passed in as a 'job'; the logging is
     * written out to 'hdlr', and after every step the callback 'reporter' is
     * called so that the governing program can update any status.
     * 
     * @param job parameters for the execution (replaces the command line)
     * @param hdlr place where log messages go
     * @param reporter callback to update status in the calling program
     * @throws AppFatal 
     */
    public Manifest(Job job, Handler hdlr, FXMLProgressController.DoManifestTask reporter) throws AppFatal {
        Handler h[];
        int i;

        // remove any handlers associated with the LOG & log messages aren't to
        // go to the parent
        h = LOG.getHandlers();
        for (i = 0; i < h.length; i++) {
            LOG.removeHandler(h[i]);
        }
        LOG.setUseParentHandlers(false);

        // add log handler from calling program
        LOG.addHandler(hdlr);
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s");
        LOG.setLevel(Level.SEVERE);

        // set up global variables from job
        files = new ArrayList();
        for (i = 0; i < job.directories.size(); i++) {
            files.add(job.directories.get(i));
        }
        manifestFile = job.manifest;
        hashAlg = job.hashAlg;
        fileCount = 0;
        debug = job.debug;
        verbose = job.verbose;
        createManifest = true; // ignored when calling from a program

        // set up callback
        this.reporter = reporter;
    }

    /**
     * Finalise...
     */
    public void close() {
        // vp.free();
    }

    /**
     * Configure
     *
     * This method gets the options for this run of the manifest generator a the
     * command line. See the comment at the start of this file for the command
     * line arguments.
     *
     * @param args[] the command line arguments
     * @param VEOFatal if a fatal error occurred
     */
    private void configure(String args[]) throws AppFatal {
        int i;
        String usage = "Manifest [-v] [-d] [-o <file>] [-g] [-i <file>] [-h <hashAlg>] (files|directories)*";

        // hashFile command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {

                    // verbose?
                    case "-v":
                        verbose = true;
                        LOG.setLevel(Level.INFO);
                        // rootLog.setLevel(Level.INFO);
                        i++;
                        break;

                    // debug?
                    case "-d":
                        debug = true;
                        LOG.setLevel(Level.FINE);
                        // rootLog.setLevel(Level.FINE);
                        i++;
                        break;

                    // '-o' specifies input manifest file
                    case "-i":
                        i++;
                        manifestFile = Paths.get(args[i]);
                        createManifest = false;
                        i++;
                        break;

                    // get hash algorithm
                    case "-h":
                        i++;
                        hashAlg = args[i];
                        i++;
                        break;

                    // '-i' specifies output manifest file
                    case "-o":
                        i++;
                        manifestFile = Paths.get(args[i]);
                        createManifest = true;
                        i++;
                        break;

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + usage);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        files.add(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + usage);
        }

        // check to see if at least one file or directory is specified
        if (files.isEmpty()) {
            throw new AppFatal("You must specify at least one file or directory to process");
        }
        if (manifestFile == null) {
            throw new AppFatal("You must specify a manifest file as an input (checking) or output (creating)");
        }

        // LOG generic things
        if (debug) {
            LOG.log(Level.INFO, "Verbose/Debug mode is selected");
        } else if (verbose) {
            LOG.log(Level.INFO, "Verbose output is selected");
        }
        LOG.log(Level.INFO, "Hash algorithm is ''{0}''", hashAlg);
        LOG.log(Level.INFO, "User id to be logged: ''{0}''", new Object[]{userId});
        LOG.log(Level.INFO, "Creating a manifest? {0}", createManifest);
    }

    /**
     * Check a file to see that it exists and is of the correct type (regular
     * file or directory). The program terminates if an error is encountered.
     *
     * @param type a String describing the file to be opened
     * @param name the file name to be opened
     * @param isDirectory true if the file is supposed to be a directory
     * @param create if true, create the directory if it doesn't exist
     * @throws AppFatal if the file does not exist, or is of the correct type
     * @return the File opened
     */
    private Path checkFile(String type, String name, boolean isDirectory, boolean create) throws AppFatal {
        Path p;

        p = Paths.get(name);

        if (!Files.exists(p)) {
            if (!create) {
                throw new AppFatal(classname, 6, type + " '" + p.toAbsolutePath().toString() + "' does not exist");
            } else {
                try {
                    Files.createDirectory(p);
                } catch (IOException ioe) {
                    throw new AppFatal(classname, 9, type + " '" + p.toAbsolutePath().toString() + "' does not exist and could not be created: " + ioe.getMessage());
                }
            }
        }
        if (isDirectory && !Files.isDirectory(p)) {
            throw new AppFatal(classname, 7, type + " '" + p.toAbsolutePath().toString() + "' is a file not a directory");
        }
        if (!isDirectory && Files.isDirectory(p)) {
            throw new AppFatal(classname, 8, type + " '" + p.toAbsolutePath().toString() + "' is a directory not a file");
        }
        return p;
    }

    /**
     * Create a manifest
     */
    public void createManifest() throws AppFatal {
        int i;
        String file;
        FileWriter fw;
        BufferedWriter bw;

        // open manifest
        try {
            fw = new FileWriter(manifestFile.toFile());
        } catch (IOException ioe) {
            System.out.println(manifestFile.toAbsolutePath().toString());
            throw new AppFatal(classname, 1, "Cannot open manifest file '" + manifestFile.toString() + "' for writing:" + ioe.getMessage());
        }
        bw = new BufferedWriter(fw);

        // go through the list of files
        for (i = 0; i < files.size(); i++) {
            file = files.get(i);
            if (file == null) {
                continue;
            }
            try {
                hashFile(Paths.get(file), bw);
            } catch (InvalidPathException ipe) {
                LOG.log(Level.WARNING, "***Ignoring file ''{0}'' as the file name was invalid: {1}", new Object[]{file, ipe.getMessage()});
            }
        }

        try {
            bw.close();
            fw.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    public boolean process(Path file) {
        StringWriter sw;

        System.out.println("Starting" + file.toString());
        sw = new StringWriter();
        try {
            hashFile(file, sw);
        } catch (InvalidPathException ipe) {
            LOG.log(Level.WARNING, "***Ignoring file ''{0}'' as the file name was invalid: {1}", new Object[]{file, ipe.getMessage()});
        } catch (AppFatal af) {
            LOG.log(Level.WARNING, "***Ignoring file ''{0}'' due to a fatal error: {1}", new Object[]{file, af.getMessage()});
        }
        try {
            sw.close();
        } catch (IOException ioe) {
            // ignore
        }
        return true;
    }

    /**
     * Process an individual directory or file. If a directory, recursively
     * process all of the files (or directories) in it.
     *
     * @param f the file or directory to hashFile
     * @param first this is the first entry in the directory
     */
    private void hashFile(Path f, Writer w) throws AppFatal {
        DirectoryStream<Path> ds;
        String hash;

        // check that file or directory exists
        if (!Files.exists(f)) {
            if (verbose) {
                LOG.log(Level.WARNING, "***File ''{0}'' does not exist", new Object[]{f.normalize().toString()});
            }
            return;
        }

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(f)) {
            if (verbose) {
                LOG.log(Level.INFO, "***Processing directory ''{0}''", new Object[]{f.normalize().toString()});
            }
            try {
                ds = Files.newDirectoryStream(f);
                for (Path p : ds) {
                    hashFile(p, w);
                }
                ds.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{f.normalize().toString(), e.getMessage()});
            }
            return;
        }

        if (Files.isRegularFile(f)) {
            try {
                hash = Manifest.this.hashFile(f);
                try {
                    w.write(f.normalize().toString());
                    w.write("\t");
                    w.write(hash);
                    w.write("\n");
                } catch (IOException ioe) {
                    throw new AppFatal("Failed writing manifest: " + ioe.getMessage());
                }
                LOG.log(Level.INFO, "Hashed ''{0}'': ''{1}''", new Object[]{f.normalize().toString(), hash});
            } catch (AppError ae) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{f.normalize().toString(), ae.getMessage()});
            }
        } else {
            LOG.log(Level.INFO, "***Ignoring directory ''{0}''", new Object[]{f.normalize().toString()});
        }
        System.out.println("Did file: " + f.toString());
        if (reporter != null) {
            reporter.updateStatus(f.toString(), null);
        }
    }

    /**
     * Check a manifest
     */
    public void checkManifest() throws AppFatal, AppError {
        int i;
        FileReader fr;
        BufferedReader br;
        String line;
        String[] tokens;
        Path base, file;
        String hash;

        // open manifest
        try {
            fr = new FileReader(manifestFile.toFile());
        } catch (IOException ioe) {
            System.out.println(manifestFile.toAbsolutePath().toString());
            throw new AppFatal(classname, 1, "Cannot open manifest file '" + manifestFile.toString() + "' for reading:" + ioe.getMessage());
        }
        br = new BufferedReader(fr);

        // get base directory
        if (files.size() < 1) {
            throw new AppFatal(classname, 2, "No base directory specified (" + files.size() + ")");
        }
        base = Paths.get(files.get(0));

        // go through manifest
        try {
            i = 0;
            while ((line = br.readLine()) != null) {
                i++;
                tokens = line.split("\t");
                if (tokens.length != 2) {
                    LOG.log(Level.SEVERE, "Line {0} ''{1}'' had {2} tokens", new Object[]{i, line, tokens.length});
                } else {
                    file = base.getParent().resolve(tokens[0]);
                    if (Files.exists(file)) {
                        hash = Manifest.this.hashFile(file);
                        if (!hash.equals(tokens[1])) {
                            LOG.log(Level.SEVERE, "File ''{0}'' corrupt: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{file.toString(), tokens[1], hash});
                        }
                        LOG.log(Level.INFO, "File ''{0}'' passed: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{file.toString(), tokens[1], hash});
                    } else {
                        LOG.log(Level.SEVERE, "File ''{0}'' is in manifest, but is not present", new Object[]{file.toString()});
                    }
                    if (reporter != null) {
                        reporter.updateStatus(file.toString(), null);
                    }
                }
            }
        } catch (IOException ioe) {
            throw new AppFatal(classname, 2, "Failed reading manifest file '" + manifestFile.toString() + "':" + ioe.getMessage());
        }

        try {
            br.close();
            fr.close();
        } catch (IOException ioe) {
            // ignore
        }
    }

    /**
     * Hash a file
     *
     * @param fileToHash the fileToHash of the file at the moment
     * @return the hash value encoded as a Base64 String
     * @throws AppError if an error occurred hashing the file
     */
    public String hashFile(Path fileToHash) throws AppError {
        String method = "hashFile";
        MessageDigest md;
        FileInputStream fis;    // input streams to read file to sign
        BufferedInputStream bis;//
        byte[] hash;            // generated hash
        int i;
        byte[] b = new byte[1000]; // buffer used to read input file

        // sanity checks...
        if (fileToHash == null) {
            throw new AppError(classname, method, 1, "fileToHash is null");
        }

        if (Files.notExists(fileToHash)) {
            throw new AppError(classname, method, 1, "File to hash '" + fileToHash.toString() + "' does not exist");
        }

        // get message digest
        try {
            md = MessageDigest.getInstance(hashAlg);
        } catch (NoSuchAlgorithmException e) {
            throw new AppError(classname, method, 1, "Hash algorithm '" + hashAlg + "' not supported");
        }
        // open the file to digest
        try {
            fis = new FileInputStream(fileToHash.toString());
        } catch (FileNotFoundException e) {
            throw new AppError(classname, method, 1, "File to hash ('" + fileToHash.toString() + "') was not found");
        }
        bis = new BufferedInputStream(fis);

        // enter the bytes from the file
        try {
            while ((i = bis.read(b)) != -1) {
                md.update(b, 0, i);
            }
        } catch (IOException e) {
            throw new AppError(classname, method, 1, "failed reading file to hash: " + e.getMessage());
        }

        // close the input file
        try {
            bis.close();
        } catch (IOException e) {
            throw new AppError(classname, method, 1, "failed closing file to hash: " + e.getMessage());
        }

        // calculate the digital signature over the input file
        // calculate signature and convert it into a byte buffer
        hash = md.digest();
        return (DatatypeConverter.printBase64Binary(hash));
    }

    /**
     * Main program
     *
     * @param args command line arguments
     */
    public static void main(String args[]) {
        Manifest m;

        try {
            m = new Manifest(args);
            if (m.createManifest) {
                m.createManifest();
            } else {
                m.checkManifest();
            }
            m.close();
            // tp.stressTest(1000);
        } catch (AppFatal | AppError e) {
            System.out.println("Fatal error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
