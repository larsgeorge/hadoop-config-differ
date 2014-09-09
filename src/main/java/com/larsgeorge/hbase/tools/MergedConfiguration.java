package com.larsgeorge.hbase.tools;

import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stores a merged configuration that can hold multiple, versioned properties.
 */
class MergedConfiguration {
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
