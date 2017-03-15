
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Timer;

public class Test1 {

	
	public static void main(String[] args) {
		System.out.println("start");

		  ActionListener taskPerformer = new ActionListener() {
			  public void actionPerformed(ActionEvent evt) {
				  System.out.println("timer working");
	          }
		  };
	      Timer timer = new Timer(50, taskPerformer);
	      timer.start();
	      try {

		      Thread.sleep(10000);
	      } catch (Exception e) {
	    	  System.out.println(e);
	      }
	      
	}
}
