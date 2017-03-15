
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.Timer;


/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

public class Sender {
	private DatagramSocket socket;
	private int timeout;
	private Lock lock = new ReentrantLock();
	private Condition wakeSender = lock.newCondition();
	private Timer timer;
	private int counter = 0;
	
	private PrintWriter out;
	private int segmentsSent = 0;
	private int packetsDropped = 0;
	private int retransmittedPackets = 0;
	private int duplicateAcks = 0;
	private long startTime;
	
	private Queue<stpPacket> window;
	private InetAddress ipAddress;
	private int port;
	private int sendbase; //seq number of last unacked packet
	private int lastbytesent;  //seq number of last byte sent
	private int ackbase;
	private boolean teardown = false;
	private int mss;
	private int mws;
	private double pdrop;
	private Random random;
	
	public Sender(InetAddress ip, int port, int timeout, int mws, int mss, double pdrop, int seed) throws IOException {
		  this.socket = new DatagramSocket();
		  socket.connect(ip, port);
		  this.window = new LinkedList<stpPacket>();
		  this.sendbase = 0; //can be anything 
		  this.lastbytesent = 0;
		  this.ackbase = 0;  // can be anything
		  this.ipAddress = ip;
		  this.port = port;
		  this.out = new PrintWriter("Sender_log.txt");

		  ActionListener taskPerformer = new ActionListener() {
			  public void actionPerformed(ActionEvent evt) {
				  resendFirstPacket();
	          }
		  };
	      Timer timer = new Timer(timeout, taskPerformer);
	      this.startTime = System.nanoTime();
	      this.timer = timer;
	      this.timer.setRepeats(false);
		  
		  this.timeout = timeout;
	      this.mws = mws;
	      this.mss = mss;
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
      
      Sender s = new Sender(receiver_ip_address, receiver_port, timeout, mws, mss, pdrop, seed);
      
      if (!s.initializeConnection()) {
    	  System.out.println("Initialize Connection failed");  
      } else {
    	  //initialize a random object
    	  Path path = Paths.get(args[2]);
    	  Charset charset = Charset.forName("US-ASCII");
    	  stpPacket dataPacket;
    	  //initialize the buffered reader
    	  try {
    		  BufferedReader reader = Files.newBufferedReader(path, charset);
    		  DatagramPacket DataRequest;
    		  byte[] bytes;
    		  int size = 0;
    	      Thread receiver = new Thread(s.new ReceivingSender());
    	      receiver.start();
    	      
    	      //packet creation loop
    		  while (true) {
    			  s.lock.lock();
    			  try {
    				  
    				  char[] data = new char[mss];
    				  size = reader.read(data, 0, mss);
    	    		  //reads mss chars of data into the array data, until theres no data left (returns -1)
    				  //exit conditions
    				  while (size == -1 && !s.window.isEmpty()) {
    					  s.teardown = true;
    					  s.wakeSender.await();
    				  }
    				  if (size == -1 && s.window.isEmpty()) {
    					  s.timer.stop();
    					  break;
    				  }
    				  
    				  byte[] dataByte = new String(data).getBytes();
    				  dataPacket = new stpPacket(size, false, false, false, s.getLastbytesent(), s.getAckbase(), dataByte);
    				  bytes = dataPacket.getBytes();
    				  s.setLastbytesent(s.getLastbytesent()+size);
    				  if (!s.timer.isRunning()) {
    		    	      s.timer.start();
    				  }
    				  
    				  //if the window is full, await
    				  while (s.getLastbytesent() > s.getSendbase() + s.getMws()) {
    					  s.wakeSender.await();
    				  }
    				  s.window.add(dataPacket);
    				  s.segmentsSent++;
    				  if (s.pld()) {
            			  DataRequest = new DatagramPacket(bytes, bytes.length, s.getIpAddress(), s.getPort());
            			  s.socket.send(DataRequest);
            			  s.out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", s.time(), "D", dataPacket.getSeqNumber(), dataPacket.getLength(), dataPacket.getAckNumber())); 
    				  } else {
    					  s.packetsDropped++;
    					  s.out.println(String.format("%5s %10d %4s %11d %6d %11d", "drop", s.time(), "D", dataPacket.getSeqNumber(), dataPacket.getLength(), dataPacket.getAckNumber())); 
    				  }
    			  } finally {
    				  s.lock.unlock();
    			  }
    		  }
    		  reader.close();
    	  } catch (IOException x) {
    		  System.err.format("IOException: %s%n",  x);
    	  } finally {
    		  s.closeConnection();
    	  }
    	  return;
      }
   }
   
