#ifndef GRAPHICS_H
#define GRAPHICS_H
#include "app.h"
#define GFX_WIDTH 212
#define GFX_HEIGHT 104
typedef enum { GFX_WHITE, GFX_BLACK, GFX_RED } GfxColor;
extern uint8_t g_framebuffer[2][EPD_PLANE_BYTES];
void gfx_clear(GfxColor color);
void gfx_pixel(int x, int y, GfxColor color);
void gfx_rect(int x, int y, int width, int height, GfxColor color);
void gfx_text(int x, int y, const char *text, int scale, GfxColor color);
int gfx_text_width(const char *text, int scale);
void gfx_demo(void);
#endif
