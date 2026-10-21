#ifndef APP_H
#define APP_H
#include "samr21g18a.h"

#define APP_MAGIC 0x45504431UL
#define EPD_WIDTH 104U
#define EPD_HEIGHT 212U
#define EPD_PLANE_BYTES (EPD_WIDTH * EPD_HEIGHT / 8U)
#define APP_STARTUP_COMMAND 6U /* Landscape white/black and red/white demo. */
typedef struct {
    uint32_t magic;
    uint32_t stage;           /* 1 boot, 2 init, 3 data, 4 refresh, 5 power-off, 6 idle, 0xe0 error */
    uint32_t error;
    uint32_t ticks_ms;
    uint32_t command;         /* 1 white, 2 black border, 3 white border, 4 pattern, 5 red, 6 demo */
    uint32_t completed;
    uint32_t last_epd_command;
    uint32_t busy;
    uint32_t busy_wait_ms;
    uint32_t refresh_ms;
    uint32_t temperature_raw;
    uint32_t border;
    uint32_t bytes_sent;
} AppStatus;
extern volatile AppStatus g_status;
extern uint32_t SystemCoreClock;
void delay_ms(uint32_t ms);
void epd_gpio_init(void);
int epd_display(uint8_t border, uint32_t pattern);
void epd_emergency_off(void);
#endif
