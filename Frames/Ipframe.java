package Frames;

import java.io.Serializable;

public class Ipframe implements Serializable{

    private Dataframe dframe;
    private String SourceIpaddress;
    private String DestinationIp;
    // Add other fields as needed

    public Ipframe(Ipframe.Builder builder) {
        this.dframe = builder.dframe;
        this.SourceIpaddress = builder.SourceIpaddress;
        this.DestinationIp = builder.DestinationIp;
        // Initialize other fields here
    }

    public String getSourceIP() {
        return SourceIpaddress;
    }

    public String getDestinationIp() {
        return DestinationIp;
    }

    public Dataframe getDframe() {
        return dframe;
    }

    // Getters for fields if needed

    public static class Builder {
        private Dataframe dframe;
        private String SourceIpaddress;
        private String DestinationIp;

        public Builder dframe(Dataframe dframe) {
            this.dframe = dframe;
            return this;
        }

        public Builder SourceIpaddress(String sourceIP) {
            this.SourceIpaddress = sourceIP;
            return this;
        }

        public Builder destinationIP(String destinationIP) {
            this.DestinationIp = destinationIP;
            return this;
        }

        public Ipframe build() {
            return new Ipframe(this);
        }
    }

}
