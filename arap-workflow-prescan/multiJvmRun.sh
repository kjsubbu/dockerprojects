#!/bin/bash

# Run 4 PreScan JVMs
# TODO: Modify to support N JVMS for any N.

PSbucket=$1
PSWFC=`expr $PSbucket - 1`

for i in `seq 0 $PSWFC`;
do
   echo "Starting JVM $i"
   ./run.sh $i $PSbucket &
done

#for ind in 0 1 2 3
#do
#	echo "Starting JVM $ind"
#	./run.sh $ind 4 &
#done

