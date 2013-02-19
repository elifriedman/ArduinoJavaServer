package streamer;

import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
public class ImageCapture {
	private static final String NAME = "images/img_";
	private static final String TYPE = ".jpg";
	private Capture c;
	private int i,lastCleanup;
	private static final int CLNUP_INTVAL = 100;
	private Runtime deleter;
	AffineTransformOp op;
	public ImageCapture() {
		AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
	    tx.translate(-Capture.WIDTH*2, 0);
	    op = new AffineTransformOp(tx,
	        AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
		c = new Capture();
		
		i = lastCleanup = 0;
		deleter = Runtime.getRuntime();
		try {
			deleter.exec("rm /Library/WebServer/Documents/HTML/LiveStreaming/images/*");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getImage() {
		try {
			if(i - lastCleanup == CLNUP_INTVAL) {
				deleter.exec("./delete "+lastCleanup + " " + (i-1));
				lastCleanup = i;
			}
			File f = new File(NAME+(i++)+TYPE);
		
			ImageIO.write(op.filter(c.getScreen(),null), "jpg", f);
			return f.getName();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
