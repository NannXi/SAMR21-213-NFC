#include "app.h"
uint32_t SystemCoreClock=8000000U;
void SystemInit(void)
{
    /* Reset clock is OSC8M/8. Keep its factory calibration; remove prescale. */
    NVMCTRL_REGS->NVMCTRL_CTRLB = NVMCTRL_CTRLB_RWS(1);
    SYSCTRL_REGS->SYSCTRL_OSC8M &= ~SYSCTRL_OSC8M_PRESC_Msk;
    while ((SYSCTRL_REGS->SYSCTRL_PCLKSR & SYSCTRL_PCLKSR_OSC8MRDY_Msk)==0U) {}
    SCB->VTOR=0;
}
void SystemCoreClockUpdate(void) { SystemCoreClock=8000000U; }
