# Flink sink

1. If you are using the main branch for the deployment , make sure that your hostname and kubernetes node name are same*
2. For instance default k8s node name is node1, name your host machine also as node1
3. The condition in the Mapped Metrics looks for this , if it fails nothing gets written in to the MappedMetric output stream
4. If you have differente name already, please use the deploy_on_murray_1 branch 
5. This has a modified MappedMetric.java file with a node level condition 
 						if(nd.equals("sdpidracnode")){
							vmparents.guestHostname = "node1";
              Line number 297 and 298
6. Modifiy the condition according to your setup (one node or multi node)

* This is assuming that the VM on which the SDP is hosted in runninhg on one of the esxi attached to the vcenter (10.243.61.115), else you will have to change esxiHostName reference (line 137) also.
