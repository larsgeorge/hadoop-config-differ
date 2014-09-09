package com.larsgeorge.hbase.tools;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.text.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
// Apache commons io libs
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import org.apache.commons.io.*;
import org.apache.commons.io.filefilter.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

/**
 * Scans the given subdirectories to look for configuration properties embedded
 * in the code.
 *
 * @author Lars George
 */
public class FindProperties implements Runnable {

  private static String defaultExpression = "\"(\\p{Alpha}\\w+\\.){%d,}(\\p{Alpha}\\w+)\"";

  @Parameter(names = { "-v", "--verbose" })
  private boolean verbose = false;
  @Parameter(names = "--debug", description = "Debug mode")
  private boolean debug = false;
  @Parameter(names = { "-h", "--help" }, description = "Print this help", help = true)
  private boolean printHelp = false;
  @Parameter(names = { "-d", "--directory" }, description = "The directory to scan",
    required = true)
  private String directory = null;
  @Parameter(names = { "-t", "--types" },
    description = "Space separated list of file types to scan, e.g. \"java xml\". " +
      "Try one or more of these: java, xml, java_code, java_all, or any (matches all files)",
    variableArity = true)
  private List<String> fileTypes = Arrays.asList("java");
  @Parameter(names = { "-e", "--expression" }, description = "Custom regular expression")
  private String expression = null;
  @Parameter(names = { "-n", "--numfields" },
    description = "Minimum number of fields to identify property")
  private int numFields = 3;
  @Parameter(names = { "-c", "--config" }, description = "Name of config file to check against")
  private String configName = null;
  @Parameter(names = { "-p", "--threads" }, description = "Number of threads to use")
  private int numThreads = 3;
  @Parameter(names = {"-u", "--unique"}, description = "Show only unique results")
  private boolean unique = false;
  @Parameter(names = {"-s", "--sorted"}, description = "Show results sorted")
  private boolean sorted = false;
  @Parameter(names = "--printFiles", description = "Print files with matches")
  private boolean printFiles = false;
  @Parameter(names = "--emitAsXml", description = "Emit all found properties as an Hadoop XML configuration")
  private boolean emitAsXml = false;
  @Parameter(names = {"-o", "--outputFile"}, description = "Write output to the specified file, not to the console")
  private String outputFile = null;
  @Parameter(names = "--exclude", description = "Exclude the given directory.")
  private List<String> exclude = new ArrayList<String>();

  private ExecutorService pool = null;
  private Results results = new Results();
  private Statistics statistics = new Statistics();
  private List<String> finalProperties = new ArrayList<String>();

  public enum FileTypes {
    ANY(null),
    JAVA_ALL(new String[] { ".java", ".jsp", ".properties" }),
    JAVA_CODE(new String[] { ".java", ".jsp" }),
    JAVA(new String[] { ".java" }),
    XML(new String[] { ".xml", ".dtd", ".xsl", ".xslt", ".ent" });

    private String[] extensions = null;

    FileTypes(String[] extensions) {
      this.extensions = extensions;
    }

    public String[] getExtensions() {
      return extensions;
    }

    public boolean match(String filename) {
      if (extensions == null) {
        return true;
      }
      for (String extension : extensions) {
        if (filename.endsWith(extension)) {
          return true;
        }
      }
      return false;
    }
  }

  class Statistics {
    int numFilesFound = -1;
    long startTime = -1L;
    long endTime = -1L;
    long elapsedTime = -1L;
    int numMatchesFound = 0;
    int numUniqueMatchesFound = 0;
    int numHiddenProperties = 0;
  }

  /**
   * Holds the results.
   */
  class Results {
    private Map<File, List<String>> matched = new LinkedHashMap<File, List<String>>();

    public Map<File, List<String>> getMatched() {
      return matched;
    }

    /**
     * Adds all values of another result instance to this one.
     *
     * @param results  The other result instance.
     */
    public synchronized void add(Results results) {
      matched.putAll(results.getMatched());
    }

