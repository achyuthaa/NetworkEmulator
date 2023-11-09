package Frames;

public class Ethernetframe {
    private Dataframe frame;
    private String SourceMacAddress;
    private String DestinationMacAddress;
    private Ethernetframe(Ethernetframe.Builder builder){
        this.frame = builder.frame;
        this.SourceMacAddress = builder.SourceMacAddress;
        this.DestinationMacAddress = builder.DestinationMacAddress;
    }
    public Dataframe getData(){
        return frame;
    }

    public String getDestinationMacAddress() {
        return DestinationMacAddress;
    }
    public String getSourceMacAddress(){
        return SourceMacAddress;
    }
    public static class Builder{
        private Dataframe frame;
        private String SourceMacAddress;
        private String DestinationMacAddress;

        public Builder(Dataframe Data){
            this.frame = frame;
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
