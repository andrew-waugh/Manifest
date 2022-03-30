/**
 * Copyright Public Record Office Victoria 2021
 * Licensed under the CC-BY license http://creativecommons.org/licenses/by/3.0/au/
 * Author Andrew Waugh
 * Version 0.0 February 2021
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
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
 *
 */
public class Manifest implements XMLConsumer {

    static String classname = "Manifest"; // for reporting
    Job job;                // job description
    int fileCount;          // number of files processed
    FXMLProgressController.DoManifestTask reporter; // call back for reporting
    XMLParser xmlp;         // parser for XML manifest
    int objectsExpected;    // total objects expected in manifest (-1 if not of interest)
    boolean help;           // true if printing a cheat list of command line options
    static Base64.Encoder b64enc = Base64.getMimeEncoder();

    private final static Logger LOG = Logger.getLogger("Manifest.Manifest");

    /**
     * Report on version...
     *
     * <pre>
     * 20210326 0.0.1 Provided version and cleaned up headers
     * 20211117 1.0 Fixed to work with JDK 16 etc
     * </pre>
     */
    static String version() {
        return ("1.0");
    }

    /**
     * Default constructor
     *
     * @param args arguments passed to program
     * @throws AppFatal if a fatal error occurred
     */
    public Manifest(String args[]) throws AppFatal {
        SimpleDateFormat sdf;
        TimeZone tz;
        FileWriter fw;
        Handler h[];
        int i;

        // Set up logging
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s%n");
        LOG.setLevel(Level.WARNING);

        // set up default global variables
        job = new Job();
        fileCount = 0;
        job.actor = System.getProperty("user.name");
        if (job.actor == null) {
            job.actor = "Unknown user";
        }
        reporter = null;
        help = false;

        // process command line arguments
        configure(args);

        // remove any handlers associated with the LOG & log messages aren't to
        // go to the parent
        h = LOG.getHandlers();
        for (i = 0; i < h.length; i++) {
            LOG.removeHandler(h[i]);
        }
        LOG.setUseParentHandlers(false);

        // if caller specified logging to a log file, open the log file &
        // associate with logging system. Otherwise, just log to the terminal
        fw = null;
        if (job.logFile != null) {
            try {
                fw = new FileWriter(job.logFile.toFile());
            } catch (IOException ioe) {
                throw new AppFatal("Opening log file failed: " + ioe.getMessage());
            }
        }
        LOG.addHandler(new LogHandler(System.out, fw, null));

        // tell what is happening
        LOG.log(Level.INFO, "******************************************************************************");
        LOG.log(Level.INFO, "*                                                                            *");
        LOG.log(Level.INFO, "*                         M A N I F E S T   T O O L                          *");
        LOG.log(Level.INFO, "*                                                                            *");
        LOG.log(Level.INFO, "*                                Version {0}                               *", new Object[]{version()});
        LOG.log(Level.INFO, "*               Copyright 2021 Public Record Office Victoria                 *");
        LOG.log(Level.INFO, "*                                                                            *");
        LOG.log(Level.INFO, "******************************************************************************");
        LOG.log(Level.INFO, "");
        tz = TimeZone.getTimeZone("GMT+10:00");
        sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss+10:00");
        sdf.setTimeZone(tz);
        LOG.log(Level.INFO, "Run at {0}", new Object[]{sdf.format(new Date())});
        LOG.log(Level.INFO, "");
        if (help) {
            // "Manifest [-help] [-v] [-d] [-o <file>] [-i <file>] [-h <hashAlg>] [-nohash] directory"
            LOG.log(Level.INFO, "Command line arguments:");
            LOG.log(Level.INFO, " Mandatory:");
            LOG.log(Level.INFO, "  Three options are supported:");
            LOG.log(Level.INFO, "   To create (output) a manifest:");
            LOG.log(Level.INFO, "    -o <manifestFile> <directory>: create (output) the specified manifest file from the specified directory");
            LOG.log(Level.INFO, "   To check (input) a manifest:");
            LOG.log(Level.INFO, "    -i <manifestFile> <directory>: check (input) the specified manifest file against the specified directory");
            LOG.log(Level.INFO, "   To load a job (specifying creating or checking a manifest using a JSON file):");
            LOG.log(Level.INFO, "    -j <jobFile.json>: load the details of what to do from a job file");
            LOG.log(Level.INFO, "");
            LOG.log(Level.INFO, " Optional:");
            LOG.log(Level.INFO, "  -l <logFile>: save the details of what happened in a file");
            LOG.log(Level.INFO, "  -h <hashAlgorithm>: specifies the hash algorithm (default SHA-1)");
            LOG.log(Level.INFO, "");
            LOG.log(Level.INFO, "  -v: verbose mode: give more details about processing");
            LOG.log(Level.INFO, "  -d: debug mode: give a lot of details about processing");
            LOG.log(Level.INFO, "  -help: print this listing");
            LOG.log(Level.INFO, "");
        }

        // check to see that user specified the mandatory arguments
        if (job.directory == null) {
            throw new AppFatal(classname, 1, "No directory specified. Usage: " + USAGE);
        }
        if (job.manifest == null) {
            throw new AppFatal(classname, 2, "No manifest specified. Usage: " + USAGE);
        }

        LOG.log(Level.INFO, job.toString());
    }

