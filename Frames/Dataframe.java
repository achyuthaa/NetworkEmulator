//By Achyuthanwesh Vanga[AV22Z], Namrata Mallampati[NM22Y]
package Frames;

import java.io.Serializable;

public class Dataframe implements Serializable {
    private String Data;

     private Dataframe(Builder builder){
        this.Data = builder.Data;
    }
    public String getData(){
        return Data;
    }

    public static class Builder{
         private String Data;
         public Builder Data(String Data){
             this.Data = Data;
             return this;
         }

         public Dataframe build(){
             return new Dataframe(this);
         }
    }
}
