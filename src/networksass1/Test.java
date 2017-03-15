
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class Test {
	
	private stpPacket test;
	
	public Test() {
		this.test = new stpPacket(0, false, true, false, 5, 6, null);
	}
	
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		Test t = new Test();
		byte[] bytes = t.getBytes(t.test);
		stpPacket finishedTest = (stpPacket) t.getObject(bytes);
		if (finishedTest.getAckNumber() != 6) {
			System.out.print("ACK NUMBER WRONG");
		} else {
			System.out.println("ACK NUMBER CORRECT");
		}
		if (finishedTest.getSeqNumber() == 5) {
			System.out.println("SEQ NUMBER CORRECT");
		} else {
			System.out.print("SEQ NUMBER WRONG");
		}
		
		
	}
	
	public byte[] getBytes(Object obj) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			try (ObjectOutputStream o = new ObjectOutputStream(b)) {
				o.writeObject(obj);
			}
			return b.toByteArray();
		}
	}
	

	
	public Object getObject(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream b = new ByteArrayInputStream(bytes)) {
			try (ObjectInputStream o = new ObjectInputStream(b)) {
				return o.readObject();
			}
		}
	}
}

