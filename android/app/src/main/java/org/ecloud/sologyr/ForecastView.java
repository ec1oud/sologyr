package org.ecloud.sologyr;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.oleaarnseth.weathercast.Forecast;
import com.oleaarnseth.weathercast.Precipitation;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;

/**
    A graph showing forecast temperature and precipitation.
 */
public class ForecastView extends View /* implements WeatherListener */ {
    private final String TAG = this.getClass().getSimpleName();
    private static final double TEMPERATURE_RANGE_GRANULARITY = 5.0;
    private static final long MILLISECONDS_PER_DAY = 86400000;
    private int m_gridColor = Color.LTGRAY; // TODO: use a default from R.color...
    private int m_colorPrecipitationMax;
    private int m_colorPrecipitation;
    private int m_colorPrecipitationMin;
    private int m_colorPositiveTemperature;
    private int m_colorNegativeTemperature;
    private float m_textSize = 24;
    private float m_millisecondsWidth = 432000000; // 5 days
    private float m_pixelsPerMmPrecipitation = 10;
    private String m_locationName = "";
    private Paint m_paint;
    private TextPaint mTextPaint;
    private float mTextWidth;
    private float mTextHeight;
    LinkedList<Forecast> m_forecast = null;
    private double m_minForecastTemp = 1000;
    private double m_maxForecastTemp = -1000;

    public ForecastView(Context context) {
        super(context);
        init(null, 0);
    }

    public ForecastView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public ForecastView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Load attributes
        final TypedArray a = getContext().obtainStyledAttributes(
                attrs, R.styleable.ForecastView, defStyle, 0);

        m_gridColor = a.getColor(
                R.styleable.ForecastView_gridColor,
                m_gridColor);
        m_colorPrecipitationMax = getResources().getColor(R.color.colorPrecipitationMax);
        m_colorPrecipitation = getResources().getColor(R.color.colorPrecipitation);
        m_colorPrecipitationMin = getResources().getColor(R.color.colorPrecipitationMin);
        m_colorPositiveTemperature = getResources().getColor(R.color.colorPositiveTemperature);
        m_colorNegativeTemperature = getResources().getColor(R.color.colorNegativeTemperature);
        // Use getDimensionPixelSize or getDimensionPixelOffset when dealing with
        // values that should fall on pixel boundaries.
        m_textSize = a.getDimension(R.styleable.ForecastView_textSize, m_textSize);

