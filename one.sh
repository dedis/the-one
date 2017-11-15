#! /bin/sh
# Original command
#java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*
java -Xmx1024M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar:lib/commons-compress-1.15.jar:lib/xz.jar core.DTNSim $*
