
import java.io.*;
import java.net.*;
import java.util.*;

/*
 * Server to process ping requests over UDP. 
 * The server sits in an infinite loop listening for incoming UDP packets. 
 * When a packet comes in, the server simply sends the encapsulated data back to the client.
 */

public class Receiver
{
	private int sendbase;
	private Queue<stpPacket> buffer; 
	private int ackbase;
	private PrintWriter writer;
	private long startTime;
	
	private int dataReceived = 0;
	private int dataSegments = 0;
	private int duplicates = 0;

	public Receiver () throws FileNotFoundException {
		this.writer = new PrintWriter("Receiver_log.txt");
		this.sendbase = 0; // can be anything
		this.ackbase = 0;
		this.buffer = new LinkedList<stpPacket>();
		this.startTime = System.nanoTime();
	}
	
	public static void main(String[] args) throws Exception
	{
      // Get command line argument.
		if (args.length != 2) {
			System.out.println("Required arguments: port file.txt");
			return;
		}
		
		int port = Integer.parseInt(args[0]);
		Receiver r =  new Receiver();
		DatagramSocket socket = new DatagramSocket(port);
		
		//handler for connection establishment
		while (true) {
			//waiting for SYN and sending back SYNACK
			DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
			socket.receive(request);
			stpPacket SYN = r.getPacket(request.getData());
			r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "S", SYN.getSeqNumber(), SYN.getLength(), SYN.getAckNumber())); 
			r.ackbase = SYN.getSeqNumber()+1;
			byte[] bytes = new stpPacket(0, true, true, false, r.sendbase, r.ackbase, null).getBytes();
			DatagramPacket reply = new DatagramPacket(bytes, bytes.length, request.getAddress(), request.getPort());
			socket.send(reply);
			r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "SA", r.sendbase, 0, r.ackbase)); 
			//waiting for ACK
			request = new DatagramPacket(new byte[1024], 1024);
			socket.receive(request);

			stpPacket ACK = r.getPacket(request.getData());
			r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "A", ACK.getSeqNumber(), ACK.getLength(), ACK.getAckNumber())); 
			if (ACK.getAckNumber() == r.sendbase+1) {
				r.sendbase++;
			}
			break;
		}
		
		//Path file = Paths.get(args[1]);
		FileOutputStream out = new FileOutputStream(args[1]);

		//handler for data transfer
		while (true) {
			DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
			socket.receive(request);
			stpPacket data = r.getPacket(request.getData());
			
			//handler for connection teardown
			if (data.isFIN()) {
				r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "F", data.getSeqNumber(), data.getLength(), data.getAckNumber())); 
				r.ackbase = data.getSeqNumber()+1;
				byte[] ackFIN = new stpPacket(0, false, true, false, r.sendbase, r.ackbase, null).getBytes();
				DatagramPacket reply = new DatagramPacket(ackFIN, ackFIN.length, request.getAddress(), request.getPort());
				socket.send(reply);

				r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "A", r.sendbase, 0, r.ackbase)); 
				byte[] FIN = new stpPacket(0, false, false, true, r.sendbase, r.ackbase, null).getBytes();
				reply = new DatagramPacket(FIN, FIN.length, request.getAddress(), request.getPort());
				while (true) {
					socket.send(reply);
					r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "F", r.sendbase, 0, r.ackbase)); 
					try {
						request = new DatagramPacket(new byte[1024], 1024);
						socket.receive(request);
						stpPacket requestACK = r.getPacket(request.getData());

						r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "A", requestACK.getSeqNumber(), 0, requestACK.getAckNumber())); 
						if (requestACK.getAckNumber() == r.sendbase+1) {
							out.close();
							break;
						}
					} catch (SocketTimeoutException e) {
						continue;
					}
				}
				break;
				
			} else if (data.getSeqNumber() == r.ackbase) {
				
				//packet received was in order
				r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "D", data.getSeqNumber(), data.getLength(), data.getAckNumber())); 
				r.dataSegments++;
				r.dataReceived = r.dataReceived + data.getLength();
				
				r.ackbase = data.getSeqNumber()+data.getLength();
				out.write(data.getData());
				while (!r.buffer.isEmpty()) {
					stpPacket packet = r.buffer.peek();
					if (packet.getSeqNumber() == r.ackbase) {
						r.buffer.remove();
						r.dataSegments++;
						r.dataReceived = r.dataReceived + packet.getLength();
						r.ackbase = r.ackbase + packet.getLength();
						out.write(packet.getData());
					} else {
						break;
					}
				}					
															//send cumulative ack	
				byte[] bytes = new stpPacket(0, false, true, false, r.sendbase, r.ackbase, null).getBytes();
				DatagramPacket reply = new DatagramPacket(bytes, bytes.length, request.getAddress(), request.getPort());
				socket.send(reply);
				r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "A", r.sendbase, 0, r.ackbase)); 
				
			} else {
				//out of order and above 
				r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "rcv", r.time(), "D", data.getSeqNumber(), data.getLength(), data.getAckNumber())); 
				if (data.getSeqNumber() > r.ackbase) {
					r.buffer.add(data);							 //return ack with same ack number as seq number
					byte[] bytes = new stpPacket(0, false, true, false, r.sendbase, r.ackbase, null).getBytes();
					DatagramPacket reply = new DatagramPacket(bytes, bytes.length, request.getAddress(), request.getPort());
					socket.send(reply);
					r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "A", r.sendbase, 0, r.ackbase)); 
					
				} else {
					//packet received was a duplicate
					r.duplicates++;
					byte[] bytes = new stpPacket(0, false, true, false, r.sendbase, r.ackbase, null).getBytes();
					DatagramPacket reply = new DatagramPacket(bytes, bytes.length, request.getAddress(), request.getPort());
					socket.send(reply);

					r.writer.println(String.format("%5s %10d %4s %11d %6d %11d", "snd", r.time(), "A", r.sendbase, 0, r.ackbase));  
					continue;
				}
			}
		}
		r.writer.println("Amount of Data Received: " + r.dataReceived);
		r.writer.println("Number of Original Data segments received: " + r.dataSegments);
		r.writer.println("Number of duplicate segments received: " + r.duplicates);
		r.writer.close();
		socket.close();
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

	   /**
	    * Records a time at program execution, and then subtracts
	    * current time from start time to get the time since program execution
	    * @return
	    */
	public long time() {
		long endTime = System.nanoTime();
		return ((endTime - this.startTime)/1000000); 
	}
}

