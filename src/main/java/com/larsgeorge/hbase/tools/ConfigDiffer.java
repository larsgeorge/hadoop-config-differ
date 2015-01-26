package com.larsgeorge.hbase.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads multiple configurations, sorts the keys and compares their details,
 * printing the differences.
 */
public class ConfigDiffer implements Runnable {

  private static final Log LOG = LogFactory.getLog(ConfigDiffer.class);

  @Parameter(description = "<filename1> <version1> <filename2> <version2> ...", required = true)
  private List<String> arguments = null;
  @Parameter(names = { "-h", "--help" }, description = "Print this help", help = true)
  private boolean printHelp = false;
  @Parameter(names = { "-t", "--template" }, description = "Optional template name")
  private String templateName = null;
  @Parameter(names = { "-q", "--quiet"}, description = "Only print data, no info text")
  private boolean quiet = false;

  private ArrayList<ConfigurationInfo> configInfos = new ArrayList<ConfigurationInfo>();
  private ArrayList<Configuration> configs = new ArrayList<Configuration>();
  private ConfigurationUtils utils = null;

  public ConfigDiffer() throws IOException {
    utils = new ConfigurationUtils(templateName, quiet);
  }

  private void parseArgs() {
    for (int index = 0; index < arguments.size(); index += 2) {
      ConfigurationInfo ci = new ConfigurationInfo(arguments.get(index), arguments.get(index + 1));
      configInfos.add(ci);
    }
  }

  private void addConfig(ConfigurationInfo info) throws Exception {
    Configuration c = utils.parseConfig(info);
    configs.add(c);
  }

  private void readConfigs() throws Exception {
    for (ConfigurationInfo info : configInfos) {
      addConfig(info);
    }
  }

  private void diff() throws IOException {
    utils.diff(configs);
  }

  @Override
  public void run() {
    try {
      parseArgs();
      readConfigs();
      diff();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Main entry point. Starts the processing.
   *
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    try {
      ConfigDiffer cd = new ConfigDiffer();
      JCommander jc = new JCommander(cd);
      jc.setProgramName(ConfigDiffer.class.getSimpleName());
      try {
        jc.parse(args);
        if (cd.printHelp) {
          jc.usage();
          System.exit(0);
        }
        if (cd.arguments == null || (cd.arguments.size() & 1) == 1) {
          System.err.println("ERROR: arguments must be specified in pairs. Aborting.");
          jc.usage();
          System.exit(1);
        }
      } catch (Exception e) {
        System.err.println(e.getMessage());
        System.err.flush();
        jc.usage();
        System.exit(-1);
      }
      cd.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
