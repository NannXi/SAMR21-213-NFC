package io.github.nannxi.epaper;
import java.util.zip.CRC32;
public final class Wire {
    private Wire() {}
    public static long crc(byte[] bytes,int length) {CRC32 c=new CRC32();c.update(bytes,0,length);return c.getValue();}
    public static int u16(byte[] p,int i) {return (p[i]&255)|((p[i+1]&255)<<8);}
    public static long u32(byte[] p,int i) {return (p[i]&255L)|((p[i+1]&255L)<<8)|((p[i+2]&255L)<<16)|((p[i+3]&255L)<<24);}
    public static void put16(byte[] p,int i,int v) {p[i]=(byte)v;p[i+1]=(byte)(v>>8);}
    public static void put32(byte[] p,int i,long v) {for(int n=0;n<4;n++)p[i+n]=(byte)(v>>(8*n));}
    public static byte[] packet(int op,int seq,long transfer,int offset,byte[] payload) {
        if(payload.length>44)throw new IllegalArgumentException("Payload too large");
        byte[] p=new byte[64];p[0]='E';p[1]='P';p[2]='D';p[3]='1';p[4]=1;p[5]=(byte)op;
        put16(p,6,seq);put32(p,8,transfer);put16(p,12,offset);p[14]=(byte)payload.length;
        System.arraycopy(payload,0,p,16,payload.length);put32(p,60,crc(p,60));return p;
    }
    public static boolean validReply(byte[] p) {
        return p.length==64&&p[0]=='E'&&p[1]=='P'&&p[2]=='A'&&p[3]=='1'&&p[4]==1&&u32(p,60)==crc(p,60);
    }
}
