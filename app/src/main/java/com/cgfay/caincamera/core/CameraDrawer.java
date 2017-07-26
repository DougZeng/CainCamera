package com.cgfay.caincamera.core;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import com.cgfay.caincamera.filter.base.BaseImageFilter;
import com.cgfay.caincamera.filter.camera.CameraFilter;
import com.cgfay.caincamera.gles.EglCore;
import com.cgfay.caincamera.gles.WindowSurface;
import com.cgfay.caincamera.utils.CameraUtils;
import com.cgfay.caincamera.utils.GlUtil;


/**
 * 使用枚举方式实现单例
 * 在surfaceCreated中创建线程实例，在surfaceDestroyed中销毁线程，
 * 主要是为了防止线程未释放，会导致退出后的内存呈现锯齿状，内存泄漏。
 * Created by cain on 2017/7/9.
 */

public enum CameraDrawer implements SurfaceTexture.OnFrameAvailableListener {

    INSTANCE;

    private CameraDrawerHandler mDrawerHandler;
    private HandlerThread mHandlerThread;

    // 锁
    private final Object mSynOperation = new Object();
    private final Object mSyncIsLooping = new Object();
    private long loopingInterval; // 根据fps计算
    private boolean isPreviewing = false;   // 是否预览状态
    private boolean isRecording = false;    // 是否录制状态

    CameraDrawer() {
    }

