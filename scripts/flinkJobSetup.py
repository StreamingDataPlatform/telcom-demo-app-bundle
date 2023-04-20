#!/usr/bin/env python3

import yaml #pip install pyyaml
import os
import time
import subprocess
import os.path
from os import path
import json

flinkJobs = []


def str_presenter(dumper, data):
    """configures yaml for dumping multiline strings"""
    if data.count('\n') > 0:  # check for multiline string
        return dumper.represent_scalar('tag:yaml.org,2002:str', data, style='|')
    return dumper.represent_scalar('tag:yaml.org,2002:str', data)



# Read from user yaml file
with open(os.path.join(os.path.dirname(__file__), '../tmp', 'sample_temp.yaml'),'r') as sample_file:
    document = yaml.safe_load(sample_file)
    projectName = document['global']['projectName']
    database = document['global']['streams'][0]['outputs'][0]['influxdb']['database']
    storageClassName = document['global']['storageClassName']
sample_file.close()

def runFlinkJobsIfExisting():
    for jobs in document['global']['flinkJobs']:
        if jobs['name'] not in flinkJobs:
            flinkJobs.append(jobs['name'])

    #Map metrics charts update
    for stream in document['global']['streams']:
        INFLUXDBUN = stream['outputs'][0]['influxdb']['username']
        INFLUXDBPW = stream['outputs'][0]['influxdb']['password']

    #updating map-metrics-values.yaml influxdb details
    with open(os.path.join(os.path.dirname(__file__), '../charts/map-metrics', 'map-metrics-values.yaml'),'r') as map_metrics_values_file:
        map_metrics_values_document = yaml.safe_load(map_metrics_values_file)
        map_metrics_values_document['influxdb']['influxDB_username'] = INFLUXDBUN
        map_metrics_values_document['influxdb']['influxDB_password'] = INFLUXDBPW
        map_metrics_values_document['influxdb']['influxDB_database'] = database
        map_metrics_values_document['localStorage']['storageClassName'] = storageClassName
        arg = "kubectl get nodes -o jsonpath='{.items[0].metadata.name}'"
        nodeName = subprocess.run(arg, capture_output=True,text=True,shell=True).stdout
        map_metrics_values_document['appParameters']['nodeName'] = nodeName 
    map_metrics_values_file.close()

    with open(os.path.join(os.path.dirname(__file__), '../charts/map-metrics', 'map-metrics-values.yaml'),'w+') as map_metrics_values_yaml:
        yaml.safe_dump(map_metrics_values_document, map_metrics_values_yaml,sort_keys=False)  
    map_metrics_values_file.close()

    #updating map-metrics-influxdb-sink-values.yaml influxdb details
    with open(os.path.join(os.path.dirname(__file__), '../charts/map-metrics', 'map-metrics-influxdb-sink-values.yaml'),'r') as map_metrics_influxdb_sink_values_file:
        map_metrics__influxdb_sink_values_document = yaml.safe_load(map_metrics_influxdb_sink_values_file)
        map_metrics__influxdb_sink_values_document['influxdb']['influxDB_username'] = INFLUXDBUN
        map_metrics__influxdb_sink_values_document['influxdb']['influxDB_password'] = INFLUXDBPW
        map_metrics__influxdb_sink_values_document['influxdb']['influxDB_database'] = database
        map_metrics__influxdb_sink_values_document['localStorage']['storageClassName'] = storageClassName
    map_metrics_influxdb_sink_values_file.close()

    with open(os.path.join(os.path.dirname(__file__), '../charts/map-metrics', 'map-metrics-influxdb-sink-values.yaml'),'w+') as map_metrics_influxdb_sink_values_yaml:
        yaml.safe_dump(map_metrics__influxdb_sink_values_document, map_metrics_influxdb_sink_values_yaml,sort_keys=False)  
    map_metrics_influxdb_sink_values_yaml.close()

    #updating grafana values file
    with open(os.path.join(os.path.dirname(__file__), '../charts/grafana-dashboards', 'values.yaml'),'r') as grafana_values_file:
        grafana_values_document = yaml.safe_load(grafana_values_file)
        grafana_values_document['namespace'] = projectName
        grafana_values_document['datasource'] = database
    grafana_values_file.close()

    with open(os.path.join(os.path.dirname(__file__), '../charts/grafana-dashboards', 'values.yaml'),'w+') as grafana_values_yaml:
        yaml.safe_dump(grafana_values_document, grafana_values_yaml,sort_keys=False)
    grafana_values_yaml.close()
    
    #updating grafana-influxdbsource.yaml influxdb details
    yaml.add_representer(str, str_presenter)
    yaml.representer.SafeRepresenter.add_representer(str, str_presenter) # to use with safe_dump
    
    with open(os.path.join(os.path.dirname(__file__), '../charts/grafana-dashboards', 'grafana-influxdbsource.yaml'),'r') as grafana_influxdbsource_file:
        grafana_influxdbsource_document = yaml.safe_load(grafana_influxdbsource_file)
        datasources = grafana_influxdbsource_document['data']['datasources.yaml']

        with open(os.path.join(os.path.dirname(__file__), '../tmp', 'datasources.yaml'),"w+") as f:
            f.write(datasources)
        f.close()

        with open(os.path.join(os.path.dirname(__file__), '../tmp', 'datasources.yaml'),"r") as test:
            test_doc = yaml.safe_load(test)
            for data_source in test_doc['datasources']:
                data_source['user'] = INFLUXDBUN
                data_source['secureJsonData']['password'] = INFLUXDBPW
                data_source['database'] = database
                data_source['name'] = database
        test.close()

        test_doc_string = json.dumps(test_doc)
        grafana_influxdbsource_document['data']['datasources.yaml'] = test_doc_string
        grafana_influxdbsource_document['metadata']['namespace'] = projectName

    grafana_influxdbsource_file.close()
    
    with open(os.path.join(os.path.dirname(__file__), '../charts/grafana-dashboards', 'grafana-influxdbsource.yaml'),'w+') as grafana_influxdbsource_yaml:
        yaml.safe_dump(grafana_influxdbsource_document, grafana_influxdbsource_yaml,sort_keys=False)
    grafana_influxdbsource_yaml.close()

    
    #Run flink Jobs
    for job in flinkJobs:
        if path.isfile(f'{job}.sh') == True:
            arg = f'./{job}.sh'
            subprocess.run(arg,capture_output=True,text=True,shell=True)
            os.system(f"echo 'Running job: {job}'")
            time.sleep(5)
        else:
            os.system(f"echo 'Could not find Job called {job}'")
            continue


if document['global']['flinkJobs'] == None:
    os.system(f"echo 'No Flink Jobs Added by User'")
else:
    #Run publish script before running Jobs
    subprocess.run(['./publish.sh'])
    time.sleep(5)
    runFlinkJobsIfExisting()