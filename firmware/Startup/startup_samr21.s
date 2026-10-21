; ATSAMR21G18A / ARM Compiler 5 startup; vectors at flash address zero.
                PRESERVE8
                THUMB
Stack_Size      EQU     0x800
                AREA    STACK, NOINIT, READWRITE, ALIGN=3
Stack_Mem       SPACE   Stack_Size
__initial_sp
                AREA    RESET, DATA, READONLY
                EXPORT  __Vectors
__Vectors       DCD     __initial_sp
                DCD     Reset_Handler
                DCD     Default_Handler
                DCD     HardFault_Handler
                DCD     0,0,0,0,0,0,0
                DCD     Default_Handler
                DCD     0,0
                DCD     Default_Handler
                DCD     SysTick_Handler
                ; 27 SAMR21 external IRQ entries, all disabled in this demo.
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                DCD     Default_Handler,Default_Handler,Default_Handler
                AREA    |.text|, CODE, READONLY
Reset_Handler   PROC
                EXPORT  Reset_Handler
                IMPORT  SystemInit
                IMPORT  __main
                LDR     R0, =SystemInit
                BLX     R0
                LDR     R0, =__main
                BX      R0
                ENDP
Default_Handler PROC
                EXPORT  Default_Handler
                B       .
                ENDP
                IMPORT  HardFault_Handler
                IMPORT  SysTick_Handler
                IF      :DEF:__MICROLIB
                EXPORT  __initial_sp
                ENDIF
                ALIGN
                END
