"""PC-side downloader for the Keil C application; MCU code is entirely C/ASM."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,json,struct,time,traceback
from elftools.elf.elffile import ELFFile
from pyocd.core.helpers import ConnectHelper
from pyocd.core.target import Target
from samr21_swd_flash import Programmer,sha,SN,SIZE,FACTORY_SHA

PROJECT=Path(__file__).resolve().parents[1]
FIELDS=['magic','stage','error','ticks_ms','command','completed','last_epd_command','busy',
        'busy_wait_ms','refresh_ms','temperature_raw','border','bytes_sent']

def symbols():
    with (PROJECT/'Objects/epd_white.axf').open('rb') as stream:
        elf=ELFFile(stream)
        return {s.name:int(s['st_value']) for s in elf.get_section_by_name('.symtab').iter_symbols()}

def status(target,address):
    values=target.read_memory_block32(address,len(FIELDS))
    return dict(zip(FIELDS,values))

def connect():
    session=ConnectHelper.session_with_chosen_probe(blocking=False,unique_id=SN,options={
        'target_override':'cortex_m','frequency':1000000,'connect_mode':'attach','auto_unlock':False,
        'resume_on_disconnect':False,'pack.debug_sequences.enable':False,'no_config':True,
        'cache.enable_memory':False,'cache.enable_register':False})
    if session is None: raise RuntimeError('Expected ST-Link is not connected')
    return session

def run():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['flash','status','white','black-border','white-border','pattern','red','demo'])
    args=parser.parse_args()
    sym=symbols()
    base=sym['g_status']
    folder=PROJECT/'Runs'/datetime.now().strftime('%Y%m%d_%H%M%S_%f')
    folder.mkdir(parents=True)
    report={'utc':datetime.now(timezone.utc).isoformat(),'action':args.action,
            'events':[],'errors':[],'success':False,'status_address':hex(base)}
    with connect() as session:
        p=Programmer(session.target,report,folder)
        try:
            if args.action=='flash':
                image=(PROJECT/'Objects/epd_white.bin').read_bytes()
                sp,reset=struct.unpack_from('<II',image)
                if not 0x20000000 < sp <= 0x20008000 or reset!=sym['Reset_Handler'] or not reset&1:
                    raise RuntimeError('BIN vector/ELF consistency check failed')
                if not 0<len(image)<=0x18000: raise RuntimeError('Image exceeds reserved application range')
                factory=Path(__file__).with_name('original_flash_256KiB_read1.bin').read_bytes()
                if len(factory)!=SIZE or sha(factory)!=FACTORY_SHA: raise RuntimeError('Factory recovery image failed hash check')
                before,aux=p.preflight(factory,force_reset=True,allow_nonoriginal=True)
                size=(len(image)+255)&~255
                padded=image+b'\xff'*(size-len(image))
                expected=padded+before[size:]
                report['firmware_bytes']=len(image)
                report['firmware_sha256']=sha(image)
                report['programmed_rows']=size//256
                p.configure()
                rows=list(range(256,size,256))+[0]  # Reset vector last.
                p.safe_to_run=False
                for address in rows:
                    p.write_row(address,padded[address:address+256])
                    p.safe_to_run=False
                after=p.read(0,SIZE)
                (folder/'after_flash_verified.bin').write_bytes(after)
                if after!=expected: raise RuntimeError('Full flash readback differs outside/inside programmed area')
                p.check_aux(aux)
                report['full_flash_readback_match']=True
                report['unwritten_flash_preserved']=True
                p.restore_config()
                p.safe_to_run=True
                p.reset_halt()
                if (p.t.read_core_register('pc')&~1)!=(reset&~1): raise RuntimeError('Application reset entry mismatch')
                p.t.resume()
                p.event('application_started',reset_pc=hex(reset&~1),image_bytes=len(image),programmed_rows=size//256)
            elif args.action!='status':
                p.t.halt()
                current=status(p.t,base)
                if current['magic']!=0x45504431 or current['stage'] not in [6,0xe0]:
                    raise RuntimeError('Application is not idle; refusing another refresh')
                code={'white':1,'black-border':2,'white-border':3,'pattern':4,'red':5,'demo':6}[args.action]
                p.t.write32(base+16,code)
                p.t.flush()
                p.t.resume()
            if args.action=='status':
                report['application_status']=status(p.t,base)
                report['success']=report['application_status']['magic']==0x45504431
            else:
                deadline=time.monotonic()+60
                previous_stage=None
                while time.monotonic()<deadline:
                    current=status(p.t,base)
                    if current['magic']==0x45504431:
                        if current['stage']!=previous_stage:
                            p.event('application_stage',**current)
                            previous_stage=current['stage']
                        if current['stage'] in [6,0xe0] and current['command']==0:
                            report['application_status']=current
                            if current['stage']==0xe0: raise RuntimeError('Application error code '+hex(current['error']))
                            report['success']=True
                            break
                    time.sleep(.2)
                if not report['success']: raise RuntimeError('Timed out observing application')
            if report['success'] and 'g_framebuffer' in sym:
                buffer=bytes(p.t.read_memory_block8(sym['g_framebuffer'],5512))
                (folder/'framebuffer.bin').write_bytes(buffer)
                report['framebuffer_sha256']=sha(buffer)
        except Exception as exc:
            report['errors'].append(str(exc))
            traceback.print_exc()
            if p.pending or (args.action=='flash' and not report.get('full_flash_readback_match')):
                p.safe_to_run=False
                p.t.halt()
        finally:
            try:
                if p.safe_to_run:
                    p.restore_config()
                    if p.t.get_state()==Target.State.HALTED: p.t.resume()
                report['final_state']=str(p.t.get_state())
            finally:
                (folder/'result.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    print(json.dumps(report,indent=2),flush=True)
    return 0 if report['success'] else 1

if __name__=='__main__': raise SystemExit(run())
