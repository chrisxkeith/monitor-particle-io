#! /bin/bash
set -x

echo "Still get NoClassDefFoundError"
exit -666

pushd ~/Documents/Github/monitor-particle-io					; if [ $? -ne 0 ] ; then exit -6 ; fi
mvn clean install												; if [ $? -ne 0 ] ; then exit -6 ; fi
ls -l target/build-particle-monitor-0.0.1-SNAPSHOT.jar			; if [ $? -ne 0 ] ; then exit -6 ; fi
rm -f target/build-particle-monitor-0.0.1-SNAPSHOT.jar			; if [ $? -ne 0 ] ; then exit -6 ; fi

pushd target/classes											; if [ $? -ne 0 ] ; then exit -6 ; fi
'/c/Program Files/Java/jdk1.8.0_65/bin/jar.exe'					\
	-cvfm '..\..\target\build-particle-monitor-0.0.1-SNAPSHOT.jar'	\
	'..\..\src\main\resources\META-INF\MANIFEST.MF'					\
	'io\verdical\monitor\UnitWatcher.class'						; if [ $? -ne 0 ] ; then exit -6 ; fi

popd															; if [ $? -ne 0 ] ; then exit -6 ; fi
ls -l target/build-particle-monitor-0.0.1-SNAPSHOT.jar			; if [ $? -ne 0 ] ; then exit -6 ; fi
java -jar 'target\build-particle-monitor-0.0.1-SNAPSHOT.jar'	; if [ $? -ne 0 ] ; then exit -6 ; fi
popd
