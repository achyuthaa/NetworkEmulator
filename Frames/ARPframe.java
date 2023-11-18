package Frames;

import java.io.Serializable;

public class ARPframe extends Ipframe implements Serializable {
   // private String SourceIp;
    // private String DestinationIp;

    private String Sourcemac;
    private String Destinationmac;
    private String Arptype;

    public String getArptype() {
        return Arptype;
    }

    public String getDestinationmac() {
        return Destinationmac;
    }

    public String getSourcemac() {
        return Sourcemac;
    }


    public ARPframe(Builder builder) {
        super(builder); // Call the constructor of the superclass (Ipframe)
        Sourcemac = builder.Sourcemac;
        Destinationmac = builder.Destinationmac;
        Arptype = builder.Arptype;
    }


    public static class Builder extends Ipframe.Builder{
        private String Sourcemac;
        private String Destinationmac;

        private String Arptype;

        public Builder Sourcemac(String Sourcemac){
            this.Sourcemac = Sourcemac;
            return this;
        }

        public Builder Destinationmac(String Destinationmac){
            this.Destinationmac = Destinationmac;
            return this;
        }

        public Builder (String Arptype) {
            this.Arptype = Arptype;
        }

        public ARPframe build(){
            return new ARPframe(this);
        }


    }


}
