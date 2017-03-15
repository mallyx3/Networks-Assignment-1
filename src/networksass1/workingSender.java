
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

public class workingSender {
	private DatagramSocket socket;
	private LinkedList<stpPacket> window;
	private InetAddress ipAddress;
	private int port;
	private int sendbase;
	private int ackbase;
	private int mss;
	private int mws;
	private int timeout;
	private double pdrop;
	private Random random;
	
	public workingSender(InetAddress ip, int port, int timeout, int mws, int mss, double pdrop, int seed) throws SocketException {
		  this.socket = new DatagramSocket();
		  socket.connect(ip, port);
		  this.window = new LinkedList<stpPacket>();
		  this.sendbase = 0; //can be anything 
		  this.ackbase = 0;  // can be anything
		  this.ipAddress = ip;
		  this.port = port;
	      this.timeout = timeout;
	      this.mws = mws;
	      this.mss = mss;
	      socket.setSoTimeout(timeout);
	      this.pdrop = pdrop;
	      this.random = new Random(seed);
	}
	
   public static void main(String[] args) throws Exception
   {
      // Get command line argument.
      if (args.length != 8) {
         System.out.println("Required arguments: host port file.txt mws mss timeout pdrop seed");
         return;
      }

      int receiver_port = Integer.parseInt(args[1]);
      InetAddress receiver_ip_address = InetAddress.getByName(args[0]);
      int timeout = Integer.parseInt(args[5]);
      int mws = Integer.parseInt(args[3]);
      int mss = Integer.parseInt(args[4]);
      double pdrop = Double.parseDouble(args[6]);
      int seed = Integer.parseInt(args[7]);
      
      workingSender s = new workingSender(receiver_ip_address, receiver_port, timeout, mws, mss, pdrop, seed);
      
      if (!s.initializeConnection()) {
    	  System.out.println("Initialize Connection failed");  
      } else {
    	  //initialize a random object
    	  Path path = Paths.get(args[2]);
    	  Charset charset = Charset.forName("US-ASCII");
    	  stpPacket dataPacket;
    	  stpPacket ACKPacket;
    	  DatagramPacket ACK;
    	  
    	  //initialize the buffered reader
    	  try {
    		  BufferedReader reader = Files.newBufferedReader(path, charset);
    		  DatagramPacket DataRequest;
    		  byte[] bytes;
    		  boolean success;
    		  int size = 0;
    		  //reads mss chars of data into the array data, until theres no data left (returns -1)
    		  while (true) {
    			  char[] data = new char[mss];
    			  while (s.window.isEmpty() || ((!s.window.isEmpty()) && ((s.window.peekLast().getSeqNumber()- s.window.peekFirst().getSeqNumber()) <= s.mws))) {

        			  size = reader.read(data, 0, mss); 

            		  byte[] dataByte = new String(data).getBytes();
    			  }
    			  
        		  
    			  if (size != -1) {
        			  success = false;
        			  //creates a packet with the data, sequence number sendbase
        			  dataPacket = new stpPacket(size, false, false, false, s.getSendbase(), s.getAckbase(), dataByte);
        			  bytes = dataPacket.getBytes();
        			  DataRequest = new DatagramPacket(bytes, bytes.length, receiver_ip_address, receiver_port);
        			  
        			  
        			  
        			  
        			  //sending and waiting for a packet
        			  while (!success) {
        				  //utilize packet loss 
        				  if (s.pld()) {
            				  s.socket.send(DataRequest);
                			  System.out.println("packet with sequence number: " + s.getSendbase() + " sent");
        				  } else {
        					  System.out.println("packet " + s.getSendbase() + " dropped");
        				  }
        				  try {
        					  ACK = new DatagramPacket(new byte[1024], 1024);
        					  s.socket.receive(ACK);
        					  ACKPacket = s.getPacket(ACK.getData());
        					  if (ACKPacket.isACK() && ACKPacket.getAckNumber() > s.getSendbase()) {
        						  s.setSendbase(ACKPacket.getAckNumber());
        						  success = true;
        					  } else {
        						  continue; //insert fast retransmit here
        					  }
        				  } catch (SocketTimeoutException e) {
        					  continue;
        				  }
        			  }
        		  } else {
        			  break;
        		  }
    		  }
    		  reader.close();
    	  } catch (IOException x) {
    		  System.err.format("IOException: %s%n",  x);
    	  } finally {
    		  s.closeConnection();
    	  }
      }
   }

