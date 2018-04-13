#! /bin/bash
set -x

cd ~/Documents/Github/monitor-particle-io						; if [ $? -ne 0 ] ; then exit -6 ; fi
mvn clean install												; if [ $? -ne 0 ] ; then exit -6 ; fi

'/c/Program Files/Java/jdk1.8.0_65/bin/jar.exe'					\
	-cvfm 'target\build-particle-monitor-0.0.1-SNAPSHOT.jar'	\
	'src\main\resources\META-INF\MANIFEST.MF'					\
	'target\classes\io\verdical\monitor\UnitWatcher.class'		; if [ $? -ne 0 ] ; then exit -6 ; fi
java -jar target\\build-particle-monitor-0.0.1-SNAPSHOT.jar		; if [ $? -ne 0 ] ; then exit -6 ; fi
