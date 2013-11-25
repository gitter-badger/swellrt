VERSION=0.3.44
ant dist-api dist-libraries dist-proto dist-pst dist-robot-client-api dist-pst-dep dist-pst dist-server-dep dist-server 
for i in `cat kune-artifacts-alone.txt` 
do 
        cd dist
  mvn deploy:deploy-file -DgroupId=org.waveprotocol -DartifactId=$i -Dversion=$VERSION -Dfile=$i.jar -Dpackaging=jar -Durl=http://archiva.comunes.org/repository/comunes-internal/ -DrepositoryId=comunes-internal
  cd ..
done

