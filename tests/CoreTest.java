import io.github.nannxi.epaper.*;
import java.util.*;
public final class CoreTest {
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  int[] p=new int[212*104];Arrays.fill(p,0xffffffff);byte[] a=ImageProcessor.pack(p);for(byte b:a)check(b==(byte)255,"white");
  int[][] points={{0,0},{211,0},{0,103},{211,103},{100,50}};
  for(int[] point:points){Arrays.fill(p,0xffffffff);int x=point[0],y=point[1],index=(211-x)*13+y/8,mask=0x80>>(y%8);p[y*212+x]=0xff000000;a=ImageProcessor.pack(p);check((a[index]&mask)==0&&(a[2756+index]&mask)!=0,"black coordinate");p[y*212+x]=0xffdc1c2d;a=ImageProcessor.pack(p);check((a[index]&mask)==0&&(a[2756+index]&mask)==0,"red coordinate");}
  Arrays.fill(p,0xff808080);int count=0;for(int d=0;d<3;d++){int[] colors=ImageProcessor.convert(p,0,0,d,true);for(int c:colors){check(c==0xffffffff||c==0xff000000||c==0xffdc1c2d,"palette");if(c==0xff000000)count++;}}
  check(count>0,"dither");check(Wire.crc("123456789".getBytes(),9)==0xcbf43926L,"crc32 standard vector");byte[] req=Wire.packet(2,0x1234,0x89abcdefL,44,new byte[]{1,2,3});check(Wire.u16(req,6)==0x1234&&Wire.u32(req,8)==0x89abcdefL&&Wire.u16(req,12)==44&&req[14]==3,"packet fields");check(Wire.crc(req,60)==Wire.u32(req,60),"packet crc");System.out.println("PASS: palette, native orientation/corners, plane polarity, dithering, CRC32 and wire layout");
 }
}
