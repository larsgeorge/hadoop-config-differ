package com.larsgeorge.hbase.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads multiple configurations, sorts the keys and compares their details,
 * printing the differences.
 */
public class ConfigDiffer {

  private static final Log LOG = LogFactory.getLog(ConfigDiffer.class);
  private static final String NULL = "NULL";

  private ArrayList<ConfigurationInfo> configInfos = new ArrayList<ConfigurationInfo>();
  private ArrayList<Configuration> configs = new ArrayList<Configuration>();

  private class ConfigurationInfo {
    private String path;
    private String version;

    private ConfigurationInfo(String path, String version) {
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

  private class Property implements Comparable {
    private String key;
    private String value;
    private String description;
    private String source;

    private Property(String key, String value, String description, String source) {
      this.key = key;
      this.value = value != null ? value : NULL;
      this.description = description != null ? description : NULL;
      this.source = source;
    }

    public String getKey() {
      return key;
    }

    public void setKey(String key) {
      this.key = key;
    }

    public String getValue() {
      return value;
    }

    public void setValue(String value) {
      this.value = value;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Property property = (Property) o;
      if (description != null ?
        !description.equals(property.description) : property.description != null) {
        return false;
      }
      if (key != null ? !key.equals(property.key) : property.key != null) {
        return false;
      }
      if (value != null ? !value.equals(property.value) : property.value != null) {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode() {
      int result = key != null ? key.hashCode() : 0;
      result = 31 * result + (value != null ? value.hashCode() : 0);
      result = 31 * result + (description != null ? description.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "Property{" +
        "key='" + key + '\'' +
        ", value='" + value + '\'' +
        ", description='" + description + '\'' +
        ", source='" + source + '\'' +
        '}';
    }

    @Override
    public int compareTo(Object o) {
      if (this == o) {
        return 0;
      }
      Property property = (Property) o;
      int kint = key != null && property.key != null ?
        key.compareTo(property.key) : -1;
      int vint = value != null && property.value != null ?
        value.compareTo(property.value) : -1;
      int dint = description != null && property.description != null ?
        description.compareTo(property.description) : -1;
      return Math.abs(kint) + Math.abs(vint) + Math.abs(dint);
    }
  }

  private class Configuration {
    private TreeSet<Property> properties = new TreeSet<Property>();

    public TreeSet<Property> getProperties() {
      return properties;
    }

    public void addProperty(Property property) {
      properties.add(property);
    }

    public int getSize() {
      return properties.size();
    }

    public Property getProperty(String key) {
      for (Property property : properties) {
        if (property.getKey().equals(key)) return property;
      }
      return null;
    }

    public Property getPropertyByDescription(String description) {
      for (Property property : properties) {
        if (property.getDescription() != null && property.getDescription().equals(description))
          return property;
      }
      return null;
    }
  }

  private class MergedConfiguration {
    private TreeMap<String, TreeSet<Property>> properties =
      new TreeMap<String, TreeSet<Property>>();

    public TreeMap<String, TreeSet<Property>> getProperties() {
      return properties;
    }

    public void addProperty(Property property) {
      TreeSet<Property> merged = properties.get(property.getKey());
      if (merged == null) {
        merged = new TreeSet<Property>();
        properties.put(property.getKey(), merged);
      }
      merged.add(property);
    }

    public int getSize() {
      return properties.size();
    }
  }

  public ConfigDiffer(String[] args) throws Exception {
    parseArgs(args);
    readConfigs();
  }

  private void parseArgs(String[] args) {
    for (int index = 0; index < args.length; index += 2) {
      ConfigurationInfo ci = new ConfigurationInfo(args[index], args[index + 1]);
      configInfos.add(ci);
    }
  }

  private void readConfigs() throws Exception {
    for (ConfigurationInfo info : configInfos) {
      addConfig(info);
    }
  }

  private void addConfig(ConfigurationInfo info) throws Exception {
    Configuration c = parseConfig(info);
    configs.add(c);
  }

  private Configuration parseConfig(ConfigurationInfo info)
    throws ParserConfigurationException, IOException, SAXException {
    Configuration conf = new Configuration();
    DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
    docBuilderFactory.setIgnoringComments(true);
    docBuilderFactory.setNamespaceAware(true);
    try {
      docBuilderFactory.setXIncludeAware(true);
    } catch (UnsupportedOperationException e) {
      LOG.error("Failed to set setXIncludeAware(true) for parser " +
        docBuilderFactory + ":" + e, e);
    }
    DocumentBuilder builder = docBuilderFactory.newDocumentBuilder();
    Document doc = builder.parse(new File(info.getPath()).getAbsoluteFile());
    Element root = doc.getDocumentElement();
    if (!"configuration".equals(root.getTagName()))
      LOG.fatal("bad conf file: top-level element not <configuration>");
    NodeList props = root.getChildNodes();
    for (int i = 0; i < props.getLength(); i++) {
      Node propNode = props.item(i);
      if (!(propNode instanceof Element)) continue;
      Element prop = (Element)propNode;
      if (!"property".equals(prop.getTagName()))
        LOG.warn("bad conf file: element not <property>");
      NodeList fields = prop.getChildNodes();
      String attr = null;
      String value = null;
      String description = null;
      boolean finalParameter = false;
      for (int j = 0; j < fields.getLength(); j++) {
        Node fieldNode = fields.item(j);
        if (!(fieldNode instanceof Element)) continue;
        Element field = (Element)fieldNode;
        if ("name".equals(field.getTagName()) && field.hasChildNodes())
          attr = ((Text) field.getFirstChild()).getData().trim();
        if ("value".equals(field.getTagName()) && field.hasChildNodes())
          value = ((Text)field.getFirstChild()).getData();
        if ("description".equals(field.getTagName()) && field.hasChildNodes())
          description = ((Text)field.getFirstChild()).getData();
        if (description != null)
          description = description.replaceAll("\n", " ").replaceAll(" +", " ").trim();
        if ("final".equals(field.getTagName()) && field.hasChildNodes())
          finalParameter = "true".equals(((Text)field.getFirstChild()).getData());
      }
      if (attr != null) {
        Property p = new Property(attr, value, description, info.getVersion());
        conf.addProperty(p);
      } else {
        System.err.println("WARNING: Attribute was null!");
      }
    }
    return conf;
  }

  private void diff() {
    System.out.println("=========================================================");
    System.out.println("Start");
    System.out.println("=========================================================");

    MergedConfiguration mc = new MergedConfiguration();
    TreeSet<String> prevKeys = null;
    Configuration prevConf = null;
    // iterate over configs gather details
    System.out.println("Checking differences across versions...\n");
    for (Configuration conf : configs) {
      TreeSet<String> keys = new TreeSet<String>();
      for (Property p : conf.getProperties()) {
        mc.addProperty(p);
        keys.add(p.getKey());
      }
      String currentVersion = conf.getProperties().first().getSource();
      // if this is the second+ config print out differences
      if (prevKeys != null) {
        // check for all newly added or renamed keys
        TreeSet<String> addedKeys = new TreeSet<String>(keys);
        addedKeys.removeAll(prevKeys);
        if (addedKeys.size() > 0) {
          System.out.println("Added or renamed keys in " + currentVersion + ":");
          for (String key : addedKeys) {
            Property p = conf.getProperty(key);
            // we assume renaming does NOT change the description (or else how can we tell?)
            Property p2 = prevConf.getPropertyByDescription(p.getDescription());
            boolean renamed = !NULL.equals(p.getDescription()) && p2 != null;
            System.out.println(renamed ? "renamed: " + p + ",\n   from: " + p2 : "added:   " + p);
          }
          System.out.println();
        }
        // determine all removed keys
        TreeSet<String> removedKeys = new TreeSet<String>(prevKeys);
        removedKeys.removeAll(keys);
        if (removedKeys.size() > 0) {
          System.out.println("Removed keys in " + currentVersion + ":");
          for (String key : removedKeys) {
            Property p = prevConf.getProperty(key);
            System.out.println(p);
          }
          System.out.println();
        }
      }
      prevKeys = keys;
      prevConf = conf;
    }
    System.out.println("---------------------------------------------------------");
    System.out.println("Checking differences per property...\n");
    int diffCount = 0;
    for (String key : mc.getProperties().keySet()) {
      TreeSet<Property> merged = mc.getProperties().get(key);
      if (merged.size() > 1) {
        System.out.println("Difference found for property " + key);
        diffCount++;
        for (Property p : merged) {
          System.out.println(p.toString());
        }
        System.out.println();
      }
    }
    System.out.println("Total: " + diffCount + " differences.");
    System.out.println("=========================================================");
  }

  private static void printHelp() {
    System.out.println("usage: " + ConfigDiffer.class.getSimpleName() +
      "<filename1> <version1> <filename2> <version2> ..."
    );
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      printHelp();
      System.exit(1);
    }
    ConfigDiffer diff = null;
    try {
      diff = new ConfigDiffer(args);
    } catch (Exception e) {
      e.printStackTrace();
    }
    diff.diff();
  }
}
