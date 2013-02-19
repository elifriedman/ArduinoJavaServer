package streamer;
/**
 * Write a description of class Capture here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
import java.awt.Robot;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;

import java.io.IOException;
import java.io.ObjectOutputStream;

public class Capture
{
	public static int WIDTH = 639-317;
    private static final Rectangle RECT = new Rectangle(317,57,639,466);
    private Robot rob;
    private ImageIcon ii;
    
    public Capture() {
        try {
            rob = new Robot();
        } catch (java.awt.AWTException awt) {
                System.err.println("Now why would an error ever occur here?: " + awt);
                System.exit(-2);
        }
    }

    public BufferedImage getScreen() {
    	return rob.createScreenCapture(RECT);
    }
}
