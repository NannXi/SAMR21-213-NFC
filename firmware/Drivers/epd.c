/* Board mapping and initialization reconstructed from the backed-up firmware.
 * Software SPI mode 3 avoids dependence on the factory's 48 MHz clock setup.
 * OTP waveform and voltage settings are preserved; no custom LUT is loaded.
 */
#include "app.h"
#include "graphics.h"
#define PA_CS    (1UL<<23)
#define PA_SDIO  (1UL<<24)
#define PA_CLK   (1UL<<25)
#define PA_BUSY  (1UL<<27)
#define PA_POWER (1UL<<28)
#define PB_DC    (1UL<<22)
#define PB_RESET (1UL<<23)
#define A (PORT_REGS->GROUP[0])
#define B (PORT_REGS->GROUP[1])

static void edge_delay(void) { __NOP(); __NOP(); __NOP(); __NOP(); }
static void spi_byte(uint8_t value)
{
    uint32_t bit;
    for (bit=0; bit<8; bit++) {
        A.PORT_OUTCLR = PA_CLK;
        if (value & 0x80U) { A.PORT_OUTSET = PA_SDIO; }
        else { A.PORT_OUTCLR = PA_SDIO; }
        edge_delay();
        A.PORT_OUTSET = PA_CLK;
        edge_delay();
        value <<= 1;
    }
}

static void command(uint8_t value)
{
    g_status.last_epd_command=value;
    B.PORT_OUTCLR=PB_DC;
    A.PORT_OUTCLR=PA_CS;
    spi_byte(value);
    A.PORT_OUTSET=PA_CS;
}

static void data(uint8_t value)
{
    B.PORT_OUTSET=PB_DC;
    A.PORT_OUTCLR=PA_CS;
    spi_byte(value);
    A.PORT_OUTSET=PA_CS;
    g_status.bytes_sent++;
}

static uint32_t busy_high(void)
{
    uint32_t high = (A.PORT_IN & PA_BUSY) != 0U;
    g_status.busy = !high;
    return high;
}

void epd_emergency_off(void)
{
    A.PORT_OUTCLR=PA_POWER|PA_CLK|PA_SDIO|PA_CS;
    B.PORT_OUTCLR=PB_RESET|PB_DC;
}

static int wait_ready(uint32_t timeout_ms, uint32_t error)
{
    uint32_t start=g_status.ticks_ms;
    while (!busy_high()) {
        g_status.busy_wait_ms=g_status.ticks_ms-start;
        if (g_status.busy_wait_ms >= timeout_ms) {
            g_status.error=error;
            g_status.stage=0xe0;
            epd_emergency_off();
            return 0;
        }
        delay_ms(1);
    }
    g_status.busy_wait_ms=g_status.ticks_ms-start;
    return 1;
}

static uint8_t temperature_read(void)
{
    uint32_t bit;
    uint8_t result=0;
    B.PORT_OUTCLR=PB_DC;
    A.PORT_OUTCLR=PA_CS;
    spi_byte(0x40);
    A.PORT_DIRCLR=PA_SDIO;
    A.PORT_PINCFG[24]=PORT_PINCFG_INEN_Msk;
    B.PORT_OUTSET=PB_DC;
    for (bit=0;bit<8;bit++) {
        A.PORT_OUTCLR=PA_CLK;
        edge_delay();
        A.PORT_OUTSET=PA_CLK;
        edge_delay();
        result=(uint8_t)((result<<1)|((A.PORT_IN&PA_SDIO)!=0U));
    }
    A.PORT_OUTSET=PA_CS;
    A.PORT_PINCFG[24]=0;
    A.PORT_DIRSET=PA_SDIO;
    return result;
}

void epd_gpio_init(void)
{
    uint32_t pin;
    PM_REGS->PM_APBBMASK |= PM_APBBMASK_PORT_Msk;
    for(pin=23;pin<=28;pin++) {
        if(pin!=26U) { A.PORT_PINCFG[pin]=0; }
    }
    B.PORT_PINCFG[22]=0;
    B.PORT_PINCFG[23]=0;
    A.PORT_OUTCLR=PA_POWER;
    A.PORT_OUTSET=PA_CS|PA_CLK|PA_SDIO;
    B.PORT_OUTSET=PB_RESET|PB_DC;
    A.PORT_DIRSET=PA_CS|PA_CLK|PA_SDIO|PA_POWER;
    B.PORT_DIRSET=PB_RESET|PB_DC;
    A.PORT_DIRCLR=PA_BUSY;
    A.PORT_PINCFG[27]=PORT_PINCFG_INEN_Msk;
}

static uint8_t plane_byte(uint32_t index, uint32_t red, uint32_t pattern)
{
    uint32_t x=(index%13U)*8U;
    uint32_t y=index/13U;
    if (pattern >= 3U) { return g_framebuffer[red][index]; }
    /* Factory 2bpp red code 2 maps to 0 in BOTH observed plane LUTs. */
    if (pattern == 2U) { return 0x00; }
    if (!pattern) { return 0xff; }
    /* Three horizontal areas in controller coordinates, no graphics library. */
    if (y>=24U && y<80U && x>=16U && x<88U && !red) { return 0x00; }
    if (y>=128U && y<184U && x>=16U && x<88U) { return 0x00; }
    return 0xff;
}

int epd_display(uint8_t border, uint32_t pattern)
{
    uint32_t i,start;
    if (pattern == 3U) { gfx_demo(); }
    g_status.stage=2;
    g_status.border=border;
    g_status.bytes_sent=0;
    A.PORT_OUTSET=PA_CS|PA_CLK|PA_SDIO;
    B.PORT_OUTSET=PB_RESET|PB_DC;
    delay_ms(20);
    A.PORT_OUTSET=PA_POWER;
    B.PORT_OUTCLR=PB_RESET;
    delay_ms(20);
    B.PORT_OUTSET=PB_RESET;
    delay_ms(20);
    command(0x06); data(0x17); data(0x17); data(0x17);
    command(0x04);
    delay_ms(5);
    if (!wait_ready(5000,1)) { return 0; }
    g_status.temperature_raw=temperature_read();
    command(0x00); data(0x0f);
    command(0x61); data(0x68); data(0x00); data(0xd4);
    command(0x50); data(border);
    g_status.stage=3;
    command(0x10);
    for(i=0;i<EPD_PLANE_BYTES;i++) { data(plane_byte(i,0,pattern)); }
    command(0x13);
    for(i=0;i<EPD_PLANE_BYTES;i++) { data(plane_byte(i,1,pattern)); }
    g_status.stage=4;
    start=g_status.ticks_ms;
    command(0x12);
    delay_ms(20);
    if (!wait_ready(45000,2)) { return 0; }
    g_status.refresh_ms=g_status.ticks_ms-start;
    g_status.stage=5;
    command(0x50); data(0xf7);
    command(0x02);
    delay_ms(5);
    if (!wait_ready(5000,3)) { return 0; }
    epd_emergency_off();
    delay_ms(100);
    return 1;
}
