#!/usr/bin/env bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.

# Build and publish the Java JAR to the Maven repo on SDP.

set -e
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh

# If not specified, get Maven repo parameters using kubectl.
if [[ $(kubectl get ing -n ${NAMESPACE} repo -o jsonpath="{.spec.tls}") == "" ]]; then
    export MAVEN_PROTOCOL=http
else
    export MAVEN_PROTOCOL=https
fi
REPO_ING=$(kubectl get ing -n ${NAMESPACE?"You must export NAMESPACE"} repo -o jsonpath='{.spec.rules[0].host}')
REPO_IP=$(kubectl get svc   nginx-ingress-controller -n nautilus-system -o jsonpath='{.status.loadBalancer.ingress[0].ip}')
if grep -q $REPO_ING /etc/hosts; then
    sudo sed -i "s/*${REPO_ING}/5i$REPO_IP $REPO_ING/g" /etc/hosts
else
    sudo sed -i "5i$REPO_IP $REPO_ING" /etc/hosts
fi

export MAVEN_URL="${MAVEN_URL:-${MAVEN_PROTOCOL}://$(kubectl get ing -n ${NAMESPACE?"You must export NAMESPACE"} repo -o jsonpath='{.spec.rules[0].host}')/maven2}"
export MAVEN_USERNAME="${MAVEN_USERNAME:-desdp}"
export MAVEN_PASSWORD="${MAVEN_PASSWORD:-$(kubectl get secret keycloak-${MAVEN_USERNAME}-creds -n nautilus-system -o jsonpath='{.data.desdp-password}' | base64 -d)}"
export APP_ARTIFACT=${ROOT_DIR}/telcom-demo/flinkprocessor/build/libs/flinkprocessor-${APP_VERSION}.jar
export APP_GROUP_ID
export APP_ARTIFACT_ID
export APP_VERSION


echo publish.sh: Building and publishing from source
cd ${ROOT_DIR}/telcom-demo
./gradlew publish ${GRADLE_OPTIONS}


echo publish.sh: Successfully published to ${MAVEN_URL}
