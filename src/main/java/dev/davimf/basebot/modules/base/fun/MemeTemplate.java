package dev.davimf.basebot.modules.base.fun;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

/** Config fracionária (0–1) de um meme: onde vai o avatar (por baixo) e o texto (por cima). */
public record MemeTemplate(String resourcePath, AvatarBox avatar, List<TextAnchor> texts) {

    public enum Align { LEFT, CENTER, RIGHT }

    /** Caixa que o avatar preenche (cover-crop). O template com furo transparente por cima recorta o formato. */
    public record AvatarBox(double centerX, double centerY, double w, double h) {}

    /** centerX/centerY/maxWidth em frações; fontSizeH = tamanho da fonte como fração da ALTURA. */
    public record TextAnchor(String key, double centerX, double centerY, double maxWidth,
                             double fontSizeH, Color color, String fontFamily, int fontStyle, Align align) {}

    private static final Color PARCH = new Color(0x3B, 0x2A, 0x1A);

    public static final MemeTemplate PROCURADO = new MemeTemplate(
            "/memes/procurado.png",
            // Cobre a abertura da moldura com folga; o furo transparente do template recorta o formato.
            new AvatarBox(0.50, 0.44, 0.66, 0.37),
            List.of(
                    new TextAnchor("title",  0.5, 0.19, 0.80, 0.055, PARCH, Font.SERIF, Font.BOLD, Align.CENTER),
                    new TextAnchor("reward", 0.5, 0.72, 0.80, 0.032, PARCH, Font.SERIF, Font.BOLD, Align.CENTER),
                    new TextAnchor("name",   0.5, 0.82, 0.75, 0.030, PARCH, Font.SERIF, Font.BOLD, Align.CENTER)));

    public static final MemeTemplate CARTA_REVERSO = new MemeTemplate(
            "/memes/carta_reverso.png",
            // Preenche toda a elipse branca; as setas + borda azul do template ficam por cima.
            new AvatarBox(0.50, 0.50, 0.90, 0.92),
            List.of());
}
