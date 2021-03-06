package server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sensor is used for reading data from the BeagleBone Black.
 * <br><br>
 * @author Nick
 *
 */
public class Sensor implements Runnable {

	private final Queue<String> queue;  // Pointer to ConccurentLinkedQueue<String>
	private final ExecutorService ex;   // Used to create a worker thread to send client realtime data

	private final BufferedWriter bf; // For realtime temp data flow
	private int sleep;               // How often the sensor data is read
	private boolean realtime;        // If the sensor is in realtime mode

	private boolean stop;

	/**
	 * Creates a new Sensor Thread that will read data from the temperature sensor on the 
	 * Beagle Bone Black.
	 * <br><br>
	 * The sensor data will be saved into a ConccurentLinkedQueue in order to allow for
	 * easier management of data between the Sensor thread and the FileWriter Thread.
	 * <br><br>
	 * @param queue - The ConcurrentLinkedQueue<String> for temperature data
	 * @param bf - BufferedWriter on the output stream of the client socket
	 * @param sleep - Time in milliseconds for the sensor to read
	 * @param realtime - boolean if data should be directly sent to client or not
	 */
	public Sensor(Queue<String> queue, BufferedWriter bf, int sleep, boolean realtime) {
		this.bf = bf;
		this.sleep = sleep;
		this.realtime = realtime;
		this.queue = queue;
		this.stop = false;
		this.ex = Executors.newSingleThreadExecutor();
	}

	/**
	 * Method gets the sensor data as a temperature
	 */
	public void run() {
		while (!stop) {
			try {
				// Read the sensor data, put into queue, and send if realtime
				readSensor();
				Thread.sleep(sleep);
			} catch (InterruptedException e1) {
				/*
				 * handle 
				 */
			}
		}

		// Clean up the thread
		ex.shutdown();

		// Write remaining queue to file
		synchronized (queue) {
			queue.notifyAll();
		}
	}

	/**
	 * Reads the data from the temperature sensor.
	 * <br><br>
	 * The value obtained from the sensor is sent to a worker thread through
	 * the java executor service where it is sent to the client as a String.
	 * <br><br>
	 * The data is formatted as a String object and placed into a ConcurrentLinkedQueue.
	 * <br><br>
	 * Finally, a notifyAll() command is done on the queue, waking up the file writing thread
	 * to attempt to write to a file on the server side.
	 */
	private void readSensor() {

		float temp = -1;


		// Execute I2C command to get data
		try {
			temp = getTempI2C();
		} catch (IOException | InterruptedException e) {
		}

		// Send to client if real-time
		if (realtime) {
			ex.execute(new ClientSender(bf, temp));
		}

		// Create proper readout and put it in the queue
		queue.add(new Readout(temp).toString());

		// Don't allow other threads to manipulate data while it is being written to file
		synchronized (queue) {
			queue.notifyAll();
		}	
	}

	/**
	 * Code From http://www.mcu-turkey.com/beaglebone-tmp102-sicaklik-sensoru-ile-i2c-java-haberlesme-uygulamasi/
	 * 
	 * @return - Temperature as a float value
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private float getTempI2C() throws IOException, InterruptedException {

		Process p=Runtime.getRuntime().exec("i2cget -y 1 0x49 0x00 w"); //shell command
		p.waitFor();

		InputStreamReader in = new InputStreamReader(p.getInputStream());
		BufferedReader br = new BufferedReader(in);

		String readVal = br.readLine(); //"0x110B"
		float constVal = (float) 0.0625; // Constant to multiply by to get result
		@SuppressWarnings("unused")
		char[] stringArray;// holds bits
		stringArray = readVal.toCharArray();

		String out = "";
		char ca = readVal.charAt(4);
		out = Character.toString(ca);
		out = out + readVal.charAt(5);
		out = out + readVal.charAt(2);
		out = out + readVal.charAt(3);

		int outInt = Integer.parseInt(out.trim(),16);
		outInt = outInt >> 4; // Shift bits by 4
		br.close();
		in.close();
		return convertToF(outInt * constVal); 
	}
	
	/**
	 * Converts C to F.
	 * @param test - Degree in C
	 * @return - Degrees in F
	 */
	private float convertToF(float test) {
		return (float) ((test * (9.0 / 5.0)) + 32.0);
	}

	public int getSleep() {
		return sleep;
	}

	public void setSleep(int sleep) {
		this.sleep = sleep;
	}

	public boolean isRealtime() {
		return realtime;
	}

	public void setRealtime(boolean realtime) {
		this.realtime = realtime;
	}

	public boolean isStop() {
		return stop;
	}

	public void setStop(boolean stop) {
		this.stop = stop;
	}
}
