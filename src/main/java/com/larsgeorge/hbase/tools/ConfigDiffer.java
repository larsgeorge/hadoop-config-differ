package com.larsgeorge.hbase.tools;

import java.io.IOException;
import java.util.ArrayList;

import com.beust.jcommander.JCommander;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads multiple configurations, sorts the keys and compares their details,
 * printing the differences.
 */
public class ConfigDiffer implements Runnable {

  private static final Log LOG = LogFactory.getLog(ConfigDiffer.class);

  private DifferParameters params = null;
  private ArrayList<ConfigurationInfo> configInfos = new ArrayList<ConfigurationInfo>();
  private ArrayList<Configuration> configs = new ArrayList<Configuration>();
  private ConfigurationUtils utils = null;

  public ConfigDiffer(DifferParameters params) throws IOException {
    this.params = params;
    utils = new ConfigurationUtils(params.templateName, params.quiet,
      params.prefix, params.lookup);
  }

  private void parseArgs() {
    for (int index = 0; index < params.arguments.size(); index += 2) {
      ConfigurationInfo ci = new ConfigurationInfo(params.arguments.get(index),
        params.arguments.get(index + 1));
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
      DifferParameters params = new DifferParameters();
      JCommander jc = new JCommander(params);
      jc.setProgramName(ConfigDiffer.class.getSimpleName());
      try {
        jc.parse(args);
        if (params.printHelp) {
          jc.usage();
          System.exit(0);
        }
        if (params.arguments == null || (params.arguments.size() & 1) == 1) {
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
      ConfigDiffer cd = new ConfigDiffer(params);
      cd.run();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
