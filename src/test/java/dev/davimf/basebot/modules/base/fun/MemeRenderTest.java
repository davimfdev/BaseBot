package dev.davimf.basebot.modules.base.fun;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemeRenderTest {

    private static BufferedImage solid(int w, int h, Color c) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return img;
    }

    private static BufferedImage readPng(byte[] bytes) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @Test
    void procuradoProducesValidPngOfTemplateSize() throws Exception {
        BufferedImage tpl = solid(200, 300, new Color(220, 200, 160));
        BufferedImage avatar = solid(128, 128, Color.BLUE);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.PROCURADO,
                Map.of("title", "PROCURADO", "reward", "RECOMPENSA $10.000", "name", "Fulano"));
        BufferedImage out = readPng(png);
        assertNotNull(out);
        assertEquals(200, out.getWidth());
        assertEquals(300, out.getHeight());
    }

    @Test
    void cartaCircleDoesNotThrow() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        BufferedImage avatar = solid(128, 128, Color.RED);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.CARTA_REVERSO, Map.of());
        assertNotNull(readPng(png));
    }

    @Test
    void hugeTextStillValid() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        BufferedImage avatar = solid(64, 64, Color.GREEN);
        String huge = "Nome absurdamente gigante ".repeat(20);
        byte[] png = MemeRender.compose(tpl, avatar, MemeTemplate.PROCURADO, Map.of("name", huge));
        assertNotNull(readPng(png));
    }

    @Test
    void cachedTemplateNotMutated() throws Exception {
        BufferedImage tpl = solid(200, 300, new Color(1, 2, 3));
        int before = tpl.getRGB(10, 10);
        MemeRender.compose(tpl, solid(64, 64, Color.RED), MemeTemplate.PROCURADO, Map.of("title", "X"));
        assertEquals(before, tpl.getRGB(10, 10), "o template cacheado não pode ser alterado");
    }

    @Test
    void missingTextKeysAreIgnored() throws Exception {
        BufferedImage tpl = solid(200, 300, Color.WHITE);
        // PROCURADO tem 3 âncoras; passamos nenhuma → não deve lançar
        byte[] png = MemeRender.compose(tpl, solid(64, 64, Color.RED), MemeTemplate.PROCURADO, Map.of());
        assertNotNull(readPng(png));
    }
}