    public void addFileResults(File file, List<String> matches) {
      matched.put(file, matches);
    }
  }

  /**
   * Runnable class that is executed by the thread pool.
   */
  class FileHandler implements Runnable {

    private File file = null;
    private int fileNo = -1;
    private List<String> matches = new ArrayList<String>();
    private Results results = null;
    private Matcher matcher = null;
    private Statistics statistics = null;

    /**
     * Creates a new instance of this class.
     *
     * @param file The file to process.
     * @param num The file number.
     * @param results The global results instance.
     */
    public FileHandler(File file, int num, Results results, Statistics statistics) {
      this.file = file;
      this.fileNo = num;
      this.results = results;
      this.statistics = statistics;
      String exp = expression != null ?
        expression : String.format(defaultExpression, numFields - 1);
      matcher = Pattern.compile(exp).matcher("");
    } // constructor

    /**
     * Called by thread, main processing method.
     *
     * @see java.lang.Runnable#run()
     */
    public void run() {
      String fn = file.getName();
      String path = file.getPath();
      // extract last part of the path
      if (path != null && path.lastIndexOf(File.separator) > -1) {
        int pos = path.lastIndexOf(File.separator);
        int pos2 = path.lastIndexOf(File.separator, pos - 1);
        if (pos2 > -1) {
          path = path.substring(pos2 + 1, pos);
        } else {
          path = path.substring(0, pos);
        }
      }
      if (verbose) System.out.println("Processing " + fn + " [" + path + "]");
      try {
        matches.clear();
        LineIterator it = getLineIterator(file);
        try {
          long n = 0;
          while (it.hasNext()) {
            if (verbose && n % 1000 == 0) System.out.print(".");
            if (verbose && n % (80 * 1000) == 0 && n > 0) System.out.println();
            String line = it.nextLine();
            processLine(line);
            n++;
          }
          synchronized (statistics) {
            statistics.numMatchesFound = statistics.numMatchesFound + matches.size();
          }
          if (verbose) System.out.println("\nProcessed lines (" + fn + " [" + path + "] #" +
              fileNo + ") -> " + n + ", matches -> " + matches.size() + "\n");
        } finally {
          LineIterator.closeQuietly(it);
        }
      } catch (Exception e) {
        System.err.println("\nFailed processing " + fn + "\n");
      }
      if (matches.size() > 0) {
        synchronized (results) {
          results.addFileResults(file, matches);
        }
      } else {
        if (verbose) System.out.println("No matches found, skipping file: " + file.getName());
      }
    } // run

    /**
     * Branches off processing based on mode.
     *
     * @param line The current line to process.
     * @throws Exception When the date is corrupt or decryption fails.
     */
    private void processLine(String line) {
      try {
        if (line != null && line.length() > 0) {
          matcher.reset(line);
          while (matcher.find()) matches.add(matcher.group());
        }
      } catch (Throwable t) {
        System.err.println("\nFailed processing line " + line);
        System.err.println("Error was " + t);
      }
    }
  } // FileHandler

  /**
   * Special walker to find all log files in the given directory tree.
   */
  class SourceDirectoryWalker extends DirectoryWalker {

    // The list of files found during the walk
    private TreeMap files = new TreeMap();
    private Statistics statistics = null;

    /**
     * Creates a new instance of this class.
     */
    public SourceDirectoryWalker(IOFileFilter fileFilter, Statistics statistics) {
      super(HiddenFileFilter.VISIBLE, fileFilter, -1);
      this.statistics = statistics;
    } // constructor

    /**
     * Check if we need to skip this directory.
     *
     * @param directory  The name of the current directory.
     * @param depth  The current depth.
     * @param results  The passed on list of results.
     * @return <code>true</code> when this directory should be handled.
     * @throws IOException When something during the check fails.
     */
    @Override
    protected boolean handleDirectory(File directory, int depth,
      Collection results) throws IOException {
      boolean handle = super.handleDirectory(directory, depth, results);
      for (String ep : exclude) {
        PathMatcher pm = FileSystems.getDefault().getPathMatcher("glob:" + ep);
        if (pm.matches(directory.toPath())) {
          if (verbose) System.out.println("Excluding path: " + directory);
          return false;
        }
      }
      return handle;
    }

