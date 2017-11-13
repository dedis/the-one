#! /bin/sh
java -Xmx512M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*
#java -Xmx1024M -cp .:lib/ECLA.jar:lib/DTNConsoleConnection.jar core.DTNSim $*
