# Observability App Bundle
The objective of the Observability App Bundle code sample is to let users easily setup a useable example project of a common use case on Dell’s Streaming Data Platform (SDP). The use case described here can show the power of ingesting, storing, analyzing, and correlating data across different data and event streams on SDP. 
The included code sample lets the user run an install script to easily setup a project on SDP with their preferred Pravega streams and Flink jobs they want to run. This example can easily be expanded to many more data sources to ingest as well as any number of transformation and analysis jobs applied to those streams.


## High Level Overview
![telecom-demo-automated](https://media.eos2git.cec.lab.emc.com/user/17324/files/66f50e5f-13c4-4088-8a1b-6ecf63133ab7)

*<p align="center">Figure 1: Architecture design of Code Sample</p>*


## Components Overview:
- The first component within the green box represents the data sources which include iDRAC, VMware, and k8s data on groups of servers. 
- The second component, from the left (designated in yellow)is the telegraf agent which is used to collect the data and output it to the SDP Rest gateway. 
- The third component (colored in Teal) is the Pravega REST gateway which is a REST endpoint that receives data from telegraf and writes to Pravega streams. 
- The fourth component next (in blue), represents the Pravega streams which are used to read continuous data. 
- The fifth component (with a light grey background) is the Apache Flink engine which is used to do some data analysis and transformations. While we use Flink here the user can swap out Apache Spark or Gstreamer based on their specific use-case and input data streams.
- Finally, the last components represent where we are sending our results of the analysis and data transformations from Flink, in this case InfluxDB database with Grafana to view the data. Many times the this could be another Pravega Stream that the results are sent to, allowing for a multi-step pipeline of activities where data is ingested, transformed and analyzed as a sequence of steps.


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
### User master yaml file:
The user master yaml file is the main file used to setup everything for this project for the user. This yaml file contains multiple options that users can customize for the project. To view the master yaml file go into the telcom-demo-app-bundle folder and open the sample.yaml file. Areas where the user can customize the master yaml file are:
1.	Project Name: The user can change the project name to their choice. The default project name provided is examples. Important naming convention: The name can be a max length of 15 characters, consist of lower-case alphanumeric characters or '-', and must start and end with an alphanumeric character.

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/498f561e-056f-439b-b00d-06957723092e)

*<p align="center">Figure 2: User Master Yaml file Project Name</p>*

2.	Data Sinks: The user can add outputs as data sinks which telegraf can output data to. Default outputs are set to influxDB, and HTTP is used by the Pravega rest gateway. To add new outputs, see the telegraf output plugins. at https://docs.influxdata.com/telegraf/v1.14/plugins/plugin-list/.
 
![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/8a965d1d-6d35-44a5-baa7-cb0eaff0075a)

*<p align="center">Figure 3: Telegraf Outputs</p>*

3.	Data Sources: The user can add inputs as data sources which telegraf can read data from. The default inputs in this project must be changed by the user to point to their data sources. This project implements 3 inputs as default. They are k8s, idrac and vsphere, shown in Figure 4, 5 and 6. The input format is shown in Figure 8 below.

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/fe4eaa57-8710-42b1-9f33-d4be196796bf)

*<p align="center">Figure 4: Input with stream name k8s, push data to prometheus, and data source input is http listener</p>*


![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/052ec64e-28c6-4d34-aac9-f2f03cd87efb)

*<p align="center">Figure 5: Input with stream name idrac, Not pushing data to prometheus, and data source input is snmp agents</p>*


![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/29125619-d503-46ec-bf83-229f649f45a5)

*<p align="center">Figure 6: Input with stream name vsphere, Not pushing data to prometheus, and data source input is vsphere vcenter</p>*


4.	Flink Jobs: The user can add the Flink Jobs they want to run. The default flink jobs are install_map_metrics and install_map_metrics_dashboards, which create Grafana dashboards shown in Figure 7. These Flink jobs is written in Java. To view them, go into the folder flinkprocessor/src/main/java/io/pravega/flinkprocessor. The input format is shown in Figure 9 below.

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/ab813140-88b9-4d59-9dc6-99d0f33e14d2)

*<p align="center">Figure 7: Default Flink Jobs for this project</p>*

### Steps of Installation:
#### 1.	Setting up the code sample
1.	Clone the GitHub repo on your local machine.
2.	Go into the telcom-demo-app-bundle folder and open the sample.yaml file.
3.	Under global.streams[0].Inputs change the inputs to your data sources, use current Inputs as reference. For each input include the name of the stream, prometheusCheck, and input to your data source. An example of an input template is:

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/40ff7f96-2cb8-4d96-88ef-23906118058b)

*<p align="center">Figure 8: Telegraf Input template</p>*


#### 2.	Run default Flink Jobs
- If the user wants to run the existing Flink jobs: Map Metrics and Map Metrics Dashboard, the user can add install_map_metrics  and install_map_metrics_dashboard under global.flinkJobs. If the user adds new inputs which are different from idrac, vsphere and k8s they would need to create a new flink job or update map metrics code for the new streams. The user can also choose to add any metric to the output stream(mappedMetrics) which gets created when running the flink job map by updating the MapMetrics.java file.

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/fb4fd453-1660-4f39-8efd-8167d53556ce)

*<p align="center">Figure 9: Default Flink Jobs created for this project</p>*


#### 3.	Create your own Flink Jobs
- If the user wants to add their own Flink Jobs, they can add a name in this format under flinkJobs:

![image](https://media.eos2git.cec.lab.emc.com/user/17324/files/cd6d12a0-142a-4c02-98a1-550ab9024e3e)

*<p align="center">Figure 10: Flink Job Template</p>*


-	Next, go into the folder charts and create a new folder with the yaml files for the new Flink job. Use map-metrics as reference when creating new flink job yaml files.
-	Next, go into the folder scripts and create a new shell script with the same name provided under flinkJobs. Use install_map_metrics.sh as reference when creating a new script.
-	Finally, if the user wants to send data to influxDB, go into the folder scripts and flinkJobSetup.py and modify the script to update influxdb details. Refer to the code block #updating map-metrics-values.yaml influxdb details in the flinkJobSetup.py for reference.


#### 4.	Install the Project
- Finally, after the sample.yaml file has been configured to add inputs and create new flink jobs or use the default ones provided, run the install shell script.
- To run the install.sh script go to telcom-demo-app-bundle folder and run the command ./install.sh. This sets up the project with the streams provided from the input and runs Flink jobs.

#### 5.	Steps of Demo Running
- After running install.sh script check if project got created in SDP 
- Check if Pravega streams got created for the project. For this example, we have 3 streams idrac, vsphere and k8s which was configured in the master yaml file. Also, the mappedMetrics stream was created automatically after running flink job map metrics.  
- Check if flinkprocessor artifact was uploaded in SDP.  
- Check if flink cluster and apps got created in SDP.   
- Finally go to features and go to the Metrics endpoint and check if Grafana dashboards are loaded with data.  


## Troubleshooting
- In Line 29 the install script is commented out when first installing. If you are running install script again uncomment line 29 `#sudo keytool -delete -noprompt -alias sdp-repo  -keystore /etc/ssl/certs/java/cacerts -storepass changeit`
- In the charts folder map-metrics-influxdb-sink-values.yaml and map-metrics-values.yaml have storageClassName set to standard. Change it to your storageClassName. To find out your default storageClassName run `kubectl get storageclass`.



