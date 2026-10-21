/* EPD1 v1: 64-byte SRAM mailbox, atomic marker + CRC32, no EEPROM writes. */
#include "protocol.h"
#include "nfc.h"
#include "graphics.h"
#define FRAME_BYTES (2U*EPD_PLANE_BYTES)
static uint8_t image[FRAME_BYTES],last_request[64],reply[64];
static uint32_t transfer_id,expected_crc,received,last_activity;
static uint16_t next_sequence;
static uint8_t active,display_state,reply_pending,last_valid,pending_display;
static uint32_t get32(const uint8_t *p) {return (uint32_t)p[0]|((uint32_t)p[1]<<8)|((uint32_t)p[2]<<16)|((uint32_t)p[3]<<24);}
static uint16_t get16(const uint8_t *p) {return (uint16_t)((uint16_t)p[0]|((uint16_t)p[1]<<8));}
static void put16(uint8_t *p,uint16_t v) {p[0]=(uint8_t)v;p[1]=(uint8_t)(v>>8);}
static void put32(uint8_t *p,uint32_t v) {p[0]=(uint8_t)v;p[1]=(uint8_t)(v>>8);p[2]=(uint8_t)(v>>16);p[3]=(uint8_t)(v>>24);}
uint32_t protocol_crc32(const uint8_t *data,uint32_t length) {
    uint32_t crc=0xffffffffU,i,bit;
    for(i=0;i<length;++i) {crc^=data[i];for(bit=0;bit<8;++bit)crc=(crc>>1)^((0U-(crc&1U))&0xedb88320U);}
    return crc^0xffffffffU;
}
static int same(const uint8_t *a,const uint8_t *b) {uint32_t i;for(i=0;i<64;++i)if(a[i]!=b[i])return 0;return 1;}
static void response(const uint8_t *request,uint8_t result) {
    uint32_t i;
    for(i=0;i<64;++i)reply[i]=0;
    reply[0]='E';reply[1]='P';reply[2]='A';reply[3]='1';reply[4]=1;
    if(request) {reply[5]=(uint8_t)(request[5]|0x80U);put16(reply+6,get16(request+6));put32(reply+8,get32(request+8));}
    put16(reply+12,(uint16_t)received);reply[14]=24;reply[15]=result;
    reply[16]=display_state;reply[17]=1;put16(reply+18,212);put16(reply+20,104);put16(reply+22,FRAME_BYTES);
    put32(reply+24,expected_crc);put32(reply+28,0x31525742U);
    for(i=0;i<7;++i)reply[32+i]=g_nfc.uid[i];
    put32(reply+60,protocol_crc32(reply,60));reply_pending=1;
}
static int write_reply(void) {
    uint32_t i;
    /* Invalidate the first block, then publish the first block last. */
    uint8_t invalid[16]={0};
    if(!nfc_write_sram(0xf8,invalid))return 0;
    for(i=1;i<4;++i)if(!nfc_write_sram((uint8_t)(0xf8+i),reply+16*i))return 0;
    if(!nfc_write_sram(0xf8,reply))return 0;
    nfc_write_reg(6,0x40,0);return 1;
}
static void accept(const uint8_t *request) {
    uint32_t op=request[5],id=get32(request+8),offset=get16(request+12),length=request[14],i;
    uint16_t seq=get16(request+6);
    uint8_t error=0;
    if(request[4]!=1||length>44U) {response(request,3);return;}
    if(last_valid&&same(request,last_request)) {response(request,0);return;}
    if(op==1U) {
        if(display_state==2U)error=4;
        else if(!id||seq!=0||offset!=0||length!=11||get16(request+16)!=212||get16(request+18)!=104||get16(request+24)!=FRAME_BYTES||request[26]!=1)error=3;
        else {
            transfer_id=id;expected_crc=get32(request+20);received=0;next_sequence=1;
            active=1;display_state=1;g_nfc.transfer=id;g_nfc.received=0;g_nfc.image_crc=expected_crc;
        }
    } else if(op==2U) {
        if(!active||id!=transfer_id)error=5;
        else if(seq!=next_sequence||offset!=received)error=2;
        else if(!length||received+length>FRAME_BYTES)error=3;
        else {for(i=0;i<length;++i)image[received+i]=request[16+i];received+=length;next_sequence++;g_nfc.received=received;}
    } else if(op==3U) {
        if(!active||id!=transfer_id)error=5;
        else if(seq!=next_sequence||offset!=FRAME_BYTES||length!=0||received!=FRAME_BYTES)error=2;
        else if(protocol_crc32(image,FRAME_BYTES)!=expected_crc)error=6;
        else {
            for(i=0;i<FRAME_BYTES;++i)g_framebuffer[i/EPD_PLANE_BYTES][i%EPD_PLANE_BYTES]=image[i];
            display_state=2;active=0;next_sequence++;pending_display=1;
        }
    } else if(op==5U) {
        if(id!=transfer_id||display_state==2U)error=4;
        else {active=0;received=0;display_state=0;}
    } else if(op!=4U)error=3;
    if(!error) {for(i=0;i<64;++i)last_request[i]=request[i];last_valid=1;last_activity=g_status.ticks_ms;g_nfc.packets++;}
    response(request,error);
    if(op==3U&&!error) {if(write_reply())reply_pending=0;}
}
void protocol_init(void) {
    active=0;display_state=0;last_valid=0;received=0;transfer_id=0;expected_crc=0;
    response(0,0);
}
void protocol_poll(void) {
    uint8_t request[64];uint32_t i;
    static uint32_t last_poll,last_mirror_check; uint8_t nc;
    if(g_nfc.stage!=5)return;
    if((uint32_t)(g_status.ticks_ms-last_poll)<50U)return;
    last_poll=g_status.ticks_ms;g_nfc.poll_count++;
    if((uint32_t)(g_status.ticks_ms-last_mirror_check)>=500U) {
        last_mirror_check=g_status.ticks_ms;
        if(nfc_read_reg(0,&nc)&&(nc&0x43U)!=3U) {
            if(!nfc_mirror(1))return;
            protocol_init();
        }
    }
    if(reply_pending) {if(write_reply())reply_pending=0;else {nfc_write_reg(6,0x40,0);return;}}
    if(pending_display&&!reply_pending) {pending_display=0;g_status.command=7;return;}
    for(i=0;i<1;++i)if(!nfc_read_block((uint8_t)(0xf8+i),request+i*16)) {nfc_write_reg(6,0x40,0);return;}
    nfc_write_reg(6,0x40,0);
    if(request[0]=='E'&&request[1]=='P'&&request[2]=='D'&&request[3]=='1') {
        for(i=1;i<4;++i)if(!nfc_read_block((uint8_t)(0xf8+i),request+i*16)) {nfc_write_reg(6,0x40,0);return;}
        nfc_write_reg(6,0x40,0);
        if(protocol_crc32(request,60)==get32(request+60)) {accept(request);if(pending_display&&!reply_pending) {pending_display=0;g_status.command=7;}}
        else {g_nfc.crc_errors++;return;}
    }
    if(active&&(uint32_t)(g_status.ticks_ms-last_activity)>30000U) {active=0;display_state=0;last_valid=0;received=0;response(0,7);}
}
void protocol_display_complete(int success) {
    display_state=success?3:4;
    response(last_valid?last_request:0,success?0:8);
}
