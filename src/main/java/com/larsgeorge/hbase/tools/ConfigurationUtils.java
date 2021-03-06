package com.larsgeorge.hbase.tools;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Helper with miscellaneous functions.
 */
public class ConfigurationUtils {
  private static final Log LOG = LogFactory.getLog(ConfigurationUtils.class);

  /** The default mustache template. */
  private static final String DEF_TEMPLATE =
    "{{action}}: " +
    "{{#property}}" +
      "Property{key='{{key}}', value='{{value}}', description='{{description}}', source='{{source}}'}" +
    "{{/property}}" +
    "{{#property2}}" +
      "\n\n   from: Property{key='{{key}}', value='{{value}}', description='{{description}}', source='{{source}}'}" +
    "{{/property2}}\n";

  private String templateName = null;
  private String template = DEF_TEMPLATE;
  private boolean quiet = false;
  private MustacheFactory mf = new DefaultMustacheFactory();
  private Mustache mustache = null;
  private String prefix = null;
  private String lookup = null;
  private boolean ignoreDescription = false;
  private Map<String, String> types = null;
  private Map<String, String> units = null;

  /** The possible actions triggering a report on a property. */
  enum Action { Added, Renamed, Removed, Changed, Baseline }

  public ConfigurationUtils() throws IOException {
  }

  public ConfigurationUtils(String templateName) throws IOException {
    this.templateName = templateName;
    loadTemplate();
  }

  public ConfigurationUtils(DifferParameters params) throws IOException {
    this(params.templateName);
    this.quiet = params.quiet;
    this.prefix = params.prefix != null ? params.prefix : "";
    this.lookup = params.lookup;
    this.ignoreDescription = params.ignoreDescription;
    if (lookup != null) loadLookupTable();
  }

