package com.example.zonasegura;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.List;

final class SprintDemoMap extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] zones = {
            {58, 65, 126, 45, 165, 83, 153, 128, 84, 139, 51, 104},
            {184, 114, 230, 99, 276, 132, 289, 183, 232, 199, 185, 167},
            {59, 177, 114, 155, 153, 182, 140, 230, 71, 238, 44, 209}
    };
    private final boolean thermal;
    private final List<float[]> locations;

    SprintDemoMap(Context context, boolean thermal, List<float[]> locations) {
        super(context);
        this.thermal = thermal;
        this.locations = locations;
        setContentDescription(context.getString(R.string.demo_map_accessibility));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 320f, getHeight() / 266f);
        canvas.drawColor(Color.rgb(226, 235, 230));
        paint.setColor(Color.WHITE);
        paint.setStrokeWidth(3);
        for (int x = -80; x < 320; x += 28) canvas.drawLine(x, 0, x + 80, 266, paint);
        for (int y = 10; y < 340; y += 28) canvas.drawLine(0, y, 320, y - 70, paint);
        paint.setColor(0xffb9c8c1);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        for (int radius = 55; radius <= 145; radius += 30) {
            canvas.drawCircle(160, 130, radius, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        drawZone(canvas, zones[0], 0xffb91c2f, 109, 94, "A");
        drawZone(canvas, zones[1], 0xffb91c2f, 233, 149, "B");
        drawZone(canvas, zones[2], 0xff167552, 99, 198, "C");
        for (float[] point : locations) {
            float x = point[0];
            float y = point[1];
            if (thermal) {
                paint.setColor(Color.WHITE);
                paint.setShader(new RadialGradient(x, y, 30, 0x8df07a30, Color.TRANSPARENT,
                        Shader.TileMode.CLAMP));
                canvas.drawCircle(x, y, 30, paint);
                paint.setShader(null);
            }
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 6, paint);
            paint.setColor(0xff192e3c);
            canvas.drawCircle(x, y, 4, paint);
        }
        canvas.restore();
    }

    private void drawZone(Canvas canvas, float[] points, int color, float x, float y, String label) {
        Path border = new Path();
        border.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) border.lineTo(points[i], points[i + 1]);
        border.close();
        paint.setColor((color & 0xffffff) | 0x52000000);
        canvas.drawPath(border, paint);
        if (thermal) {
            canvas.save();
            canvas.clipPath(border);
            paint.setColor(Color.WHITE);
            paint.setShader(new RadialGradient(x, y, 65, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, 65, paint);
            paint.setShader(null);
            canvas.restore();
        }
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f);
        canvas.drawPath(border, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x, y, 12, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(12);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(label, x, y + 4, paint);
    }
}
