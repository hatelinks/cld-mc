import dev.cld.tcw.gen.*;
public class Bench { public static void main(String[] a){
 Terrain t=new Terrain(42); Macro m=t.macro;
 long t0=System.nanoTime(); Macro.Sample s=new Macro.Sample(); double acc=0;
 for(int i=0;i<200000;i++){acc+=m.sample(i*7.3,i*3.1,s).height;}
 System.out.println("macro us "+(System.nanoTime()-t0)/200000/1000.0);
 t0=System.nanoTime(); Hydro h=t.hydro(0,0); System.out.println("hydro ms "+(System.nanoTime()-t0)/1e6+" segs "+h.segments());
 Terrain.Column c=new Terrain.Column(); double cx=m.cellCenterX(0,0), cz=m.cellCenterZ(0,0);
 t0=System.nanoTime(); for(int i=0;i<100000;i++){acc+=t.column(cx+(i%300)*4,cz+(i/300)*4,c).height;}
 System.out.println("column us "+(System.nanoTime()-t0)/100000/1000.0+" "+acc);
}}