    /**
     * Traverses the files and stores them for subsequent processing.
     *
     * @param file The current file.
     * @param depth The depth in terms of directories.
     * @param results The result list handed in.
     */
    @Override
    protected void handleFile(File file, int depth, Collection results) throws IOException {
      super.handleFile(file, depth, results);
      files.put(file.getAbsolutePath(), file);
    }

    /**
     * Starts the directory walk and analyze process.
     *
     * @param dir The start directory.
     * @return The list of results.
     * @throws IOException When the start directory does not exist.
     * @throws InterruptedException When the pool has an issue waiting.
     */
    public void find(String dir) throws IOException, InterruptedException {
      DateFormat df = DateFormat.getDateTimeInstance();
      statistics.startTime = System.currentTimeMillis();
      if (verbose)
        System.err.println("Processing started: " + df.format(new Date(statistics.startTime)));
      walk(new File(dir), null);
      int n = files.size();
      if (verbose) System.err.println("Number of files found: " + n);
      statistics.numFilesFound = n;
      // create thread pool with requested number of threads
      pool = Executors.newFixedThreadPool(numThreads);
      for (Iterator it = files.keySet().iterator(); it.hasNext(); )
        pool.execute(new FileHandler((File) files.get(it.next()), n--, results, statistics));
      pool.shutdown();
      pool.awaitTermination(1, TimeUnit.DAYS);
      statistics.endTime = System.currentTimeMillis();
      statistics.elapsedTime = statistics.endTime - statistics.startTime;
      if (verbose) {
        System.out.println();
        System.out.println("Processing time: " + (statistics.elapsedTime / 1000));
        System.out.println("Processing ended: " + df.format(new Date(statistics.endTime)));
        System.out.println();
      }
    }
  } // SourceDirectoryWalker

  /**
   * Creates a new instance of this class.
   */
  public FindProperties() {
  } // constructor

  /**
   * Creates a new LineIterator instance while handling compresses files
   * internally.
   *
   * @param file The current file to get the iterator for.
   * @return The iterator.
   * @throws IOException When there is a problem with the file.
   * @throws FileNotFoundException When the file suddenly is gone missing.
   */
  private static LineIterator getLineIterator(File file) throws IOException {
    InputStream in = new FileInputStream(file);
    Reader reader = new InputStreamReader(in);
    reader = new BufferedReader(reader, 64 * 1024); // 64k blocks
    LineIterator it = IOUtils.lineIterator(reader);
    return it;
  }

  /**
   * Creates a list of file filters combined with a boolean OR or returns <code>null</code> when
   * there is none (or the user specified the ANY type).
   *
   * @return The list of filters OR'ed or <code>null</code>.
   */
  private IOFileFilter getFileFilters() {
    IOFileFilter result = null;
    if (fileTypes.size() > 0) {
      List<IOFileFilter> filters = new ArrayList<IOFileFilter>();
      for (String type : fileTypes) {
        FileTypes ft = null;
        try { ft = FileTypes.valueOf(type.toUpperCase()); } catch (Exception e) { /* ignore */ }
        if (ft != null) {
          switch (ft) {
            case ANY:
              return null;
            default:
              for (String ext : ft.getExtensions())
                filters.add(FileFilterUtils.suffixFileFilter(ext));
          }
        } else {
          System.err.println("Warning: file type \"" + type + "\" is unkown, ignoring it...");
        }
      }
      return FileFilterUtils.or(filters.toArray(new IOFileFilter[] { }));
    }
    return result;
  }

  /**
   * Helper to remove optional quotes.
   *
   * @param prop The property text.
   * @return The same but with removed quotes (if any there were).
   */
  private String stripQuotes(String prop) {
    String result = prop;
    if (result.startsWith("\"")) result = result.substring(1);
    if (result.endsWith("\"")) result = result.substring(0, result.length() - 1);
    return result;
  }

