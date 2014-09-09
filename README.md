# Hadoop Configuration Differ

This projects offers a few tools to figure out some information about Hadoop configuration files.
You can apply them to Hadoop itself, as well as HBase and any other project that uses the XML based
Hadoop style configuration files.

You build the project like so:

```
$ mvn package
```

Note: You need Java 7 to compile the project, since it uses the `PathMatcher` class.

## Tool: ConfigDiffer

This tool allows you to read two or more Hadoop configuration files and have it print out the
difference between them.

### Usage

You run it like so to get all parameters:

```
$ sh target/bin/run-differ -h
Usage: ConfigDiffer [options] <filename1> <version1> <filename2> <version2> ...
  Options:
    -h, --help   Print this help
                 Default: false

```

A concrete example might look like this:

```
$ sh target/bin/run-differ /hbase-0.92-r1130916/src/main/resources/hbase-default.xml 0.92 \
  /hbase-0.94/src/main/resources/hbase-default.xml 0.94 \
  /hbase-0.96/hbase-common/src/main/resources/hbase-default.xml 0.96
```

Here we compare a couple of HBase versions against each other. The output will list which
properties have been added, renamed, or removed in what version. The renaming is based on the
assumption that the description stayed the same. If that is not the case the properties will
simply show up in the added and removed sections respectively.

## Tool: FindProperties

If you need to find out which properties are used in the source code of a project, or more
interestingly, which are not named in a accompanying configuration file, then this is the tool
for you. It scans a given directory tree and greps the matching file types for properties,
looking like "first.second.third.fourth". You can specify the minimum number of parts a
property should have to be recognized (default is 3).

### Usage

A concrete example might look like this:

```
$ sh target/bin/run-finder -d /hbase/hbase-0.92-rw -u -s --exclude **/generated \
  --exclude **/generated/** --exclude **/target --exclude **/test/** \
  -c src/main/resources/hbase-default.xml -o /dev/null
```

In this example we ignore the actual list of properties found by redirecting it to `/dev/null`.
What we receive on the console is the list of all properties that are _hidden_, i.e. not listed in
the given configuration file.

The `--exclude` parameters help excluding the autogenerated class files for Thrift etc. It is
implemented using the `PathMatcher` class and the `glob` syntax. See the online
[PathFinder](http://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String))
help for the full supported syntax.

Run the command with the `--help` (or `-h`) parameter to see all possible options:

```
$ sh target/bin/run-finder -h
Usage: FindProperties [options]
  Options:
    -c, --config       Name of config file to check against
        --debug        Debug mode
                       Default: false
  * -d, --directory    The directory to scan
        --emitAsXml    Emit all found properties as an Hadoop XML configuration
                       Default: false
        --exclude      Exclude the given directory.
                       Default: []
    -e, --expression   Custom regular expression
    -h, --help         Print this help
                       Default: false
    -n, --numfields    Minimum number of fields to identify property
                       Default: 3
    -o, --outputFile   Write output to the specified file, not to the console
        --printFiles   Print files with matches
                       Default: false
    -s, --sorted       Show results sorted
                       Default: false
    -p, --threads      Number of threads to use
                       Default: 3
    -t, --types        Space separated list of file types to scan, e.g. "java
                       xml". Try one or more of these: java, xml, java_code, java_all,
                       or any (matches all files)
                       Default: [java]
    -u, --unique       Show only unique results
                       Default: false
    -v, --verbose
                       Default: false
```

Enjoy!