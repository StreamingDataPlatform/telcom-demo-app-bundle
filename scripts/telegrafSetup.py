#!/usr/bin/env python3

import subprocess
import time
import yaml #pip install pyyaml
import os

input_list = []
stream_name_list = []
stream_input_dictionary = {}
role_list = []
metrics = {}
prometheus_flag = False
svc_ip_list = []
remote_write_list = []

subprocess.run("helm repo add influxdata https://helm.influxdata.com/", capture_output=True,text=True,shell=True)

def str_presenter(dumper, data):
    """configures yaml for dumping multiline strings"""
    if data.count('\n') > 0:  # check for multiline string
        return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='|')
    return dumper.represent_scalar('tag:yaml.org,2002:str', data)

# Function to edit prometheus server config map for k8s data
def editConfigMap():
    os.system("kubectl get configmap prometheus-server -n cluster-monitoring -o yaml > ./../tmp/config.yaml")
    with open(os.path.join(os.path.dirname(__file__), '../tmp', 'config.yaml'),"r") as f:
        configdocs = yaml.safe_load(f)
    f.close()

    yaml.add_representer(str, str_presenter)
    yaml.representer.SafeRepresenter.add_representer(str, str_presenter) # to use with safe_dum

    prometheus_yaml = yaml.safe_load(configdocs['data']['prometheus.yml'])

    for svp_ips in svc_ip_list:
       ip = {'url': f"http://{svp_ips}:8080/receive", 'queue_config': {'max_shards': 3, 'max_samples_per_send': 100}}
       if ip not in remote_write_list:
        remote_write_list.append(ip)
       prometheus_yaml['remote_write'] = remote_write_list
        
    prometheus_yaml_updated = yaml.safe_dump(prometheus_yaml, sort_keys=False)
    configdocs['data']['prometheus.yml'] = prometheus_yaml_updated

    with open(os.path.join(os.path.dirname(__file__), '../tmp', 'config.yaml'),"w") as f:
        yaml.dump(configdocs,f,sort_keys=False)
    f.close()

    os.system("kubectl apply -f ./../tmp/config.yaml")
    

# Read from tmp yaml file
with open(os.path.join(os.path.dirname(__file__), '../tmp', 'sample_temp.yaml'),'r') as sample_file:
    docs = yaml.safe_load(sample_file)
    project_name = docs['global']['projectName']
    username = docs['global']['streams'][0]['outputs'][0]['influxdb']['username']
    password = docs['global']['streams'][0]['outputs'][0]['influxdb']['password']
    secret = docs['global']['streams'][0]['outputs'][1]['http']['headers']['X-Pravega-Secret']
    role_list = docs['global']['project-feature']['ingestgateway']['keycloak']['roles']
    for input in docs['global']['streams'][0]['Inputs']:
        if input not in input_list:
            input_list.append(input)
        else:
            continue
    for input in input_list:
        stream_name = docs['global']['streams'][0]['Inputs'][input]['name']
        if stream_name not in stream_name_list:
            stream_name_list.append(stream_name)
        else:
            continue
        if stream_name not in stream_input_dictionary:
            stream_input_dictionary[stream_name] = docs['global']['streams'][0]['Inputs'][input]['input']
        else:
            continue

        
        with open(os.path.join(os.path.dirname(__file__), '../charts/telegraf-chart/telegraf', 'kubernetes.yaml'),'r') as file:
            kubernetes_docs = yaml.safe_load(file)
            if docs['global']['streams'][0]['Inputs'][input]['prometheusCheck'] == True:
                print(f'{stream_name} Prometheus check is true')
                metrics[stream_name] = kubernetes_docs['metrics']
                prometheus_flag = True
        file.close()

sample_file.close()

# Read from telegraf values file
with open(os.path.join(os.path.dirname(__file__), '../charts/telegraf-chart/telegraf', 'values.yaml'),'r') as file:
    values_docs = yaml.safe_load(file)
    values_docs['config']['outputs'] = docs['global']['streams'][0]['outputs']
file.close()

# Create a new values for the input streams provided which is used by telegraf helm chart to install 
for name in stream_name_list:
    print(f"Setting up stream:{name}............")

    url = f"http://ingest-gateway.{project_name}.svc.cluster.local/v1/scope/{project_name}/stream/{name}/event?routingKeyType=none&addTimestamp=Timestamp"
    values_docs['config']['outputs'][1]['http']['url'] = url
    values_docs['config']['outputs'][1]['http']['headers']['X-Pravega-Client-ID'] = 'default'
    values_docs['config']['inputs'] = stream_input_dictionary[name]
    if name in metrics:
        values_docs['metrics'] = metrics[name]
        values_docs['service']['enabled'] = True
        values_docs['service']['type'] = "LoadBalancer"
        
    with open(os.path.join(os.path.dirname(__file__), '../charts/telegraf-chart/telegraf', f'{name}_values.yaml'),'w+') as dump_file:
        yaml.safe_dump(values_docs, dump_file,sort_keys=False)  
    arg = f'''helm upgrade --install telegraf-{name} -f ./../charts/telegraf-chart/telegraf/{name}_values.yaml ./../charts/telegraf-chart/telegraf/ -n {project_name}'''
    stream = subprocess.run(arg, capture_output=True,text=True,shell=True)
    print(subprocess.run(arg, capture_output=True,text=True,shell=True).stderr)


dump_file.close()

# Check if user want's to push data to prometheus 
if prometheus_flag == True:
    os.system("echo 'Waiting for External IP to be ready...'")
    time.sleep(10)
    arg = f"kubectl get svc -n {project_name}"+" | grep telegraf-| awk '{print $4}'"
    svc_ip = subprocess.run(arg, capture_output=True,text=True,shell=True).stdout.strip()
    svc_ip_list = svc_ip.split()
    editConfigMap()

