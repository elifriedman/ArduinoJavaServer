package streamer;
/**
 * Write a description of class Debug here.
 * 
 * @author (your name) 
 * @version (a version number or a date)
 */
public class Debug
{
    public static boolean on = true;
    public static void print(String s) {
        if(on)
            System.out.println(s);
    }
}
