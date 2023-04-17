package io.pravega.flinkprocessor.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DataMap implements Serializable {
   public String instance;

   public long timestamp;

   public String vcenter;

   public String esxhost;

   public String idrac;

   public String pod;
   
   public int metricValue; 

   public String toString() {
      return "DataMap{instance='" + this.instance + '\'' +
              ", esxhost='" + this.esxhost + '\'' +
              ", idrac='" + this.idrac + '\'' +
              ", timestamp='" + this.timestamp + '\'' +
              ", vcenter='" + this.vcenter + '\'' +
              ", pod='" + this.pod + '\'' +
              ", metricValue='" + this.metricValue + '\'' +
              '}';
   }
}
