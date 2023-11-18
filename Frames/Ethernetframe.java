package Frames;

import java.io.Serializable;

public class Ethernetframe implements Serializable {
    private Dataframe frame;

    private Ipframe iframe;
    private ARPframe aframe;
    private String SourceMacAddress;
    private String DestinationMacAddress;

    private int type;
    private Ethernetframe(Ethernetframe.Builder builder){
        this.frame = builder.frame;
        this.SourceMacAddress = builder.SourceMacAddress;
        this.DestinationMacAddress = builder.DestinationMacAddress;
        this.iframe = builder.iframe;
        this.type = builder.type;
    }
    public Dataframe getData(){
        return frame;
    }

    public int getType() {
        return type;
    }

    public String getDestinationMacAddress() {
        return DestinationMacAddress;
    }
    public String getSourceMacAddress(){
        return SourceMacAddress;
    }

    public Ipframe getIframe() {
        return iframe;
    }

    public ARPframe getAframe() {
        return aframe;
    }

    @Override
    public String toString() {
        return "Ethernetframe{" +
                "frame=" + frame +
                ", iframe=" + iframe +
                ", aframe=" + aframe +
                ", SourceMacAddress='" + SourceMacAddress + '\'' +
                ", DestinationMacAddress='" + DestinationMacAddress + '\'' +
                ", type=" + type +
                '}';
    }

    public static class Builder{
        private Dataframe frame;
        private String SourceMacAddress;
        private String DestinationMacAddress;
        private Ipframe iframe;

        private int type;

        public Builder Dataframe(Dataframe dframe){
            this.frame = dframe;
            return this;
        }

        public Builder getType(int type) {
            this.type = type;
            return this;
        }

        public Builder ipframe(Ipframe iframe){
            this.iframe = iframe;
            return this;
        }

        public Ethernetframe.Builder SourceMacAddress(String SourceMacAddress){
            this.SourceMacAddress = SourceMacAddress;
            return this;
        }
        public Ethernetframe.Builder DestinationMacAddress(String DestinationMacAddress){
            this.DestinationMacAddress = DestinationMacAddress;
            return this;
        }

        public Ethernetframe build(){
            return new Ethernetframe(this);
        }
    }
}
