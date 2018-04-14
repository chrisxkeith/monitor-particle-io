#! /bin/bash
set -x

pushd ~/Documents/Github/monitor-particle-io					; if [ $? -ne 0 ] ; then exit -6 ; fi
mvn clean install												; if [ $? -ne 0 ] ; then exit -6 ; fi
java -cp \
	'target\build-particle-monitor-0.0.1-SNAPSHOT.jar;\Users\Chris\.m2\repository\org\seleniumhq\selenium\selenium-api\3.11.0\*' \
	io.verdical.monitor.UnitWatcher
popd