    /**
     * Constructor used when called by a program (typically a GUI). The
     * parameters of the execution are passed in as a 'job'; the logging is
     * written out to 'hdlr', and after every step the callback 'reporter' is
     * called so that the governing program can update any status.
     *
     * @param job parameters for the execution (replaces the command line)
     * @param results place where log messages go
     * @param reporter callback to update status in the calling program
     * @throws AppFatal
     */
    public Manifest(Job job, ArrayList<String> results, FXMLProgressController.DoManifestTask reporter) throws AppFatal {
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
        LOG.addHandler(new LogHandler(null, null, results));
        System.setProperty("java.util.logging.SimpleFormatter.format", "%5$s");
        LOG.setLevel(Level.WARNING);
        if (job.verbose) {
            LOG.setLevel(Level.INFO);
        } else if (job.debug) {
            LOG.setLevel(Level.FINE);
        }

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
        job.free();
        job = null;
        reporter = null;
    }

    /**
     * Log Handler used to direct log messages to a file, to stdout, or to an
     * arrayList. Warning & severe log messages are prefixed by the keywords
     * "WARNING:" and "SEVERE:" to highlight them
     */
    private class LogHandler extends Handler {

        final SimpleFormatter sf;
        Writer w;
        ArrayList<String> r;
        PrintStream ps;

        /**
         * Create the log handler. The Writer receives a copy of all log
         * messages it it is not null.
         *
         * @param writer
         */
        public LogHandler(PrintStream ps, Writer writer, ArrayList<String> results) {
            sf = new SimpleFormatter();
            w = writer;
            r = results;
            this.ps = ps;
        }

        @Override
        public void publish(LogRecord record) {
            String s;

            s = sf.format(record);
            try {
                if (record.getLevel() == Level.SEVERE) {
                    s = "SEVERE: " + s;
                }
                if (record.getLevel() == Level.WARNING) {
                    s = "WARNING: " + s;
                }
                if (w != null) {
                    w.write(s);
                }
                if (r != null) {
                    r.add(s);
                }
                if (ps != null) {
                    ps.print(s);
                }
            } catch (IOException ioe) {
                System.err.println(ioe.toString() + "Manifest.LogHandler.publish()");
            }
        }

        @Override
        public void flush() {
            try {
                w.flush();
            } catch (IOException ioe) {
                System.err.println(ioe.toString() + "Manifest.LogHandler.close()");
            }
        }

        @Override
        public void close() {
            if (w != null) {
                try {
                    w.close();
                } catch (IOException ioe) {
                    System.err.println(ioe.toString() + "Manifest.LogHandler.close()");
                }
            }
        }
    }

    /**
     * Configure
     *
     * This method gets the options for this run of the manifest generator from
     * the command line. See the comment at the start of this file for the
     * command line arguments.
     *
     * @param args[] the command line arguments
     * @param VEOFatal if a fatal error occurred
     */
    final String USAGE = "Manifest [-help] [-j <file>] [-o <file>] [-i <file>] [-h <hashAlg>] [-l logfile] [-nohash] [-v] [-d] directory";

