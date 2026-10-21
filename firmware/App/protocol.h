#ifndef PROTOCOL_H
#define PROTOCOL_H
#include "app.h"
void protocol_init(void);
void protocol_poll(void);
void protocol_display_complete(int success);
uint32_t protocol_crc32(const uint8_t *data,uint32_t length);
#endif
