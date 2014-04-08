Hadoop Configuration Differ
===========================

This tool allows you to read two or more Hadoop configuration files and have it print out the difference between them.

Usage
-----

You build and run it like so:

    $ mvn package
    $ sh target/bin/run-differ

A concrete example might look like this:

    $ sh target/bin/run-differ /hbase-0.92-r1130916/src/main/resources/hbase-default.xml 0.92 /hbase-0.94/src/main/resources/hbase-default.xml 0.94 //hbase-0.96/hbase-common/src/main/resources/hbase-default.xml 0.96

Enjoy!