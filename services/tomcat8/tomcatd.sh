#!/bin/bash

# Modified per pulsar-system-configure.sh

NAME=tomcat8
DEFAULT=/etc/default/$NAME

export JAVA_OPTS="-Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true -Xms1024m -Xmx1024m -XX:MaxPermSize=256m -XX:+UseConcMarkSweepGC"
export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
export CATALINA_HOME=/usr/share/$NAME
export CATALINA_BASE=/var/lib/$NAME
#export CATALINA_PID=/tmp/$$
export CATALINA_PID=/var/run/$NAME.pid

# overwrite settings from default file (pulsar-system-configure.sh sets this per templates)
#if [ -f "$DEFAULT" ]; then
#	. "$DEFAULT"
#fi

if [ -r /etc/default/locale ]; then
	. /etc/default/locale
	export LANG
fi


# Source: https://confluence.atlassian.com/plugins/viewsource/viewpagesrc.action?pageId=252348917
function shutdown()
{
    date
    echo "Shutting down Tomcat"
    rm -f $CATALINA_PID
    unset CATALINA_PID # Necessary in some cases
    unset LD_LIBRARY_PATH # Necessary in some cases
    unset JAVA_OPTS # Necessary in some cases

    $CATALINA_HOME/bin/catalina.sh stop
}

date
echo "Starting Tomcat"


. $CATALINA_HOME/bin/catalina.sh start

# Allow any signal which would kill a process to stop Tomcat
trap shutdown HUP INT QUIT ABRT KILL ALRM TERM TSTP

PID=`cat $CATALINA_PID`
echo "Waiting for Tomcat (PID $PID)...."
wait $PID

