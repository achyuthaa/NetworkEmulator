/* By Achyuthanwesh Vanga, Namrata Mallampati */ 

Compiling and deleting the class files:

1) First compile the two main java programs which are [Bridge and Station] using command -> make
2) To clear the class files created using command -> make clean

Bridge Usage:

1) To run the bridge use command ->java Bridge <Bridgename> <numofports>
for example -> java cs1 5

Station usage:

1) To run the station use command ->java Station -no <path of ifaces> <path of rtables> <hosts file> 
for example -> java Station -no ifaces/ifaces.a rtables/rtable.a hosts

Router usage:

1) To run the router use command -> java Station -route <path of ifaces> <path of rtables> <hosts file>
for example -> java Station -route ifaces/ifaces.r1 rtables/rtable.r1 hosts 

The supported commands in Station/router are: 

	   send <destination> <message> // send message to a destination host
	   show arp 		// show the ARP cache table information
	   show pq 		// show the pending_queue
	   show	host 		// show the IP/name mapping table
	   show	iface 		// show the interface information
	   show	rtable 		// show the contents of routing table
	   quit // close the station

The supported commands in Bridge are :

           show sl 		// show the contents of self-learning table
	   quit                 // close the bridge

-> There is scripting file with name run_simulation which can be run on linux systems which opens all the terminals at once.

          B              C                D
          |              |                |
         cs1-----R1------cs2------R2-----cs3
          |              |                |
          -------A--------                E


