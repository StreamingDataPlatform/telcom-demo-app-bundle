#!/usr/bin/env bash

PROJECTNAMEKEY="projectName"
KEY="${PROJECTNAMEKEY}:"
NAMESPACE=`grep -m2 $KEY $(pwd)/../tmp/sample_temp.yaml | tail -n1 | awk '{ print $2}'`
export NAMESPACE
