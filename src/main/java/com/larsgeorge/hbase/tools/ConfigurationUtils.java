package com.larsgeorge.hbase.tools;

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
import java.io.IOException;
import java.util.ArrayList;
import java.util.TreeSet;

/**
 * Helper with miscellaneous functions.
 */
public class ConfigurationUtils {
  private static final Log LOG = LogFactory.getLog(ConfigurationUtils.class);

  public ConfigurationUtils() {
  }

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
        Property p = new Property(attr, value, description, info.getVersion());
        conf.addProperty(p);
      } else {
        LOG.error("WARNING: Attribute was null!");
      }
    }
    return conf;
  }

  public void diff(ArrayList<Configuration> configs) {
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
            boolean renamed = !Property.NULL.equals(p.getDescription()) && p2 != null;
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
}
