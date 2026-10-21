package io.github.nannxi.epaper;

public final class ImageProcessor {
    public static final int WIDTH=212, HEIGHT=104, PLANE=2756;
    private static final int[][] BAYER={{0,8,2,10},{12,4,14,6},{3,11,1,9},{15,7,13,5}};
    private ImageProcessor() {}
    public static int[] convert(int[] source,int brightness,int contrast,int dither,boolean redEnabled) {
        if(source.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("Image must be 212x104");
        int[] result=new int[source.length];
        float[][] error=new float[3][source.length+WIDTH+1];
        double factor=Math.pow(2,contrast/80.0);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int i=y*WIDTH+x,p=source[i];
            double r=clamp((((p>>16)&255)-128)*factor+128+brightness);
            double g=clamp((((p>>8)&255)-128)*factor+128+brightness);
            double b=clamp(((p&255)-128)*factor+128+brightness);
            if(dither==1) {double shift=(BAYER[y%4][x%4]-7.5)*5;r=clamp(r+shift);g=clamp(g+shift);b=clamp(b+shift);}
            if(dither==2) {r=clamp(r+error[0][i]);g=clamp(g+error[1][i]);b=clamp(b+error[2][i]);}
            boolean isRed=redEnabled&&r>90&&r>g*1.28&&r>b*1.25&&(r-g)>28;
            int color=isRed?0xffdc1c2d:((.2126*r+.7152*g+.0722*b)>=140?0xffffffff:0xff000000);
            result[i]=color;
            if(dither==2) {
                double[] diff={r-((color>>16)&255),g-((color>>8)&255),b-(color&255)};
                for(int c=0;c<3;c++) {
                    if(x+1<WIDTH)error[c][i+1]+=diff[c]*7/16;
                    if(y+1<HEIGHT) {
                        if(x>0)error[c][i+WIDTH-1]+=diff[c]*3/16;
                        error[c][i+WIDTH]+=diff[c]*5/16;
                        if(x+1<WIDTH)error[c][i+WIDTH+1]+=diff[c]/16;
                    }
                }
            }
        }
        return result;
    }
    public static byte[] pack(int[] pixels) {
        if(pixels.length!=WIDTH*HEIGHT)throw new IllegalArgumentException("Frame size");
        byte[] data=new byte[PLANE*2];java.util.Arrays.fill(data,(byte)255);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int index=(WIDTH-1-x)*13+y/8,mask=0x80>>(y%8),c=pixels[y*WIDTH+x];
            if(c!=0xffffffff)data[index]&=~mask;
            if(c==0xffdc1c2d)data[PLANE+index]&=~mask;
        }
        return data;
    }
    private static double clamp(double value) {return Math.max(0,Math.min(255,value));}
}
