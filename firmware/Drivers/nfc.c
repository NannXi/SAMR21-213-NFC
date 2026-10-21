/* SERCOM1 hardware I2C on the original board pins; SRAM/session writes only. */
#include "nfc.h"
#include "protocol.h"
#define A (PORT_REGS->GROUP[0])
#define I (SERCOM1_REGS->I2CM)
volatile NfcStatus g_nfc;
static uint8_t address_next;
static int sync_wait(void) {
    uint32_t start=g_status.ticks_ms;
    while(I.SERCOM_SYNCBUSY)if((uint32_t)(g_status.ticks_ms-start)>10U)return 0;
    return 1;
}
static int i2c_init(void) {
    uint32_t start;
    PM_REGS->PM_APBCMASK|=PM_APBCMASK_SERCOM1_Msk;
    GCLK_REGS->GCLK_CLKCTRL=GCLK_CLKCTRL_ID_SERCOM1_CORE|GCLK_CLKCTRL_GEN_GCLK0|GCLK_CLKCTRL_CLKEN_Msk;
    start=g_status.ticks_ms;
    while(GCLK_REGS->GCLK_STATUS&GCLK_STATUS_SYNCBUSY_Msk)if((uint32_t)(g_status.ticks_ms-start)>10U)return 0;
    I.SERCOM_CTRLA=SERCOM_I2CM_CTRLA_SWRST_Msk;
    start=g_status.ticks_ms;
    while((I.SERCOM_CTRLA&SERCOM_I2CM_CTRLA_SWRST_Msk)||I.SERCOM_SYNCBUSY)if((uint32_t)(g_status.ticks_ms-start)>10U)return 0;
    A.PORT_PMUX[8]=(uint8_t)0x22;
    A.PORT_OUTSET=(1UL<<16)|(1UL<<17);
    A.PORT_PINCFG[16]=PORT_PINCFG_PMUXEN_Msk|PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    A.PORT_PINCFG[17]=PORT_PINCFG_PMUXEN_Msk|PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    I.SERCOM_CTRLA=SERCOM_I2CM_CTRLA_MODE_I2C_MASTER|SERCOM_I2CM_CTRLA_SDAHOLD_75NS;
    I.SERCOM_CTRLB=0;I.SERCOM_BAUD=35;
    I.SERCOM_CTRLA|=SERCOM_I2CM_CTRLA_ENABLE_Msk;
    if(!sync_wait())return 0;
    I.SERCOM_STATUS=SERCOM_I2CM_STATUS_BUSSTATE(1);
    return sync_wait();
}
static int flag_wait(uint8_t mask) {
    uint32_t start=g_status.ticks_ms;
    while(!(I.SERCOM_INTFLAG&mask)) {
        if(I.SERCOM_STATUS&(SERCOM_I2CM_STATUS_BUSERR_Msk|SERCOM_I2CM_STATUS_ARBLOST_Msk))return 0;
        if((uint32_t)(g_status.ticks_ms-start)>10U)return 0;
    }
    return 1;
}
static int start(void) {address_next=1;return sync_wait();}
static void stop(void) {
    if((I.SERCOM_STATUS&SERCOM_I2CM_STATUS_BUSSTATE_Msk)==SERCOM_I2CM_STATUS_BUSSTATE(2)) {
        I.SERCOM_CTRLB=(I.SERCOM_CTRLB&~SERCOM_I2CM_CTRLB_CMD_Msk)|SERCOM_I2CM_CTRLB_CMD(3);
        sync_wait();
    }
}
static int send(uint8_t value) {
    int ok;
    if(address_next) {
        address_next=0;I.SERCOM_CTRLB=0;if(!sync_wait())return 0;
        I.SERCOM_ADDR=value;
        ok=flag_wait((value&1U)?(SERCOM_I2CM_INTFLAG_SB_Msk|SERCOM_I2CM_INTFLAG_MB_Msk):SERCOM_I2CM_INTFLAG_MB_Msk);
    } else {I.SERCOM_DATA=value;ok=flag_wait(SERCOM_I2CM_INTFLAG_MB_Msk);}
    if(!ok||(I.SERCOM_STATUS&SERCOM_I2CM_STATUS_RXNACK_Msk)) {g_nfc.failures++;return 0;}
    return 1;
}
static int receive(uint8_t *value,int last) {
    if(!flag_wait(SERCOM_I2CM_INTFLAG_SB_Msk))return 0;
    *value=(uint8_t)I.SERCOM_DATA;
    I.SERCOM_CTRLB=(last?SERCOM_I2CM_CTRLB_ACKACT_Msk:0)|SERCOM_I2CM_CTRLB_CMD(last?3:2);
    return sync_wait();
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
    if(index>6U)return 0;
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
        /* Static locks F0 FF, dynamic locks FF 00 00 on this board.
           RF pages 90..9f use dynamic lock byte 1 bit 0, verified clear.
           Dynamic lock bytes occupy offsets 8..10 in I2C block 38. */
        if(g_nfc.config38[9]&1U)return 0;
        if(!nfc_write_reg(2,0xff,NFC_MIRROR_BLOCK))return 0;
        if(!nfc_write_reg(1,0xff,NFC_RF_LAST_BLOCK))return 0;
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
    g_nfc.error=0;
    PM_REGS->PM_APBBMASK|=PM_APBBMASK_PORT_Msk;
    A.PORT_PINCFG[15]=0; A.PORT_OUTSET=1UL<<15; A.PORT_DIRSET=1UL<<15;
    A.PORT_PINCFG[16]=PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    A.PORT_PINCFG[17]=PORT_PINCFG_INEN_Msk|PORT_PINCFG_PULLEN_Msk;
    if(!i2c_init()) {g_nfc.error=6;return;}
    delay_ms(10);
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
    g_nfc.error=0;
    protocol_init();
}
void nfc_poll(void) {
    uint32_t cmd=g_nfc.command,i; uint8_t value=0,data[16]; int ok=0;
    if(!cmd) {
        static uint32_t last_retry;
        if(g_nfc.stage!=5U&&(uint32_t)(g_status.ticks_ms-last_retry)>=1000U) {
            last_retry=g_status.ticks_ms;nfc_init();
        }
        protocol_poll();return;
    }
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
