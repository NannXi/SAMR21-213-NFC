"""SWD->I2C->SRAM protocol validation. Does NOT validate phone/RF operation."""
from pathlib import Path
import sys,time,struct,zlib,json,hashlib
ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'firmware/Tools'))
from flash_app import connect,symbols
sym=symbols();base=sym['g_nfc'];status=sym['g_status'];report={'transport':'SWD debug commands -> MCU SERCOM1 hardware I2C -> NFC SRAM -> MCU protocol','phone_rf_tested':False,'checks':[]}
def packet(op,seq,tid,offset,payload=b''):
 b=bytearray(64);b[:4]=b'EPD1';struct.pack_into('<BBHIHB',b,4,1,op,seq,tid,offset,len(payload));b[16:16+len(payload)]=payload;struct.pack_into('<I',b,60,zlib.crc32(b[:60]));return b
with connect() as session:
 t=session.target
 def cmd(code,arg=0,data=None):
  before=t.read32(base+20)
  if data is not None:t.write_memory_block8(base+112,data)
  t.write32(base+16,arg);t.write32(base+12,code);t.flush();end=time.monotonic()+4
  while time.monotonic()<end:
   if t.read32(base+20)!=before:
    err=t.read32(base+8)
    if err:
     if time.monotonic()<end-.05:
      before=t.read32(base+20);time.sleep(.012);t.write32(base+16,arg);t.write32(base+12,code);t.flush();continue
     raise RuntimeError(f'debug command {code}/{arg:02x}: {err:x}')
    return bytes(t.read_memory_block8(base+112,16))
   time.sleep(.001)
  raise TimeoutError('debug command')
 def read():return b''.join(cmd(1,0xf8+i) for i in range(4))
 def write(b):
  cmd(2,0xf8,bytes(16))
  for i in range(1,4):cmd(2,0xf8+i,b[16*i:16*i+16])
  cmd(2,0xf8,b[:16])
 def exchange(b):
  for attempt in range(4):
   write(b);end=time.monotonic()+2.2
   while time.monotonic()<end:
    a=read()
    if a[:4]==b'EPA1' and zlib.crc32(a[:60])==struct.unpack_from('<I',a,60)[0] and a[6:12]==b[6:12] and a[5]==(b[5]|128):return a
    time.sleep(.005)
  raise TimeoutError('protocol acknowledgement')
 def check(value,name):
  assert value,name
  report['checks'].append(name);print('PASS: '+name,flush=True)
 check(t.read32(base+4)==5,'volatile SRAM mirror enabled')
 check(cmd(3,2)[0]==0x24 and cmd(3,1)[0]==0x27,'RF mailbox 90h..9fh and final-block read trigger configured')
 check(cmd(1,0)[10:12]==bytes.fromhex('f0ff'),'factory static page locks preserved')
 locks=cmd(1,0x38)[8:11]
 check(locks==bytes.fromhex('ff0000') and (locks[1]&1)==0,'dynamic locks FF0000 preserved, target page lock bit is clear')
 before=bytes(t.read_memory_block8(base+128,896));frame=bytes(t.read_memory_block8(sym['g_framebuffer'],5512));completed=t.read32(status+20)
 tid=0x21320261;crc=zlib.crc32(frame);begin=struct.pack('<HHIHB',212,104,crc,len(frame),1)
 ack=exchange(packet(1,0,tid,0,begin));check(ack[15]==0 and ack[16]==1,'BEGIN accepted')
 first=packet(2,1,tid,0,frame[:44]);ack=exchange(first);check(ack[15]==0 and struct.unpack_from('<H',ack,12)[0]==44,'first chunk accepted')
 ack=exchange(first);check(ack[15]==0 and struct.unpack_from('<H',ack,12)[0]==44,'duplicate DATA is idempotent')
 ack=exchange(packet(2,2,tid,45,frame[44:88]));check(ack[15]==2 and struct.unpack_from('<H',ack,12)[0]==44,'wrong offset rejected without advancing')
 bad=packet(2,2,tid,44,frame[44:88]);bad[60]^=1;count=t.read32(base+40);write(bad);time.sleep(.05);check(t.read32(base+40)>count and t.read32(base+44)==44,'bad packet CRC rejected')
 seq=2
 for offset in range(44,len(frame),44):
  data=frame[offset:offset+44];ack=exchange(packet(2,seq,tid,offset,data));seq+=1
  check(ack[15]==0 and struct.unpack_from('<H',ack,12)[0]==offset+len(data),'full image received') if offset+len(data)==len(frame) else None
  if seq%25==0:print('TRANSFER: '+str(offset+len(data))+'/5512',flush=True)
 check(t.read32(status+20)==completed,'no screen refresh before COMMIT')
 write(packet(3,seq,tid,len(frame)));end=time.monotonic()+3
 while time.monotonic()<end:
  ack=bytes(t.read_memory_block8(sym['reply'],64))
  if ack[5]==0x83 and ack[16]==2 and t.read8(sym['reply_pending'])==0:break
  time.sleep(.005)
 check(ack[5]==0x83 and ack[15]==0 and ack[16]==2 and t.read8(sym['reply_pending'])==0,'COMMIT reply physically published before blocking display call')
 end=time.monotonic()+45
 while t.read32(status+20)==completed and time.monotonic()<end:time.sleep(.2)
 check(t.read32(status+20)==completed+1 and t.read32(status+8)==0,'physical display refresh completed')
 time.sleep(.1);ack=read();check(ack[:4]==b'EPA1' and ack[16]==3 and ack[15]==0,'refresh DONE acknowledgement published')
 check(bytes(t.read_memory_block8(sym['g_framebuffer'],5512))==frame,'received frame matches 5512 native bytes')
 # The startup capture covers EEPROM blocks 0..55. Re-read physically after transfer.
 after=b''.join(cmd(1,i) for i in range(56));check(before==after,'NFC EEPROM unchanged during I2C test')
 report.update(uid=before[:7].hex(),frame_crc32=f'{crc:08x}',frame_sha256=hashlib.sha256(frame).hexdigest(),eeprom_sha256=hashlib.sha256(after).hexdigest(),nfc_status=t.read_memory_block32(base,14),display_status=t.read_memory_block32(status,13))
(ROOT/'docs/hardware_protocol_validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2),flush=True)
