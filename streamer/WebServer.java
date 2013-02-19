package streamer;

import java.io.*;
import java.net.*;
import java.util.*;


/**
 * This is a very simple, multi-threaded HTTP server, which is 
 * based on the one that came with the Arduino source code.
 */

public class WebServer implements HttpConstants {
	public static void main(String args[]) {
		try {
			WebServer.launch();
		} catch (IOException ioe) {
			
		}
	}
	static final String SERIAL_PORT = "/dev/tty.usbmodem621";
	static final int SERIAL_DEBUG_RATE = 9600;
	static final char SERIAL_PARITY = 'N';
	static final int SERIAL_DATABITS = 8;
	static final float SERIAL_STOPBITS = 1;
	static String id = "null";
	static ArduinoSerial ard;
    /* Where worker threads stand idle */
    @SuppressWarnings("rawtypes")
	static Vector threads = new Vector();

    /* timeout on client connections */
    static int timeout = 10000;

    /* max # worker threads */
    static int workers = 5;

    /* print to the log file */
    @SuppressWarnings("unused")
	protected static void log(String s) {
      if (false) {
        System.out.println(s);
      }
    }

    static ImageCapture ic = new ImageCapture();
    
    static public void launch() throws IOException {  
    	ard = new ArduinoSerial(SERIAL_PORT,
				SERIAL_DEBUG_RATE,
				SERIAL_PARITY,
				SERIAL_DATABITS,
				SERIAL_STOPBITS);
        final int port = 8080;
        
        Runnable r = new Runnable() {
          public void run() {
            try {
              ServerSocket ss = new ServerSocket(port);
              while (true) {
                Socket s = ss.accept();
                WebServerWorker w = null;
                synchronized (threads) {
                  if (threads.isEmpty()) {
                    WebServerWorker ws = new WebServerWorker();
                    ws.setSocket(s);
                    (new Thread(ws, "additional worker")).start();
                  } else {
                    w = (WebServerWorker) threads.elementAt(0);
                    threads.removeElementAt(0);
                    w.setSocket(s);
                  }
                }
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          }
        };
        new Thread(r).start();
 
    }
}


class WebServerWorker implements HttpConstants, Runnable {
	final static String PATH = "/Library/WebServer/Documents";
  
    final static int BUF_SIZE = 2048;

    final static byte[] EOL = { (byte)'\r', (byte)'\n' };
	
    final static int FILE = 0;
    final static int UPDATE_STREAM = 1;
    final static int CONTROL = 2;
    
    /* buffer to use for requests */
    byte[] buf;
    /* Socket to client we're handling */
    private Socket s;
    static int number = 0;
    int myNumber,callTimes;
    
    WebServerWorker() {      
      buf = new byte[BUF_SIZE];
      s = null;
      myNumber = number++;
      callTimes = 0;
      
  }

    synchronized void setSocket(Socket s) {
        this.s = s;
        notify();
    }

    public synchronized void run() {
        while(true) {
            if (s == null) {
                /* nothing to do */
                try {
                    wait();
                } catch (InterruptedException e) {
                    /* should not happen */
                    continue;
                }
            }
            try {
				handleClient();
            } catch (Exception e) {
                e.printStackTrace();
            }
            /* go back in wait queue if there's fewer
             * than numHandler connections.
             */
            s = null;
            Vector pool = WebServer.threads;
            synchronized (pool) {
                if (pool.size() >= WebServer.workers) {
                     //too many threads, exit this one 
                    return;
                } else {
                    pool.addElement(this);
                }
            }
        }
    }

    
    void handleClient() throws IOException {
        InputStream is = new BufferedInputStream(s.getInputStream());
        PrintStream ps = new PrintStream(s.getOutputStream());
        // we will only block in read for this many milliseconds
        // before we fail with java.io.InterruptedIOException,
        // at which point we will abandon the connection.
        s.setSoTimeout(WebServer.timeout);
        s.setTcpNoDelay(true);
        // zero out the buffer from last time
        for (int i = 0; i < BUF_SIZE; i++) {
            buf[i] = 0;
        }
        try {
         
            int nread = 0, r = 0;

outerloop:
            while (nread < BUF_SIZE) {
                r = is.read(buf, nread, BUF_SIZE - nread);
                if (r == -1) {
                    return;  // EOF
                }
                int i = nread;
                nread += r;
                for (; i < nread; i++) {
                    if (buf[i] == (byte)'\n' || buf[i] == (byte)'\r') {
                        break outerloop;  // read one line 
                    }
                }
            }

            String msg = new String(buf);
            
            int requestType = getRequestType(msg);
            switch(requestType) {
            case FILE:
            	retrieveFile(msg,ps);
            	break;
            case UPDATE_STREAM:
            	updateStream(msg,ps);	
            	break;
            case CONTROL:
            	//Only one person is allowed to control
            	//the Arduino at a time, so check to see
            	//if this person has the correct cookie ID
            	if(msg.indexOf(WebServer.id)>0) {
            		sendToArduino(msg,ps);
            	}
            	break;
            }

        } finally {
            s.close();
        }
    }
    
    private void sendToArduino(String msg,PrintStream ps) {
    	ps.print("HTTP/1.0 " + HTTP_OK+" OK");
		int[] x = new int[4];
		int checksum = 99;
		x[0] = msg.charAt(msg.indexOf("L=")+2);
		x[1] = msg.charAt(msg.indexOf("U=")+2);
		x[2] = msg.charAt(msg.indexOf("R=")+2);
		x[3] = msg.charAt(msg.indexOf("D=")+2);
		WebServer.ard.write((char)checksum);
		for(int i = 0; i < x.length;i++) {
			WebServer.ard.write((char)x[i]);
		}
	}

	private void updateStream(String msg, PrintStream ps) throws IOException {
    	ps.print("HTTP/1.0 " + HTTP_OK+" OK");
    	ps.write(EOL);
    	ps.println("Content-type: text/event-stream");
        ps.write(EOL);
    	ps.print("Cache-Control: no-cache");
        ps.write(EOL);
        
        String data = WebServer.ic.getImage();
        ps.print("data: " + data);
        ps.write(EOL);
        if(msg.indexOf("Chrome") <= 0)
        	ps.print("retry: 70");
        ps.write(EOL);
	}

	private void retrieveFile(String msg, PrintStream ps) throws IOException {
    	int i = msg.indexOf(' ') + 1;
		int index = msg.indexOf(' ', i);
		String fname = msg.substring(i, index);

		File f = new File(PATH,fname);
		if (f.isDirectory()) {
			File ind = new File(f, "index.html");
			if (ind.exists()) {
				f = ind;
			}
		}
		
		boolean ok = printHeaders(f, ps);
		if (f.exists()) {
		  if (ok) {
			sendFile(f, ps);
		  }
		} else {
		  send404(ps);
		}
	}

	private int getRequestType(String msg) {
		
		if(msg.indexOf("updateStream") > 0 ) {
    		return UPDATE_STREAM;
    	} else if(msg.indexOf("dataStream")>0) {
    		return CONTROL;
    	} else
    		return FILE;
	}

	boolean printHeaders(File targ, PrintStream ps) throws IOException {
		boolean ret = false;
        int rCode = 0;
        if (!targ.exists() || targ.getName().indexOf("..")>0) {
            rCode = HTTP_NOT_FOUND;
            ps.print("HTTP/1.0 " + HTTP_NOT_FOUND + " Not Found");
            ps.write(EOL);
            ret = false;
        }  else {
            rCode = HTTP_OK;
            ps.print("HTTP/1.0 " + HTTP_OK+" OK");
            ps.write(EOL);
            ret = true;
        }
        WebServer.log("From " +s.getInetAddress().getHostAddress()+": GET " + targ.getAbsolutePath()+"-->"+rCode);
        ps.print("Server: simpleness");
        ps.write(EOL);
        ps.print("Date: " + (new Date()));
        ps.write(EOL);
        if (ret) {
            if (!targ.isDirectory()) {
                ps.print("Content-length: " + targ.length());
                ps.write(EOL);
                ps.print("Last Modified: " + new Date(targ.lastModified()));
                ps.write(EOL);
                String name = targ.getName();
                int ind = name.lastIndexOf('.');
                String ct = null;
                if (ind > 0) {
                    ct = (String) map.get(name.substring(ind));
                }
                if (ct == null) {
                    ct = "unknown/unknown";
                }
                ps.print("Content-type: " + ct);
                ps.write(EOL);
            } else {
                ps.print("Content-type: text/html");
                ps.write(EOL);
            }
            if(targ.getName().equals("FileWriter.html") && WebServer.id.equals("null")) {
            	WebServer.id = Double.toString(Math.random());
            	ps.print("Set-Cookie: zehut="+WebServer.id);
            	ps.write(EOL);
            }
        }
        return ret;
    }

    
    void send404(PrintStream ps) throws IOException {
        ps.write(EOL);
        ps.write(EOL);
        ps.print("<html><body><h1>404 Not Found</h1>"+
                   "The requested resource was not found.</body></html>");
        ps.write(EOL);
        ps.write(EOL);
    }

    
    void sendFile(File targ, PrintStream ps) throws IOException {
        InputStream is = null;
        ps.write(EOL);
        if (targ.isDirectory()) {
            //listDirectory(targ, ps);
            return;
        } else {
            is = new FileInputStream(targ.getAbsolutePath());
        }
        sendFile(is, ps);
    }
    
    
    void sendFile(InputStream is, PrintStream ps) throws IOException {
        try {
            int n;
            while ((n = is.read(buf)) > 0) {
                ps.write(buf, 0, n);
            }
        } finally {
            is.close();
        }
    }

    /* mapping of file extensions to content-types */
    static java.util.Hashtable map = new java.util.Hashtable();

    static {
        fillMap();
    }
    static void setSuffix(String k, String v) {
        map.put(k, v);
    }

    static void fillMap() {
        setSuffix("", "content/unknown");

        setSuffix(".uu", "application/octet-stream");
        setSuffix(".exe", "application/octet-stream");
        setSuffix(".ps", "application/postscript");
        setSuffix(".zip", "application/zip");
        setSuffix(".sh", "application/x-shar");
        setSuffix(".tar", "application/x-tar");
        setSuffix(".snd", "audio/basic");
        setSuffix(".au", "audio/basic");
        setSuffix(".wav", "audio/x-wav");
        
        setSuffix(".gif", "image/gif");
        setSuffix(".jpg", "image/jpeg");
        setSuffix(".jpeg", "image/jpeg");
        
        setSuffix(".htm", "text/html");
        setSuffix(".html", "text/html");
        setSuffix(".css", "text/css"); 
        setSuffix(".js", "application/javascript");
		setSuffix(".xml","application/xml");
        
        setSuffix(".txt", "text/plain");
        setSuffix(".java", "text/plain");
        
        setSuffix(".c", "text/plain");
        setSuffix(".cc", "text/plain");
        setSuffix(".c++", "text/plain");
        setSuffix(".h", "text/plain");
        setSuffix(".pl", "text/plain");
    }

    void listDirectory(File dir, PrintStream ps) throws IOException {
        ps.println("<TITLE>Directory listing</TITLE><P>\n");
        ps.println("<A HREF=\"..\">Parent Directory</A><BR>\n");
        String[] list = dir.list();
        for (int i = 0; list != null && i < list.length; i++) {
            File f = new File(dir, list[i]);
            if (f.isDirectory()) {
                ps.println("<A HREF=\""+list[i]+"/\">"+list[i]+"/</A><BR>");
            } else {
                ps.println("<A HREF=\""+list[i]+"\">"+list[i]+"</A><BR");
            }
        }
        ps.println("<P><HR><BR><I>" + (new Date()) + "</I>");
    }

}


interface HttpConstants {
    /** 2XX: generally "OK" */
    public static final int HTTP_OK = 200;
    public static final int HTTP_CREATED = 201;
    public static final int HTTP_ACCEPTED = 202;
    public static final int HTTP_NOT_AUTHORITATIVE = 203;
    public static final int HTTP_NO_CONTENT = 204;
    public static final int HTTP_RESET = 205;
    public static final int HTTP_PARTIAL = 206;

    /** 3XX: relocation/redirect */
    public static final int HTTP_MULT_CHOICE = 300;
    public static final int HTTP_MOVED_PERM = 301;
    public static final int HTTP_MOVED_TEMP = 302;
    public static final int HTTP_SEE_OTHER = 303;
    public static final int HTTP_NOT_MODIFIED = 304;
    public static final int HTTP_USE_PROXY = 305;

    /** 4XX: client error */
    public static final int HTTP_BAD_REQUEST = 400;
    public static final int HTTP_UNAUTHORIZED = 401;
    public static final int HTTP_PAYMENT_REQUIRED = 402;
    public static final int HTTP_FORBIDDEN = 403;
    public static final int HTTP_NOT_FOUND = 404;
    public static final int HTTP_BAD_METHOD = 405;
    public static final int HTTP_NOT_ACCEPTABLE = 406;
    public static final int HTTP_PROXY_AUTH = 407;
    public static final int HTTP_CLIENT_TIMEOUT = 408;
    public static final int HTTP_CONFLICT = 409;
    public static final int HTTP_GONE = 410;
    public static final int HTTP_LENGTH_REQUIRED = 411;
    public static final int HTTP_PRECON_FAILED = 412;
    public static final int HTTP_ENTITY_TOO_LARGE = 413;
    public static final int HTTP_REQ_TOO_LONG = 414;
    public static final int HTTP_UNSUPPORTED_TYPE = 415;

    /** 5XX: server error */
    public static final int HTTP_SERVER_ERROR = 500;
    public static final int HTTP_INTERNAL_ERROR = 501;
    public static final int HTTP_BAD_GATEWAY = 502;
    public static final int HTTP_UNAVAILABLE = 503;
    public static final int HTTP_GATEWAY_TIMEOUT = 504;
    public static final int HTTP_VERSION = 505;
}
