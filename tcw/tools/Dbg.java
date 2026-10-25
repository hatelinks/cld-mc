import dev.cld.tcw.gen.*;
import javax.imageio.ImageIO; import java.awt.image.BufferedImage; import java.io.File;
public class Dbg { public static void main(String[] a) throws Exception {
 Macro m=new Macro(42); int n=512; double k=96; BufferedImage img=new BufferedImage(n,n,1); Macro.Sample s=new Macro.Sample();
 for(int j=0;j<n;j++)for(int i=0;i<n;i++){double x=(i-n/2)*k,z=(j-n/2)*k; m.sample(x,z,s);
  int c = s.coast>0 ? (s.height<63? 0xff0000 : 0x00a000) : (s.height>=63?0xffff00:0x0000a0);
  if(Math.abs(s.coast)<60)c=0xffffff; img.setRGB(i,j,c);}
 ImageIO.write(img,"png",new File(a[0]));}}
