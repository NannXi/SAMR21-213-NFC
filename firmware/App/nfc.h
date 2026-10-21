#ifndef NFC_H
#define NFC_H
#include "app.h"
typedef struct {
    uint32_t magic, stage, error, command, argument, completed, failures;
    uint32_t ns_reg, poll_count, packets, crc_errors, received, transfer, image_crc;
    uint8_t uid[16], session[8], config38[16], config3a[16], data[16];
    uint8_t eeprom[896], sram[64];
} NfcStatus;
extern volatile NfcStatus g_nfc;
void nfc_init(void);
void nfc_poll(void);
int nfc_read_block(uint8_t address,uint8_t *data);
int nfc_write_sram(uint8_t address,const uint8_t *data);
int nfc_read_reg(uint8_t index,uint8_t *value);
int nfc_write_reg(uint8_t index,uint8_t mask,uint8_t value);
int nfc_mirror(int enabled);
#endif