  /**
   * Loads a special properties file with details about the type and unit of a config key.
   */
  private void loadLookupTable() throws IOException {
    File propFile = new File(lookup);
    if (propFile.exists()) {
      types = new HashMap<String, String>();
      units = new HashMap<String, String>();
      Properties props = new Properties();
      props.load(new FileReader(propFile));
      Enumeration keys = props.propertyNames();
      int num = 0;
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        String value = props.getProperty(key);
        String[] parts = value.split("\\|");
        if (parts[0].length() > 0) types.put(key, parts[0]);
        if (parts.length > 1 && parts[1].length() > 0) units.put(key, parts[1]);
        num++;
      }
      if (!quiet) System.out.println(prefix + "Using " + num + " lookup entries.");
    } else {
      if (!quiet) System.out.println(prefix + "WARNING: Properties file not found, skipping...");
    }
  }

  /**
   * Parses a configuration file (XML based) into an internal structure.
   *
   * @param info The details about a given configuration file.
   * @return The parsed configuration details in an internal format.
   * @throws ParserConfigurationException When the XML given is faulty.
   * @throws IOException When reading the configuration file fails.
   * @throws SAXException When parsing the XML fails.
   */
  public Configuration parseConfig(ConfigurationInfo info)
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
        String type = types != null ? types.get(attr) : null;
        String unit = units != null ? units.get(attr) : null;
        Property p = new Property(attr, value, type, unit, description, info.getVersion());
        if (ignoreDescription) p.setIgnoreDescription(true);
        conf.addProperty(p);
      } else {
        LOG.error("WARNING: Attribute was null!");
      }
    }
    return conf;
  }

  /**
   * Builds the difference of the given configurations and emits the results.
   *
   * @param configs The list of given configurations.
   * @throws IOException When emitting the results fails.
   */
  public void diff(ArrayList<Configuration> configs) throws IOException {
    if (!quiet) {
      System.out.println(prefix + "=========================================================");
      System.out.println(prefix + "Start");
      System.out.println(prefix + "=========================================================");
    }

    MergedConfiguration mc = new MergedConfiguration();
    TreeSet<String> prevKeys = null;
    Configuration prevConf = null;
    // iterate over configs gather details
    if (!quiet) System.out.println(prefix + "Checking differences across versions...\n");
    for (Configuration conf : configs) {
      TreeSet<String> keys = new TreeSet<String>();
      TreeSet<String> keysMissingType = new TreeSet<String>();
      for (Property p : conf.getProperties()) {
        mc.addProperty(p);
        keys.add(p.getKey());
      }
      // do not check empty configurations
      if (conf.getProperties().size() > 0) {
        String currentVersion = conf.getProperties().first().getSource();
        // if this is the second+ config print out differences
        if (prevKeys != null) {
          // check for all newly added or renamed keys
          TreeSet<String> addedKeys = new TreeSet<String>(keys);
          addedKeys.removeAll(prevKeys);
          if (addedKeys.size() > 0) {
            if (!quiet) System.out.println(prefix + "Added or Renamed Keys in " +
              currentVersion + ":");
            int addedCount = 0, renamedCount = 0, missingType = 0;
            for (String key : addedKeys) {
              Property p = conf.getProperty(key);
              // we assume renaming does NOT change the description (or else how can we tell?)
              Property p2 = prevConf.getPropertyByDescription(p.getDescription());
              boolean renamed = !Property.NULL.equals(p.getDescription()) && p2 != null;
              if (renamed) renamedCount++; else addedCount++;
              if (p.getType() == null) {
                keysMissingType.add(p.getKey());
                missingType++;
              }
              printProperty(p, p2, renamed ? Action.Renamed : Action.Added);
            }
            if (!quiet) System.out.println(prefix + "Summary for " + currentVersion + ": " +
              addedCount + " added and " + renamedCount + " renamed properties.");
            if (!quiet) {
              System.out.println(prefix + "Missing type info: " + missingType);
              if (keysMissingType.size() > 0) System.out.println(prefix + keysMissingType);
            }
            System.out.println();
          }
          // determine all removed keys
          TreeSet<String> removedKeys = new TreeSet<String>(prevKeys);
          removedKeys.removeAll(keys);
          if (removedKeys.size() > 0) {
            if (!quiet) System.out.println(prefix + "Removed Keys in " + currentVersion + ":");
            int removedCount = 0;
            for (String key : removedKeys) {
              Property p = prevConf.getProperty(key);
              removedCount++;
              printProperty(p, null, Action.Removed);
            }
            if (!quiet) System.out.println(prefix + "Summary for " + currentVersion + ": " +
              removedCount + " removed properties.");
            System.out.println();
          }
        }
      }
      prevKeys = keys;
      prevConf = conf;
    }
    if (!quiet) System.out.println(prefix +
      "---------------------------------------------------------");
    if (!quiet) System.out.println(prefix + "Checking differences per property...\n");
    int diffCount = 0;
    for (String key : mc.getProperties().keySet()) {
      TreeSet<Property> merged = mc.getProperties().get(key);
      if (merged.size() > 1) {
        if (!quiet) System.out.println(prefix + "Difference found for property " + key);
        diffCount++;
        boolean first = true;
        for (Property p : merged) {
          printProperty(p, null, first ? Action.Baseline : Action.Changed);
          first = false;
        }
        System.out.println();
      }
    }
    if (!quiet) System.out.println(prefix + "Total: " + diffCount + " differences.");
    if (!quiet) System.out.println(prefix +
      "=========================================================");
  }

  /**
   * Optionally loads and compiles (if not null) the given Mustache template.
   *
   * @throws IOException When loading the given external template file fails.
   */
  private void loadTemplate() throws IOException {
    if (templateName != null) {
      template = FileUtils.readFileToString(new File(templateName));
    }
    if (template != null) mustache = mf.compile(new StringReader(template), "template");
  }

  /**
   * Prints a property and optional related property.
   *
   * @param p The property to print.
   * @param p2  The optional related property.
   * @param action The action that triggered the print out.
   */
  private void printProperty(Property p, Property p2, Action action) throws IOException {
    HashMap<String, Object> context = new HashMap<String, Object>();
    context.put("action", action);
    context.put("property", p);
    if (p2 != null) context.put("property2", p2);
    Writer writer = new OutputStreamWriter(System.out);
    mustache.execute(writer, context);
    writer.flush();
  }
}
