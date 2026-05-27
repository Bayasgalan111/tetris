package projekt;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.event.KeyAdapter;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseAdapter;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class project implements GLEventListener {

    int winW=1200, winH=800;
    int leftW, rightX;

    static final int COLS=10, DEPTH=10, MAX_Y=20;
    private int[][][] board=new int[MAX_Y][DEPTH][COLS];

    private static final int[][][] SHAPES={
        {{0,0},{1,0},{2,0},{3,0}},
        {{0,0},{1,0},{0,1},{1,1}},
        {{1,0},{0,1},{1,1},{2,1}},
        {{0,0},{0,1},{1,1},{2,1}},
        {{2,0},{0,1},{1,1},{2,1}},
        {{1,0},{2,0},{0,1},{1,1}},
        {{0,0},{1,0},{1,1},{2,1}}
    };
    private static final String[] SNAMES={"I","O","T","J","L","S","Z"};
    private static final float[][] COLORS={
        {0f,0f,0f,0f},
        {0.45f,0.85f,0.95f,1f},
        {0.95f,0.88f,0.50f,1f},
        {0.75f,0.55f,0.90f,1f},
        {0.50f,0.65f,0.95f,1f},
        {0.95f,0.70f,0.40f,1f},
        {0.55f,0.90f,0.65f,1f},
        {0.95f,0.55f,0.60f,1f}
    };

    int[][] curCells;
    int curColor,curIdx,curX,curZ,curY;
    private int[][] nxtCells;
    private int nxtColor,nxtIdx;

    private int score=0,hi=0,level=1,lines=0;
    private int[] stats=new int[7];
    private long lastFall;
    private int fallMs=700;
    boolean started=false,paused=false,over=false;

    private int flashTimer=0;
    private List<Integer> flashY = new ArrayList<>();

    private String assistNotification = "";
    private int notificationTimer = 0;

    double camYaw=Math.toRadians(35),camPitch=Math.toRadians(38);
    float camZoom=26f,origX,origY;

    boolean dragging=false;
    int dragLastX,dragLastY;

    private float shakeAmt = 0.0f;
    private Random shakeRand = new Random();

    private List<int[][][]> boardHistory = new ArrayList<>();
    private List<Integer> scoreHistory = new ArrayList<>();
    private List<Integer> linesHistory = new ArrayList<>();
    private List<Integer> levelHistory = new ArrayList<>();
    private List<int[]> statsHistory = new ArrayList<>();
    private static final int MAX_HISTORY = 10;

    private int[][] btnRects=new int[11][4];
    private static final int BTN_FWD=0,BTN_ROTL=1,BTN_ROTR=2;
    private static final int BTN_LEFT=3,BTN_DOWN=4,BTN_RGHT=5;
    private static final int BTN_SNAP=6,BTN_HARD=7,BTN_PAUS=8,BTN_UNDO=9,BTN_RSET=10;

    private TextRenderer bigFont,medFont,smallFont,tinyFont;

    public project(){
        recalc(winW,winH);
        prepareNext();
        spawnPiece();
    }

    private void recalc(int w,int h){
        winW=w; winH=h;
        leftW =w*16/100;
        rightX=w*71/100;
        origX =leftW+(rightX-leftW)*0.5f;
        origY =h*0.20f;
        camZoom=h*0.030f;
    }

    private void prepareNext(){
        nxtIdx=new Random().nextInt(SHAPES.length);
        nxtCells=deepCopy(SHAPES[nxtIdx]);
        nxtColor=nxtIdx+1;
    }

    private void spawnPiece(){
        curIdx=nxtIdx; 
        curCells=deepCopy(nxtCells); 
        curColor=nxtColor;
        curX=COLS/2-1; curZ=DEPTH/2-1; curY=MAX_Y-1;
        
        if (!canPlace(curCells,curX,curZ,curY)) {
            over=true;
            playSoftSound(220, 180, 300);
            return;
        }
        prepareNext();
    }

    private int[][] deepCopy(int[][] s){
        int[][] d=new int[s.length][];
        for (int i=0;i<s.length;i++) d[i]=s[i].clone();
        return d;
    }

    private int[][][] deepCopyBoard(int[][][] b) {
        int[][][] d = new int[MAX_Y][DEPTH][COLS];
        for (int y = 0; y < MAX_Y; y++) {
            for (int z = 0; z < DEPTH; z++) {
                d[y][z] = b[y][z].clone();
            }
        }
        return d;
    }

    private void saveStateToHistory() {
        if (boardHistory.size() >= MAX_HISTORY) {
            boardHistory.remove(0); scoreHistory.remove(0); linesHistory.remove(0); levelHistory.remove(0); statsHistory.remove(0);
        }
        boardHistory.add(deepCopyBoard(board));
        scoreHistory.add(score); linesHistory.add(lines); levelHistory.add(level); statsHistory.add(stats.clone());
    }

    public void undoLastMove() {
        if (boardHistory.isEmpty() || !started || over) return;
        int idx = boardHistory.size() - 1;
        board = boardHistory.remove(idx);
        score = scoreHistory.remove(idx);
        lines = linesHistory.remove(idx);
        level = levelHistory.remove(idx);
        stats = statsHistory.remove(idx);
        fallMs = Math.max(80, 700 - (level - 1) * 60);
        flashTimer = 0; flashY.clear();
        spawnPiece();
        playSoftSound(400, 400, 60);
    }

    boolean canPlace(int[][] cells,int ox,int oz,int oy){
        for (int[] c:cells){
            int x=ox+c[0],z=oz+c[1];
            if (x<0||x>=COLS||z<0||z>=DEPTH||oy<0) return false;
            if (oy<MAX_Y&&board[oy][z][x]!=0) return false;
        }
        return true;
    }

    void autoAlignSmart() {
        int bestX = curX; int bestZ = curZ; int bestRotations = 0;
        double bestScore = -999999.0;
        int[][] originalCells = deepCopy(curCells);
        int[][] testCells = deepCopy(originalCells);

        for (int rot = 0; rot < 4; rot++) {
            if (rot > 0) {
                int mz = 0;
                for (int[] c : testCells) mz = Math.max(mz, c[1]);
                int[][] rotated = new int[testCells.length][2];
                for (int i = 0; i < testCells.length; i++) {
                    rotated[i][0] = mz - testCells[i][1]; rotated[i][1] = testCells[i][0];
                }
                int mn = Integer.MAX_VALUE;
                for (int[] c : rotated) mn = Math.min(mn, c[1]);
                if (mn < 0) for (int[] c : rotated) c[1] -= mn;
                testCells = rotated;
            }

            for (int tx = 0; tx < COLS; tx++) {
                for (int tz = 0; tz < DEPTH; tz++) {
                    if (canPlace(testCells, tx, tz, curY)) {
                        int ty = curY;
                        while (canPlace(testCells, tx, tz, ty - 1)) ty--;
                        int heightScore = ty; int holesCreated = 0;
                        for (int[] c : testCells) {
                            int bx = tx + c[0]; int bz = tz + c[1];
                            for (int checkY = ty - 1; checkY >= 0; checkY--) {
                                if (board[checkY][bz][bx] == 0) holesCreated++;
                                else break;
                            }
                        }
                        double totalScore = (heightScore * -2.5) + (holesCreated * -15.0);
                        if (totalScore > bestScore) {
                            bestScore = totalScore; bestX = tx; bestZ = tz; bestRotations = rot;
                        }
                    }
                }
            }
        }
        curCells = originalCells;
        for (int i = 0; i < bestRotations; i++) rotateRight();
        curX = bestX; curZ = bestZ;
        playSoftSound(480, 580, 60); 
    }

    private void evaluateGamerMovement(int tx, int tz, int ty, int[][] cells) {
        int holesCreated = 0;
        for (int[] c : cells) {
            int bx = tx + c[0]; int bz = tz + c[1];
            for (int checkY = ty - 1; checkY >= 0; checkY--) {
                if (board[checkY][bz][bx] == 0) holesCreated++;
                else break;
            }
        }

        if (holesCreated == 0 && ty < 14) {
            int awardPoints = 10; 
            score += awardPoints;
            if (score > hi) hi = score;
            assistNotification = "EXCELLENT MOVE! +" + awardPoints;
            notificationTimer = 90;
            playSoftSound(523, 659, 150); 
        }
    }

    void rotateLeft(){
        int mx=0;
        for (int[] c:curCells) mx=Math.max(mx,c[0]);
        int[][] rot=new int[curCells.length][2];
        for (int i=0;i<curCells.length;i++){rot[i][0]=curCells[i][1];rot[i][1]=mx-curCells[i][0];}
        int mn=Integer.MAX_VALUE;
        for (int[] c:rot) mn=Math.min(mn,c[0]);
        if (mn<0) for (int[] c:rot) c[0]-=mn;
        if      (canPlace(rot,curX,  curZ,curY)){curCells=rot; playSoftSound(550, 550, 20);}
        else if (canPlace(rot,curX-1,curZ,curY)){curCells=rot;curX--; playSoftSound(550, 550, 20);}
        else if (canPlace(rot,curX+1,curZ,curY)){curCells=rot;curX++; playSoftSound(550, 550, 20);}
    }

    void rotateRight(){
        int mz=0;
        for (int[] c:curCells) mz=Math.max(mz,c[1]);
        int[][] rot=new int[curCells.length][2];
        for (int i=0;i<curCells.length;i++){rot[i][0]=mz-curCells[i][1];rot[i][1]=curCells[i][0];}
        int mn=Integer.MAX_VALUE;
        for (int[] c:rot) mn=Math.min(mn,c[1]);
        if (mn<0) for (int[] c:rot) c[1]-=mn;
        if      (canPlace(rot,curX,  curZ,curY)){curCells=rot; playSoftSound(550, 550, 20);}
        else if (canPlace(rot,curX-1,curZ,curY)){curCells=rot;curX--; playSoftSound(550, 550, 20);}
        else if (canPlace(rot,curX+1,curZ,curY)){curCells=rot;curX++; playSoftSound(550, 550, 20);}
    }

    void moveDown(){
        if (canPlace(curCells,curX,curZ,curY-1)) curY--;
        else lock();
    }

    void hardDrop(){
        while (canPlace(curCells,curX,curZ,curY-1)) curY--;
        lock();
    }

    private void lock(){
        if (over) return;
        saveStateToHistory();
        stats[curIdx]++;
        evaluateGamerMovement(curX, curZ, curY, curCells);

        for (int[] c:curCells){
            int x=curX+c[0],z=curZ+c[1];
            if (curY>=0 && curY<MAX_Y) {
                board[curY][z][x]=curColor;
            }
        }
        playSoftSound(300, 200, 40);
        runGroutFillAssist();
        clearLines();
        
        if (!over) {
            spawnPiece();
        }
    }

    private void runGroutFillAssist() {
        for (int y = 0; y < MAX_Y; y++) {
            for (int z = 1; z < DEPTH - 1; z++) {
                for (int x = 1; x < COLS - 1; x++) {
                    if (board[y][z][x] == 0) {
                        if (board[y][z][x-1] != 0 && board[y][z][x+1] != 0 && board[y][z-1][x] != 0 && board[y][z+1][x] != 0) {
                            board[y][z][x] = 1;
                        }
                    }
                }
            }
        }
    }

    private void clearLines(){
        flashY.clear();
        for (int y=0;y<MAX_Y;y++){
            boolean full=true;
            outer: for (int z=0;z<DEPTH;z++) {
                for (int x=0;x<COLS;x++) {
                    if (board[y][z][x]==0){full=false;break outer;}
                }
            }
            if (full) flashY.add(y);
        }
        
        if (flashY.isEmpty()) return;
        
        shakeAmt = 2.0f * flashY.size(); 
        playSoftSound(440, 523, 200);
        flashTimer=20; 

        int[][][] newBoard = new int[MAX_Y][DEPTH][COLS];
        int targetY = 0;
        for (int y = 0; y < MAX_Y; y++) {
            if (flashY.contains(y)) {
                continue;
            }
            for (int z = 0; z < DEPTH; z++) {
                newBoard[targetY][z] = board[y][z].clone();
            }
            targetY++;
        }
        board = newBoard;

        lines += flashY.size();
        score += flashY.size() * 100 * level;
        if (score > hi) hi = score;
        
        level=lines/10+1;
        fallMs=Math.max(80,700-(level-1)*60);
    }
    
    void resetGame(){
        board=new int[MAX_Y][DEPTH][COLS];
        boardHistory.clear(); scoreHistory.clear(); linesHistory.clear(); levelHistory.clear(); statsHistory.clear();
        score=0;level=1;lines=0;stats=new int[7];
        fallMs=700;paused=false;over=false;started=true;
        flashTimer=0;flashY.clear();shakeAmt=0f;
        assistNotification = ""; notificationTimer = 0;
        prepareNext();spawnPiece();
        lastFall=System.currentTimeMillis();
        playSoftSound(350, 450, 100);
    }

    void togglePause(){
        if (!started||over) return;
        paused=!paused;
        playSoftSound(350, 280, 60);
    }

    private int ghostY(){
        int gy=curY;
        while (canPlace(curCells,curX,curZ,gy-1)) gy--;
        return gy;
    }

    public void playSoftSound(int startFreq, int endFreq, int durationMs) {
        new Thread(() -> {
            try {
                float sampleRate = 8000f;
                AudioFormat af = new AudioFormat(sampleRate, 8, 1, true, false);
                javax.sound.sampled.DataLine.Info info = new javax.sound.sampled.DataLine.Info(SourceDataLine.class, af);
                if (!AudioSystem.isLineSupported(info)) return;
                SourceDataLine sdl = (SourceDataLine) AudioSystem.getLine(info);
                sdl.open(af, 1024);
                sdl.start();
                int numSamples = (int) (sampleRate * (durationMs / 1000f));
                byte[] buf = new byte[numSamples];
                for (int i = 0; i < numSamples; i++) {
                    double pct = (double) i / numSamples;
                    double freq = startFreq + (endFreq - startFreq) * pct;
                    double angle = 2.0 * Math.PI * freq * ((double) i / sampleRate);
                    buf[i] = (byte) (Math.sin(angle) * 30 * Math.sin(pct * Math.PI));
                }
                sdl.write(buf, 0, buf.length);
                sdl.drain();
                sdl.close();
            } catch (Exception ex) {}
        }).start();
    }

    @Override
    public void init(GLAutoDrawable d){
        GL2 gl=d.getGL().getGL2();
        gl.glClearColor(0.01f,0.02f,0.05f,1f);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA,GL2.GL_ONE_MINUS_SRC_ALPHA);
        buildFonts();
        lastFall=System.currentTimeMillis();
    }

    private void buildFonts(){
        int b=Math.max(9,winH/55);
        bigFont  =new TextRenderer(new Font("Arial",Font.BOLD,b+8));
        medFont  =new TextRenderer(new Font("Arial",Font.BOLD,b+2));
        smallFont=new TextRenderer(new Font("Arial",Font.BOLD,b-1));
        tinyFont =new TextRenderer(new Font("Arial",Font.PLAIN,b-2));
    }

    @Override
    public void reshape(GLAutoDrawable d,int x,int y,int w,int h){
        if (w<=0||h<=0) return;
        recalc(w,h); buildFonts();
    }

    @Override
    public void display(GLAutoDrawable d){
        GL2 gl=d.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT);
        gl.glMatrixMode(GL2.GL_PROJECTION); gl.glLoadIdentity(); gl.glOrtho(0,winW,0,winH,-1,1);
        gl.glMatrixMode(GL2.GL_MODELVIEW); gl.glLoadIdentity();

        if (started&&!paused&&!over){
            long now=System.currentTimeMillis();
            if (now-lastFall>fallMs){lastFall=now;moveDown();}
            if (flashTimer>0) flashTimer--;
            if (notificationTimer>0) notificationTimer--;
        }

        gl.glPushMatrix();
        if (shakeAmt > 0.1f) {
            float sx = (shakeRand.nextFloat() * 2f - 1f) * shakeAmt;
            float sy = (shakeRand.nextFloat() * 2f - 1f) * shakeAmt;
            gl.glTranslatef(sx, sy, 0f); shakeAmt *= 0.85f;
        }

        drawBG(gl); drawLeft(gl); drawCentre(gl); drawRight(gl);
        gl.glPopMatrix();

        if (notificationTimer > 0 && !assistNotification.isEmpty()) {
            drawTxt(medFont, assistNotification, leftW + 30, winH - (winH / 10), 0.4f, 0.9f, 0.6f, 1.0f);
        }

        if (!started)               drawStartScreen(gl);
        if (over)                   drawGameOver(gl);
        if (paused&&started&&!over) drawPauseOverlay(gl);
    }

    @Override public void dispose(GLAutoDrawable d){}

    private float[] proj(double bx,double by,double bz){
        double cx=bx-COLS*0.5,cy=by,cz=bz-DEPTH*0.5;
        double rx=cx*Math.cos(camYaw)+cz*Math.sin(camYaw);
        double rz=-cx*Math.sin(camYaw)+cz*Math.cos(camYaw);
        double sx=rx,sy=cy*Math.cos(camPitch)-rz*Math.sin(camPitch);
        return new float[]{(float)(origX+sx*camZoom),(float)(origY+sy*camZoom)};
    }

    private void drawCube(GL2 gl,double bx,double by,double bz,float[] col,float alpha){
        float[] p000=proj(bx,by,bz),   p100=proj(bx+1,by,bz);
        float[] p110=proj(bx+1,by,bz+1),p010=proj(bx,by,bz+1);
        float[] p001=proj(bx,by+1,bz), p101=proj(bx+1,by+1,bz);
        float[] p111=proj(bx+1,by+1,bz+1),p011=proj(bx,by+1,bz+1);
        float r=col[0],g=col[1],b=col[2];
        gl.glColor4f(r,g,b,alpha);        quad(gl,p001,p101,p111,p011);
        gl.glColor4f(1f,1f,1f,0.16f*alpha);    quad(gl,p001,p101,p111,p011);
        gl.glColor4f(r*.65f,g*.65f,b*.65f,alpha); quad(gl,p000,p100,p101,p001);
        gl.glColor4f(r*.44f,g*.44f,b*.44f,alpha); quad(gl,p100,p110,p111,p101);
        gl.glColor4f(r*.54f,g*.54f,b*.54f,alpha); quad(gl,p000,p010,p011,p001);
        gl.glColor4f(r*.34f,g*.34f,b*.34f,alpha); quad(gl,p010,p110,p111,p011);
        gl.glColor4f(r*.20f,g*.20f,b*.20f,alpha); quad(gl,p000,p100,p110,p010);
        gl.glColor4f(0f,0f,0f,0.28f*alpha);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(p001[0],p001[1]);gl.glVertex2f(p101[0],p101[1]);
        gl.glVertex2f(p111[0],p111[1]);gl.glVertex2f(p011[0],p011[1]);
        gl.glEnd();
    }

    private void drawBG(GL2 gl){
        int th=winH/20;
        gl.glColor4f(0.02f,0.04f,0.10f,1f); fillRect(gl,0,winH-th,winW,th);
        gl.glColor4f(0.20f,0.45f,0.80f,0.22f); hLine(gl,winH-th,0,winW);
        gl.glColor4f(0.18f,0.40f,0.75f,0.15f); vLine(gl,leftW,0,winH); vLine(gl,rightX,0,winH);
        drawTxt(bigFont,"TETRIS 3D",winW/2-120,winH-th+6,0.28f,0.70f,1.0f,1f);
    }

    private void drawLeft(GL2 gl){
        int th=winH/20; int top=winH-th-4;
        drawTxt(medFont,"STATISTICS",4,top-22,0.38f,0.75f,1.0f,1f);
        gl.glColor4f(0.25f,0.50f,0.90f,0.18f); hLine(gl,top-28,3,leftW-3);
        int avail=top-32-4; int rowH=avail/7;
        for (int i=0;i<7;i++){
            int y=(top-32)-i*rowH; float[] c=COLORS[i+1];
            gl.glColor4f(c[0]*.07f,c[1]*.07f,c[2]*.07f,0.90f); fillRect(gl,3,y-rowH+3,leftW-6,rowH-3);
            gl.glColor4f(c[0],c[1],c[2],0.80f); fillRect(gl,3,y-rowH+3,3,rowH-3);
            float msz=rowH*0.20f; int mxC=0,mxR=0;
            for (int[] cc:SHAPES[i]){mxC=Math.max(mxC,cc[0]);mxR=Math.max(mxR,cc[1]);}
            float mox=18+(3-mxC)*msz*0.5f,moy=(y-rowH/2f)-mxR*msz*0.5f;
            for (int[] cc:SHAPES[i]){
                float bx=mox+cc[0]*msz,by=moy+cc[1]*msz;
                gl.glColor4f(c[0],c[1],c[2],0.88f); fillRect(gl,bx+1,by+1,msz-2,msz-2);
                gl.glColor4f(1f,1f,1f,0.22f); fillRect(gl,bx+1,by+msz-3,msz-2,2);
            }
            drawTxt(smallFont,SNAMES[i],18,y-rowH+5,c[0],c[1],c[2],0.85f);
            String cnt=String.valueOf(stats[i]);
            drawTxt(medFont,cnt,leftW-8-cnt.length()*8,y-rowH/2-7,1f,1f,1f,1f);
        }
    }

    private void drawCentre(GL2 gl){
        drawPit(gl);
        for (int y=0;y<MAX_Y;y++) {
            for (int z=0;z<DEPTH;z++) {
                for (int x=0;x<COLS;x++){
                    int ci=board[y][z][x]; if (ci==0) continue;
                    boolean fl=flashY.contains(y);
                    
                    if (fl) {
                        float progress = (20 - flashTimer) / 20.0f;
                        float opacity = (float) Math.sin(progress * Math.PI) * 0.40f;
                        float[] softLight = {0.9f, 0.95f, 1.0f};
                        drawCube(gl, x, y, z, softLight, opacity);
                    } else {
                        drawCube(gl, x, y, z, COLORS[ci], 1.0f);
                    }
                }
            }
        }
        if (started&&!over){
            int gy=ghostY();
            if (gy!=curY)
                for (int[] c:curCells){
                    int x=curX+c[0],z=curZ+c[1];
                    if (x>=0&&x<COLS&&z>=0&&z<DEPTH&&gy>=0&&gy<MAX_Y) drawCube(gl,x,gy,z,COLORS[curColor],0.28f);
                }
            for (int[] c:curCells){
                int x=curX+c[0],z=curZ+c[1];
                if (x>=0&&x<COLS&&z>=0&&z<DEPTH&&curY>=0&&curY<MAX_Y) drawCube(gl,x,curY,z,COLORS[curColor],1f);
            }
        }
    }

    private void drawPit(GL2 gl){
        gl.glColor4f(0.04f,0.04f,0.12f,0.95f); quad(gl,proj(0,0,0),proj(COLS,0,0),proj(COLS,0,DEPTH),proj(0,0,DEPTH));
        for (int y=0;y<MAX_Y;y++){
            float t=y/(float)MAX_Y;
            gl.glColor4f(0.03f+t*.02f,0.03f+t*.02f,0.08f+t*.04f,0.80f); quad(gl,proj(0,y,0),proj(0,y+1,0),proj(0,y+1,DEPTH),proj(0,y,DEPTH));
            gl.glColor4f(0.02f+t*.02f,0.02f+t*.02f,0.07f+t*.03f,0.75f); quad(gl,proj(0,y,0),proj(COLS,y,0),proj(COLS,y+1,0),proj(0,y+1,0));
        }
        gl.glColor4f(0.22f,0.45f,0.85f,0.14f);
        for (int x=0;x<=COLS;x++){line(gl,proj(x,0,0),proj(x,0,DEPTH));line(gl,proj(x,0,0),proj(x,MAX_Y,0));}
        for (int z=0;z<=DEPTH;z++){line(gl,proj(0,0,z),proj(COLS,0,z));line(gl,proj(0,0,z),proj(0,MAX_Y,z));}
        for (int y=0;y<=MAX_Y;y++){line(gl,proj(0,y,0),proj(0,y,DEPTH));line(gl,proj(0,y,0),proj(COLS,y,0));}
        gl.glColor4f(0.22f,0.45f,0.85f,0.09f);
        line(gl,proj(0,0,0),proj(0,MAX_Y,0)); line(gl,proj(COLS,0,0),proj(COLS,MAX_Y,0));
        line(gl,proj(0,0,DEPTH),proj(0,MAX_Y,DEPTH)); line(gl,proj(COLS,0,DEPTH),proj(COLS,MAX_Y,DEPTH));
    }

    private void drawRight(GL2 gl){
        int th=winH/20; int rx=rightX+6; int pw=winW-rightX-8;
        int nextH =winH*13/100; int boxH  =winH*7/100; int boxGap=boxH+4; int btnH  =winH*6/100; int btnGap=btnH+3;
        int nextTop=winH-th-4;
        drawTxt(medFont,"NEXT",rx+pw/2-20,nextTop-20,0.38f,0.75f,1.0f,1f);
        gl.glColor4f(0.22f,0.45f,0.85f,0.15f); hLine(gl,nextTop-26,rightX+4,winW-4);
        int nextBoxY=nextTop-26-nextH;
        gl.glColor4f(0.03f,0.03f,0.10f,0.92f); fillRect(gl,rx,nextBoxY,pw,nextH);
        gl.glColor4f(0.22f,0.45f,0.85f,0.20f); outlineRect(gl,rx,nextBoxY,pw,nextH);
        if (nxtCells!=null){
            float[] nc=COLORS[nxtColor]; int mxC=0,mxR=0;
            for (int[] c:nxtCells){mxC=Math.max(mxC,c[0]);mxR=Math.max(mxR,c[1]);}
            float bs=Math.min(pw/(mxC+2f),nextH/(mxR+2f))*0.72f;
            float nox=rx+(pw-(mxC+1)*bs)*0.5f; float noy=nextBoxY+(nextH-(mxR+1)*bs)*0.5f;
            for (int[] c:nxtCells){
                float bx=nox+c[0]*bs,by=noy+c[1]*bs;
                gl.glColor4f(nc[0],nc[1],nc[2],1f); fillRect(gl,bx+1,by+1,bs-2,bs-2);
                gl.glColor4f(1f,1f,1f,0.24f); fillRect(gl,bx+1,by+bs-5,bs-2,4);
                gl.glColor4f(0f,0f,0f,0.18f); fillRect(gl,bx+bs-5,by+1,4,bs-5);
            }
        }

        int infoTop=nextBoxY-6;
        infoBox(gl,rx,infoTop,          pw,boxH,"SCORE",   String.valueOf(score));
        infoBox(gl,rx,infoTop-boxGap,   pw,boxH,"HI-SCORE",String.valueOf(hi));
        infoBox(gl,rx,infoTop-boxGap*2, pw,boxH,"LEVEL",   String.valueOf(level));
        infoBox(gl,rx,infoTop-boxGap*3, pw,boxH,"LINES",   String.valueOf(lines));

        int btnAreaTop=infoTop-boxGap*4-4;
        int rowAy=btnAreaTop-btnH; int rowBy=rowAy-btnGap; int rowCy=rowBy-btnGap; int rowDy=rowCy-btnGap; int rowEy=rowDy-btnGap;
        int cs=(pw-4)/3;

        storeBtn(BTN_FWD, rx,         rowAy,cs-1,btnH); storeBtn(BTN_ROTL,rx+cs+1,    rowAy,cs-1,btnH); storeBtn(BTN_ROTR,rx+(cs+1)*2,rowAy,cs-1,btnH);
        drawArrBtn(gl,btnRects[BTN_FWD],"^",0.50f,0.75f,1.0f); drawRotBtn(gl,btnRects[BTN_ROTL],false); drawRotBtn(gl,btnRects[BTN_ROTR],true);

        storeBtn(BTN_LEFT,rx,         rowBy,cs-1,btnH); storeBtn(BTN_DOWN,rx+cs+1,    rowBy,cs-1,btnH); storeBtn(BTN_RGHT,rx+(cs+1)*2,rowBy,cs-1,btnH);
        drawArrBtn(gl,btnRects[BTN_LEFT],"<",0.50f,0.75f,1.0f); drawArrBtn(gl,btnRects[BTN_DOWN],"v",0.50f,0.75f,1.0f); drawArrBtn(gl,btnRects[BTN_RGHT],">",0.50f,0.75f,1.0f);

        storeBtn(BTN_SNAP,rx,     rowCy,pw-cs-4,btnH); storeBtn(BTN_HARD,rx+pw-cs-2,rowCy,cs,btnH);
        drawBtn(gl,btnRects[BTN_SNAP],"SNAP ASSIST",0.20f,0.80f,0.40f); drawArrBtn(gl,btnRects[BTN_HARD],"v",0.50f,0.75f,1.0f);

        int[] hdRect={rx,rowDy,pw,btnH}; btnRects[BTN_HARD]=hdRect;
        drawBtn(gl,btnRects[BTN_HARD],"HARD DROP",0.95f,0.75f,0.20f);

        int splitW = (pw - 6) / 3;
        storeBtn(BTN_PAUS, rx,                  rowEy, splitW, btnH);
        storeBtn(BTN_UNDO, rx + splitW + 3,     rowEy, splitW, btnH);
        storeBtn(BTN_RSET, rx + (splitW + 3)*2, rowEy, splitW, btnH);

        drawBtn(gl,btnRects[BTN_PAUS],paused?"RESUME":"PAUSE",0.10f,0.50f,0.95f);
        drawBtn(gl,btnRects[BTN_UNDO],"UNDO",0.85f,0.65f,0.15f);
        drawBtn(gl,btnRects[BTN_RSET],"RESET",0.90f,0.18f,0.28f);

        drawTxt(tinyFont,"FWD  ROT-L  ROT-R",rx,rowAy+btnH+2,0.32f,0.52f,0.78f,0.52f);
        drawTxt(tinyFont,"Drag to orbit / [A] Snap Assist / [Ctrl+Z] Undo",rx,rowEy-14,0.25f,0.45f,0.68f,0.50f);
    }

    private void infoBox(GL2 gl,int x,int topY,int w,int h,String lbl,String val){
        int y=topY-h;
        gl.glColor4f(0.03f,0.03f,0.10f,0.92f); fillRect(gl,x,y,w,h);
        gl.glColor4f(0.20f,0.42f,0.80f,0.18f); outlineRect(gl,x,y,w,h);
        drawTxt(tinyFont,lbl,x+6,y+h-h/3,  0.38f,0.72f,1.0f,0.82f);
        drawTxt(medFont, val,x+6,y+h/3-4,  1.0f, 1.0f, 1.0f,1.0f);
    }

    private void storeBtn(int idx,int x,int y,int w,int h){
        btnRects[idx][0]=x;btnRects[idx][1]=y;btnRects[idx][2]=w;btnRects[idx][3]=h;
    }

    private void drawBtn(GL2 gl,int[] r,String lbl,float cr,float cg,float cb){
        gl.glColor4f(cr*.15f,cg*.15f,cb*.15f,0.95f); fillRect(gl,r[0],r[1],r[2],r[3]);
        gl.glColor4f(cr*.60f,cg*.60f,cb*.60f,0.65f); fillRect(gl,r[0],r[1]+r[3]-3,r[2],3);
        gl.glColor4f(cr,cg,cb,0.68f);                outlineRect(gl,r[0],r[1],r[2],r[3]);
        drawTxt(smallFont,lbl,r[0]+r[2]/2-lbl.length()*4,r[1]+r[3]/2-7,cr,cg,cb,1f);
    }

    private void drawArrBtn(GL2 gl,int[] r,String sym,float cr,float cg,float cb){
        gl.glColor4f(cr*.12f,cg*.12f,cb*.12f,0.95f); fillRect(gl,r[0],r[1],r[2],r[3]);
        gl.glColor4f(cr*.55f,cg*.55f,cb*.55f,0.60f); fillRect(gl,r[0],r[1]+r[3]-3,r[2],3);
        gl.glColor4f(cr,cb,cg,0.65f);                outlineRect(gl,r[0],r[1],r[2],r[3]);
        drawTxt(medFont,sym,r[0]+r[2]/2-6,r[1]+r[3]/2-9,cr,cg,cb,1f);
    }

    private void drawRotBtn(GL2 gl,int[] r,boolean cw){
        float cr=0.75f,cg=0.55f,cb=0.95f;
        gl.glColor4f(cr*.12f,cg*.12f,cb*.12f,0.95f); fillRect(gl,r[0],r[1],r[2],r[3]);
        gl.glColor4f(cr*.55f,cg*.55f,cb*.55f,0.60f); fillRect(gl,r[0],r[1]+r[3]-3,r[2],3);
        gl.glColor4f(cr,cg,cb,0.65f);                outlineRect(gl,r[0],r[1],r[2],r[3]);

        float cx=r[0]+r[2]*0.5f; float cy=r[1]+r[3]*0.5f; float sz=Math.min(r[2],r[3])*0.30f;
        gl.glColor4f(cr,cg,cb,0.92f); gl.glLineWidth(2.2f);

        if (cw){
            gl.glBegin(GL2.GL_LINE_STRIP);
            for (int i=0;i<=16;i++){
                double a=Math.PI*1.25 - Math.PI*1.5*i/16.0; gl.glVertex2f(cx+(float)(Math.cos(a)*sz),cy+(float)(Math.sin(a)*sz));
            }
            gl.glEnd();
            float ax=cx+(float)(Math.cos(-Math.PI*0.25)*sz); float ay=cy+(float)(Math.sin(-Math.PI*0.25)*sz);
            gl.glBegin(GL2.GL_TRIANGLES); gl.glVertex2f(ax,ay); gl.glVertex2f(ax+sz*.5f,ay+sz*.15f); gl.glVertex2f(ax+sz*.15f,ay-sz*.5f); gl.glEnd();
        } else {
            gl.glBegin(GL2.GL_LINE_STRIP);
            for (int i=0;i<=16;i++){
                double a=-Math.PI*0.25 + Math.PI*1.5*i/16.0; gl.glVertex2f(cx+(float)(Math.cos(a)*sz),cy+(float)(Math.sin(a)*sz));
            }
            gl.glEnd();
            float ax=cx+(float)(Math.cos(Math.PI*1.25)*sz); float ay=cy+(float)(Math.sin(Math.PI*1.25)*sz);
            gl.glBegin(GL2.GL_TRIANGLES); gl.glVertex2f(ax,ay); gl.glVertex2f(ax-sz*.5f,ay+sz*.15f); gl.glVertex2f(ax-sz*.15f,ay-sz*.5f); gl.glEnd();
        }
        gl.glLineWidth(1.0f);
        String lbl=cw?"R":"L"; drawTxt(tinyFont,lbl,r[0]+4,r[1]+4,cr,cg,cb,0.80f);
    }

    private void drawStartScreen(GL2 gl){
        gl.glColor4f(0f,0f,0f,.80f); fillRect(gl,0,0,winW,winH);
        int cx=winW/2;
        drawTxt(bigFont,"TETRIS 3D",cx-145,winH*68/100,0.22f,0.72f,1.0f,1f);
        drawTxt(smallFont,"Fill a complete 10x10 XZ layer to score",cx-162,winH*61/100,0.45f,0.65f,0.90f,.80f);
        drawTxt(smallFont,"Drag 3D view to orbit  -  Scroll to zoom",cx-165,winH*58/100,0.35f,0.55f,0.80f,.70f);
        String[][] kd={
            {"LEFT/RIGHT","Move X axis"}, {"UP/DOWN",   "Move Z axis"}, {"L",         "Rotate left (CCW)"},
            {"R",         "Rotate right (CW)"}, {"S",         "Soft drop"}, {"A",         "Snap Assist"},
            {"SPACE",     "Hard drop"}, {"Ctrl + Z",  "Undo Previous Placed Block"}, {"P",         "Pause"},
            {"F",         "Toggle fullscreen"}, {"ESC",       "Reset / exit fullscreen"}
        };
        int kx=cx-140;
        for (int i=0;i<kd.length;i++){
            int ky=winH*54/100-i*(winH/28);
            gl.glColor4f(.08f,.16f,.35f,.90f); fillRect(gl,kx,ky-2,100,20);
            gl.glColor4f(.28f,.52f,.90f,.48f); outlineRect(gl,kx,ky-2,100,20);
            drawTxt(smallFont,kd[i][0],kx+4,ky+1,1f,1f,1f,1f); drawTxt(smallFont,kd[i][1],kx+108,ky+1,.72f,.85f,1.0f,.85f);
        }
        drawTxt(bigFont,"Press  SPACE  to  START",cx-140,winH/12,.18f,.68f,1.0f,.90f);
    }

    private void drawPauseOverlay(GL2 gl){
        gl.glColor4f(0f,0f,0f,.60f); fillRect(gl,leftW,winH/20,rightX-leftW,winH-winH/20);
        drawTxt(bigFont,"P A U S E D",winW/2-90,winH/2-6,.35f,.78f,1.0f,1f);
        drawTxt(smallFont,"press P to resume",winW/2-68,winH/2-34,1f,1f,1f,.52f);
    }

    private void drawGameOver(GL2 gl){
        gl.glColor4f(0f,0f,0f,.70f); fillRect(gl,leftW,winH/20,rightX-leftW,winH-winH/20);
        drawTxt(bigFont,"GAME  OVER",winW/2-95,winH/2+28,.95f,.22f,.28f,1f);
        drawTxt(medFont,"Score: "+score,winW/2-55,winH/2-8,1f,1f,1f,.85f);
        drawTxt(smallFont,"Press ESC to restart",winW/2-78,winH/2-36,1f,1f,1f,.52f);
    }

    private void quad(GL2 gl,float[]a,float[]b,float[]c,float[]dd){
        gl.glBegin(GL2.GL_QUADS); gl.glVertex2f(a[0],a[1]);gl.glVertex2f(b[0],b[1]);gl.glVertex2f(c[0],c[1]);gl.glVertex2f(dd[0],dd[1]); gl.glEnd();
    }
    private void line(GL2 gl,float[]a,float[]b){
        gl.glBegin(GL2.GL_LINES);gl.glVertex2f(a[0],a[1]);gl.glVertex2f(b[0],b[1]);gl.glEnd();
    }
    private void fillRect(GL2 gl,float x,float y,float w,float h){
        gl.glBegin(GL2.GL_QUADS); gl.glVertex2f(x,y);gl.glVertex2f(x+w,y);gl.glVertex2f(x+w,y+h);gl.glVertex2f(x,y+h); gl.glEnd();
    }
    private void outlineRect(GL2 gl,float x,float y,float w,float h){
        gl.glBegin(GL2.GL_LINE_LOOP); gl.glVertex2f(x,y);gl.glVertex2f(x+w,y);gl.glVertex2f(x+w,y+h);gl.glVertex2f(x,y+h); gl.glEnd();
    }
    private void hLine(GL2 gl,float y,float x0,float x1){
        gl.glBegin(GL2.GL_LINES);gl.glVertex2f(x0,y);gl.glVertex2f(x1,y);gl.glEnd();
    }
    private void vLine(GL2 gl,float x,float y0,float y1){
        gl.glBegin(GL2.GL_LINES);gl.glVertex2f(x,y0);gl.glVertex2f(x,y1);gl.glEnd();
    }
    private void drawTxt(TextRenderer tr,String t,int x,int y,float r,float g,float b,float a){
        tr.beginRendering(winW,winH);tr.setColor(r,g,b,a);tr.draw(t,x,y);tr.endRendering();
    }
    private boolean hit(int[] r,int mx,int sy){
        return mx>=r[0]&&mx<=r[0]+r[2]&&sy>=r[1]&&sy<=r[1]+r[3];
    }

    public static void main(String[] args){
        GLProfile profile=GLProfile.get(GLProfile.GL2);
        GLCapabilities caps=new GLCapabilities(profile);
        caps.setSampleBuffers(true); caps.setNumSamples(4);
        final GLWindow win=GLWindow.create(caps);
        win.setSize(1200,800); win.setTitle("Tetris 3D");

        final project g=new project();
        win.addGLEventListener(g);
        FPSAnimator animator = new FPSAnimator(win, 60);

        win.addKeyListener(new KeyAdapter(){
            @Override public void keyPressed(KeyEvent e){
                int k=e.getKeyCode();
                if (k==KeyEvent.VK_ESCAPE){
                    if (win.isFullscreen()) win.setFullscreen(false);
                    else g.resetGame();
                    return;
                }
                if (k==KeyEvent.VK_Z && e.isControlDown()) { g.undoLastMove(); return; }
                if (k==KeyEvent.VK_F){win.setFullscreen(!win.isFullscreen());return;}
                if (k==KeyEvent.VK_SPACE){
                    if (!g.started){g.resetGame();return;}
                    if (!g.over) g.hardDrop();
                    return;
                }
                if (k==KeyEvent.VK_P){g.togglePause();return;}
                if (!g.started||g.paused||g.over) return;
                if (k==KeyEvent.VK_LEFT  &&g.canPlace(g.curCells,g.curX-1,g.curZ,g.curY)) { g.curX--; g.playSoftSound(500, 500, 15); }
                if (k==KeyEvent.VK_RIGHT &&g.canPlace(g.curCells,g.curX+1,g.curZ,g.curY)) { g.curX++; g.playSoftSound(500, 500, 15); }
                if (k==KeyEvent.VK_UP    &&g.canPlace(g.curCells,g.curX,g.curZ-1,g.curY)) { g.curZ--; g.playSoftSound(500, 500, 15); }
                if (k==KeyEvent.VK_DOWN  &&g.canPlace(g.curCells,g.curX,g.curZ+1,g.curY)) { g.curZ++; g.playSoftSound(500, 500, 15); }
                if (k==KeyEvent.VK_L) g.rotateLeft();
                if (k==KeyEvent.VK_R) g.rotateRight();
                if (k==KeyEvent.VK_S) g.moveDown();
                if (k==KeyEvent.VK_A) g.autoAlignSmart();
            }
        });

        win.addMouseListener(new MouseAdapter(){
            @Override public void mousePressed(MouseEvent e){
                int mx=e.getX(),sy=g.winH-e.getY();
                if (!g.started||g.over) return;
                if (g.hit(g.btnRects[BTN_PAUS],mx,sy)){g.togglePause();return;}
                if (g.hit(g.btnRects[BTN_RSET],mx,sy)){g.resetGame();return;}
                if (g.hit(g.btnRects[BTN_UNDO],mx,sy)){g.undoLastMove();return;}

                if (!g.paused){
                    if (g.hit(g.btnRects[BTN_FWD], mx,sy)&&g.canPlace(g.curCells,g.curX,g.curZ-1,g.curY)) { g.curZ--; g.playSoftSound(500, 500, 15); }
                    if (g.hit(g.btnRects[BTN_SNAP],mx,sy)) g.autoAlignSmart();
                    if (g.hit(g.btnRects[BTN_LEFT],mx,sy)&&g.canPlace(g.curCells,g.curX-1,g.curZ,g.curY)) { g.curX--; g.playSoftSound(500, 500, 15); }
                    if (g.hit(g.btnRects[BTN_RGHT],mx,sy)&&g.canPlace(g.curCells,g.curX+1,g.curZ,g.curY)) { g.curX++; g.playSoftSound(500, 500, 15); }
                    if (g.hit(g.btnRects[BTN_DOWN],mx,sy)) g.moveDown();
                    if (g.hit(g.btnRects[BTN_HARD],mx,sy)) g.hardDrop();
                    if (g.hit(g.btnRects[BTN_ROTL],mx,sy)) g.rotateLeft();
                    if (g.hit(g.btnRects[BTN_ROTR],mx,sy)) g.rotateRight();
                }
                if (mx>g.leftW&&mx<g.rightX){ g.dragging=true;g.dragLastX=e.getX();g.dragLastY=e.getY(); }
            }
            @Override public void mouseReleased(MouseEvent e){g.dragging=false;}
            @Override public void mouseDragged(MouseEvent e){
                if (!g.dragging) return;
                int dx=e.getX()-g.dragLastX,dy=e.getY()-g.dragLastY;
                g.camYaw+=dx*.012; g.camPitch-=dy*.010;
                if (g.camPitch<Math.toRadians(5))  g.camPitch=Math.toRadians(5);
                if (g.camPitch>Math.toRadians(80)) g.camPitch=Math.toRadians(80);
                g.dragLastX=e.getX();g.dragLastY=e.getY();
            }
        });

        win.setVisible(true);
        animator.start();
    }
}