    public void surfaceCreated(SurfaceHolder holder) {
        create();
        if (mDrawerHandler != null) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_SURFACE_CREATED, holder));
        }
    }

    public void surfacrChanged(int width, int height) {
        if (mDrawerHandler != null) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_SURFACE_CHANGED, width, height));
        }
        startPreview();
    }

    public void surfaceDestroyed() {
        stopPreview();
        if (mDrawerHandler != null) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_SURFACE_DESTROYED));
        }
        destory();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        if (mDrawerHandler != null) {
            mDrawerHandler.addNewFrame();
        }
    }

    /**
     * 创建HandlerThread和Handler
     */
    private void create() {
        mHandlerThread = new HandlerThread("CameraDrawer Thread");
        mHandlerThread.start();
        mDrawerHandler = new CameraDrawerHandler(mHandlerThread.getLooper());
        mDrawerHandler.sendEmptyMessage(CameraDrawerHandler.MSG_INIT);
        loopingInterval = CameraUtils.DESIRED_PREVIEW_FPS;
    }

    /**
     * 开始预览
     */
    public void startPreview() {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_START_PREVIEW));
            synchronized (mSyncIsLooping) {
                isPreviewing = true;
            }
        }
    }

    /**
     * 停止预览
     */
    public void stopPreview() {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_STOP_PREVIEW));
            synchronized (mSyncIsLooping) {
                isPreviewing = false;
            }
        }
    }

    /**
     * 改变Filter类型
     */
    public void changeFilterType(FilterType type) {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_FILTER_TYPE, type));
        }
    }

    /**
     * 更新预览大小
     * @param width
     * @param height
     */
    public void updatePreview(int width, int height) {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_UPDATE_PREVIEW, width, height));
        }
    }

    /**
     * 开始录制
     */
    public void startRecording() {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_START_RECORDING));
        }
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendEmptyMessage(CameraDrawerHandler.MSG_STOP_RECORDING);
            synchronized (mSyncIsLooping) {
                isRecording = false;
            }
        }
    }

    /**
     * 拍照
     */
    public void takePicture() {
        if (mDrawerHandler == null) {
            return;
        }
        synchronized (mSynOperation) {
            // 发送拍照命令
            mDrawerHandler.sendMessage(mDrawerHandler
                    .obtainMessage(CameraDrawerHandler.MSG_TAKE_PICTURE));
        }
    }

    /**
     * 销毁当前持有的Looper 和 Handler
     */
    public void destory() {
        // Handler不存在时，需要销毁当前线程，否则可能会出现重新打开不了的情况
        if (mDrawerHandler == null) {
            if (mHandlerThread != null) {
                mHandlerThread.quitSafely();
                try {
                    mHandlerThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                mHandlerThread = null;
            }
            return;
        }
        synchronized (mSynOperation) {
            mDrawerHandler.sendEmptyMessage(CameraDrawerHandler.MSG_DESTROY);
            mHandlerThread.quitSafely();
            try {
                mHandlerThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mHandlerThread = null;
            mDrawerHandler = null;
        }
    }

    private class CameraDrawerHandler extends Handler {

        private static final String TAG = "CameraDrawer";

        static final int MSG_SURFACE_CREATED = 0x001;
        static final int MSG_SURFACE_CHANGED = 0x002;
        static final int MSG_FRAME = 0x003;
        static final int MSG_FILTER_TYPE = 0x004;
        static final int MSG_RESET = 0x005;
        static final int MSG_SURFACE_DESTROYED = 0x006;
        static final int MSG_INIT = 0x007;
        static final int MSG_DESTROY = 0x008;

        static final int MSG_START_PREVIEW = 0x100;
        static final int MSG_STOP_PREVIEW = 0x101;
        static final int MSG_UPDATE_PREVIEW = 0x102;

        static final int MSG_START_RECORDING = 0x200;
        static final int MSG_STOP_RECORDING = 0x201;

        static final int MSG_RESET_BITRATE = 0x300;

        static final int MSG_TAKE_PICTURE = 0x400;

        // EGL共享上下文
        private EglCore mEglCore;
        // EGLSurface
        private WindowSurface mDisplaySurface;
        // CameraTexture对应的Id
        private int mTextureId;
        private SurfaceTexture mCameraTexture;
        // 矩阵
        private final float[] mMatrix = new float[16];
        // 视图宽高
        private int mViewWidth, mViewHeight;
        // 预览图片大小
        private int mImageWidth, mImageHeight;

        // 更新帧的锁
        private final Object mSyncFrameNum = new Object();
        private final Object mSyncTexture = new Object();
        private int mFrameNum = 0;
        private boolean hasNewFrame = false;
        public boolean dropNextFrame = false;
        private boolean isTakePicture = false;
        private boolean mSaveFrame = false;
        private int mSkipFrame = 0;

        private CameraFilter mCameraFilter;
        private BaseImageFilter mFilter;

        public CameraDrawerHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                // 初始化
                case MSG_INIT:
                    break;

                // 销毁
                case MSG_DESTROY:

                    break;

                // surfacecreated
                case MSG_SURFACE_CREATED:
                    onSurfaceCreated((SurfaceHolder)msg.obj);
                    break;

                // surfaceChanged
                case MSG_SURFACE_CHANGED:
                    onSurfaceChanged(msg.arg1, msg.arg2);
                    break;

                // surfaceDestroyed;
                case MSG_SURFACE_DESTROYED:
                    onSurfaceDestoryed();
                    break;

                // 帧可用（考虑同步的问题）
                case MSG_FRAME:
                    drawFrame();
                    break;

                case MSG_FILTER_TYPE:
                    setFilter((FilterType) msg.obj);
                    break;

                // 重置
                case MSG_RESET:
                    break;

                // 开始预览
                case MSG_START_PREVIEW:
                    break;

                // 停止预览
                case MSG_STOP_PREVIEW:
                    break;

                // 更新预览大小
                case MSG_UPDATE_PREVIEW:
                    synchronized (mSyncIsLooping) {
                        mViewWidth = msg.arg1;
                        mViewHeight = msg.arg2;
                    }
                    break;

                // 开始录制
                case MSG_START_RECORDING:
                    break;

                // 停止录制
                case MSG_STOP_RECORDING:
                    break;

                // 重置bitrate(录制视频时使用)
                case MSG_RESET_BITRATE:
                    break;

                // 拍照
                case MSG_TAKE_PICTURE:
                    isTakePicture = true;
                    break;

                default:
                    throw new IllegalStateException("Can not handle message what is: " + msg.what);
            }
        }

        private void onSurfaceCreated(SurfaceHolder holder) {
            mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);
            mDisplaySurface = new WindowSurface(mEglCore, holder.getSurface(), false);
            mDisplaySurface.makeCurrent();
            if (mCameraFilter == null) {
                mCameraFilter = new CameraFilter();
            }
            mTextureId = createTextureOES();
            mCameraTexture = new SurfaceTexture(mTextureId);
            mCameraTexture.setOnFrameAvailableListener(CameraDrawer.this);
            CameraUtils.openFrontalCamera(CameraUtils.DESIRED_PREVIEW_FPS);
            calculateImageSize();
            mCameraFilter.onInputSizeChanged(mImageWidth, mImageHeight);
            mFilter = FilterManager.getFilter(FilterType.SATURATION);
            // 禁用深度测试和背面绘制
            GLES30.glDisable(GLES30.GL_DEPTH_TEST);
            GLES30.glDisable(GLES30.GL_CULL_FACE);
        }

        private void onSurfaceChanged(int width, int height) {
            mViewWidth = width;
            mViewHeight = height;
            onFilterChanged();
            CameraUtils.startPreviewTexture(mCameraTexture);
        }

        private void onSurfaceDestoryed() {
            CameraUtils.releaseCamera();
            if (mCameraTexture != null) {
                mCameraTexture.release();
                mCameraTexture = null;
            }
            if (mDisplaySurface != null) {
                mDisplaySurface.release();
                mDisplaySurface = null;
            }
            if (mEglCore != null) {
                mEglCore.release();
                mEglCore = null;
            }
        }

        /**
         * 计算imageView 的宽高
         */
        private void calculateImageSize() {
            Camera.Size size = CameraUtils.getPreviewSize();
            Camera.CameraInfo info = CameraUtils.getCameraInfo();
            if (size != null) {
                if (info.orientation == 90 || info.orientation == 270) {
                    mImageWidth = size.height;
                    mImageHeight = size.width;
                } else {
                    mImageWidth = size.width;
                    mImageHeight = size.height;
                }
            } else {
                mImageWidth = 0;
                mImageHeight = 0;
            }
        }

        /**
         * 滤镜或视图发生变化时调用
         */
        private void onFilterChanged() {
            mCameraFilter.onDisplayChanged(mViewWidth, mViewHeight);
            if (mFilter != null) {
                mCameraFilter.initCameraFramebuffer(mImageWidth, mImageHeight);
            } else {
                mCameraFilter.destroyFramebuffer();
            }
        }

        /**
         * 更新filter
         * @param type Filter类型
         */
        private void setFilter(FilterType type) {
            if (mFilter != null) {
                mFilter.release();
            }
            mFilter = FilterManager.getFilter(type);
            onFilterChanged();
        }

        /**
         * 绘制帧
         */
        private void drawFrame() {
            mDisplaySurface.makeCurrent();
            synchronized (mSyncFrameNum) {
                synchronized (mSyncTexture) {
                    if (mCameraTexture != null) {
                        // 如果存在新的帧，则更新帧
                        while (mFrameNum != 0) {
                            mCameraTexture.updateTexImage();
                            --mFrameNum;
                            // 是否舍弃下一帧
                            if (!dropNextFrame) {
                                hasNewFrame = true;
                            } else {
                                dropNextFrame = false;
                                hasNewFrame = false;
                            }
                        }
                    } else {
                        return;
                    }
                }
            }
            mCameraTexture.getTransformMatrix(mMatrix);
            if (mFilter == null) {
                mCameraFilter.drawFrame(mTextureId, mMatrix);
            } else {
                int id = mCameraFilter.drawToTexture(mTextureId, mMatrix);
                mFilter.drawFrame(id, mMatrix);
            }
            mDisplaySurface.swapBuffers();
        }

        /**
         * 添加新的一帧
         */
        public void addNewFrame() {
            synchronized (mSyncFrameNum) {
                ++mFrameNum;
                removeMessages(MSG_FRAME);
                sendMessageAtFrontOfQueue(obtainMessage(MSG_FRAME));
            }
        }

        /**
         * 创建OES 类型的Texture
         * @return
         */
        private int createTextureOES() {
            return GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        }
    }
}
