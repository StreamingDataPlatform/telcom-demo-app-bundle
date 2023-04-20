#!/bin/bash

PROJECTNAMEKEY="projectName"
KEY="${PROJECTNAMEKEY}:"
NAMESPACE=`grep -m2 $KEY $(pwd)/sample.yaml | tail -n1 | awk '{ print $2}'`
RELEASENAME="project-release-${NAMESPACE}"

echo "Deleting:" ${RELEASENAME}

helm del telegraf-idrac -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Chart for Telegraf - Idrac. Exit code $le1" >&2
fi

sleep 5

helm del telegraf-k8s -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Chart for Telegraf - K8s. Exit code $le1" >&2
fi

sleep 5

helm del telegraf-vsphere -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Chart for Telegraf - vsphere. Exit code $le1" >&2
fi

sleep 5

helm del map-metrics-grafana-dashboards -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Project Chart. Exit code $le1" >&2
fi

sleep 5


helm del map-metrics -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Chart for Flink Job - InfluxDB. Exit code $le1" >&2
fi

sleep 5

helm del map-metrics-influx -n $NAMESPACE >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Chart for Flink Job - Map-Metrics. Exit code $le1" >&2
fi

echo "deleting all pravega streams"
pushd ./scripts
./pravegaUninstall.sh -s $NAMESPACE delete-streams
sleep 5
popd


helm del $RELEASENAME >/dev/null 2>&1
le1=$?
if [ "$le1" -eq 0 ]; then
    echo "Uninstalling Project"
else
    echo "Could not uninstall Project Chart. Exit code $le1" >&2
fi


while true; do
    kubectl get namespace $NAMESPACE >/dev/null 2>&1
    if [ $? -ne 0 ]
    then
        break
    fi
    echo "Waiting for namespace to be deleted..."
    sleep 10
done

echo "Uninstall Complete"