package com.larsgeorge.hbase.tools;

/**
 * Stores meta data about a configuration instance.
 */
class ConfigurationInfo {
  private String path;
  private String version;

  ConfigurationInfo(String path, String version) {
    this.path = path;
    this.version = version;
  }

  public String getPath() {
    return path;
  }

  public String getVersion() {
    return version;
  }
}
