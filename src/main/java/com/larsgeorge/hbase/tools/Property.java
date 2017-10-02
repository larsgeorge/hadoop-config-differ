package com.larsgeorge.hbase.tools;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.time.DurationFormatUtils;

/**
 * Holds the details of a configuration property.
 */
class Property implements Comparable {
  public static final String NULL = "NULL";

  private String key;
  private String value;
  private String formattedValue;
  private String type;
  private String unit;
  private String description;
  private String source;
  private boolean ignoreDescription = false;

  Property(String key, String value, String description, String source) {
    this(key, value, null, null, description, source);
  }

  Property(String key, String value, String type, String unit, String description, String source) {
    this.key = key;
    this.value = value;
    this.type = type;
    this.unit = unit;
    this.description = description != null ? description : NULL;
    this.source = source;
    formatValue();
  }

  public boolean isIgnoreDescription() {
    return ignoreDescription;
  }

  public void setIgnoreDescription(boolean ignoreDescription) {
    this.ignoreDescription = ignoreDescription;
  }

  private void formatValue() {
    if (value != null && unit != null && type != null &&
      (type.equalsIgnoreCase("int") || type.equalsIgnoreCase("long"))) {
      try {
        long longValue = Long.parseLong(value);
        if (unit.equalsIgnoreCase("bytes")) {
          formattedValue = humanReadableByteCount(longValue);
        } else if (unit.equalsIgnoreCase("milliseconds")) {
          formattedValue = humanReadableTime(longValue);
        }
      } catch (Exception e) {
        System.err.println("WARNING: Failed to parse value \"" + value + "\" for key " + key);
      }
    }
  }

  private String humanReadableByteCount(long bytes) {
    return FileUtils.byteCountToDisplaySize(bytes);
  }

  private String humanReadableTime(long millis) {
    return DurationFormatUtils.formatDurationWords(millis, true, true);
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

  public String getFormattedValue() {
    return formattedValue;
  }

  public void setFormattedValue(String formattedValue) {
    this.formattedValue = formattedValue;
  }

  public String getType() { return type; }

  public void setType(String type) { this.type = type; }

  public String getUnit() { return unit; }

  public void setUnit(String unit) { this.unit = unit; }

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
    if (key != null ? !key.equals(property.key) : property.key != null) {
      return false;
    }
    if (value != null ? !value.equals(property.value) : property.value != null) {
      return false;
    }
    if (!ignoreDescription) {
      if (description != null ?
        !description.equals(property.description) : property.description != null) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    int result = key != null ? key.hashCode() : 0;
    result = 31 * result + (value != null ? value.hashCode() : 0);
    if (!ignoreDescription)
      result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return "Property{" +
      "key='" + key + '\'' +
      ", value='" + value + '\'' +
      ", type='" + type + '\'' +
      ", unit='" + unit + '\'' +
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

    int kint = compareOne(key, property.key);
    int vint = compareOne(value, property.value);
    if (!ignoreDescription) {
      int dint = compareOne(description, property.description);
      return Math.abs(kint) + Math.abs(vint) + Math.abs(dint);
    } else {
      return Math.abs(kint) + Math.abs(vint);
    }
  }

  private int compareOne(Comparable here, Comparable there) {
    int result = 0;
    if (here != null && there != null) {
      result = here.compareTo(there);
    } else if (here != null && there == null) {
      result = 1;
    } else if (here == null && there != null) {
      result = -1;
    }
    return result;
  }
}
