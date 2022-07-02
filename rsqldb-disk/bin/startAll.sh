#!/bin/sh

binDir=$(cd `dirname $0`;pwd)
echo "binDir=$binDir"

cd $binDir/..
homeDir=$(pwd)

echo "homeDir=$homeDir"

cd $binDir

chmod +x startTaskExecutor.sh
chmod +x startSqlReceiver.sh



sh startTaskExecutor.sh $homeDir

sh startSqlReceiver.sh $homeDir




