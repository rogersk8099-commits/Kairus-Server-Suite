package uk.kairu.client;
/** Coordinates are Minecraft GUI units; resize and GUI-scale changes rebuild these bounds. */
public record Layout(int x,int y,int width,int height,boolean sidebar,int contentX,int contentY,int contentWidth,int bottom,int columns,int rows) {
    public static Layout of(int screenWidth,int screenHeight) {
        int margin=screenHeight<240?6:12;
        int w=Math.max(1,Math.min(1120,screenWidth-margin*2)),h=Math.max(1,screenHeight-margin*2);
        int x=(screenWidth-w)/2,y=margin;
        boolean side=w>=540;
        int cx=x+(side?154:12),cy=y+(side?55:82),cw=Math.max(1,w-(side?166:24));
        int bottom=y+h-44,columns=cw>=580?3:cw>=370?2:1;
        int rows=Math.max(1,(bottom-cy-32)/32);
        return new Layout(x,y,w,h,side,cx,cy,cw,bottom,columns,rows);
    }
    public int pageSize(){return columns*rows;}
}
