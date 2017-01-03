package org.ecloud.sologyr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

/**
    Renders a bar graph of predicted activity for one day.
 */
public class PebbleActivityView extends View implements PebbleActivityListener {
    private final String TAG = this.getClass().getSimpleName();
    private int mBarColor = Color.CYAN;
    private Paint mPaint;
    PebbleUtil m_pebbleUtil = null;
    byte[] m_vmcData = null; // array of shorts actually

    public PebbleActivityView(Context context) {
        super(context);
        init(null, 0);
    }

    public PebbleActivityView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public PebbleActivityView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    public void updateDailyActivity(byte[] vmcData) {
        m_vmcData = vmcData;
        invalidate();
    }

    private void init(AttributeSet attrs, int defStyle) {

        // Set up a default TextPaint object
        mPaint = new Paint();
        mPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mPaint.setTextAlign(Paint.Align.LEFT);

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();
    }

    private void invalidateTextPaintAndMeasurements() {
        mPaint.setColor(mBarColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (m_pebbleUtil == null) {
            m_pebbleUtil = PebbleUtil.instance();
            if (m_pebbleUtil != null) {
                m_pebbleUtil.addActivityListener(this);
                m_pebbleUtil.requestDailyActivity(0);
            }
        }
        if (m_pebbleUtil == null)
            Log.e(TAG, "PebbleUtil not available");

        super.onDraw(canvas);

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;

//        mPaint.setARGB(255, 255, 255, 255);
//        mPaint.setColor(0xFFFFFFFF);
        canvas.drawLine(0, 0, contentWidth, contentHeight, mPaint);

        if (m_vmcData != null)
            for (int i = 1; i < m_vmcData.length; i += 2) {
                int val = m_vmcData[i - 1] + m_vmcData[i] * 256;
                int x = (i * contentWidth) / (24 * 4 * 2);
                canvas.drawLine(x, 0, x, (val * contentHeight) / 16536, mPaint);
            }
    }
}
