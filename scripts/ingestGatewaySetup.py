#!/usr/bin/env python3

import yaml #pip install pyyaml
import os
import sys
import time
import subprocess

mappingDictionary = {}
client_secret_dict = {}
roles_list = []
roles_list_copy = []

# Read from user yaml file
with open(os.path.join(os.path.dirname(__file__), '../', 'sample.yaml'),'r') as sample_file:
    document = yaml.safe_load(sample_file)
    project_name = document['global']['projectName']
    openshift_flag = document['global']['openshift']
    document['global']['streams'][0]['outputs'][0]['influxdb']['url'] = f"http://project-metrics.{project_name}.svc.cluster.local:8086"
    document['global']['streams'][0]['outputs'][1]['http']['url'] = f"http://ingest-gateway.{project_name}.svc.cluster.local/v1/scope/{project_name}/stream/streamName/event?routingKeyType=none&addTimestamp=Timestamp"


    for input in document['global']['streams'][0]['Inputs']:
        if document['global']['streams'][0]['Inputs'][input]['name'] not in roles_list:
            roles_list.append(document['global']['streams'][0]['Inputs'][input]['name'])

    document['global']["project-feature"]['ingestgateway']['keycloak']['roles'] = roles_list
  
    #Copying roles list to aviod YAML Anchors And Aliases
    for role_name in roles_list:
        if role_name not in roles_list_copy:
            roles_list_copy.append(role_name)

    mappingDictionary['default'] = roles_list_copy  
    document['global']["project-feature"]['ingestgateway']['keycloak']['mapping'] = mappingDictionary
  
sample_file.close()

#update yaml file roles 
with open(os.path.join(os.path.dirname(__file__), '../', 'sample.yaml'),'w') as sample_file:
    yaml.safe_dump(document, sample_file, sort_keys= False)
sample_file.close()

#update project values file
with open(os.path.join(os.path.dirname(__file__), '../charts/project-chart', 'values.yaml'),'r') as project_values:
    project_document = yaml.safe_load(project_values)
    project_document['global']['projectName'] = project_name
    project_document['global']['openshift'] = openshift_flag
    project_document['global']['project-feature']['ingestgateway']['keycloak']['roles'] = roles_list
    project_document['global']["project-feature"]['ingestgateway']['keycloak']['mapping'] = mappingDictionary
project_values.close()

with open(os.path.join(os.path.dirname(__file__), '../charts/project-chart', 'values.yaml'),'w') as project_values:
    yaml.safe_dump(project_document, project_values, sort_keys= False)
project_values.close()

os.system("echo 'Installing Project..........................'")

#Install project
arg = f'helm upgrade  --install project-release-{project_name} ./../charts/project-chart/ -f ./../charts/project-chart/values.yaml'
if(subprocess.run(arg, capture_output=True,text=True,shell=True).returncode != 0):
    os.system("echo 'Could not install Project chart'")
    sys.exit(-1)

#Check project Installation
arg = f'''kubectl get namespace {project_name}'''+''' -o jsonpath='{.status.phase}' '''
while(subprocess.run(arg, capture_output=True,text=True,shell=True).stdout != "Active"):
    time.sleep(2)
    os.system("echo 'Waiting for Namespace to be ready...'")

arg = f'''kubectl get pods -l=release='ingest-gateway' -n {project_name}'''+''' -o jsonpath='{.items[*].status.containerStatuses[0].ready}' '''
while(subprocess.run(arg, capture_output=True,text=True,shell=True).stdout != "true"):
    time.sleep(10)
    os.system("echo 'Waiting for Ingest Gateway to be ready...'")

arg = f'''kubectl get pods -l=release='ingest-gateway-haproxy' -n {project_name}'''+''' -o jsonpath='{.items[*].status.containerStatuses[0].ready}' '''
while(subprocess.run(arg, capture_output=True,text=True,shell=True).stdout != "true"):
    time.sleep(10)
    os.system("echo 'Waiting for Ingest Gateway to be ready...'")


arg = f'''kubectl get pods -l=release='zookeeper' -n {project_name}'''+''' -o jsonpath='{.items[*].status.containerStatuses[0].ready}' '''
while("true" not in subprocess.run(arg, capture_output=True,text=True,shell=True).stdout):
    time.sleep(40)
    os.system("echo 'Waiting for Zookeeper to be ready...'")


arg = f'''kubectl get secrets  -n {project_name}'''+''' -o json | jq -r '.items[] | select(.metadata.name | startswith("ingest-gateway-default")).metadata.name' | sed 's/ingest-gateway-//' '''
K8SECRET= subprocess.run(arg, capture_output=True,text=True,shell=True).stdout.strip()



# Get Influx DB secrets, username and password
arg = f'''kubectl get secrets  -n {project_name}'''+''' -o json | jq -r '.items[] | select(.metadata.name | contains("influxdb")).metadata.name' '''
INFLUXDB= subprocess.run(arg, capture_output=True,text=True,shell=True).stdout.strip()




userName_arugment = f'kubectl get secret {INFLUXDB}'+f' -n {project_name}'+' -o jsonpath="{.data.username}" | base64 -d'
INFLUXDBUN = subprocess.run(userName_arugment, capture_output=True,text=True,shell=True).stdout
password_argument = f'kubectl get secret {INFLUXDB}'+f' -n {project_name}'+' -o jsonpath="{.data.password}" | base64 -d'
INFLUXDBPW = subprocess.run(password_argument, capture_output=True,text=True,shell=True).stdout

# Get ingest gateway secret 
arg = f'''kubectl get secret ingest-gateway-{K8SECRET}'''+f''' -n {project_name}'''+''' -o jsonpath="{.data.keycloak\.json}" | base64 -d | jq ."credentials.secret"'''
encrypted_secret = subprocess.run(arg, capture_output=True,text=True,shell=True)
client_secret_dict[K8SECRET] = encrypted_secret.stdout.strip().strip('"')

# Write secrets to tmp file 
os.makedirs("../tmp", exist_ok=True)

with open(os.path.join(os.path.dirname(__file__), '../', 'sample.yaml'),'r') as sample_file:
    secret_document = yaml.safe_load(sample_file)
    for stream in secret_document['global']['streams']:
        stream['outputs'][0]['influxdb']['username'] = INFLUXDBUN
        stream['outputs'][0]['influxdb']['password'] = INFLUXDBPW
        stream['outputs'][1]['http']['headers']['X-Pravega-Client-ID'] = stream['name']
        stream['outputs'][1]['http']['headers']['X-Pravega-Secret'] =  client_secret_dict[stream['name']]
sample_file.close()



with open(os.path.join(os.path.dirname(__file__), '../tmp', 'sample_temp.yaml'),'w') as sample_file:
    yaml.safe_dump(secret_document, sample_file, sort_keys= False)
sample_file.close()

os.system("echo 'Installation Complete......................'")