    private void configure(String args[]) throws AppFatal {
        int i;

        // hashFiles command line arguments
        i = 0;
        try {
            while (i < args.length) {
                switch (args[i]) {

                    case "Manifest.jar":
                        i++;
                        break;

                    // print help?
                    case "-help":
                        help = true;
                        i++;
                        break;

                    // verbose?
                    case "-v":
                        job.verbose = true;
                        LOG.setLevel(Level.INFO);
                        i++;
                        break;

                    // debug?
                    case "-d":
                        job.debug = true;
                        LOG.setLevel(Level.FINE);
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

                    // '-j' specifies a job file
                    case "-j":
                        i++;
                        try {
                            job.loadJob(Paths.get(args[i]));
                        } catch (AppError ae) {
                            throw new AppFatal(ae.getMessage());
                        }
                        i++;
                        break;

                    // '-l' specifies a log file
                    case "-l":
                        i++;
                        job.logFile = Paths.get(args[i]);
                        i++;
                        break;

                    // don't recalculate hash values when verifying
                    case "-nohash":
                        i++;
                        job.verifyHash = false;
                        i++;
                        break;

                    // '-o' specifies output manifest file
                    case "-o":
                        i++;
                        job.manifest = Paths.get(args[i]);
                        job.task = Job.Task.CREATE;
                        i++;
                        break;

                    default:
                        // if unrecognised arguement, print help string and exit
                        if (args[i].charAt(0) == '-') {
                            throw new AppFatal("Unrecognised argument '" + args[i] + "' Usage: " + USAGE);
                        }

                        // if doesn't start with '-' assume a file or directory name
                        if (job.directory != null) {
                            throw new AppFatal("Only one directory can be present '" + args[i] + "' Usage: " + USAGE);
                        }
                        job.directory = Paths.get(args[i]);
                        i++;
                        break;
                }
            }
        } catch (ArrayIndexOutOfBoundsException ae) {
            throw new AppFatal("Missing argument. Usage: " + USAGE);
        }
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
                    throw new AppFatal(classname, 9, type + " '" + p.toAbsolutePath().toString() + "' does not exist and could not be created: " + ioe.toString());
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
     * Create a manifest - an XML file. If the user cancels the creation half
     * way through, whatever has been produced is output.
     *
     * @throws VERSCommon.AppFatal
     * @throws VERSCommon.AppError
     */
    public void createManifest() throws AppFatal, AppError {
        XMLCreator xmlc;
        boolean cancelled;

        // create manifest
        xmlc = new XMLCreator(true);
        xmlc.startXMLDoc(job.manifest, "Manifest", null);
        xmlc.includeElement("Creator", null, job.actor, false);
        xmlc.includeElement("HashAlgorithm", null, job.hashAlg, false);
        xmlc.includeElement("SourceDirectory", null, job.directory.toString(), false);
        xmlc.startElement("History", null, false);
        xmlc.includeElement("DateTimeCreated", null, VERSDate.versDateTime(0), false);
        xmlc.includeElement("Comment", null, job.comment, false);
        xmlc.endElement("History", false);
        xmlc.startElement("Files", null, false);
        cancelled = createHashes(job.directory, xmlc);
        if (cancelled) {
            return;
        }
        xmlc.endElement("Files", false);
        // xmlc.includeElement("FileCount", null, Integer.toString(fileCount), false);
        xmlc.endXMLDoc();
    }

    /**
     * Process an individual directory or file. If a directory, recursively
     * process all of the files (or directories) in it.
     *
     * @param p the file or directory to hashFiles
     * @param first this is the first entry in the directory
     */
    private boolean createHashes(Path p, XMLCreator xmlc) throws AppFatal {
        DirectoryStream<Path> ds;
        String hash;
        boolean cancelled;

        // check that file or directory exists
        if (!Files.exists(p)) {
            LOG.log(Level.WARNING, "***File ''{0}'' does not exist", new Object[]{p.normalize().toString()});
            return false;
        }

        // check that it is not a Windows 10 back-up file
        if (p.getFileName().toString().startsWith("~$")) {
            LOG.log(Level.WARNING, "***File name ''{0}'' begins with a '~$' and will not be included", new Object[]{p.normalize().toString()});
            return false;
        }

        cancelled = false;

        // if file is a directory, go through directory and test all the files
        if (Files.isDirectory(p)) {
            LOG.log(Level.INFO, "***Processing directory ''{0}''", new Object[]{p.normalize().toString()});
            ds = null;
            try {
                ds = Files.newDirectoryStream(p);
                for (Path p1 : ds) {
                    cancelled = createHashes(p1, xmlc);
                    if (cancelled) {
                        break;
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to process directory ''{0}'': {1}", new Object[]{p.normalize().toString(), e.toString()});
            } finally {
                if (ds != null) {
                    try {
                        ds.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
            return cancelled;
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
                LOG.log(Level.WARNING, "Failed to process file ''{0}'': {1}", new Object[]{p.normalize().toString(), ae.toString()});
            }
        } else {
            LOG.log(Level.INFO, "***Ignoring directory ''{0}''", new Object[]{p.normalize().toString()});
        }
        // System.out.println("Did file: " + p.toString());
        if (reporter != null) {
            if (p.getNameCount() > 3) {
                p = p.subpath(p.getNameCount() - 3, p.getNameCount());
            }
            cancelled = reporter.updateStatus(".../" + p.toString(), null);
        }

        return cancelled;
    }

    /**
     * Check a manifest
     *
     * @param objectsExpected Total objects expected in manifest (-1 if not
     * known)
     * @throws VERSCommon.AppFatal
     * @throws VERSCommon.AppError
     */
    public void checkManifest(int objectsExpected) throws AppFatal, AppError {

        // check parameters
        if (job.manifest == null) {
            throw new AppError("Passed null manifest to be processed");
        }
        this.objectsExpected = objectsExpected;
        fileCount = 0;

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
            case "Manifest/Creator":         // creator of manifest
            case "Manifest/HashAlgorithm":   // hash algorithm used
            case "Manifest/SourceDirectory": // root of source directory
            case "Manifest/History/DateTimeCreated": // date created
            case "Manifest/Files/f/p": // path name of file in manifest
            case "Manifest/Files/f/h": // hash algorithm
                he = new HandleElement(HandleElement.VALUE_TO_STRING, false, null);
                break;
            case "Manifest/Files/f":      // detail of each file
                file = null;
                hashValue = null;
                fileCount++;
                break;
            case "Manifest":                 // manifest
            case "Manifest/History":
            case "Manifest/History/Comment": // a comment that the creator added
            case "Manifest/Files":           // list of files in manifest
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
            case "Manifest/Creator":    // Creator
                oldDetails.actor = value;
                break;
            case "Manifest/HashAlgorithm":    // hash algorithm used
                oldDetails.hashAlg = value;
                break;
            case "Manifest/SourceDirectory":    // source directory
                oldDetails.directory = value != null ? Paths.get(value) : null;
                break;
            case "Manifest/History/DateTimeCreated":    // date time created
                oldDetails.dateTimeCreated = value;
                break;
            case "Manifest/Files/f/p":    // path within source directory
                file = value != null ? Paths.get(value) : null;
                break;
            case "Manifest/Files/f/h":    // hash value of file
                hashValue = value;
                break;
            case "Manifest/Files/f":
                if (file == null || hashValue == null) {
                    throw new SAXException("File without both file (" + file + ") and hash (" + hashValue);
                }
                try {
                    processFile(file, hashValue);
                } catch (AppError ae) {
                    throw new SAXException(ae.toString());
                }
                break;
            case "Manifest":
                if (objectsExpected != -1 && fileCount != objectsExpected) {
                    LOG.log(Level.SEVERE, "Number of files in manifest ({0}) did not match files in specified directory ({1})", new Object[]{fileCount, objectsExpected});
                }
                break;
            default:
                break;
        }
    }

    private boolean processFile(Path partialFile, String hash) throws AppError {
        Path actualFile, p;
        String recalcHash;
        boolean cancelled;

        actualFile = job.directory.resolve(partialFile);
        // System.out.println("Processing... '"+job.directory.toString()+"' '" + partialFile.toString() + "' '"+actualFile.toString()+"' hash " + hash);
        if (Files.exists(actualFile)) {
            if (job.verifyHash) {
                recalcHash = hashFile(actualFile);
                if (!recalcHash.equals(hash)) {
                    LOG.log(Level.SEVERE, "File ''{0}'' is corrupt: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{actualFile.toString(), hash, recalcHash});
                } else {
                    LOG.log(Level.INFO, "File ''{0}'' passed: Recorded hash ''{1}'' Calculated hash ''{2}''", new Object[]{actualFile.toString(), hash, recalcHash});
                }
            } else {
                LOG.log(Level.INFO, "File ''{0}'' passed (Hash NOT checked)", new Object[]{actualFile.toString()});
            }
        } else {
            LOG.log(Level.SEVERE, "File ''{0}'' is in manifest, but is not present", new Object[]{actualFile.toString()});
        }
        if (reporter != null) {
            if (actualFile.getNameCount() > 3) {
                p = actualFile.subpath(actualFile.getNameCount() - 3, actualFile.getNameCount());
            } else {
                p = actualFile;
            }
            cancelled = reporter.updateStatus("...\\" + p.toString(), null);
            if (cancelled) {
                throw new AppError("User cancelled verification partway through");
            }
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
            throw new AppError(classname, method, 1, "failed reading file to hash: " + e.toString());
        }

        // close the input file
        try {
            bis.close();
        } catch (IOException e) {
            throw new AppError(classname, method, 1, "failed closing file to hash: " + e.toString());
        }

        // calculate the digital signature over the input file
        // calculate signature and convert it into a byte buffer
        hash = md.digest();
        /*
        for (int j=0; j<hash.length; j++) {
            System.out.print(String.format("%02x",hash[j]));
        }
        System.out.println("");*/
        return (b64enc.encodeToString(hash));
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
            if (m.job.task == Job.Task.CREATE) {
                m.createManifest();
            } else {
                m.checkManifest(-1);
            }
            m.close();
        } catch (AppFatal | AppError e) {
            System.out.println("Fatal error: " + e.toString());
            System.exit(-1);
        }
    }
}
