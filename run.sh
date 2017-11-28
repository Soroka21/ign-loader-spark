#!/bin/bash

echo Starting ignite loader

LIB=$(mapr classpath)
IGNITE_HOME=/opt/mapr/ignite/apache-ignite-2.2.0


source $IGNITE_HOME/bin/include/setenv.sh

echo $IGNITE_LIBS

export CLASSPATH=$LIB:$IGNITE_LIBS:./lib/ignite-console-1.0-SNAPSHOT-all.jar
echo $CLASSPATH

CFG=/opt/mapr/ignite/apache-ignite-2.2.0/config/default-config.xml

/opt/mapr/spark/spark-current/bin/spark-submit \
        --class sncr.xdf.ignite.SparkLoader \
       ./lib/ignite-console-1.0-SNAPSHOT-all.jar "$@"


