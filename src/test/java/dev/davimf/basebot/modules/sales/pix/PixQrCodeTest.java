package dev.davimf.basebot.modules.sales.pix;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PixQrCodeTest {

    @Test
    void generatedQrDecodesBackToContent() throws Exception {
        String content = "00020126...PIX-TEST-PAYLOAD...6304ABCD";
        byte[] png = PixQrCode.pngBytes(content, 300);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertTrue(img.getWidth() >= 300 && img.getHeight() >= 300, "image sized");

        Result decoded = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img))));
        assertEquals(content, decoded.getText());
    }
}
