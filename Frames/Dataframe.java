package Frames;

import java.io.Serializable;

public class Dataframe implements Serializable {
    private String Data;
    private String SourceIpaddress;
    private String DestinationIpaddress;
     private Dataframe(Builder builder){
        this.Data = builder.Data;
        this.SourceIpaddress = builder.SourceIpaddress;
        this.DestinationIpaddress = builder.DestinationIpaddress;
    }
    public String getData(){
        return Data;
    }

    public String getDestinationIpaddress() {
        return DestinationIpaddress;
    }
    public String getSourceIpaddress(){
        return SourceIpaddress;
    }
    public static class Builder{
         private String Data;
         private String SourceIpaddress;
         private String DestinationIpaddress;

         public Builder(String Data){
             this.Data = Data;
         }
         public Builder SourceIpaddress(String SourceIpaddress){
             this.SourceIpaddress= SourceIpaddress;
             return this;
         }
         public Builder DestinationIpaddress(String DestinationIpaddress){
             this.DestinationIpaddress = DestinationIpaddress;
             return this;
         }

         public Dataframe build(){
             return new Dataframe(this);
         }
    }
}
