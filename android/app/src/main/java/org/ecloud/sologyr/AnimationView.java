package org.ecloud.sologyr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
    A view for animated GIFs
 */
public class AnimationView extends View {
    private final String TAG = this.getClass().getSimpleName();
    private Movie mMovie = null;
    Handler m_handler = new Handler();
    RefreshThread m_refresher = null;

    public AnimationView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public AnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public AnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public class RefreshThread extends Thread {
        @Override public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                m_handler.post(new Runnable() {
                    public void run() {
                        AnimationView.this.invalidate();
                    }
                });
                try {
                    Thread.sleep(250); // 4 fps
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void setByteArray(byte[] b) {
        if (m_refresher != null)
            m_refresher.interrupt();
        mMovie = Movie.decodeByteArray(b, 0, b.length);
        Log.d(TAG, "decoded " + b.length + " bytes to " + mMovie.duration() + " msecs, " +
            mMovie.width() + "x" + mMovie.height());
        // TODO get appropriate frame rate from movie?
        m_refresher = new RefreshThread();
        m_refresher.start();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mMovie == null)
            return;
        int dur = mMovie.duration();
        if (dur == 0)
            dur = 1000;
        mMovie.setTime((int) SystemClock.uptimeMillis() % dur);
//        Log.d(TAG, "rendering @time " + (SystemClock.uptimeMillis() % dur) + " of " + dur + " canvas " + canvas);
        // TODO scale to the display
        mMovie.draw(canvas, getWidth() / 2 - mMovie.width() / 2, 0);
    }
}
