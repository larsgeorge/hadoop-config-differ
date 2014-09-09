package com.larsgeorge.hbase.tools;

import java.util.TreeSet;

/**
 * Stores the details of a configuration.
 */
class Configuration {
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
