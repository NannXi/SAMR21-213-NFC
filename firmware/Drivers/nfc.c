/* Open-drain I2C on the original board pins; no EEPROM/config/fuse writes. */
#include "nfc.h"
#include "protocol.h"
#define A (PORT_REGS->GROUP[0])
#define SDA (1UL<<16)
#define SCL (1UL<<17)
volatile NfcStatus g_nfc;
static void pause_edge(void) { volatile uint32_t n; for(n=0;n<5;++n) { __NOP(); } }
static void low(uint32_t pin) { A.PORT_OUTCLR=pin; A.PORT_DIRSET=pin; pause_edge(); }
static void release(uint32_t pin) { A.PORT_DIRCLR=pin; A.PORT_OUTSET=pin; pause_edge(); }
static int clock_high(void) {
    uint32_t start=g_status.ticks_ms;
    release(SCL);
    while(!(A.PORT_IN&SCL)) {
        if((uint32_t)(g_status.ticks_ms-start)>3U) { g_nfc.error=2; return 0; }
    }
    return 1;
}
static int start(void) { release(SDA); if(!clock_high())return 0; low(SDA); low(SCL); return 1; }
static void stop(void) { low(SDA); clock_high(); release(SDA); }
static int send(uint8_t value) {
    uint32_t bit; int ack;
    for(bit=0;bit<8;++bit) {
        if(value&0x80U)release(SDA); else low(SDA);
        if(!clock_high())return 0;
        low(SCL); value<<=1;
    }
    release(SDA); if(!clock_high())return 0;
    ack=!(A.PORT_IN&SDA); low(SCL);
    if(!ack)g_nfc.failures++;
    return ack;
}
static int receive(uint8_t *value,int last) {
    uint32_t bit; uint8_t result=0;
    release(SDA);
    for(bit=0;bit<8;++bit) {
        if(!clock_high())return 0;
        result=(uint8_t)((result<<1)|((A.PORT_IN&SDA)!=0U)); low(SCL);
    }
    if(last)release(SDA); else low(SDA);
    if(!clock_high())return 0;
    low(SCL); release(SDA); *value=result; return 1;
}
int nfc_read_block(uint8_t address,uint8_t *data) {
    uint32_t i;
    if(!start()||!send(0xaa)||!send(address)) { stop(); return 0; }
    stop();
    if(!start()||!send(0xab)) { stop(); return 0; }
    for(i=0;i<16;++i)if(!receive(data+i,i==15U)) { stop(); return 0; }
    stop(); return 1;
}
int nfc_write_sram(uint8_t address,const uint8_t *data) {
    uint32_t i;
    if(address<0xf8U||address>0xfbU)return 0;
    if(!start()||!send(0xaa)||!send(address)) { stop(); return 0; }
    for(i=0;i<16;++i)if(!send(data[i])) { stop(); return 0; }
    stop(); return 1;
}
int nfc_read_reg(uint8_t index,uint8_t *value) {
    if(index>7U)return 0;
    if(!start()||!send(0xaa)||!send(0xfe)||!send(index)) { stop(); return 0; }
    stop();
    if(!start()||!send(0xab)||!receive(value,1)) { stop(); return 0; }
    stop(); return 1;
}
int nfc_write_reg(uint8_t index,uint8_t mask,uint8_t value) {
    if(index>6U)return 0;
    if(!start()||!send(0xaa)||!send(0xfe)||!send(index)||!send(mask)||!send(value)) { stop(); return 0; }
    stop(); return 1;
}
int nfc_mirror(int enabled) {
    uint8_t value; uint32_t i;
    uint8_t empty[16]={0};
    if(enabled) {
        for(i=0;i<4;++i)if(!nfc_write_sram((uint8_t)(0xf8U+i),empty))return 0;
        if(!nfc_write_reg(2,0xff,1))return 0;
    }
    if(!nfc_write_reg(0,0x43,enabled?0x03:0x01))return 0;
    if(!nfc_write_reg(6,0x40,0))return 0;
    if(!nfc_read_reg(0,&value))return 0;
    g_nfc.session[0]=value;
    if(enabled && (value&0x43U)!=3U)return 0;
    g_nfc.stage=enabled?5:4;
    return 1;
}
void nfc_init(void) {
    uint32_t i; uint8_t data[16],value;
    g_nfc.magic=0x4e464331;
    g_nfc.stage=1;
    PM_REGS->PM_APBBMASK|=PM_APBBMASK_PORT_Msk;
    A.PORT_PINCFG[15]=0; A.PORT_OUTSET=1UL<<15; A.PORT_DIRSET=1UL<<15;
    A.PORT_PINCFG[16]=PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    A.PORT_PINCFG[17]=PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    release(SDA|SCL); delay_ms(10);
    if(!nfc_read_block(0,data)) { g_nfc.error=1; return; }
    for(i=0;i<16;++i)g_nfc.uid[i]=data[i];
    g_nfc.stage=2;
    for(i=0;i<7;++i) {
        if(!nfc_read_reg((uint8_t)i,&value)) { g_nfc.error=3; return; }
        g_nfc.session[i]=value;
    }
    g_nfc.stage=3;
    for(i=0;i<56;++i) {
        if(!nfc_read_block((uint8_t)i,data)) { g_nfc.error=4; return; }
        {uint32_t j;for(j=0;j<16;++j)g_nfc.eeprom[i*16+j]=data[j];}
    }
    if(nfc_read_block(0x38,data))for(i=0;i<16;++i)g_nfc.config38[i]=data[i];
    if(nfc_read_block(0x3a,data))for(i=0;i<16;++i)g_nfc.config3a[i]=data[i];
    for(i=0;i<4;++i) {
        if(nfc_read_block((uint8_t)(0xf8+i),data)) {uint32_t j;for(j=0;j<16;++j)g_nfc.sram[i*16+j]=data[j];}
    }
    nfc_write_reg(6,0x40,0); /* Volatile arbitration release, never EEPROM. */
    g_nfc.stage=4;
    if(!nfc_mirror(1)) {g_nfc.error=5;return;}
    protocol_init();
}
void nfc_poll(void) {
    uint32_t cmd=g_nfc.command,i; uint8_t value=0,data[16]; int ok=0;
    if(!cmd) {protocol_poll();return;}
    g_nfc.command=0;
    if(cmd==1)ok=nfc_read_block((uint8_t)g_nfc.argument,data);
    if(cmd==2) {for(i=0;i<16;++i)data[i]=g_nfc.data[i];ok=nfc_write_sram((uint8_t)g_nfc.argument,data);}
    if(cmd==3) {ok=nfc_read_reg((uint8_t)g_nfc.argument,&value); data[0]=value;}
    if(cmd==5)ok=nfc_mirror(1);
    if(cmd==6)ok=nfc_mirror(0);
    if(cmd==1&&ok)for(i=0;i<16;++i)g_nfc.data[i]=data[i];
    if(cmd==3&&ok)g_nfc.data[0]=value;
    nfc_write_reg(6,0x40,0);
    if(!ok)g_nfc.error=0x100+cmd; else g_nfc.error=0;
    g_nfc.completed++;
}
