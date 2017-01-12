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
    private Movie m_movie = null;
    Handler m_handler = new Handler();
    RefreshThread m_refresher = null;
    int m_waited = 0; // count in seconds during which m_refresher sees that there's no movie

    public AnimationView(Context context) {
        super(context);
        init();
    }

    public AnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AnimationView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public void stop() {
        Log.d(TAG, "stop");
        if (m_refresher != null) {
            m_refresher.interrupt();
            m_refresher = null;
        }
    }

    public void start() {
        Log.d(TAG, "start");
        // TODO get appropriate frame rate from movie?
        m_refresher = new RefreshThread();
        m_refresher.start();
    }

    public class RefreshThread extends Thread {
        @Override public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                if (m_movie == null) {
                    if (m_waited++ > 60) {
                        Log.d(TAG, "refresh thread: no movie, giving up on rendering");
                        return;
                    }
                } else {
                    m_handler.post(new Runnable() {
                        public void run() {
                            AnimationView.this.invalidate();
                        }
                    });
                }
                try {
                    Thread.sleep(m_movie == null ? 1000 : 250); // 4 fps
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public void setByteArray(byte[] b) {
        m_movie = Movie.decodeByteArray(b, 0, b.length);
        if (b != null && m_movie != null) {
            Log.d(TAG, "decoded " + b.length + " bytes to " + m_movie.duration() + " msecs, " +
                    m_movie.width() + "x" + m_movie.height());
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (m_movie == null)
            return;
        int dur = m_movie.duration();
        if (dur == 0)
            dur = 1000;
        m_movie.setTime((int) SystemClock.uptimeMillis() % dur);
//        Log.d(TAG, "rendering @time " + (SystemClock.uptimeMillis() % dur) + " of " + dur + " canvas " + canvas);
        float scale = Math.min((float)this.getWidth() / (float)m_movie.width(),
                (float)this.getHeight() / (float)m_movie.height());
        canvas.scale(scale, scale);
        m_movie.draw(canvas, getWidth() / 2 - m_movie.width() * scale / 2, 0);
    }
}
