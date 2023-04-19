# Observability App bundle: Customer Jumpstart on SDP Demo 
The following Streaming Data Platform (SDP) application example showcases how enterprises with similar complex real-time systems with many interconnected microservices can use SDP to ingest many different data streams in parallel, do data transformations and drive real-time dashboards and alerts.

The included code sample lets the user run an install script to easily setup a project on SDP with their preferred Pravega streams and Flink jobs they want to run.
This example can easily be expanded to do correlation of events across those streams and do anomaly detection for unusual events or patterns that occur, even across completely unrelated systems.

![Observability demo app](https://user-images.githubusercontent.com/112410039/233161838-263a64cf-9b7b-4c7a-b54c-79e529e603aa.png)

This example will use data streams that are typically available for many enterprises to show how this process is done: iDRAC telemetry, Kubernetes metrics and vSphere data. But there is nothing specific about these streams, you can alter this demo to include you need for your specific challenge.


## Design/Detail

![Architecture design of Code Sample](https://user-images.githubusercontent.com/112410039/233161886-25c7ebf0-2cf9-4a2c-8a29-06f9ffdb8f18.png)


*<p align="center">Figure 1: Architecture design of Code Sample</p>*

This high-level diagram highlighted in red shows all the components which are used in this sample application.
## Components Overview:
- The first component within the green box represents the data sources which include iDRAC, VMware, and k8s data on groups of servers.
- The second component, from the left (designated in yellow) is the Telegraf agent which is used to collect the data and output it to the SDP REST gateway.
- The third component (colored in Teal) is the Pravega REST gateway which is a REST endpoint that receives data from Telegraf and writes to Pravega streams.
- The fourth component next (in blue), represents the Pravega streams which are used to read continuous data.
- The fifth component (with a light grey background) is the Apache Flink engine which is used to do some data analysis and transformations. While we use Flink here the user can swap out Apache Spark or Gstreamer based on their specific use-case and input data streams.
- Finally, the last components represent where we are sending our results of the analysis and data transformations from Flink, in this case InfluxDB database with Grafana to view the data. Many times, this could be another Pravega Stream that the results are sent to, allowing for a multi-step pipeline of activities where data is ingested, transformed, and analyzed as a sequence of steps.

The data source and Telegraf agent is set outside of SDP and the Pravega ingest rest gateway, Pravega streams, Apache Flink, InfluxDB and Grafana are all part of SDP.

The End-to-End flow of this code sample can be thought of like this:

1. First the Telegraf agent collects data from different input systems which the user can configure.
2. The Telegraf agent then outputs the data to the Pravega rest gateway, which is a rest API endpoint.
3. The Pravega REST gateway receives the data and writes it into the individual Pravega streams.
4. After deploying our example transformation Flink Jobs (can be anything provided by the user), the Flink jobs read the data from the Pravega streams and execute the Job.
5. In this example, the example job provided by the application will read the stream data and run the map metrics job and then write the results out to Influx and create a new stream with combined idrac, vsphere and k8s data.
6. Finally, the results can be shown on the Grafana dashboards (deployed by the sample code)


## Code Sample Overview:
- The `install.sh` shell script sets up an SDP project with ingest-gateway. Next, it sets up telegraf agents to collect data from the input provided and output data to influxDB and pravega ingest-gateway. Next, it imports java trustca certs into keystore. Next, it cleans gradle shadowJar and publishes the flinkprocessor jar file onto SDP maven artifacts. Finally, it deploys the flink jobs map-metrics and map-metrics-influx and installs grafana dashboards to view on SDP. These scripts which  the `install.sh` shells script executes  are inside the scripts folder.
- The `sample.yaml` file is the main yaml file for user customizations. This is where the user will add inputs and outputs for telegraf to get data and output to pravega streams. This file is also where the user can add flink jobs to run. 
- The `uninstall.sh` shell script stops the 3 streams idrac, vsphere, and k8s. Then deletes map-metrics-grafana-dashboards, map-metrics, and map-metrics-influx. Finally, it uninstalls the pravega streams and deletes the SDP  project.

### Charts Folder
Inside the charts folder, there are multiple charts used by helm to install the corresponding charts onto Kubernetes.
- The project-chart folder has the chart to install an SDP project with ingest gateway. 
- The telegraf-chart folder has the chart to install telegraf to collect and output data to pravega. 
- The map-metrics folder has a chart that sinks a Pravega stream to an InfluxDB instance in SDP. 
- The grafana-dashboards folder has a chart that creates grafana dashboards from the InfluxDB instance in SDP.

### Scripts Folder
Inside the scripts folder, there are multiple scripts that are used in the `install.sh` script.
- The `ingestGatewaySetup.py` is the first script that is used to read the `sample.yaml`. This script updates the roles and mapping for ingest gateway in that file with the stream names. It also updates the values for the project-chart to create the correct roles and mapping for ingest gateway in the SDP project. It then installs the chart using helm and creates a new temp sample yaml file with all the pravega secrets and influxdb username and password.
- The `telegrafSetup.py` is the second script that is used to create telegraf helm charts for each stream input. It then install the charts which lets telegraf output the data to pravega ingest-gateway in SDP. It also pushes the Kubernetes data to Prometheus.
- The `env-local.sh` shell script is used to get the project name from the user `sample.yaml` file.
- The `env.sh` shell script is used to set the APP name, group id, artifact id, version, and gradle options.
- The `publish.sh` shell script is used to publish the maven artifact jar file to SDP using gradle.
- The `flinkJobSetup.py` script is used to update map-metrics-chart and grafana-dashboard chart's influxDB details. It also runs the scripts `install_map_metrics_dashboards.sh` and `install_map_metrics_final.sh` which are provided in the `sample.yaml` file under flinkJobs.
- The `install_map_metrics_dashboards.sh` shell script is used by helm to install grafana dashboards.
- The `install_map_metrics_final.sh` shell script is used by helm to install the Flink jobs map metrics flink application and map metrics influxDB flink application.
- The `pravegaUninstall.sh` shell script is used by the `uninstall.sh` shell script to delete pravega streams using pravega's rest API's.

## Quick Start Guide
### Prerequisites:
- Python 3 
- Helm v2 or later
- Gradle 7.2
- SDP installed and setup
- Java 11

## Preparation of User master yaml file:

The user master yaml file is the main file used to setup everything for this project for the user. This yaml file contains multiple options that users can customize for the project. To view the master yaml file go into the **telcom-demo-app-bundle** folder and open the **sample.yaml** file. Areas where the user can customize the master yaml file are:

1. Project Name: The user can change the project name to their choice. The default project name provided is **examples**. Important naming convention: The name can be a max length of 15 characters, consist of lower-case alphanumeric characters or '-', and must start and end with an alphanumeric character.

2. Storage Class Name: The user **must** change the storage class name to their storage class name on kubernetes. To find out your default storageClassName run **kubectl get storageclass**
```
global:
  projectName: examples # User can change to any project name default project name is examples 
  openshift: false
  project-feature:
    ingestgateway:
      keycloak:
        roles:
        clients:
        - default
        mapping:
  storageClassName: standard
```

*<p align="center">Figure 2: User Master Yaml file Project Name</p>*

3. Data Sinks: The user can add outputs as data sinks which Telegraf can output data to. Default outputs are set to **influxDB**, and HTTP is used by the Pravega rest gateway. To add new outputs, see the Telegraf output plugins. 
at [https://docs.influxdata.com/telegraf/v1.14/plugins/plugin-list/](https://docs.influxdata.com/telegraf/v1.14/plugins/plugin-list/).

```
  streams:
  - name: default
    outputs: # Users can add outputs. Default outputs set to influxDB and SDP Pravega rest gateway 
    - influxdb:
        url: http://project-metrics.newproject.svc.cluster.local:8086
        database: examples
        username: default
        password: default
    - http:
        url: http://ingest-gateway.newproject.svc.cluster.local/v1/scope/examples/stream/streamName/event?routingKeyType=none&addTimestamp=Timestamp
        timeout: 5s
        method: POST
        data_format: json
        headers:
          Content-Type: application/json; charset=utf-8
          X-Pravega-Client-ID: default
          X-Pravega-Secret: default
```

*<p align="center">Figure 3: Telegraf Outputs</p>*

4. Data Sources: The user can add inputs as data sources which Telegraf can read data from. The default inputs in this project **must be changed** by the user to point to their data sources. This project implements 3 inputs as default. They are **k8s**,**idrac** and **vsphere**, shown in Figure 4, 5 and 6. The input format is shown in Figure 8 below.

```
    Inputs:
      input1:
      input2:
      input3:
        name: k8s
        prometheusCheck: true
        input:
        - http_listener_v2:
            service_address: :8080
            paths:
            - /receive
            data_format: prometheusremotewrite
```
*<p align="center">Figure 4: Input with stream name k8s, push data to prometheus, and data source input is http listener</p>*


```
    Inputs:
      input1:
        name: idrac
        prometheusCheck: false
        input:
        - snmp:
            agents:
            - 10.243.61.74
            - 10.243.61.75
            - 10.243.61.77
            - 10.243.61.78
            - 10.243.61.79
            - 10.243.61.80
            - 10.243.61.81
```

*<p align="center">Figure 5: Input with stream name idrac, Not pushing data to prometheus, and data source input is snmp agents</p>*


```
    Inputs:
      input1:
      input2:
        name: vsphere
        prometheusCheck: false
        input:
        - vsphere:
            insecure_skip_verify: true
            vcenters:
            - https://10.243.61.115/sdk
            username: administrator@vsphere.local
            password: Testvxrail123!
```

*<p align="center">Figure 6: Input with stream name vsphere, Not pushing data to prometheus, and data source input is vsphere vcenter</p>*


5. Flink Jobs: The user can add the Flink Jobs they want to run. The default Flink jobs are **install_map_metrics** and **install_map_metrics_dashboards**, which create Grafana dashboards shown in Figure 7. These Flink jobs are written in Java. To view them, go into the folder **flinkprocessor/src/main/java/io/pravega/flinkprocessor**. The input format is shown in Figure 9 below.

```
  flinkJobs:
  - name: install_map_metrics
  - name: install_map_metrics_dashboards
```

*<p align="center">Figure 7: Default Flink Jobs for this project</p>*

### Steps of Installation:

1. **Setting up the code sample:**
    1. Clone the GitHub repo on your local machine.
    2. Go into the **telcom-demo-app-bundle** folder and open the **sample.yaml** file.
    3. Under **global.streams[0].Inputs** change the inputs to your data sources, use current Inputs as reference. For each input include the name of the stream, prometheusCheck, and input to your data source. An example of an input template is:

```
input<number>:
        name: <streamName>
        prometheusCheck: <true or false> # true if you want to push data to prometheus
        input: <Input to your datasource>
```
*<p align="center">Figure 8: Telegraf Input template</p>*


2. **Run default Flink Jobs**

- If the user wants to run the existing Flink jobs: Map Metrics and Map Metrics Dashboard, the user can add **install_map_metrics** and **install_map_metrics_dashboard** under **global.flinkJobs**. If the user adds new inputs which are different from idrac, vsphere and k8s they would need to create a new Flink job or update map metrics code for the new streams. The user can also choose to add any metric to the output stream(mappedMetrics) which gets created when running the Flink job map by updating the MapMetrics.java file.

```
  flinkJobs:
  - name: install_map_metrics
  - name: install_map_metrics_dashboards
```

*<p align="center">Figure 9: Default Flink Jobs created for this project</p>*


3. **Create your own Flink Jobs**

- If the user wants to add their own Flink Jobs, they can add a name in this format under flinkJobs:

```
  flinkJobs:
  - name: <Flink Job Name>
```

*<p align="center">Figure 10: Flink Job Template</p>*


- Next, go into the folder **charts** and create a new folder with the yaml files for the new Flink job. Use **map-metrics** as reference when creating new Flink job yaml files.
- Next, go into the folder **scripts** and create a new shell script with the same name provided under flinkJobs. Use  **install_map_metrics.sh**  as reference when creating a new script.
- Finally, if the user wants to send data to influxDB, go into the folder **scripts** and **flinkJobSetup.py**  and modify the script to update influxdb details. Refer to the code block #updating map-metrics-values.yaml influxdb details in the flinkJobSetup.py for reference.

4. **Install the Project**

- Finally, after the **sample.yaml** file has been configured to add inputs and create new Flink jobs or use the default ones provided, run the install shell script.
- To run the **install.sh** script go to **telcom-demo-app-bundle** folder and run the command  **./install.sh**. This sets up the project with the streams provided from the input and runs Flink jobs.

5. **Steps of Demo Running**

- After running the install.sh shell script, check if a project named examples got created in the SDP UI. 

![SDP project UI](https://user-images.githubusercontent.com/112410039/233169757-9c1e4169-a8db-4d56-92b3-db893522b6f2.png)

- Check if Pravega streams got created for the project. For this example, we have 3 streams idrac, vsphere and k8s which were configured in the master yaml file. Also, the mappedMetrics stream was created automatically after running Flink job map metrics.

![SDP project Pravega streams](https://user-images.githubusercontent.com/112410039/233169840-6f6dde92-421c-42ee-899d-f559e7a980b4.png)

- Check if flinkprocessor artifact was uploaded in SDP. 

![SDP Flink processor artifact](https://user-images.githubusercontent.com/112410039/233169917-ab8ff7a3-8bd0-490b-a564-bb2a6eb578e2.png)

- Check if Flink cluster and apps got created in SDP. 

![SDP Flink cluster](https://user-images.githubusercontent.com/112410039/233169965-3771a23f-a195-4841-8e84-cf275306eaff.png)

![SDP Flink apps](https://user-images.githubusercontent.com/112410039/233169979-53f95406-e97c-46cf-92a5-aef490d7ddac.png)

- Finally go to features and go to the Metrics endpoint and check if Grafana dashboards are loaded with data. 

![Grafana Dashboard](https://user-images.githubusercontent.com/112410039/233170001-55e75563-7936-44f5-b17c-d6ba7f09b688.png)

- Screenshot below shows the VMware vSphere – Hosts dashboard with all the live data 

![VmWare Vsphere Host Dashboard](https://user-images.githubusercontent.com/112410039/233170031-de243a93-0c45-438a-9d77-914a9c68be12.png)

### Uninstall Project

- First find all streams created by Telegraf using the command: kubectl get pods -n <projectname>. An example of idrac, vsphere and k8s are shown below.

![pravega example streams](https://user-images.githubusercontent.com/112410039/233170067-cf876061-2dcf-450b-9c6c-bc05cce9b5c7.png)


- Stop each stream that has "telegraf" using the command: helm del <podname> -n <projectname>
- Delete the Flink jobs/cluster, Pravega streams and project in that order from the SDP UI or create update the uninstall.sh shell script with the new streams and run it.

### Troubleshooting

- Line 29 in the install script is commented out when first installing. If you are running install script again uncomment line 29 #sudo keytool -delete -noprompt -alias sdp-repo  -keystore /etc/ssl/certs/java/cacerts -storepass changeit
- If there is error with openssl importing java certs go to the file: **/etc/hosts** and delete the **repo-<projectName>.<clusterinfo>.sdp.hop.lab.emc.com** near the top of the file.
- In the charts folder map-metrics-influxdb-sink-values.yaml and map-metrics-values.yaml have storageClassName set to standard. Change it to your storageClassName. To find out your default storageClassName run **kubectl get storageclass**


