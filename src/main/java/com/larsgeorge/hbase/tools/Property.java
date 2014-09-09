package com.larsgeorge.hbase.tools;

/**
 * Holds the details of a configuration property.
 */
class Property implements Comparable {
  public static final String NULL = "NULL";

  private String key;
  private String value;
  private String description;
  private String source;

  Property(String key, String value, String description, String source) {
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
