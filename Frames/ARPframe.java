package Frames;

import java.io.Serializable;

public class ARPframe implements Serializable {
    private String SourceIp;
    private String DestinationIp;

    private String Sourcemac;
    private String Destinationmac;
    private int Arptype;

    public int getArptype() {
        return Arptype;
    }

    public String getDestinationIp() {
        return DestinationIp;
    }

    public String getDestinationmac() {
        return Destinationmac;
    }

    public String getSourceIp() {
        return SourceIp;
    }

    public String getSourcemac() {
        return Sourcemac;
    }


    public ARPframe(Builder builder){
        Sourcemac = builder.Sourcemac;
        Destinationmac = builder.Destinationmac;
        SourceIp = builder.SourceIp;
        DestinationIp = builder.DestinationIp;
        Arptype = builder.Arptype;
    }


    public static class Builder{
        private String SourceIp;
        private String DestinationIp;

        private String Sourcemac;
        private String Destinationmac;

        private int Arptype;

        public Builder SourceIp(String SourceIp){
            this.Sourcemac = SourceIp;
            return this;
        }
        public Builder DestinationIp(String DestinationIp){
            this.DestinationIp = DestinationIp;
            return this;
        }
        public Builder Sourcemac(String Sourcemac){
            this.Sourcemac = Sourcemac;
            return this;
        }

        public Builder Destinationmac(String Destinationmac){
            this.Destinationmac = Destinationmac;
            return this;
        }

        public Builder Arptype(int Arptype) {
            this.Arptype = Arptype;
            return this;
        }

        public ARPframe build(){
            return new ARPframe(this);
        }


    }


}
