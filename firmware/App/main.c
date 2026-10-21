#include "app.h"
#include "nfc.h"
#include "protocol.h"

volatile AppStatus g_status;

void SysTick_Handler(void)
{
    g_status.ticks_ms++;
}

void delay_ms(uint32_t ms)
{
    uint32_t start = g_status.ticks_ms;
    while ((uint32_t)(g_status.ticks_ms-start) < ms) { __WFI(); }
}

void HardFault_Handler(void)
{
    epd_emergency_off();
    g_status.error = 0x100;
    g_status.stage = 0xe0;
    while (1) { __NOP(); }
}

int main(void)
{
    uint32_t request;
    g_status.magic = APP_MAGIC;
    g_status.stage = 1;
    SCB->VTOR = 0;
    SCB->SCR = 0;  /* Keep debugger accessible; no deep sleep. */
    SysTick_Config(SystemCoreClock/1000U);
    __enable_irq();
    epd_gpio_init();
    nfc_init();
    g_status.command = APP_STARTUP_COMMAND;  /* One refresh per power-up, then wait. */
    while (1) {
        nfc_poll();
        request = g_status.command;
        if (request != 0U) {
            g_status.command = 0;
            g_status.error = 0;
            if (request >= 1U && request <= 7U) {
                if (epd_display(request == 2U ? 0x37U : 0x77U,
                                request == 7U ? 4U : (request == 6U ? 3U : (request == 5U ? 2U : (request == 4U))))) {
                    g_status.completed++;
                    g_status.stage = 6;
                    if(request==7U)protocol_display_complete(1);
                } else if(request==7U)protocol_display_complete(0);
            } else {
                g_status.error = 0x101;
                g_status.stage = 0xe0;
            }
        }
        __NOP(); /* NFC preview: stay awake; low power is not enabled. */
    }
}
