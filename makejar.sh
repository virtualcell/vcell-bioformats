#!/usr/bin/env bash

mvn clean install assembly:single

echo
echo "----------Dont Forget to update the following with the new bioformats jar name----------"
echo "  {vcellroot}/vcell.sh"
echo "    -Dvcell.bioformatsJarFileName=vcell-bioformats-x.x.x-jar-with-dependencies.jar"
echo "    -Dvcell.bioformatsJarDownloadURL=http://vcell.org/webstart/vcell-bioformats-x.x.x-jar-with-dependencies.jar"
echo "  all README_xxx.md files that make reference to old jar anem"
echo "  {vcellroot}/docker/swarm/serverconfig-uch.sh"
echo "    VCELL_BIOFORMATS_JAR_FILE=vcell-bioformats-x.x.x-jar-with-dependencies.jar"
echo "    VCELL_BIOFORMATS_JAR_URL=http://vcell.org/webstart/vcell-bioformats-x.x.x-jar-with-dependencies.jar"
echo "  {vcellroot}/docker/build/Dockerfile-clientgen-dev (compiler_bioformatsJarFile and compiler_bioformatsJarDownloadURL)"
echo "    compiler_bioformatsJarFile=vcell-bioformats-x,x,x-jar-with-dependencies.jar"
echo "    compiler_bioformatsJarDownloadURL=http://vcell.org/webstart/vcell-bioformats-0.0.4-jar-with-dependencies.jar"
echo "  Copy new bioformats jar to http://vcell.org/webstart"
echo "    /share/apps/vcell3/apache_webroot/htdocs/webstart"
echo "----------------------------------------------------------------------------------------"

