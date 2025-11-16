package com.example.edgedetectionapp.gl;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicReference;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class EdgeRenderer implements GLSurfaceView.Renderer {
    private static final float[] VERT = {
            -1f,-1f, 0f,0f,
             1f,-1f, 1f,0f,
            -1f, 1f, 0f,1f,
             1f, 1f, 1f,1f
    };
    private FloatBuffer vb;
    private int prog, aPos, aTex, uTex, uTexXform, uScale;
    private int[] tex = new int[1];
    private int surfaceW, surfaceH;
    private int frameW = 0, frameH = 0, rotationDeg = 0;
    private int uploadFrameW = 0, uploadFrameH = 0;
    private boolean rotateInput = false;
    private boolean verticalFlip = false;
    private final AtomicReference<byte[]> latest = new AtomicReference<>(null);
    private ByteBuffer grayBuf, rgbaBuf;
    private boolean initialized = false;
    private long lastNs = 0;
    private int frameCount = 0;
    private float fps = 0f;

    private static int loadShader(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        return s;
    }

    private static final String VERT_SRC =
            "attribute vec2 aPos;" +
            "attribute vec2 aTex;" +
            "uniform vec2 uScale;" +
            "varying vec2 vTex;" +
            "void main(){vTex=aTex;gl_Position=vec4(aPos*uScale,0.0,1.0);}";

            private static final String FRAG_SRC =
                "precision mediump float;" +
                "varying vec2 vTex;" +
                "uniform sampler2D uTex;" +
                "uniform mat3 uTexXform;" +
                "void main(){vec3 t=uTexXform*vec3(vTex,1.0);float g=texture2D(uTex,t.xy).r;gl_FragColor=vec4(g,g,g,1.0);}";

    public EdgeRenderer() {
        vb = ByteBuffer.allocateDirect(VERT.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vb.put(VERT).position(0);
    }

    public void updateFrame(byte[] data, int w, int h) {
        int srcW = w, srcH = h;
        if (rotateInput) {
            // We pre-rotate into the grayBuf so the uploaded texture is already
            // oriented landscape; uploadFrameW/uploadFrameH are swapped dims.
            if (grayBuf == null || grayBuf.capacity() < uploadFrameW * uploadFrameH)
                grayBuf = ByteBuffer.allocateDirect(uploadFrameW * uploadFrameH);
            grayBuf.position(0);

            if (rotationDeg == 90) {
                // rotate 90 CW: dst(x',y') = src(x,y) -> x' = srcH-1 - y; y' = x
                for (int y = 0; y < srcH; y++) {
                    for (int x = 0; x < srcW; x++) {
                        int srcIdx = y * srcW + x;
                        int dstX = srcH - 1 - y;
                        int dstY = x;
                        int dstIdx = dstY * uploadFrameW + dstX;
                        grayBuf.put(dstIdx, data[srcIdx]);
                    }
                }
            } else if (rotationDeg == 270) {
                // rotate 270 CW (or 90 CCW): dstX = y; dstY = srcW-1 - x
                for (int y = 0; y < srcH; y++) {
                    for (int x = 0; x < srcW; x++) {
                        int srcIdx = y * srcW + x;
                        int dstX = y;
                        int dstY = srcW - 1 - x;
                        int dstIdx = dstY * uploadFrameW + dstX;
                        grayBuf.put(dstIdx, data[srcIdx]);
                    }
                }
            } else if (rotationDeg == 180) {
                for (int y = 0; y < srcH; y++) {
                    for (int x = 0; x < srcW; x++) {
                        int srcIdx = y * srcW + x;
                        int dstX = srcW - 1 - x;
                        int dstY = srcH - 1 - y;
                        int dstIdx = dstY * uploadFrameW + dstX;
                        grayBuf.put(dstIdx, data[srcIdx]);
                    }
                }
            }

            grayBuf.position(0);
            latest.set(data);
        } else {
            if (grayBuf == null || grayBuf.capacity() < srcW * srcH)
                grayBuf = ByteBuffer.allocateDirect(srcW * srcH);
            grayBuf.position(0);
            grayBuf.put(data, 0, srcW * srcH);
            grayBuf.position(0);
            latest.set(data);
        }
    }


    public void setFrameInfo(int w, int h, int rot) {
        rotationDeg = ((rot%360)+360)%360;
        // Determine whether we need to pre-rotate the input so uploaded texture
        // is always landscape. If rotation is 90 or 270, swap dims for upload.
        rotateInput = (rotationDeg % 180) != 0;
        uploadFrameW = rotateInput ? h : w;
        uploadFrameH = rotateInput ? w : h;
        frameW = uploadFrameW; frameH = uploadFrameH;

        if (rgbaBuf == null || rgbaBuf.capacity() < uploadFrameW * uploadFrameH * 4)
            rgbaBuf = ByteBuffer.allocateDirect(uploadFrameW * uploadFrameH * 4).order(ByteOrder.nativeOrder());
        initialized = false;
    }

    public float getFps() { return fps; }

    public void setVerticalFlip(boolean flip) { this.verticalFlip = flip; }

    @Override public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        int vs = loadShader(GLES20.GL_VERTEX_SHADER, VERT_SRC);
        int fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAG_SRC);
        prog = GLES20.glCreateProgram();
        GLES20.glAttachShader(prog, vs);
        GLES20.glAttachShader(prog, fs);
        GLES20.glLinkProgram(prog);
        aPos = GLES20.glGetAttribLocation(prog,"aPos");
        aTex = GLES20.glGetAttribLocation(prog,"aTex");
        uTex = GLES20.glGetUniformLocation(prog,"uTex");
        uTexXform = GLES20.glGetUniformLocation(prog,"uTexXform");
        uScale = GLES20.glGetUniformLocation(prog,"uScale");
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        // Use NEAREST filtering to avoid linear interpolation artifacts when the
        // texture is rotated or scaled. NEAREST preserves row alignment.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        GLES20.glClearColor(0f,0f,0f,1f);
    }

    @Override public void onSurfaceChanged(GL10 gl, int w, int h) {
        surfaceW=w; surfaceH=h;
        GLES20.glViewport(0,0,w,h);
    }

    @Override public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (frameW<=0||frameH<=0) return;
        byte[] data = latest.get();
        if (data!=null && data.length>=frameW*frameH) {
            rgbaBuf.position(0);
            for (int i = 0; i < frameW * frameH; i++) {
                byte g = data[i];
                rgbaBuf.put(g).put(g).put(g).put((byte)255);
            }
            rgbaBuf.position(0);
        }

        float sx=1f,sy=1f;
        // Always treat the frame as landscape (no rotation applied) to avoid
        // sampling/transform artifacts when the texture is flipped.
        if (surfaceW>0&&surfaceH>0&&frameW>0&&frameH>0) {
            float frameAspect = (frameW/(float)frameH); // fixed landscape aspect
            float surfAspect = surfaceW/(float)surfaceH;
            if (frameAspect>surfAspect) sy=surfAspect/frameAspect;
            else sx=frameAspect/surfAspect;
        }

        float[] M;
        if (rotationDeg == 180) {
            M = new float[] {-1f,0f,1f, 0f,-1f,1f, 0f,0f,1f};
        } else {
            M = new float[] {1f,0f,0f, 0f,1f,0f, 0f,0f,1f};
        }

        // If vertical flip requested, pre-multiply by V = [1,0,0; 0,-1,1; 0,0,1]
        if (verticalFlip) {
            float[] V = new float[] {1f,0f,0f, 0f,-1f,1f, 0f,0f,1f};
            M = mul3(V, M);
        }

        GLES20.glUseProgram(prog);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1);
        if (grayBuf != null && grayBuf.capacity() >= frameW * frameH) {
            grayBuf.position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, frameW, frameH, 0,
                    GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, grayBuf);
        } else if (rgbaBuf != null && rgbaBuf.capacity() >= frameW * frameH * 4) {
            rgbaBuf.position(0);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, frameW, frameH, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaBuf);
        }
        GLES20.glUniform1i(uTex,0);
        GLES20.glUniform2f(uScale,sx,sy);
        // Always apply computed texture transform so rotation is preserved.
        GLES20.glUniformMatrix3fv(uTexXform,1,false,M,0);

        vb.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos,2,GLES20.GL_FLOAT,false,4*4,vb);
        vb.position(2);
        GLES20.glEnableVertexAttribArray(aTex);
        GLES20.glVertexAttribPointer(aTex,2,GLES20.GL_FLOAT,false,4*4,vb);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aTex);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);

        long now=System.nanoTime();
        if (lastNs==0) lastNs=now;
        frameCount++;
        if (now-lastNs>=1_000_000_000L) {
            fps=frameCount*1_000_000_000f/(now-lastNs);
            frameCount=0;
            lastNs=now;
        }
    }

    // Multiply 3x3 matrices A*B (row-major arrays)
    private float[] mul3(float[] A, float[] B) {
        float[] R = new float[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                float v = 0f;
                for (int k = 0; k < 3; k++) {
                    v += A[r*3 + k] * B[k*3 + c];
                }
                R[r*3 + c] = v;
            }
        }
        return R;
    }
}