   /**
    * Function that handles the timeout resend
    */
   void resendFirstPacket() {
	   byte[] bytes = window.peek().getBytes();
	   if (pld()) {
			DatagramPacket DataRequest = new DatagramPacket(bytes, bytes.length, getIpAddress(), getPort());
			try {
				socket.send(DataRequest);
				retransmittedPackets++;
				out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "D", window.peek().getSeqNumber(), window.peek().getLength(), window.peek().getAckNumber())); 
			} catch (IOException e) {
				System.out.println(e);
			}
	   } else {
			packetsDropped++;
			out.println(String.format("%5s %10d %4s %11d %6d %11d", "drop", time(), "D", window.peek().getSeqNumber(), window.peek().getLength(), window.peek().getAckNumber())); 
	   }
	   timer.restart();
   }
   
   boolean pld() {
	   float x = this.random.nextFloat();
	   if ( x > this.pdrop ) {
		   return true;
	   } else {
		   return false;
	   }
   }
   
   /**
    * Records a time at program execution, and then subtracts
    * current time from start time to get the time since program execution
    * @return
    */
   public long time() {
	   long endTime = System.nanoTime();
	   return ((endTime - this.startTime)/1000000); 
   }
   
   /**
    * Function that handles connection teardown.
    * FIN/ACK/FIN/ACK
    * @throws IOException
    * @throws ClassNotFoundException
    */
   void closeConnection() throws IOException, ClassNotFoundException {
		  stpPacket finPacket = new stpPacket(0, false, false, true, this.getSendbase(), this.getAckbase(), null);
		  byte[] bytes = finPacket.getBytes();
		  DatagramPacket toFin = new DatagramPacket(bytes, bytes.length, this.ipAddress, this.port);
		  while(true) {
			  this.socket.send(toFin);
			  out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "F", finPacket.getSeqNumber(), finPacket.getLength(), finPacket.getAckNumber())); 
			  try {
				  DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
				  socket.receive(request);
				  stpPacket ACK = getPacket(request.getData());
				  out.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", time(), "A", ACK.getSeqNumber(), ACK.getLength(), ACK.getAckNumber())); 
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
			  out.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", time(), "F", FIN.getSeqNumber(), FIN.getLength(), FIN.getAckNumber())); 
			  this.setAckbase(FIN.getSeqNumber() + 1);
			  stpPacket ACK = new stpPacket(0, false, true, false, this.sendbase, this.ackbase, null);
			  bytes = ACK.getBytes();
			  DatagramPacket toAck = new DatagramPacket(bytes, bytes.length, this.ipAddress, this.port);
			  socket.send(toAck);
			  out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "A", ACK.getSeqNumber(), ACK.getLength(), ACK.getAckNumber())); 
			  socket.close();
			  out.println("Amount of (original) Data Transferred (in bytes): " + (getLastbytesent()-1));
			  out.println("Number of Data Segments Sent (excluding retransmissions): " + segmentsSent);
			  out.println("Number of (all) Packets Dropped (by the PLD module): " + packetsDropped);
			  out.println("Number of retransmitted segments: " + retransmittedPackets);
			  out.println("Number of Duplicate Acks received: " + duplicateAcks);
			  out.close();
			  
		  } catch (SocketTimeoutException e) {
			  socket.close();
		  }
   }
   
   /**
    * Function that handles the SYN/SYNACK/ACK connection establishment
    * @return
    * @throws IOException
    */
   boolean initializeConnection() throws IOException {
	 stpPacket SYN = new stpPacket(0, true, false, false, sendbase, 0, null);
	 byte[] bytes = SYN.getBytes();
	 DatagramPacket SYNRequest = new DatagramPacket(bytes, bytes.length, this.getIpAddress(), this.getPort());
	 while (true) {
		 socket.send(SYNRequest);
		 out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "S", SYN.getSeqNumber(), SYN.getLength(), SYN.getAckNumber()));
		 try {
			 DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
			 socket.receive(request);
			 stpPacket SYNACK = getPacket(request.getData());

			 out.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", time(), "SA", SYNACK.getSeqNumber(), SYNACK.getLength(), SYNACK.getAckNumber()));
			 if (SYNACK.isSYN()&& SYNACK.isACK() && SYNACK.getAckNumber() == sendbase+1) {
				 sendbase = SYNACK.getAckNumber();
				 lastbytesent = sendbase;
				 ackbase = SYNACK.getSeqNumber() + 1;
				 stpPacket ACK = new stpPacket(0, false, true, false, sendbase, ackbase, null);
				 bytes = ACK.getBytes();
				 DatagramPacket ACKRequest = new DatagramPacket(bytes, bytes.length);
				 socket.send(ACKRequest);
				 out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "A", SYN.getSeqNumber(), SYN.getLength(), SYN.getAckNumber()));
				
				 return true;
			 } else {
				 return false;
			 }
		 } catch (SocketTimeoutException e) {
			 continue;
		 }
	 }
   }

   /**
    * Function that takes a byte array and returns a stpPacket object
    * @param bytes
    * @return
    */
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

	/**
	 * Thread for ReceivingSender. Part of the Sender that 
	 * receives ACKs and updates the Sender's window so 
	 * sender can keep making packets
	 */
	public class ReceivingSender implements Runnable {
		@Override
		public void run() {
			while (true) {
				DatagramPacket ACK = new DatagramPacket(new byte[1024], 1024);
				try {
					socket.receive(ACK);
					
				} catch (IOException e) {
					System.out.println(e);
				}
				lock.lock();
				try {
					//handler for dealing with a valid ack
					stpPacket ACKPacket = getPacket(ACK.getData());
					out.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", time(), "A", ACKPacket.getSeqNumber(), ACKPacket.getLength(), ACKPacket.getAckNumber())); 
					if (ACKPacket.isACK() && ACKPacket.getAckNumber() > getSendbase()) {
						counter = 0;
						while (!window.isEmpty() && (window.peek().getSeqNumber() < ACKPacket.getAckNumber())) {
							window.remove();
						}
						if (!window.isEmpty()) {
							timer.restart();
						}
						setSendbase(ACKPacket.getAckNumber());
						wakeSender.signal();
						if (teardown && getSendbase() == getLastbytesent()) {
							return;
						}
					} else if (ACKPacket.isACK() && ACKPacket.getAckNumber() == getSendbase()) {
						//fast retransmit section
						duplicateAcks++;
						if (counter == 2) {
							counter = 0;
							byte[] bytes = window.peek().getBytes();
		    				if (pld()) {
		    					DatagramPacket DataRequest = new DatagramPacket(bytes, bytes.length, getIpAddress(), getPort());
		    					try {
		    						out.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", time(), "D", window.peek().getSeqNumber(), window.peek().getLength(), window.peek().getAckNumber()));
		    						socket.send(DataRequest); 
		    						retransmittedPackets++;
		    					} catch (IOException e) {
		    						System.out.println(e);
		    					}
		    				} else {
		    					  packetsDropped++;
		    					  out.println(String.format("%5s %10d %4s %11d %6d %11d", "drop", time(), "D", window.peek().getSeqNumber(), window.peek().getLength(), window.peek().getAckNumber())); 
		    				}
						} else {
							counter++;
						}
					} else {
						//ack received that was already acked
						duplicateAcks++;
					}
				} finally {
					lock.unlock();
				}
			}
		}
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

	public Queue<stpPacket> getWindow() {
		return window;
	}

	public void setWindow(Queue<stpPacket> window) {
		this.window = window;
	}

	public int getAckbase() {
		return ackbase;
	}

	public void setAckbase(int ackbase) {
		this.ackbase = ackbase;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public Lock getLock() {
		return lock;
	}

	public void setLock(Lock lock) {
		this.lock = lock;
	}

	public Condition getWakeSender() {
		return wakeSender;
	}

	public void setWakeSender(Condition wakeSender) {
		this.wakeSender = wakeSender;
	}

	public int getLastbytesent() {
		return lastbytesent;
	}

	public void setLastbytesent(int lastbytesent) {
		this.lastbytesent = lastbytesent;
	}

	
	
}