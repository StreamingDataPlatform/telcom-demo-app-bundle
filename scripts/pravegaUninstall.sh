#!/usr/bin/env bash

usage() {
  echo "Manage Pravega streams on SDP" 1>&2
  echo 1>&2
  echo "Usage:" 1>&2
  echo "  $0 -s <scope> list-streams <stream>" 1>&2
  echo "  $0 -s <scope> delete-stream <stream>" 1>&2
  echo "  $0 -s <scope> delete-streams \"<stream regex>\"" 1>&2
  echo "  $0 -s <scope> delete-reader-group <reader group>" 1>&2
  echo 1>&2
  echo "This tool requires kubectl, curl, and jq." 1>&2
  exit 1
}

# Find the pravega controller authentication details
_get_pravega_cred(){
   un_pwd="`kubectl get pravegaCluster -n nautilus-pravega -o yaml | grep -i pravega.client.auth.token | awk '{ print $2; }' | base64 -d`"
}

# Find the pravega controller URL
_get_pravega_controller_url(){
  # TLS Enabled
  pravega_url=`kubectl get ingress -n nautilus-pravega pravega-controller-api | grep pravega-controller | awk '{ print $3; }'`
  if [[ -z "$pravega_url" ]]; then
      echo "Not Found TLS enabled Pravega URL ${pravega_url}" >&2
      # TLS Disabled
      pravega_url=(`kubectl get -n nautilus-pravega svc nautilus-pravega-controller \
        -o go-template=$'{{index .metadata.annotations "external-dns.alpha.kubernetes.io/hostname"}}\n'`)
      pravega_url=http://${pravega_url}:9090/v1
      echo "Found TLS disabled Pravega URL ${pravega_url}" >&2
  else
     pravega_url=https://${pravega_url}/v1
  fi
  echo "Pravega URL: ${pravega_url}" >&2
}

_list_streams(){
  curl -k -s -u $un_pwd -X GET ${pravega_url}/scopes/${scope}/streams | \
    jq -r '.streams[].streamName'
}

_delete_stream(){
  stream_name=$1
  echo "Deleting stream ${scope}/${stream_name}"
  # Seal stream
  curl -k -u $un_pwd -X PUT -H "Content-Type: application/json" -d '{"streamState":"SEALED"}' \
      ${pravega_url}/scopes/${scope}/streams/${stream_name}/state
  echo
  # Delete stream
  CODE=$(curl -ksSL -w "%{http_code}" -o /dev/null -u $un_pwd -X DELETE ${pravega_url}/scopes/${scope}/streams/${stream_name})
  if [[ "$CODE" == "204" ]]; then
    echo "Stream ${scope}/${stream_name} deleted successfully"
  elif [[ "$CODE" == "404" ]]; then
    echo "Stream ${scope}/${stream_name} not found"
  else
    echo "Error ${CODE} deleting stream ${scope}/${stream_name}"
    exit 1
  fi
}

_delete_streams_regex(){
  stream_name_regex=$1
  stream_names=$(_list_streams)
  echo "deleting streams"
  for stream_name in ${stream_names}; do
    _delete_stream ${stream_name}
  done
}

_delete_reader_group(){
  _delete_stream _RG${1}
}

while getopts ":s:" o; do
    case "${o}" in
        s)
            scope=${OPTARG}
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ "$1" == "list-streams" ]; then
  _get_pravega_cred
  _get_pravega_controller_url
  _list_streams
elif [ "$1" == "delete-stream" ]; then
  _get_pravega_cred
  _get_pravega_controller_url
  _delete_stream $2
elif [ "$1" == "delete-streams" ]; then
  _get_pravega_cred
  _get_pravega_controller_url
  _delete_streams_regex $2
elif [ "$1" == "delete-reader-group" ]; then
  _get_pravega_cred
  _get_pravega_controller_url
  _delete_reader_group $2
else
  usage
fi