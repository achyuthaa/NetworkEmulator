package Frames;

public class PacketQ {


        private String nextHop;
        private Ipframe iframe;


        // Private constructor to enforce the use of the builder
        private PacketQ(Builder builder) {
            this.iframe = builder.iframe;
            this.nextHop = builder.nextHop;
        }



        // Getter methods

        public String getNextHop() {
            return nextHop;
        }

    public Ipframe getIframe() {
        return iframe;
    }

    // Builder class
        public static class Builder {
            private String nextHop;
            private Ipframe iframe;


            public Builder nextHop(String nextHop) {
                this.nextHop = nextHop;
                return this;
            }
            public Builder iframe(Ipframe iframe){
                this.iframe = iframe;
                return this;
            }

            // Build method to create an instance of MyBuilderPattern
            public PacketQ build() {
                return new PacketQ(this);
            }
        }
}
