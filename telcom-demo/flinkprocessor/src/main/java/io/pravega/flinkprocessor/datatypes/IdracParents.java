package io.pravega.flinkprocessor.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IdracParents implements Serializable {
   public String systemName;

   public String systemFqdn;

   public String systemModel;

   public long timestamp;

   public String systemOsName;

   public String systemServiceTag;

   public String osVersion;

   public String toString() {
      return "IdracParents {systemName='" + this.systemName + '\'' +
              ", systemFqdn='" + this.systemFqdn + '\'' +
              ", systemModel='" + this.systemModel + '\'' +
              ", systemOsName=" + this.systemOsName + '\'' +
              ", systemServiceTag='" + this.systemServiceTag + '\'' +
              ", osVersion='" + this.osVersion + '\'' +
              ", timestamp='" + this.timestamp + '\'' +
              '}';
   }
}