        a.recycle();

        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);

        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();

        m_paint = new Paint();
        m_paint.setFlags(Paint.ANTI_ALIAS_FLAG);
    }

    private void invalidateTextPaintAndMeasurements() {
        mTextPaint.setTextSize(m_textSize);
        mTextPaint.setColor(m_gridColor);
        mTextWidth = mTextPaint.measureText(m_locationName);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;
    }

    public void updateForecast(String locationName, LinkedList<Forecast> forecast) {
        m_locationName = locationName;
        m_forecast = forecast;
        m_minForecastTemp = 1000;
        m_maxForecastTemp = -1000;
        if (m_forecast != null)
            for (Forecast fc : m_forecast) {
                if (fc.getTemperature() == null)
                    continue;
                double t = fc.getTemperature().getTemperatureDouble();
                if (t > m_maxForecastTemp)
                    m_maxForecastTemp = t;
                if (t < m_minForecastTemp)
                    m_minForecastTemp = t;
            }
        Log.d(TAG, "updateForecast " + locationName + " temp min " + m_minForecastTemp + " max " + m_maxForecastTemp);
        m_minForecastTemp = Math.floor(m_minForecastTemp / TEMPERATURE_RANGE_GRANULARITY) * TEMPERATURE_RANGE_GRANULARITY;
        m_maxForecastTemp = Math.ceil(m_maxForecastTemp / TEMPERATURE_RANGE_GRANULARITY) * TEMPERATURE_RANGE_GRANULARITY;
        Log.d(TAG, "rounded temp min " + m_minForecastTemp + " max " + m_maxForecastTemp);
        mTextWidth = mTextPaint.measureText(m_locationName);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int contentWidth = canvas.getWidth();
        int contentHeight = canvas.getHeight();
        float pixelsPerMillisecond = contentWidth / m_millisecondsWidth;

        Log.d(TAG, contentWidth + " x " + contentHeight);

        // Draw the text.
        canvas.drawText(m_locationName,
                (contentWidth - mTextWidth) / 2,
                (contentHeight + mTextHeight) / 2 - 12,
                mTextPaint);

        long now = System.currentTimeMillis();
        Calendar beginningOfDay = Calendar.getInstance();
        beginningOfDay.set(Calendar.HOUR_OF_DAY, 0);
        beginningOfDay.set(Calendar.MINUTE, 0);
        beginningOfDay.set(Calendar.SECOND, 0);
        beginningOfDay.set(Calendar.MILLISECOND, 0);
        long tomorrow = beginningOfDay.getTime().getTime() + MILLISECONDS_PER_DAY;
        beginningOfDay.add(Calendar.DAY_OF_MONTH, 1);
        m_paint.setColor(m_gridColor);
        m_paint.setStrokeWidth(1);
        boolean spaceForTopAxisLabel = (tomorrow - now) * pixelsPerMillisecond > 80;
        Log.d(TAG, "pixelsPerMillisecond "  + pixelsPerMillisecond + " forecast? " + (m_forecast == null ? 0 : m_forecast.size()) + " text height " + mTextHeight);
        for (float x = (tomorrow - now) * pixelsPerMillisecond;
                x < contentWidth;
                x += MILLISECONDS_PER_DAY * pixelsPerMillisecond) {
            Log.d(TAG, "day grid line @x" + x);
            canvas.drawLine(x, 0, x, contentHeight, m_paint);
            canvas.drawText(beginningOfDay.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()), x + 2, m_textSize, mTextPaint);
            beginningOfDay.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (m_forecast != null) {
            long lastEndTime = -1;
            m_paint.setColor(m_colorPrecipitationMax);
            for (Forecast fc : m_forecast) {
                if (fc.getPrecipitation() != null) {
                    Precipitation prec = fc.getPrecipitation();
                    long startTime = fc.getTimeFrom().getTime() - now;
                    long endTime = fc.getTimeTo().getTime() - now;
                    if (endTime < 0 || endTime == lastEndTime)
                        continue;
                    float precValue = (float)prec.getPrecipitationMax();
                    float barHeight = Math.min(contentHeight, precValue * m_pixelsPerMmPrecipitation);
//                    Log.d(TAG, "precipitation max " + precValue + " @ " + fc.getTimeFrom() + " " + startTime + " x " + (startTime * pixelsPerMillisecond) + " to x " + endTime * pixelsPerMillisecond + " barHeight " + barHeight);
                    if (precValue > 0)
                        canvas.drawRect(startTime * pixelsPerMillisecond, contentHeight - barHeight,
                                endTime * pixelsPerMillisecond, contentHeight, m_paint);
                    lastEndTime = endTime;
                }
            }
            m_paint.setColor(m_colorPrecipitation);
            for (Forecast fc : m_forecast) {
                if (fc.getPrecipitation() != null) {
                    Precipitation prec = fc.getPrecipitation();
                    long startTime = fc.getTimeFrom().getTime() - now;
                    long endTime = fc.getTimeTo().getTime() - now;
                    if (endTime < 0 || endTime == lastEndTime)
                        continue;
                    float precValue = (float)prec.getPrecipitation();
                    float barHeight = Math.min(contentHeight, precValue * m_pixelsPerMmPrecipitation);
                    if (precValue > 0)
                        canvas.drawRect(startTime * pixelsPerMillisecond, contentHeight - barHeight,
                                endTime * pixelsPerMillisecond, contentHeight, m_paint);
                    lastEndTime = endTime;
                }
            }
            m_paint.setColor(m_colorPrecipitationMin);
            for (Forecast fc : m_forecast) {
                if (fc.getPrecipitation() != null) {
                    Precipitation prec = fc.getPrecipitation();
                    long startTime = fc.getTimeFrom().getTime() - now;
                    long endTime = fc.getTimeTo().getTime() - now;
                    if (endTime < 0 || endTime == lastEndTime)
                        continue;
                    float precValue = (float)prec.getPrecipitationMin();
                    float barHeight = Math.min(contentHeight, precValue * m_pixelsPerMmPrecipitation);
                    if (precValue > 0)
                        canvas.drawRect(startTime * pixelsPerMillisecond, contentHeight - barHeight,
                                endTime * pixelsPerMillisecond, contentHeight, m_paint);
                    lastEndTime = endTime;
                }
            }

            // Temperature
            float lastX = -1;
            float lastY = -1;
            double lastTemp = 0;
            double scale = contentHeight / (m_maxForecastTemp - m_minForecastTemp); // pixels per degree
            float zeroToPx = (float)(scale * m_maxForecastTemp);
            m_paint.setColor(m_gridColor);
            canvas.drawLine(0, zeroToPx, contentWidth, zeroToPx, m_paint);
            m_paint.setStrokeWidth(2);
            for (float t = (float)m_minForecastTemp; t <= m_maxForecastTemp; t += TEMPERATURE_RANGE_GRANULARITY) {
                float y = (float)(zeroToPx - t * scale);
                canvas.drawLine(0, y, 15, y, m_paint);
                canvas.drawLine(contentWidth - 15, t, contentWidth, t, m_paint);
                if (t == m_minForecastTemp)
                    canvas.drawText(String.valueOf((int)t) + "℃", 15, y, mTextPaint);
                else if (t == m_maxForecastTemp) {
                    if (spaceForTopAxisLabel)
                        canvas.drawText(String.valueOf((int) t) + "℃", 15, y + m_textSize, mTextPaint);
                } else if (t != 0)
                    canvas.drawText(String.valueOf((int)t), 15, y + m_textSize / 2, mTextPaint);
//                Log.d(TAG, "big tick " + t + " " + y);
                if (scale >= 3.0)
                    for (float t2 = t + 1; t2 < m_maxForecastTemp && t2 < t + TEMPERATURE_RANGE_GRANULARITY; ++t2) {
                        float y2 = (float)(zeroToPx - t2 * scale);
                        canvas.drawLine(0, y2, 5, y2, m_paint);
                        canvas.drawLine(contentWidth - 5, y2, contentWidth, y2, m_paint);
//                        Log.d(TAG, "little tick " + t2 + " " + y2);
                    }
            }
            m_paint.setStrokeWidth(3);
            Log.d(TAG, "temperature scale " + scale + " zeroToPx " + zeroToPx);
            for (Forecast fc : m_forecast) {
                if (fc.getTemperature() != null) {
                    double temp = fc.getTemperature().getTemperatureDouble();
//                    long startTime = fc.getTimeFrom().getTime() - now;
                    long endTime = fc.getTimeTo().getTime() - now;
                    float x = endTime * pixelsPerMillisecond;
                    if (x < 0 || x == lastX)
                        continue;
                    float y = (float)(zeroToPx - temp * scale);
//                    Log.d(TAG, "temperature is " + temp + " @ " + fc.getTimeFrom() + " " + lastX + ", " + lastY + " to " + x + ", " + y );
                    if (lastX >= 0) {
                        if (lastTemp >= 0 && temp >= 0) {
                            m_paint.setColor(m_colorPositiveTemperature);
                            canvas.drawLine(lastX, lastY, x, y, m_paint);
                        } else if (lastTemp <= 0 && temp <= 0){
                            m_paint.setColor(m_colorNegativeTemperature);
                            canvas.drawLine(lastX, lastY, x, y, m_paint);
                        } else {
                            // draw separate positive and negative portions
                            long dt = Math.abs(Math.round(lastTemp * (x - lastX) / (lastTemp - temp)));
                            m_paint.setColor(lastTemp < 0 ? m_colorNegativeTemperature : m_colorPositiveTemperature);
                            canvas.drawLine(lastX, lastY, lastX + dt, zeroToPx, m_paint);
                            m_paint.setColor(temp < 0 ? m_colorNegativeTemperature : m_colorPositiveTemperature);
                            canvas.drawLine(lastX + dt, zeroToPx, x, y, m_paint);
                        }
                    }
                    lastX = x;
                    lastY = y;
                    lastTemp = temp;
                }
            }
        }
    }

    public int getGridColor() {
        return m_gridColor;
    }

    public void setGridColor(int c) {
        m_gridColor = c;
        invalidateTextPaintAndMeasurements();
    }

    public float getTextSize() {
        return m_textSize;
    }

    public void setTextSize(float s) {
        m_textSize = s;
        invalidateTextPaintAndMeasurements();
    }
}
