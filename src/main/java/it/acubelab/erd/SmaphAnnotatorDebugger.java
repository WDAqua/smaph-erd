package it.acubelab.erd;

import java.io.FileNotFoundException;
import java.io.PrintStream;

public class SmaphAnnotatorDebugger {
	public static PrintStream out = System.out;
	public static void activate(){
		out = System.out;
	}
	public static void disable(){
		try {
			out = new PrintStream("/dev/null");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
