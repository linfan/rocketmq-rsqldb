#!/bin/sh
set -e

PROG_NAME=$0
JOB_FILE_PATH=$1
JOB_NAMESPACE=$2
JOB_NAMES=$3
JVM_CONFIG=$4


if [ -z "${JVM_CONFIG}" ]; then
  JVM_CONFIG="-Xms2048m -Xmx2048m -Xss512k"
fi
ROCKETMQ_STREAMS_HOME=$(cd $(dirname ${BASH_SOURCE[0]})/..; pwd)
ROCKETMQ_STREAMS_CONFIGURATION=$ROCKETMQ_STREAMS_HOME/conf
ROCKETMQ_STREAMS_EXT=$ROCKETMQ_STREAMS_HOME/ext
ROCKETMQ_STREAMS_DEPENDENCIES=$ROCKETMQ_STREAMS_HOME/lib
ROCKETMQ_STREAMS_LOGS=$ROCKETMQ_STREAMS_HOME/log/catalina.out

if [ -z "${JAVA_HOME:-}" ]; then
  JAVA="java -server"
else
  JAVA="$JAVA_HOME/bin/java -server"
fi

JAVA_OPTIONS=${JAVA_OPTIONS:-}

JVM_OPTS=()
if [ ! -z "${JAVA_OPTIONS}" ]; then
  JVM_OPTS+=("${JAVA_OPTIONS}")
fi
if [ ! -z "${JVM_CONFIG}" ]; then
  JVM_OPTS+=("${JVM_CONFIG}")
fi

JVM_OPTS+=( "-Dlog4j.configuration=$ROCKETMQ_STREAMS_CONFIGURATION/log4j.xml" )

# shellcheck disable=SC2068
# shellcheck disable=SC2039

if [ ! -z "${JOB_NAMES}" -a ! -z "${JOB_NAMESPACE}" ]; then
  eval exec $JAVA ${JVM_OPTS[@]} -classpath "$ROCKETMQ_STREAMS_DEPENDENCIES/*:$ROCKETMQ_STREAMS_EXT/*:$ROCKETMQ_STREAMS_CONFIGURATION/*" org.apache.rsqldb.runner.SqlAction $JOB_FILE_PATH $JOB_NAMESPACE $JOB_NAMES "&" >>"$ROCKETMQ_STREAMS_LOGS" 2>&1
elif [ ! -z "${JOB_NAMESPACE}" ]; then
  eval exec $JAVA ${JVM_OPTS[@]} -classpath "$ROCKETMQ_STREAMS_DEPENDENCIES/*:$ROCKETMQ_STREAMS_EXT/*:$ROCKETMQ_STREAMS_CONFIGURATION/*" org.apache.rsqldb.runner.SqlAction $JOB_FILE_PATH $JOB_NAMESPACE "&" >>"$ROCKETMQ_STREAMS_LOGS" 2>&1
else
  eval exec $JAVA ${JVM_OPTS[@]} -classpath "$ROCKETMQ_STREAMS_DEPENDENCIES/*:$ROCKETMQ_STREAMS_EXT/*:$ROCKETMQ_STREAMS_CONFIGURATION/*" org.apache.rsqldb.runner.SqlAction $JOB_FILE_PATH "&" >>"$ROCKETMQ_STREAMS_LOGS" 2>&1
fi


