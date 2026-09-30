#!/bin/zsh

mvn compiler:compile

mvn exec:java -Dexec.args="-e 0 -rn 0 -dn 9 -dt grid3,3 -np 0.4 -ns cp0.0_uniform -f 1 -el 100 -rtt 5 -rd 9"