#!/bin/bash

set -e

#Environment setup
pip install -r requirements.txt

PROJECTNAMEKEY="projectName"
KEY="${PROJECTNAMEKEY}:"
export NAMESPACE=`grep -m2 $KEY $(pwd)/sample.yaml | tail -n1 | awk '{ print $2}'`


#Execute scripts
pushd ./scripts
./ingestGatewaySetup.py


echo 'Setting up telegraf streams................'
sleep 30


./telegrafSetup.py
popd

export REPO_ING=$(kubectl get ing -n ${NAMESPACE?"You must export NAMESPACE"} repo -o jsonpath='{.spec.rules[0].host}')

#import Java Certs
echo -n | openssl s_client -connect $REPO_ING:443 | sed -ne '/-BEGIN CERTIFICATE-/,/-END CERTIFICATE-/p' > sdp_repo.crt

#Uncomment when running install script again
#sudo keytool -delete -noprompt -alias sdp-repo  -keystore /etc/ssl/certs/java/cacerts -storepass changeit
sudo keytool -import -trustcacerts -keystore /etc/ssl/certs/java/cacerts -storepass changeit -noprompt -alias sdp-repo -file sdp_repo.crt
sleep 15

#clean gradle
echo 'cleaning gradle shadowJar..........'
pushd ./telcom-demo
./gradlew -stop 
./gradlew clean shadowJar
popd

echo 'Publishing Jar and setting up Flink Jobs..........'
pushd ./scripts
./flinkJobSetup.py
popd




