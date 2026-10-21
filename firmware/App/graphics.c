/* Landscape graphics: controller x=y, controller y=211-x; original 5x7 glyphs.
 * Plane 0 / plane 1 bits: white=1/1, black=0/1, red=0/0.
 */
#include "graphics.h"

uint8_t g_framebuffer[2][EPD_PLANE_BYTES];

typedef struct { char character; uint8_t rows[7]; } Glyph;
static const Glyph font[] = {
    {'-', {0,0,0,31,0,0,0}},
    {'.', {0,0,0,0,0,12,12}},
    {'1', {4,12,4,4,4,4,14}},
    {'2', {14,17,1,2,4,8,31}},
    {'3', {30,1,1,14,1,1,30}},
    {'H', {17,17,17,31,17,17,17}},
    {'P', {30,17,17,30,16,16,16}},
    {'W', {17,17,17,21,21,21,10}},
    {'a', {0,0,14,1,15,17,15}},
    {'c', {0,0,14,16,16,17,14}},
    {'d', {1,1,15,17,17,17,15}},
    {'e', {0,0,14,17,31,16,14}},
    {'h', {16,16,30,17,17,17,17}},
    {'i', {4,0,12,4,4,4,14}},
    {'l', {12,4,4,4,4,4,14}},
    {'n', {0,0,30,17,17,17,17}},
    {'o', {0,0,14,17,17,17,14}},
    {'p', {0,30,17,17,30,16,16}},
    {'r', {0,0,22,25,16,16,16}},
    {'w', {0,0,17,17,21,21,10}}
};

void gfx_clear(GfxColor color)
{
    uint32_t i;
    for (i=0; i<EPD_PLANE_BYTES; ++i) {
        g_framebuffer[0][i] = color==GFX_WHITE ? 0xff : 0;
        g_framebuffer[1][i] = color==GFX_RED ? 0 : 0xff;
    }
}

void gfx_pixel(int x, int y, GfxColor color)
{
    uint32_t controller_x, index;
    uint8_t mask;
    if (x<0 || x>=GFX_WIDTH || y<0 || y>=GFX_HEIGHT) { return; }
    controller_x=(uint32_t)y;
    index=(uint32_t)(GFX_WIDTH-1-x)*(EPD_WIDTH/8U)+controller_x/8U;
    mask=(uint8_t)(0x80U>>(controller_x%8U));
    if (color==GFX_WHITE) { g_framebuffer[0][index]|=mask; }
    else { g_framebuffer[0][index]&=(uint8_t)~mask; }
    if (color==GFX_RED) { g_framebuffer[1][index]&=(uint8_t)~mask; }
    else { g_framebuffer[1][index]|=mask; }
}

void gfx_rect(int x, int y, int width, int height, GfxColor color)
{
    int column,row;
    for (row=0; row<height; ++row) {
        for (column=0; column<width; ++column) { gfx_pixel(x+column,y+row,color); }
    }
}

int gfx_text_width(const char *text, int scale)
{
    int length=0;
    while (*text++) { ++length; }
    return length ? (length*6-1)*scale : 0;
}

void gfx_text(int x, int y, const char *text, int scale, GfxColor color)
{
    uint32_t glyph;
    int row,column;
    while (*text) {
        for (glyph=0; glyph<sizeof(font)/sizeof(font[0]); ++glyph) {
            if (font[glyph].character==*text) {
                for (row=0; row<7; ++row) {
                    for (column=0; column<5; ++column) {
                        if (font[glyph].rows[row] & (1U<<(4-column))) {
                            gfx_rect(x+column*scale,y+row*scale,scale,scale,color);
                        }
                    }
                }
                break;
            }
        }
        x+=6*scale;
        ++text;
    }
}

static void outline(int x,int y,int width,int height,GfxColor color)
{
    gfx_rect(x,y,width,1,color);
    gfx_rect(x,y+height-1,width,1,color);
    gfx_rect(x,y,1,height,color);
    gfx_rect(x+width-1,y,1,height,color);
}

void gfx_demo(void)
{
    const char *top="Hello World";
    const char *bottom="2.13 inch e-Paper";
    int pin;
    gfx_clear(GFX_WHITE);
    gfx_rect(0,52,GFX_WIDTH,52,GFX_RED);
    gfx_text((GFX_WIDTH-gfx_text_width(top,2))/2,19,top,2,GFX_BLACK);
    gfx_text((GFX_WIDTH-gfx_text_width(bottom,2))/2,71,bottom,2,GFX_WHITE);
    /* Microchip and battery: small corner accents, clear of the text. */
    outline(11,20,11,11,GFX_BLACK);
    gfx_rect(15,24,3,3,GFX_BLACK);
    for (pin=0; pin<3; ++pin) {
        gfx_rect(13+3*pin,17,1,3,GFX_BLACK);
        gfx_rect(13+3*pin,31,1,3,GFX_BLACK);
        gfx_rect(8,22+3*pin,3,1,GFX_BLACK);
        gfx_rect(22,22+3*pin,3,1,GFX_BLACK);
    }
    outline(186,21,15,9,GFX_BLACK);
    gfx_rect(201,24,2,3,GFX_BLACK);
    gfx_rect(189,24,8,3,GFX_BLACK);
    /* A tiny display below the white caption. */
    outline(100,91,12,7,GFX_WHITE);
    gfx_rect(105,98,2,2,GFX_WHITE);
    gfx_rect(102,100,8,1,GFX_WHITE);
}
