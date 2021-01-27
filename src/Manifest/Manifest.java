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
import VERSCommon.HandleElement;
import VERSCommon.VERSDate;
import VERSCommon.XMLConsumer;
import VERSCommon.XMLCreator;
import VERSCommon.XMLParser;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.DatatypeConverter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class Manifest implements XMLConsumer {

    static String classname = "Manifest"; // for reporting
    Job job;                // job description
    Runtime r;

    int fileCount;          // number of files processed
    String userId;          // user performing the conversion
    FXMLProgressController.DoManifestTask reporter; // call back for reporting
    XMLParser xmlp;         // parser for XML manifest

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
        job = new Job();
        fileCount = 0;
        userId = System.getProperty("user.name");
        if (userId == null) {
            userId = "Unknown user";
        }
        // xmlp = new XMLParser(this);
        reporter = null;
        r = Runtime.getRuntime();

        // process command line arguments
        configure(args);
    }

    /**
     * Constructor used when called by a program (typically a GUI). The
     * parameters of the execution are passed in as a 'job'; the logging is
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
        this.job = job;
        fileCount = 0;

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
        String usage = "Manifest [-v] [-d] [-o <file>] [-g] [-i <file>] [-h <hashAlg>] directory";

        // hashFiles command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {

                    // verbose?
                    case "-v":
                        job.verbose = true;
                        LOG.setLevel(Level.INFO);
                        // rootLog.setLevel(Level.INFO);
                        i++;
                        break;

                    // debug?
                    case "-d":
                        job.debug = true;
                        LOG.setLevel(Level.FINE);
                        // rootLog.setLevel(Level.FINE);
                        i++;
                        break;

                    // '-i' specifies input manifest file
                    case "-i":
                        i++;
                        job.manifest = Paths.get(args[i]);
                        job.task = Job.Task.VERIFY;
                        i++;
                        break;

                    // get hash algorithm
                    case "-h":
                        i++;
                        job.hashAlg = args[i];
                        i++;
                        break;

                    // '-i' specifies output manifest file
                    case "-o":
                        i++;
                        job.manifest = Paths.get(args[i]);
                        job.task = Job.Task.CREATE;
                        i++;
                        break;

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + usage);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        if (job.directory != null) {
                            throw new AppFatal("Only one directory can be present '" + args[i] + "' Usage: " + usage);
                        }
                        job.directory = Paths.get(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + usage);
        }

        // check to see if at least one file or directory is specified
        if (job.directory == null) {
            throw new AppFatal("You must a directory to process");
        }
        if (job.manifest == null) {
            throw new AppFatal("You must specify a manifest file as an input (checking) or output (creating)");
        }

        // LOG generic things
        if (job.debug) {
            LOG.log(Level.INFO, "Verbose/Debug mode is selected");
        } else if (job.verbose) {
            LOG.log(Level.INFO, "Verbose output is selected");
        }
        LOG.log(Level.INFO, "Hash algorithm is ''{0}''", job.hashAlg);
        LOG.log(Level.INFO, "User id to be logged: ''{0}''", new Object[]{userId});
        LOG.log(Level.INFO, "Creating a manifest? {0}", job.task);
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
     * Create a manifest - an XML file.
     *
     * @throws VERSCommon.AppFatal
     */
    public void createManifest() throws AppFatal, AppError {
        XMLCreator xmlc;

        // create manifest
        xmlc = new XMLCreator(true);
        xmlc.startXMLDoc(job.manifest, "Manifest", null);
        xmlc.includeElement("creator", null, job.actor, false);
        xmlc.includeElement("hashAlgorithm", null, job.hashAlg, false);
        xmlc.includeElement("dateTimeCreated", null, VERSDate.versDateTime(0), false);
        xmlc.includeElement("directory", null, job.directory.toString(), false);
        xmlc.startElement("files", null, false);
        hashTheFiles(job.directory, xmlc);
        xmlc.endElement("files", false);
        xmlc.endXMLDoc();
    }

    /**
     * Process an individual directory or file. If a directory, recursively
     * process all of the files (or directories) in it.
     *
     * @param p the file or directory to hashFiles
     * @param first this is the first entry in the directory
     */
    private void hashTheFiles(Path p, XMLCreator xmlc) throws AppFatal {
        DirectoryStream<Path> ds;
        String hash;

        // check that file or directory exists
        if (!Files.exists(p)) {
            if (job.verbose) {
                LOG.log(Level.WARNING, "***File ''{0}'' does not exist", new Object[]{p.normalize().toString()});
            }
            return;
        }

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(p)) {
            if (job.verbose) {
                LOG.log(Level.INFO, "***Processing directory ''{0}''", new Object[]{p.normalize().toString()});
            }
            try {
                ds = Files.newDirectoryStream(p);
                for (Path p1 : ds) {
                    hashTheFiles(p1, xmlc);
                }
                ds.close();
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{p.normalize().toString(), e.getMessage()});
            }
            return;
        }

        if (Files.isRegularFile(p)) {
            try {
                hash = hashFile(p);
                xmlc.startElement("f", null, false);
                xmlc.includeElement("p", null, job.directory.relativize(p).toString(), true);
                xmlc.includeElement("h", null, hash, true);
                xmlc.endElement("f", true);
                LOG.log(Level.INFO, "Hashed ''{0}'': ''{1}''", new Object[]{p.normalize().toString(), hash});
            } catch (AppError ae) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{p.normalize().toString(), ae.getMessage()});
            }
        } else {
            LOG.log(Level.INFO, "***Ignoring directory ''{0}''", new Object[]{p.normalize().toString()});
        }
        // System.out.println("Did file: " + p.toString());
        if (reporter != null) {
            if (p.getNameCount() > 3) {
                p = p.subpath(p.getNameCount() - 3, p.getNameCount());
            }
            reporter.updateStatus(".../"+p.toString(), null);
        }
    }

    /**
     * Check a manifest
     */
    public void checkManifest() throws AppFatal, AppError {
        // check parameters
        if (job.manifest == null) {
            throw new AppError("Passed null manifest to be processed");
        }

        // parse it
        oldDetails = new Job();
        try {
            xmlp = new XMLParser(this);
            xmlp.parse(job.manifest);
            xmlp = null;
        } catch (AppFatal | AppError ae) {
            System.out.println("Error! " + ae.toString());
            throw ae;
        }
    }

    /**
     * SAX Events captured
     */
    /**
     * Start of element
     *
     * This event is called when the parser finds a new element.
     *
     * @param eFound path of found element
     * @param attributes any attributes associated with found element
     * @throws SAXException
     */
    Job oldDetails;         // the old details read from the XML file
    Path file;              // the current file being processed
    String hashValue;       // the hash value of the current file

    @Override
    public HandleElement startElement(String eFound, Attributes attributes) throws SAXException {
        HandleElement he;

        // match the path to see if do we do something special?
        he = null;
        switch (eFound) {
            case "Manifest/creator":         // creator of manifest
            case "Manifest/hashAlgorithm":   // hash algorithm used
            case "Manifest/dateTimeCreated": // date created
            case "Manifest/directory":       // root of source directory
            case "Manifest/files/f/p": // path name of file in manifest
            case "Manifest/files/f/h": // hash algorithm
                he = new HandleElement(HandleElement.VALUE_TO_STRING, false, null);
                break;
            case "Manifest/files/f":      // detail of each file
                file = null;
                hashValue = null;
                break;
            case "Manifest":                 // manifest
            case "Manifest/files":           // list of files in manifest
                break;
            default:
                break;
        }
        return he;
    }

    /**
     * End of an element
     *
     * Found the end of an element.
     *
     * @param eFound
     * @param value
     * @throws org.xml.sax.SAXException
     */
    @Override
    public void endElement(String eFound, String value, String element)
            throws SAXException {

        // if recording store element value in appropriate global variable
        switch (eFound) {
            case "Manifest/creator":    // Creator
                oldDetails.actor = value;
                break;
            case "Manifest/hashAlgorithm":    // hash algorithm used
                oldDetails.hashAlg = value;
                break;
            case "Manifest/dateTimeCreated":    // date time created
                oldDetails.dateTimeCreated = value;
                break;
            case "Manifest/directory":    // source directory
                oldDetails.directory = value != null?Paths.get(value):null;
                break;
            case "Manifest/files/f/p":    // path within source directory
                file = value!=null?Paths.get(value):null;
                break;
            case "Manifest/files/f/h":    // hash value of file
                hashValue = value;
                break;
            case "Manifest/files/f":
                if (file == null || hashValue == null) {
                    throw new SAXException("File without both file (" + file + ") and hash (" + hashValue);
                }
                try {
                    processFile(file, hashValue);
                } catch (AppError ae) {
                    throw new SAXException(ae.getMessage());
                }
                break;
            default:
                break;
        }
    }

    private boolean processFile(Path partialFile, String hash) throws AppError {
        Path actualFile, p;
        String recalcHash;
        int i;

        
        actualFile = job.directory.resolve(partialFile);
        // System.out.println("Processing... '"+job.directory.toString()+"' '" + partialFile.toString() + "' '"+actualFile.toString()+"' hash " + hash);
        if (Files.exists(actualFile)) {
            recalcHash = hashFile(actualFile);
            if (!recalcHash.equals(hash)) {
                LOG.log(Level.SEVERE, "File ''{0}'' corrupt: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{actualFile.toString(), hash, recalcHash});
            }
            LOG.log(Level.INFO, "File ''{0}'' passed: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{actualFile.toString(), hash, recalcHash});
        } else {
            LOG.log(Level.SEVERE, "File ''{0}'' is in manifest, but is not present", new Object[]{actualFile.toString()});
        }
        if (reporter != null) {
            if (actualFile.getNameCount() > 3) {
                p = actualFile.subpath(actualFile.getNameCount() - 3, actualFile.getNameCount());
            } else {
                p = actualFile;
            }
            reporter.updateStatus(".../"+p.toString(), null);
        }
        return true;
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
            md = MessageDigest.getInstance(job.hashAlg);
        } catch (NoSuchAlgorithmException e) {
            throw new AppError(classname, method, 1, "Hash algorithm '" + job.hashAlg + "' not supported");
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
            // if (m.createManifest) {
            // m.createManifest();
            // } else {
            m.checkManifest();
            //}
            m.close();
            // tp.stressTest(1000);
        } catch (AppFatal | AppError e) {
            System.out.println("Fatal error: " + e.getMessage());
            System.exit(-1);
        }
    }
}
