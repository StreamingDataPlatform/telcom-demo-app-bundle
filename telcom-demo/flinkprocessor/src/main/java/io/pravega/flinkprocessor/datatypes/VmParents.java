/* Decompiler 3ms, total 242ms, lines 25 */
package io.pravega.flinkprocessor.datatypes;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;

@JsonIgnoreProperties(
   ignoreUnknown = true
)
public class VmParents implements Serializable {
   public String clusterName;
   public String dcName;
   public String esxHostname;
   public long timestamp;
   public String guest;
   public String moid;
   public String vmName;
   public String vcenter;
   public String guestHostname;
   public IdracParents idracServer;

   public String toString() {
      return "VmParents{clusterName='" + this.clusterName + '\'' +
              ", dcName='" + this.dcName + '\'' +
              ", esxHostname='" + this.esxHostname + '\'' +
              ", guest=" + this.guest + '\'' +
              ", moid='" + this.moid + '\'' +
              ", vmName='" + this.vmName + '\'' +
              ", vcenter='" + this.vcenter + '\'' +
              ", idracServer='" + this.idracServer + '\'' +
              ", guestHostname='" + this.guestHostname + '\'' +
              ", timestamp='" + this.timestamp + '\'' +
              '}';
   }
}