   boolean pld() {
	   float x = this.random.nextFloat();
	   if ( x > this.pdrop ) {
		   return true;
	   } else {
		   return false;
	   }
   }
   
   void closeConnection() throws IOException, ClassNotFoundException {
		  stpPacket finPacket = new stpPacket(0, false, false, true, this.getSendbase(), this.getAckbase(), null);
		  byte[] bytes = finPacket.getBytes();
		  DatagramPacket toFin = new DatagramPacket(bytes, bytes.length, this.ipAddress, this.port);
		  while(true) {
			  this.socket.send(toFin);
			  try {
				  DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
				  socket.receive(request);
				  stpPacket ACK = getPacket(request.getData());
				  if (ACK.getAckNumber() == this.getSendbase()+1) {
					  break;
				  }
			  } catch (SocketTimeoutException e) {
				  continue;
			  }
		  }
		  try {
			  DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
			  socket.receive(request);
			  stpPacket FIN = getPacket(request.getData());
			  this.setAckbase(FIN.getSeqNumber() + 1);
			  stpPacket ACK = new stpPacket(0, false, true, false, this.sendbase, this.ackbase, null);
			  bytes = ACK.getBytes();
			  DatagramPacket toAck = new DatagramPacket(bytes, bytes.length, this.ipAddress, this.port);
			  socket.send(toAck);
			  socket.close();
		  } catch (SocketTimeoutException e) {
			  socket.close();
		  }
   }
   
   boolean initializeConnection() throws IOException {
	 stpPacket SYN = new stpPacket(0, true, false, false, sendbase, 0, null);
	 byte[] bytes = SYN.getBytes();
	 DatagramPacket SYNRequest = new DatagramPacket(bytes, bytes.length, this.getIpAddress(), this.getPort());
	 while (true) {
		 socket.send(SYNRequest);
		 try {
			 DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
			 socket.receive(request);
			 stpPacket SYNACK = getPacket(request.getData());
			 if (SYNACK.isSYN()&& SYNACK.isACK() && SYNACK.getAckNumber() == sendbase+1) {
				 sendbase = SYNACK.getAckNumber();
				 ackbase = SYNACK.getSeqNumber() + 1;
				 stpPacket ACK = new stpPacket(0, false, true, false, sendbase, ackbase, null);
				 bytes = ACK.getBytes();
				 DatagramPacket ACKRequest = new DatagramPacket(bytes, bytes.length);
				 socket.send(ACKRequest);
				 return true;
			 } else {
				 return false;
			 }
		 } catch (SocketTimeoutException e) {
			 continue;
		 }
	 }
   }

	public stpPacket getPacket (byte[] bytes) {
		int seqNumber = ((bytes[0] << 24) & 0xFF000000) + ((bytes[1] << 16) & 0x00FF00000) + ((bytes[2] << 8) & 0x0000FF00) + ((bytes[3]) & 0x000000FF);
		int ackNumber = ((bytes[4] << 24) & 0xFF000000) + ((bytes[5] << 16) & 0x00FF00000) + ((bytes[6] << 8) & 0x0000FF00) + ((bytes[7]) & 0x000000FF);
		boolean isSYN = bytes[8] == 1 ? true : false;
		boolean isACK = bytes[9] == 1 ? true : false;
		boolean isFIN = bytes[10] == 1;
		int length = ((bytes[11] << 24) & 0xFF000000) + ((bytes[12] << 16) & 0x00FF00000) + ((bytes[13] << 8) & 0x0000FF00) + ((bytes[14]) & 0x000000FF);
		byte[] dataA = null;
		if (length != 0) {
			dataA = new byte[length];
			System.arraycopy(bytes, 15, dataA, 0, length);
		} 
		stpPacket packet = new stpPacket(length, isSYN, isACK, isFIN, seqNumber, ackNumber, dataA);
		return packet;
	}

	public int getSendbase() {
		return sendbase;
	}

	public void setSendbase(int sendbase) {
		this.sendbase = sendbase;
	}

	public int getMss() {
		return mss;
	}

	public void setMss(int mss) {
		this.mss = mss;
	}

	public int getMws() {
		return mws;
	}

	public void setMws(int mws) {
		this.mws = mws;
	}

	public int getTimeout() {
		return timeout;
	}
	
	public DatagramSocket getSocket() {
		return socket;
	}

	public void setSocket(DatagramSocket socket) {
		this.socket = socket;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getAckbase() {
		return ackbase;
	}

	public void setAckbase(int ackbase) {
		this.ackbase = ackbase;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}
	
	
	
	
}