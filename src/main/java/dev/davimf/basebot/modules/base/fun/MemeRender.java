package dev.davimf.basebot.modules.base.fun;

import dev.davimf.basebot.modules.base.fun.MemeTemplate.AvatarBox;
import dev.davimf.basebot.modules.base.fun.MemeTemplate.TextAnchor;

import javax.imageio.ImageIO;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/** Composição de meme: avatar por baixo → template (com furo transparente) por cima → texto. */
public final class MemeRender {

    private static final float MIN_FONT = 10f;

    private MemeRender() {}

    public static byte[] compose(BufferedImage template, BufferedImage avatar, MemeTemplate spec,
                                 Map<String, String> textos) throws IOException {
        int tW = template.getWidth();
        int tH = template.getHeight();
        BufferedImage out = new BufferedImage(tW, tH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 1. Avatar POR BAIXO, cover-crop na caixa (o template mascara o formato exato).
        drawAvatarCover(g, avatar, spec.avatar(), tW, tH);
        // 2. Template POR CIMA — o furo transparente revela o avatar; o resto cobre.
        g.drawImage(template, 0, 0, null);
        // 3. Texto por cima do template (só as chaves presentes no map).
        for (TextAnchor a : spec.texts()) {
            String text = textos.get(a.key());
            if (text != null && !text.isBlank()) {
                drawText(g, text, a, tW, tH);
            }
        }

        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", baos);
        return baos.toByteArray();
    }

    /** Preenche a caixa com o avatar (cover-crop, sem distorção). O template por cima recorta o formato. */
    private static void drawAvatarCover(Graphics2D g, BufferedImage avatar, AvatarBox box, int tW, int tH) {
        int boxW = (int) Math.round(box.w() * tW);
        int boxH = (int) Math.round(box.h() * tH);
        int boxX = (int) Math.round(box.centerX() * tW) - boxW / 2;
        int boxY = (int) Math.round(box.centerY() * tH) - boxH / 2;
        int aW = avatar.getWidth();
        int aH = avatar.getHeight();
        double scale = Math.max((double) boxW / aW, (double) boxH / aH);
        int drawW = (int) Math.round(aW * scale);
        int drawH = (int) Math.round(aH * scale);
        int drawX = boxX + (boxW - drawW) / 2;
        int drawY = boxY + (boxH - drawH) / 2;
        g.drawImage(avatar, drawX, drawY, drawW, drawH, null);
    }

    private static void drawText(Graphics2D g, String text, TextAnchor a, int tW, int tH) {
        int maxW = (int) Math.round(a.maxWidth() * tW);
        float size = (float) (a.fontSizeH() * tH);
        Font font = new Font(a.fontFamily(), a.fontStyle(), 12).deriveFont(size);
        FontMetrics fm = g.getFontMetrics(font);
        while (fm.stringWidth(text) > maxW && size > MIN_FONT) {
            size -= 1f;
            font = font.deriveFont(size);
            fm = g.getFontMetrics(font);
        }
        String draw = text;
        if (fm.stringWidth(draw) > maxW) {
            draw = ellipsize(draw, fm, maxW);
        }
        int strW = fm.stringWidth(draw);
        int cx = (int) Math.round(a.centerX() * tW);
        int tx = switch (a.align()) {
            case CENTER -> cx - strW / 2;
            case LEFT -> cx;
            case RIGHT -> cx - strW;
        };
        int ty = (int) Math.round(a.centerY() * tH) + (fm.getAscent() - fm.getDescent()) / 2;
        g.setFont(font);
        g.setColor(a.color());
        g.drawString(draw, tx, ty);
    }

    private static String ellipsize(String text, FontMetrics fm, int maxW) {
        String ell = "…";
        if (fm.stringWidth(ell) > maxW) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() > 0 && fm.stringWidth(sb + ell) > maxW) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb + ell;
    }
}