  /**
   * Based on the found information, compute the final list of found properties.
   */
  private void determineResults() {
    for (Map.Entry<File, List<String>> entry : results.getMatched().entrySet()) {
      List<String> matches = entry.getValue();
      for (String match : matches) {
        match = stripQuotes(match);
        if ((unique && !finalProperties.contains(match)) || !unique) finalProperties.add(match);
      }
    }
    statistics.numUniqueMatchesFound = finalProperties.size();
    if (sorted) Collections.sort(finalProperties);
  }

  /**
   * Formats the results for print out.
   *
   * @return The formatted results.
   */
  private String formatResults() {
    StringBuilder sb = new StringBuilder();
    if (emitAsXml) {
      sb.append("<?xml version=\"1.0\"?>\n");
      sb.append("<?xml-stylesheet type=\"text/xsl\" href=\"configuration.xsl\"?>\n");
      sb.append("<configuration>\n");
      for (String prop : finalProperties) {
        sb.append("  <property >\n");
        sb.append("    <name>" + prop + "</name>\n");
        sb.append("    <value></value>\n");
        sb.append("  </property>\n");
      }
      sb.append("</configuration>");
    } else {
      for (String prop : finalProperties) sb.append(prop + "\n");
    }
    return sb.toString();
  }

  /**
   * Dumps the actual files and what was found in them.
   */
  private void printFilesWithMatches() {
    for (Map.Entry<File, List<String>> entry : results.getMatched().entrySet()) {
      List<String> matches = entry.getValue();
      System.out.println("File: " + entry.getKey().getName() + " -> " + matches.size() + "\n");
      for (String match : matches) System.out.println("\t" + match + "\n");
    }
  }
  /**
   * Output the results where specified, i.e. console or an output file.
   *
   * @throws FileNotFoundException When there is an error creating the output file.
   */
  private void printResults() throws FileNotFoundException {
    PrintStream out = outputFile == null ? System.out : new PrintStream(outputFile);
    if (outputFile == null && verbose) out.println("Results:\n");
    out.println(formatResults());
    if (outputFile != null) {
      out.flush();
      out.close();
    }
  }

  /**
   * Prints the difference of a given configuration file to the list of found properties.
   */
  private void printHiddenProperties() throws Exception {
    ConfigurationUtils utils = new ConfigurationUtils();
    String dir = directory.endsWith(File.separator) ? directory : directory + File.separator;
    String fn = configName.startsWith(File.separator) ? configName : dir + configName;
    ConfigurationInfo info = new ConfigurationInfo(fn, "hidden");
    Configuration config = utils.parseConfig(info);
    System.out.println("\nKeys not found in configuration file:\n");
    for (String key : finalProperties) {
      if (config.getProperty(key) == null) {
        System.out.println(key);
        statistics.numHiddenProperties++;
      }
    }
    System.out.println("Total number of hidden properties: " + statistics.numHiddenProperties);
  }

  /**
   * Main processing starts here.
   *
   * @see java.lang.Runnable#run()
   */
  public void run() {
    try {
      IOFileFilter fileFilter = getFileFilters();
      SourceDirectoryWalker dw = new SourceDirectoryWalker(fileFilter, statistics);
      dw.find(directory);
      determineResults();
      if (printFiles) printFilesWithMatches();
      printResults();
      if (configName != null) printHiddenProperties();
      System.out.println("Number of files found in total: " + statistics.numFilesFound);
      System.out.println("Number of matches total: " + statistics.numMatchesFound);
      System.out.println("Number of files with matches: " + results.getMatched().size());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Program entry point.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    try {
      FindProperties fp = new FindProperties();
      JCommander jc = new JCommander(fp);
      jc.setProgramName(FindProperties.class.getSimpleName());
      try {
        jc.parse(args);
        if (fp.printHelp) {
          jc.usage();
          System.exit(0);
        }
      } catch (Exception e) {
        System.err.println(e.getMessage());
        jc.usage();
        System.exit(-1);
      }
      fp.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}