#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.

set -e
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}

# Start a job
VALUES_FILE=${ROOT_DIR}/charts/grafana-dashboards/values.yaml
CM_FILE=${ROOT_DIR}/charts/grafana-dashboards/grafana-influxdbsource.yaml

export RELEASE_NAME=map-metrics-grafana-dashboards
echo ${RELEASE_NAME}

kubectl apply -f ${CM_FILE} -n ${NAMESPACE} 
kubectl rollout restart sts/project-metrics-grafana -n ${NAMESPACE} 

#deploy map metrics flink application
helm upgrade --install \
    ${RELEASE_NAME} \
    ${ROOT_DIR}/charts/grafana-dashboards \
    --namespace ${NAMESPACE} \
    -f "${VALUES_FILE}" \
    $@