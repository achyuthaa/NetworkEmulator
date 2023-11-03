package Frames;

import java.io.Serializable;

public class Dataframe implements Serializable {
    private String Data;
    private String SourceMacAddress;
    private String DestinationMacAddress;
     private Dataframe(Builder builder){
        this.Data = builder.Data;
        this.SourceMacAddress = builder.SourceMacAddress;
        this.DestinationMacAddress = builder.DestinationMacAddress;
    }
    public String getData(){
        return Data;
    }

    public String getDestinationMacAddress() {
        return DestinationMacAddress;
    }
    public String getSourceMacAddress(){
        return SourceMacAddress;
    }
    public static class Builder{
         private String Data;
         private String SourceMacAddress;
         private String DestinationMacAddress;

         public Builder(String Data){
             this.Data = Data;
         }
         public Builder SourceMacAddress(String SourceMacAddress){
             this.SourceMacAddress = SourceMacAddress;
             return this;
         }
         public Builder DestinationMacAddress(String DestinationMacAddress){
             this.DestinationMacAddress = DestinationMacAddress;
             return this;
         }

         public Dataframe build(){
             return new Dataframe(this);
         }
    }
}
