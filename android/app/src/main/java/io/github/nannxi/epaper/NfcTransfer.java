package io.github.nannxi.epaper;
import android.nfc.Tag;
import android.nfc.tech.NfcA;
import android.os.SystemClock;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NfcTransfer {
    public interface Listener {void identified(String uid);void progress(int percent);void refreshing();void complete();}
    private final AtomicBoolean cancelled;
    public NfcTransfer(AtomicBoolean value) {cancelled=value;}
    private void check() throws IOException {if(cancelled.get())throw new IOException("传输已取消");}
    private byte[] transceive(NfcA nfc,byte[] command,int expected) throws IOException {
        IOException last=null;
        for(int attempt=0;attempt<12;attempt++) {
            check();
            try {
                byte[] result=nfc.transceive(command);
                if(result.length==expected&&(command[0]!=(byte)0xa2||(result[0]&15)==10))return result;
                last=new IOException("NFC 暂忙，请保持位置");
            } catch(android.nfc.TagLostException e) {throw e;}
              catch(IOException e) {last=e;}
            SystemClock.sleep(12);
        }
        throw last==null?new IOException("NFC 无响应"):last;
    }
    private byte[] read(NfcA nfc) throws IOException {
        byte[] result=new byte[64];
        for(int i=0;i<4;i++)System.arraycopy(transceive(nfc,new byte[]{0x30,(byte)(4+i*4)},16),0,result,i*16,16);
        return result;
    }
    private void page(NfcA nfc,int page,byte[] data,int offset) throws IOException {
        byte[] c=new byte[6];c[0]=(byte)0xa2;c[1]=(byte)page;System.arraycopy(data,offset,c,2,4);
        byte[] response=transceive(nfc,c,1);
        if((response[0]&15)!=10)throw new IOException("NFC 拒绝写入，检查供电和固件");
    }
    private void write(NfcA nfc,byte[] data) throws IOException {
        page(nfc,4,new byte[4],0);
        for(int i=1;i<16;i++)page(nfc,4+i,data,i*4);
        page(nfc,4,data,0);
    }
    private byte[] exchange(NfcA nfc,byte[] request) throws IOException {
        for(int retry=0;retry<4;retry++) {
            write(nfc,request);
            long end=SystemClock.elapsedRealtime()+2200;
            while(SystemClock.elapsedRealtime()<end) {
                check();byte[] reply=read(nfc);
                if(Wire.validReply(reply)&&Wire.u32(reply,8)==Wire.u32(request,8)&&Wire.u16(reply,6)==Wire.u16(request,6)&&(reply[5]&255)==((request[5]&255)|128)) {
                    if(reply[15]!=0)throw new IOException("固件校验失败，错误码 "+(reply[15]&255));
                    return reply;
                }
                SystemClock.sleep(15);
            }
        }
        throw new IOException("设备确认超时，请保持手机贴近 NFC 线圈");
    }
    public void upload(Tag tag,byte[] image,Listener listener) throws IOException {
        NfcA nfc=NfcA.get(tag);
        if(nfc==null)throw new IOException("这不是支持的 NFC-A 价签");
        try {
            nfc.connect();nfc.setTimeout(1000);
            byte[] uid=tag.getId();
            if(uid.length!=7||uid[0]!=4)throw new IOException("不是支持的 NXP 价签，未写入");
            byte[] identity=read(nfc);
            if(!Wire.validReply(identity)||identity[17]!=1||Wire.u16(identity,18)!=212||Wire.u16(identity,20)!=104||Wire.u16(identity,22)!=5512||Wire.u32(identity,28)!=0x31525742L)throw new IOException("未识别到 NFC 墨水屏固件。请检查主板供电并烧录配套固件");
            for(int i=0;i<7;i++)if(identity[32+i]!=uid[i])throw new IOException("价签 UID 与固件不一致，未写入");
            StringBuilder id=new StringBuilder();for(byte value:uid)id.append(String.format("%02X",value&255));
            listener.identified(id.toString());
            long transfer=new java.security.SecureRandom().nextInt()&0xffffffffL;if(transfer==0)transfer=1;
            byte[] begin=new byte[11];Wire.put16(begin,0,212);Wire.put16(begin,2,104);Wire.put32(begin,4,Wire.crc(image,image.length));Wire.put16(begin,8,image.length);begin[10]=1;
            exchange(nfc,Wire.packet(1,0,transfer,0,begin));
            int sequence=1;
            for(int offset=0;offset<image.length;offset+=44) {
                int length=Math.min(44,image.length-offset);
                byte[] ack=exchange(nfc,Wire.packet(2,sequence++,transfer,offset,Arrays.copyOfRange(image,offset,offset+length)));
                if(Wire.u16(ack,12)!=offset+length)throw new IOException("分块位置不一致，请重新上传");
                listener.progress((offset+length)*100/image.length);
            }
            listener.refreshing();
            byte[] commit=Wire.packet(3,sequence,transfer,image.length,new byte[0]);
            byte[] ack=exchange(nfc,commit);
            if(ack[16]==4)throw new IOException("屏幕刷新失败");
            long end=SystemClock.elapsedRealtime()+55000;
            while(SystemClock.elapsedRealtime()<end) {
                check();
                if(Wire.validReply(ack)&&Wire.u32(ack,8)==transfer) {
                    if(ack[16]==3){listener.complete();return;}
                    if(ack[16]==4)throw new IOException("屏幕刷新失败，请检查供电");
                }
                SystemClock.sleep(200);ack=read(nfc);
            }
            throw new IOException("图像已发送，但未收到刷新完成确认");
        } finally {try{nfc.close();}catch(IOException ignored){}}
    }
}
