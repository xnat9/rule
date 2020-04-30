#!/bin/sh
cd `dirname $0`
if [ ! -d "./lib" ]; then
  echo copy dependencies jar
  if [ -d "./gradle-embed" ]; then
    sh ./gradle-embed/bin/gradle clean deps
  else
    gradle clean deps
  fi
fi

echo start...

# 不写两行的话, docker里面执行的时候多个空格分割的参数会报错
JAVA_OPTS=$@
export JAVA_OPTS

cd src
sh ../bin/groovy main.groovy

# nohup sh start.sh [-Dprofile=pro] -Xms128m -Xmx512m > /dev/null 2>&1 &