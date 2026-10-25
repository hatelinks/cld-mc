import dev.cld.tcw.gen.*;
public class Find { public static void main(String[] a){
 Macro m=new Macro(42); Macro.Sample s=new Macro.Sample();
 for(Macro.Style st:Macro.STYLES){ double best=-1,bx=0,bz=0;
  for(int i=-40;i<=40;i++)for(int j=-40;j<=40;j++){double x=i*400,z=j*400; m.sample(x,z,s);
   if(s.style[st.ordinal()]>0.9 && s.coast>800){double sc=s.height+ (s.coast<3000?200:0); if(st==Macro.Style.MESA||st==Macro.Style.KARST||st==Macro.Style.PLATEAU) sc=s.coast; if(sc>best){best=sc;bx=x;bz=z;}}}
  System.out.println(st+" "+bx+" "+bz+" "+best);}
}}
