#! /bin/bash
set -x

pushd ~/Documents/Github/monitor-particle-io					; if [ $? -ne 0 ] ; then exit -6 ; fi
mvn clean install												; if [ $? -ne 0 ] ; then exit -6 ; fi

jcp='target\build-particle-monitor-0.0.1-SNAPSHOT.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\seleniumhq\selenium\selenium-api\3.11.0\selenium-api-3.11.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\seleniumhq\selenium\selenium-chrome-driver\3.11.0\selenium-chrome-driver-3.11.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\seleniumhq\selenium\selenium-remote-driver\3.11.0\selenium-remote-driver-3.11.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\seleniumhq\selenium\selenium-support\3.11.0\selenium-support-3.11.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\net\bytebuddy\byte-buddy\1.7.9\byte-buddy-1.7.9.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\apache\commons\commons-exec\1.3\commons-exec-1.3.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\google\code\gson\gson\2.8.2\gson-2.8.2.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\google\code\findbugs\jsr305\1.3.9\jsr305-1.3.9.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\checkerframework\checker-compat-qual\2.0.0\checker-compat-qual-2.0.0.jar'\
jcp=${jcp}';\Users\Chris\.m2\repository\com\google\errorprone\error_prone_annotations\2.1.3\error_prone_annotations-2.1.3.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\google\j2objc\j2objc-annotations\1.1\j2objc-annotations-1.1.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\codehaus\mojo\animal-sniffer-annotations\1.14\animal-sniffer-annotations-1.14.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\apache\httpcomponents\httpclient\4.5.3\httpclient-4.5.3.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\org\apache\httpcomponents\httpcore\4.4.6\httpcore-4.4.6.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\google\guava\guava\23.0\guava-23.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\squareup\okhttp3\okhttp\3.9.1\okhttp-3.9.1.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\com\squareup\okio\okio\1.13.0\okio-1.13.0.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\commons-io\commons-io\2.6\commons-io-2.6.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\commons-codec\commons-codec\1.10\commons-codec-1.10.jar'
jcp=${jcp}';\Users\Chris\.m2\repository\commons-logging\commons-logging\1.2\commons-logging-1.2.jar'

java -cp $jcp io.verdical.monitor.UnitWatcher
popd
