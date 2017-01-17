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
import java.util.Date;
import java.util.LinkedList;

/**
    A graph showing forecast temperature and precipitation.
 */
public class ForecastView extends View /* implements WeatherListener */ {
    private final String TAG = this.getClass().getSimpleName();
    private int m_gridColor = Color.LTGRAY; // TODO: use a default from R.color...
    private int m_colorPrecipitationMax;
    private int m_colorPrecipitation;
    private int m_colorPrecipitationMin;
    private int m_colorPositiveTemperature;
    private int m_colorNegativeTemperature;
    private float mExampleDimension = 0; // TODO: use a default from R.dimen...
    private static final long MILLISECONDS_PER_DAY = 86400000;
    private float m_millisecondsWidth = 259200000; // 3 days
    private float m_pixelsPerMmPrecipitation = 10;
    private String mExampleString = "hello";
    private Paint m_paint;
    private TextPaint mTextPaint;
    private float mTextWidth;
    private float mTextHeight;
    LinkedList<Forecast> m_forecast = null;

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
        mExampleDimension = a.getDimension(
                R.styleable.ForecastView_exampleDimension,
                mExampleDimension);

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
        mTextPaint.setTextSize(mExampleDimension);
        mTextPaint.setColor(m_gridColor);
        mTextWidth = mTextPaint.measureText(mExampleString);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;
    }

//    @Override
//    public void updateLocation(double lat, double lon, String name, int distance) { }
//
//    @Override
//    public void updateCurrentWeather(double temperature, double cloudCover, WeatherIcon icon,
//                                     double windSpeed, double windBearing, double humidity, double dewPoint,
//                                     double pressure, double ozone, double precipIntensity) { }
//
//    @Override
//    public void updateNowCast(LinkedList<Forecast> nowcast) { }
//
//    @Override
//    public void updateSunriseSunset(int sunriseHour, int sunriseMinute, int sunsetHour, int sunsetMinute) { }

    public void updateForecast(LinkedList<Forecast> forecast) {
        Log.d(TAG, "updateForecast");
        m_forecast = forecast;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // TODO: consider storing these as member variables to reduce
        // allocations per draw cycle.
        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;
        float pixelsPerMillisecond = contentWidth / m_millisecondsWidth;
        float zeroToPx = contentHeight / 2;

        Log.d(TAG, contentWidth + " x " + contentHeight);

        // Draw the text.
        canvas.drawText(mExampleString,
                paddingLeft + (contentWidth - mTextWidth) / 2,
                paddingTop + (contentHeight + mTextHeight) / 2,
                mTextPaint);

        long now = System.currentTimeMillis();
        Calendar beginningOfToday = Calendar.getInstance();
        beginningOfToday.set(Calendar.HOUR_OF_DAY, 0);
        beginningOfToday.set(Calendar.MINUTE, 0);
        beginningOfToday.set(Calendar.SECOND, 0);
        beginningOfToday.set(Calendar.MILLISECOND, 0);
        long tomorrow = beginningOfToday.getTime().getTime() + MILLISECONDS_PER_DAY;
        m_paint.setColor(m_gridColor);
        Log.d(TAG, "pixelsPerMillisecond "  + pixelsPerMillisecond + " forecast? " + (m_forecast == null ? 0 : m_forecast.size()));
        for (float x = (tomorrow - now) * pixelsPerMillisecond;
                x < contentWidth;
                x += MILLISECONDS_PER_DAY * pixelsPerMillisecond) {
            Log.d(TAG, "day grid line @x" + x);
            canvas.drawLine(x, 0, x, contentHeight, m_paint);
        }
        canvas.drawLine(0, zeroToPx, contentWidth, zeroToPx, m_paint);

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
//Log.d(TAG, "precipitation " + precValue + " @ " + fc.getTimeFrom() + " " + startTime + " x " + (startTime * pixelsPerMillisecond) + " to x " + endTime * pixelsPerMillisecond + " barHeight " + barHeight);
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
            // TODO scale the data to fit; draw grid lines; etc.
            float lastX = -1;
            float lastY = -1;
            double lastTemp = 0;
            m_paint.setStrokeWidth(3);
            for (Forecast fc : m_forecast) {
                if (fc.getTemperature() != null) {
                    double temp = fc.getTemperature().getTemperatureDouble();
//                    long startTime = fc.getTimeFrom().getTime() - now;
                    long endTime = fc.getTimeTo().getTime() - now;
                    float x = endTime * pixelsPerMillisecond;
                    if (x < 0 || x == lastX)
                        continue;
                    float y = (float)(zeroToPx - temp * 10);
//                    Log.d(TAG, "temp is " + temp + " @ " + fc.getTimeFrom() + " " + startTime + " x " + startTime * pixelsPerMillisecond + " to x " + endTime * pixelsPerMillisecond);
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

    /**
     * Gets the example dimension attribute value.
     *
     * @return The example dimension attribute value.
     */
    public float getExampleDimension() {
        return mExampleDimension;
    }

    /**
     * Sets the view's example dimension attribute value. In the example view, this dimension
     * is the font size.
     *
     * @param exampleDimension The example dimension attribute value to use.
     */
    public void setExampleDimension(float exampleDimension) {
        mExampleDimension = exampleDimension;
        invalidateTextPaintAndMeasurements();
    }
}
