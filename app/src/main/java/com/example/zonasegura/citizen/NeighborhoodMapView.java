package com.example.zonasegura.citizen;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** Ilustración local: no es un servicio cartográfico ni calcula una capa térmica real. */
public final class NeighborhoodMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public NeighborhoodMapView(Context context, AttributeSet attributes) { super(context, attributes); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 360f, getHeight() / 230f);
        canvas.drawColor(Color.rgb(232, 237, 226));
        paint.setColor(Color.rgb(217, 226, 207));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 4; x++)
            canvas.drawRoundRect(x * 103 - 18, y * 90 - 18, x * 103 + 63, y * 90 + 50, 7, 7, paint);
        paint.setColor(Color.rgb(182, 210, 174));
        canvas.drawRoundRect(188, 74, 269, 140, 10, 10, paint);
        paint.setColor(Color.WHITE); paint.setStrokeWidth(13);
        for (int x = 0; x < 3; x++) canvas.drawLine(75 + x * 103, 0, 75 + x * 103, 230, paint);
        for (int y = 0; y < 2; y++) canvas.drawLine(0, 62 + y * 90, 360, 62 + y * 90, paint);
        Path avenue = new Path(); avenue.moveTo(-10, 222); avenue.cubicTo(110, 175, 225, 38, 370, 15);
        paint.setColor(Color.rgb(215, 221, 208)); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(24);
        canvas.drawPath(avenue, paint); paint.setColor(Color.WHITE); paint.setStrokeWidth(18); canvas.drawPath(avenue, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new RadialGradient(282, 44, 45, new int[]{0x55DDA34B, 0x00DDA34B}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(282, 44, 45, paint); paint.setShader(null);
        paint.setColor(Color.rgb(95, 117, 93)); paint.setTextSize(10); paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        canvas.drawText("PLAZA", 211, 111, paint);
        paint.setTextSize(9); paint.setTypeface(android.graphics.Typeface.DEFAULT);
        canvas.drawText("Av. del barrio", 19, 207, paint); canvas.drawText("Calle vecinal", 10, 57, paint);
        marker(canvas, 126, 116, Color.rgb(193, 129, 37));
        marker(canvas, 282, 44, Color.rgb(23, 107, 84));
        marker(canvas, 225, 183, Color.rgb(193, 129, 37));
        paint.setColor(Color.WHITE); canvas.drawCircle(177, 157, 11, paint);
        paint.setColor(Color.rgb(65, 114, 174)); canvas.drawCircle(177, 157, 6, paint);
        canvas.restore();
    }
    private void marker(Canvas canvas, float x, float y, int color) {
        paint.setColor(Color.WHITE); canvas.drawCircle(x, y, 12, paint);
        paint.setColor(color); canvas.drawCircle(x, y, 9, paint);
        paint.setColor(Color.WHITE); canvas.drawCircle(x, y, 3, paint);
    }
}
