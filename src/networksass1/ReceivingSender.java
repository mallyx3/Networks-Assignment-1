import java.net.DatagramPacket;

public class ReceivingSender implements Runnable {
	
	
	@Override
	public void run() {
		while (true) {
			lock.lock();
			int counter = 0;
			try {
				DatagramPacket ACK = new DatagramPacket(new byte[1024], 1024);
				s.socket.receive(ACK);
				stpPacket ACKPacket = s.getPacket(ACK.getData());
				if (ACKPacket.isACK() && ACKPacket.getAckNumber() > s.getSendbase()) {
					s.setSendbase(ACKPacket.getAckNumber());
					wakeSender.notify();
				} else {
					
					continue; //insert fast retransmit here
				}
			} finally {
				lock.unlock();
			}
		
		// TODO Auto-generated method stub
		}
	}

}
