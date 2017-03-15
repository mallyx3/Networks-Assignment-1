
public class stpPacket {
	private int seqNumber;
	private int AckNumber;
	private boolean SYN;
	private boolean ACK;
	private boolean FIN;
	private int length;
	private byte[] data;
	
	/**
	 * Return's an stpPacket's byte array
	 * @return
	 */
	public byte[] getBytes () {
		byte[] bytes = new byte[15+this.length];
		bytes[0] = (byte) (this.seqNumber >> 24);
		bytes[1] = (byte) (this.seqNumber >> 16);
		bytes[2] = (byte) (this.seqNumber >> 8);
		bytes[3] = (byte) (this.seqNumber);
		bytes[4] = (byte) (this.AckNumber >> 24);
		bytes[5] = (byte) (this.AckNumber >> 16);
		bytes[6] = (byte) (this.AckNumber >> 8);
		bytes[7] = (byte) (this.AckNumber);
		
		bytes[8] = (byte) (this.SYN ? 1 : 0);
		bytes[9] = (byte) (this.ACK ? 1 : 0);
		bytes[10] = (byte) (this.FIN? 1: 0);
		bytes[11] = (byte) (this.length >> 24);
		bytes[12] = (byte) (this.length >> 16);
		bytes[13] = (byte) (this.length >> 8);
		bytes[14] = (byte) (this.length);
		if (!(this.data == null)) {
			System.arraycopy(this.data, 0 , bytes, 15, length);
		}
		return bytes;
	}
	
	public stpPacket(int size, boolean syn, boolean ack, boolean fin, int seqNo, int AckNO, byte[] d) {
		this.length = size;
		this.SYN = syn;
		this.ACK = ack;
		this.FIN = fin;
		this.seqNumber = seqNo;
		this.AckNumber = AckNO;
		this.data = d;
	}

	public int getLength() {
		return length;
	}

	public void setLength(int length) {
		this.length = length;
	}

	public boolean isSYN() {
		return SYN;
	}

	public void setSYN(boolean sYN) {
		SYN = sYN;
	}

	public boolean isACK() {
		return ACK;
	}

	public void setACK(boolean aCK) {
		ACK = aCK;
	}

	public boolean isFIN() {
		return FIN;
	}

	public void setFIN(boolean fIN) {
		FIN = fIN;
	}

	public int getSeqNumber() {
		return seqNumber;
	}

	public void setSeqNumber(int seqNumber) {
		this.seqNumber = seqNumber;
	}

	public int getAckNumber() {
		return AckNumber;
	}

	public void setAckNumber(int ackNumber) {
		AckNumber = ackNumber;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	
	
	
	
}
