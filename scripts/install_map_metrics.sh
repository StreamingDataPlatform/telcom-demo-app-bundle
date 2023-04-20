#! /bin/bash
# Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.

set -e
ROOT_DIR=$(readlink -f $(dirname $0)/..)
source ${ROOT_DIR}/scripts/env.sh
: ${NAMESPACE?"You must export NAMESPACE"}

# Start a job
MM_VALUES_FILE=${ROOT_DIR}/charts/map-metrics/map-metrics-values.yaml
MMI_VALUES_FILE=${ROOT_DIR}/charts/map-metrics/map-metrics-influxdb-sink-values.yaml

export MM_RELEASE_NAME=map-metrics
echo ${MM_RELEASE_NAME}

export MMI_RELEASE_NAME=map-metrics-influx
echo ${MMI_RELEASE_NAME}


#deploy map metrics flink application
helm upgrade --install \
    ${MM_RELEASE_NAME} \
    ${ROOT_DIR}/charts/map-metrics \
    --namespace ${NAMESPACE} \
    -f "${MM_VALUES_FILE}" \
    $@

#deploy map metrics influxDB flink application
helm upgrade --install \
    ${MMI_RELEASE_NAME} \
    ${ROOT_DIR}/charts/map-metrics \
    --namespace ${NAMESPACE} \
    -f "${MMI_VALUES_FILE}" \
    $@