package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.leanback.widget.PlaybackControlsRow.MultiAction;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.controllers.VoiceOverTranslationController;
import com.liskovsoft.smartyoutubetv2.tv.R;

public class TranslateAction extends MultiAction {
    public static final int STATE_OFF = VoiceOverTranslationController.STATE_OFF;
    public static final int STATE_WAITING = VoiceOverTranslationController.STATE_WAITING;
    public static final int STATE_FINISHED = VoiceOverTranslationController.STATE_FINISHED;

    public TranslateAction(Context context) {
        super(R.id.action_voice_over_translation);

        Bitmap baseBitmap = getBitmapFromVectorDrawable(context, R.drawable.ic_translate);
        Drawable[] drawables = new Drawable[3];
        if (baseBitmap != null) {
            // Серый (Выключено)
            int grayColor = Color.parseColor("#888888");
            drawables[STATE_OFF] = new BitmapDrawable(context.getResources(), ActionHelpers.createBitmap(baseBitmap, grayColor));
            
            // Желтый (Ожидание перевода/загрузка)
            int yellowColor = Color.parseColor("#FFCC00");
            drawables[STATE_WAITING] = new BitmapDrawable(context.getResources(), ActionHelpers.createBitmap(baseBitmap, yellowColor));
            
            // Зеленый (Переведено)
            int greenColor = Color.parseColor("#4CAF50");
            drawables[STATE_FINISHED] = new BitmapDrawable(context.getResources(), ActionHelpers.createBitmap(baseBitmap, greenColor));
        }
        setDrawables(drawables);

        String[] labels = new String[3];
        labels[STATE_OFF] = context.getString(R.string.vot_title);
        labels[STATE_WAITING] = context.getString(R.string.vot_title) + " (Ожидание)";
        labels[STATE_FINISHED] = context.getString(R.string.vot_title) + " (Готово)";
        setLabels(labels);

        setIndex(STATE_OFF);
    }

    private static Bitmap getBitmapFromVectorDrawable(Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return null;
        }
        int width = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96;
        int height = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }
}
