package com.example.edgedetectionapp.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;

public class EdgeGLSurfaceView extends GLSurfaceView {
    private final EdgeRenderer renderer;
    public EdgeGLSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        renderer = new EdgeRenderer();
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }
    public void updateFrame(byte[] data, int w, int h) {
        Log.d("GLView", "updateFrame: " + data.length + " bytes " + w + "x" + h);
        renderer.updateFrame(data, w, h);
        requestRender();
    }
    public void setFrameInfo(int w, int h, int rotationDeg) {
        renderer.setFrameInfo(w, h, rotationDeg);
    }
    public float getFps() { return renderer.getFps(); }
}
