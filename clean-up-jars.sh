#! /bin/bash
set -x
IFS="
"
pushd ~/.m2/repository/org/seleniumhq/selenium/			; if [ $? -ne 0 ] ; then exit -6 ; fi
for v in 3\.1\.0 3\.4\.0 3\.5\.3 ; do
	dirs=`find . -name $v`								; if [ $? -ne 0 ] ; then exit -6 ; fi
	if [ "$dirs" != "" ] ; then
		for d in $dirs ; do
			rm -rf $d									; if [ $? -ne 0 ] ; then exit -6 ; fi
		done
		dirs=`find . -name $v`							; if [ $? -ne 0 ] ; then exit -6 ; fi
		for d in $dirs ; do
			ls $d
		done
	fi
done
popd
