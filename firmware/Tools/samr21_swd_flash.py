"""ATSAMR21G18A original-firmware recovery over ST-Link SWD.

Requires pyocd==0.46.0 and libusb-package. Only normal 0..0x3ffff flash
is writable here. No chip erase, fuse writes, lock changes, or SW breakpoints.
Reference: Microchip.SAMR21_DFP.2.1.155 scripts/dap_cortex-m0plus.py,
ATSAMR21G18A.atdf (NVMCTRL). Manual page-write commands use ATDF enums.
"""
from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, json, os, struct, time, traceback
from pyocd.core.helpers import ConnectHelper
from pyocd.core.target import Target

SN = os.environ.get('EPD_PROBE_SERIAL', '37FF71064E57343693001043')
FACTORY_SHA = '023efc81fb12f6bbfec844374120924c8c1e8597ea3e2884af3306df3d8639c8'
SIZE = 0x40000
NVM = 0x41004000
AUX = [('user_row', 0x804000, 256), ('aux1', 0x806000, 256),
       ('aux3', 0x80a000, 256), ('calibration', 0x800000, 8)]

def sha(data):
    return hashlib.sha256(data).hexdigest()

class Programmer:
    def __init__(self, target, report, folder):
        self.t, self.report, self.folder = target, report, folder
        self.pending = None
        self.previous_cfg = None
        self.active = set()
        self.safe_to_run = True

    def event(self, kind, **data):
        row = {'kind': kind, **data}
        self.report['events'].append(row)
        print(json.dumps(row), flush=True)
        self.save_report()

    def save_report(self):
        (self.folder/'result.json').write_text(json.dumps(self.report, indent=2), encoding='utf-8')

    def read(self, address, size):
        return bytes(self.t.read_memory_block8(address, size))

    def add_bp(self, address):
        assert self.t.set_breakpoint(address, Target.BreakpointType.HW)
        self.t.selected_core.bp_manager.flush()
        assert self.t.get_breakpoint_type(address) == Target.BreakpointType.HW
        self.active.add(address)

    def remove_bp(self, address):
        self.t.remove_breakpoint(address)
        self.t.selected_core.bp_manager.flush()
        self.active.discard(address)

    def reset_halt(self):
        # Manufacturer sequence, tested at PC=0xaa0. It does not depend on
        # any function address or on the current application being functional.
        self.t.halt()
        old_demcr=self.t.read32(0xe000edfc)
        try:
            self.t.write32(0xe000edfc,old_demcr|0x01000001)
            self.t.write32(0xe000ed0c,0x05fa0004)
            self.t.flush()
            deadline=time.monotonic()+3
            while time.monotonic()<deadline:
                dhcsr=self.t.read32(0xe000edf0)
                statusa=self.t.read8(0x41002101)
                if dhcsr&0x02000000 or statusa&2:
                    self.t.write32(0xe000edf0,0xa05f0003)
                    if statusa&2:
                        self.t.write8(0x41002101,2)
                    self.t.flush()
                if self.t.get_state()==Target.State.HALTED and not self.t.read8(0x41002101)&2:
                    self.event('reset_caught',pc=hex(self.t.read_core_register('pc')))
                    return
                time.sleep(.01)
            raise RuntimeError('Reset vector catch failed')
        finally:
            self.t.write32(0xe000edfc,old_demcr)
            self.t.flush()

    def halt(self, force_reset=False):
        if force_reset or self.t.read8(0x41002101)&2:
            self.reset_halt()
        else:
            self.t.halt()
        if self.t.get_state() != Target.State.HALTED:
            raise RuntimeError('Core must be halted before programming')

    def preflight(self, source, force_reset=False, allow_nonoriginal=False):
        self.halt(force_reset)
        regs = {'did': self.t.read32(0x41002118), 'param': self.t.read32(NVM+8),
                'statusb': self.t.read8(0x41002102), 'lock': self.t.read16(NVM+0x20),
                'wdt_ctrl': self.t.read8(0x40001000), 'pc': self.t.read_core_register('pc')}
        self.report['preflight'] = {k: hex(v) for k,v in regs.items()}
        if regs['did'] != 0x10010319 or regs['param'] != 0x31000:
            raise RuntimeError('Unexpected device or flash geometry')
        if regs['statusb'] & 1 or regs['lock'] != 0xffff:
            raise RuntimeError('Flash is protected; automatic unlocking is disabled')
        if regs['wdt_ctrl'] & 2:
            raise RuntimeError('Watchdog is enabled; abort instead of modifying its configuration')
        aux = {name: self.read(address,size) for name,address,size in AUX}
        if aux['user_row'][0] & 7 != 7:
            raise RuntimeError('BOOTPROT is enabled; fuse changes are disabled')
        for name,data in aux.items():
            (self.folder/('before_'+name+'.bin')).write_bytes(data)
        first, second = self.read(0,SIZE), self.read(0,SIZE)
        if first != second:
            raise RuntimeError('Two halted flash backups do not match')
        (self.folder/'before_flash_read1.bin').write_bytes(first)
        (self.folder/'before_flash_read2.bin').write_bytes(second)
        self.report['backup_sha256'] = sha(first)
        self.report['backup_double_read_match'] = True
        changes = [i for i,(a,b) in enumerate(zip(first,source)) if a!=b]
        self.report['before_vs_factory'] = {'different_bytes': len(changes),
            'first_offsets': [hex(x) for x in changes[:32]]}
        # This tool is for this board's verified original, not arbitrary firmware.
        # Preserve unknown current state rather than restoring a different image.
        if not allow_nonoriginal and any(not 0x3f800 <= x < 0x3f900 for x in changes):
            raise RuntimeError('Current firmware differs outside the known configuration row; recovery requires restore-original --allow-nonoriginal-current')
        self.event('preflight_passed', backup_sha256=sha(first), differing_bytes=len(changes))
        return first, aux

    def ready(self):
        deadline = time.monotonic()+1
        while True:
            flags = self.t.read8(NVM+0x14)
            if flags & 2:
                raise RuntimeError('NVM error: INTFLAG=%#x STATUS=%#x' % (flags,self.t.read16(NVM+0x18)))
            if flags & 1:
                return
            if time.monotonic() > deadline:
                raise RuntimeError('NVM READY timeout')
            time.sleep(.001)

    def command(self, command):
        self.ready()
        self.t.write16(NVM, 0xa500 | command)
        self.t.flush()
        self.ready()

    def configure(self):
        self.previous_cfg = self.t.read32(NVM+4)
        cfg = (self.previous_cfg & ~(0x1e|0x300)) | 0x1e | 0x80 | 0x40000 | 0x300
        self.t.write32(NVM+4,cfg)  # RWS=15, MANW, no cache, no sleep power reduction.
        self.t.flush()
        if self.t.read16(NVM+0x18) & 1:
            self.command(0x43)  # CPRM
        self.command(0x46)  # INVALL
        self.event('nvm_configured', original_ctrlb=hex(self.previous_cfg), programming_ctrlb=hex(cfg))

    def write_row_raw(self, address, data):
        if address % 256 or len(data) != 256 or not 0 <= address <= SIZE-256:
            raise ValueError('Only aligned normal-flash rows are allowed')
        if self.t.get_state() != Target.State.HALTED:
            raise RuntimeError('Core unexpectedly left halt')
        self.t.write32(NVM+0x1c,address>>1)
        self.command(2)
        if self.read(address,256) != b'\xff'*256:
            raise RuntimeError('Erase blank check failed at %#x' % address)
        for offset in range(0,256,64):
            page = data[offset:offset+64]
            if page == b'\xff'*64:
                continue
            self.command(0x44)  # PBC
            self.t.write_memory_block32(address+offset, list(struct.unpack('<16I',page)))
            self.t.flush()
            self.t.write32(NVM+0x1c,(address+offset)>>1)
            self.command(4)  # WP
            if self.read(address+offset,64) != page:
                raise RuntimeError('Page readback mismatch at %#x' % (address+offset))
        self.command(0x46)
        if self.read(address,256) != data:
            raise RuntimeError('Row readback mismatch at %#x' % address)

    def write_row(self, address, data):
        old = self.read(address,256)
        self.pending = (address,old)
        self.safe_to_run = False
        self.write_row_raw(address,data)
        self.pending = None
        self.safe_to_run = True

    def restore_config(self):
        if self.previous_cfg is not None:
            self.command(0x46)
            self.t.write32(NVM+4,self.previous_cfg)
            self.t.flush()
            self.previous_cfg = None

    def check_aux(self, before):
        for name,address,size in AUX:
            data = self.read(address,size)
            (self.folder/('after_'+name+'.bin')).write_bytes(data)
            if data != before[name]:
                raise RuntimeError('Auxiliary area changed: '+name)
        self.report['auxiliary_unchanged'] = True

    def smoke_boot(self):
        self.add_bp(0x1194c)
        self.add_bp(0x14bc)
        self.t.write32(0xe000ed0c,0x05fa0004)
        self.t.flush()
        time.sleep(.05)
        if self.t.read8(0x41002101)&2:
            self.t.write8(0x41002101,2)
            self.t.flush()
        if self.t.get_state() == Target.State.HALTED:
            pc = self.t.read_core_register('pc') & ~1
            if pc not in self.active:
                self.t.resume()
        commands, main_hit = [], False
        deadline = time.monotonic()+25
        while time.monotonic()<deadline:
            if self.t.get_state()!=Target.State.HALTED:
                time.sleep(.005)
                continue
            pc = self.t.read_core_register('pc')&~1
            if pc == 0x1194c:
                main_hit = True
                self.remove_bp(pc)
                self.event('original_main_reached',pc=hex(pc))
            elif pc == 0x14bc:
                command = self.t.read_core_register('r0')&255
                commands.append(command)
                if command == 0x12:
                    self.remove_bp(pc)
                    self.event('original_refresh_reached',commands=[hex(x) for x in commands])
                    break
                self.remove_bp(pc)
                self.t.step()
                self.add_bp(pc)
            else:
                raise RuntimeError('Unexpected halt during boot at %#x' % pc)
            self.t.resume()
        self.report['boot_check'] = {'main_reached':main_hit,'epd_commands':commands,'refresh_reached':0x12 in commands}
        if not main_hit or 0x12 not in commands:
            raise RuntimeError('Original boot/refresh command was not observed')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode',choices=['inspect','row-test','restore-original'])
    parser.add_argument('--image',type=Path,default=Path(__file__).with_name('original_flash_256KiB_read1.bin'))
    parser.add_argument('--output',type=Path,required=True,help='New directory for backups and report')
    parser.add_argument('--reset-halt',action='store_true',help='Catch reset before application startup')
    parser.add_argument('--allow-nonoriginal-current',action='store_true',help='Explicitly allow restoring factory backup over custom/damaged current firmware')
    args=parser.parse_args()
    if args.allow_nonoriginal_current and args.mode!='restore-original':
        parser.error('--allow-nonoriginal-current is only valid with restore-original')
    source=args.image.read_bytes()
    if len(source)!=SIZE or sha(source)!=FACTORY_SHA:
        raise RuntimeError('Image must be the verified original 256 KiB backup')
    args.output.mkdir(parents=True,exist_ok=False)
    report={'utc':datetime.now(timezone.utc).isoformat(),'mode':args.mode,'source_sha256':sha(source),
            'probe':SN,'events':[],'errors':[],'success':False,
            'method':'Halted CPU, SWD NVMCTRL ER/PBC/WP; 256-byte row blank checks; 64-byte page readbacks; full flash readback; no auxiliary writes'}
    session=ConnectHelper.session_with_chosen_probe(blocking=False,unique_id=SN,options={
        'target_override':'cortex_m','frequency':1000000,'connect_mode':'attach','auto_unlock':False,
        'resume_on_disconnect':False,'pack.debug_sequences.enable':False,'no_config':True,
        'cache.enable_memory':False,'cache.enable_register':False})
    if session is None:
        raise RuntimeError('Expected ST-Link not found')
    started=time.monotonic()
    with session:
        p=Programmer(session.target,report,args.output)
        try:
            before,aux=p.preflight(source,args.reset_halt or args.allow_nonoriginal_current,args.allow_nonoriginal_current)
            if args.mode!='inspect':
                p.configure()
                # Known non-empty original image row. Always restore its current content.
                address=0x19c00
                p.write_row(address,before[address:address+256])
                p.event('row_test_passed',address=hex(address),bytes=256,erased_blank_checked=True,readback_verified=True)
                if args.mode=='restore-original':
                    rows=[a for a in range(0,SIZE,256) if source[a:a+256]!=b'\xff'*256 or before[a:a+256]!=source[a:a+256]]
                    # Preserve bootloader until the rest is complete; reset vector row is last.
                    rows=[a for a in rows if a>=0xc00]+sorted([a for a in rows if a<0xc00],reverse=True)
                    report['planned_rows']=len(rows)
                    for i,address in enumerate(rows,1):
                        p.write_row(address,source[address:address+256])
                        report['completed_rows']=i
                        if i%32==0 or i==len(rows):
                            p.event('flash_progress',completed_rows=i,total_rows=len(rows),address=hex(address))
                after=p.read(0,SIZE)
                (args.output/'after_flash_verified.bin').write_bytes(after)
                expected=source if args.mode=='restore-original' else before
                if after!=expected:
                    raise RuntimeError('Full 256 KiB readback differs from expected image')
                report['verified_flash_sha256']=sha(after)
                report['full_flash_readback_match']=True
                p.event('full_flash_verified',sha256=sha(after),bytes=len(after))
                p.check_aux(aux)
                p.restore_config()
                p.smoke_boot()
            report['success']=True
        except Exception as exc:
            report['errors'].append(str(exc))
            traceback.print_exc()
            if p.pending:
                address,old=p.pending
                try:
                    # Clear only transient NVM error flags before restoring the interrupted row.
                    p.t.write16(NVM+0x18,0x1c)
                    p.t.write8(NVM+0x14,2)
                    p.t.flush()
                    p.write_row_raw(address,old)
                    p.pending=None
                    p.safe_to_run=True
                    p.event('interrupted_row_recovered',address=hex(address))
                except Exception as recovery_error:
                    report['errors'].append('row recovery failed: '+str(recovery_error))
            if args.allow_nonoriginal_current and report.get('completed_rows',0) and not report.get('full_flash_readback_match'):
                # A partial recovery can contain both old and restored code.
                # Keep it halted, even if the interrupted row was recovered.
                p.safe_to_run=False
        finally:
            try:
                for address in list(p.active):
                    p.remove_bp(address)
                if p.safe_to_run:
                    p.restore_config()
                    if p.t.get_state()==Target.State.HALTED:
                        p.t.resume()
                report['final_state']=str(p.t.get_state())
            except Exception as cleanup_error:
                report['errors'].append('cleanup: '+str(cleanup_error))
                report['success']=False
            report['elapsed_seconds']=round(time.monotonic()-started,3)
            p.save_report()
    print(json.dumps(report,indent=2),flush=True)
    return 0 if report['success'] else 1

if __name__=='__main__':
    raise SystemExit(main())
