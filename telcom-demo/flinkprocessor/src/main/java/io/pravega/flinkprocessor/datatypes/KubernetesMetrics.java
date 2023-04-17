package io.pravega.flinkprocessor.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KubernetesMetrics implements Serializable {
   public String instance;

   public long timestamp;

   public JsonNode jsonNode;

   public VmParents vmParents;

   public String TimeStamp;

   public String toString() {
      return "KubernetesMetrics{instance='" + this.instance + '\'' +
              ", vmParents='" + this.vmParents + '\'' +
              ", jsonNode='" + this.jsonNode + '\'' +
              ", timestamp='" + this.timestamp + '\'' +
              ", TimeStamp='" + this.TimeStamp + '\'' +
              '}';
   }
}
