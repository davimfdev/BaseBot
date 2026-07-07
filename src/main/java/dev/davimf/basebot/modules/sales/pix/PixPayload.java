// [OUTLINE START]
// Package: dev.davimf.basebot.modules.sales.pix
// 
// Class: PixPayload
// 
// Constructors:
//   - `Constructor` : `private PixPayload(Builder b)`
// 
// Methods:
//   - `Method` : `public static Builder builder()`
//   - `Method` : `package-private static String emv(String id, String value)`
//   - `Method` : `public String toBrCode()`
//   - `Method` : `private static String clip(String s, int max)`
// 
// Fields:
//   - `Field` : `private final String key`
//   - `Field` : `private final String merchantName`
//   - `Field` : `private final String merchantCity`
//   - `Field` : `private final BigDecimal amount`
//   - `Field` : `private final String txid`
//   - `Field` : `private final String description`
// 
// Class: Builder
// 
// Methods:
//   - `Method` : `public Builder key(String v)`
//   - `Method` : `public Builder merchantName(String v)`
//   - `Method` : `public Builder merchantCity(String v)`
//   - `Method` : `public Builder amount(BigDecimal v)`
//   - `Method` : `public Builder txid(String v)`
//   - `Method` : `public Builder description(String v)`
//   - `Method` : `public PixPayload build()`
// 
// Fields:
//   - `Field` : `private String key`
//   - `Field` : `private String merchantName`
//   - `Field` : `private String merchantCity`
//   - `Field` : `private BigDecimal amount`
//   - `Field` : `private String txid`
//   - `Field` : `private String description`
// [OUTLINE END]



package dev.davimf.basebot.modules.sales.pix;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

/**
 * Builds a Pix "BR Code" (EMV®QRCPS) static payload string. Fields are encoded as
 * TLV (id + 2-digit length + value); tag 63 holds the CRC-16 over everything that
 * precedes it (including the "6304" prefix). See BOTSPECS Module 3.
 */
public final class PixPayload {

    private final String key;
    private final String merchantName;
    private final String merchantCity;
    private final BigDecimal amount;     // nullable -> omitted (open amount)
    private final String txid;           // nullable/blank -> "***"
    private final String description;    // nullable -> omitted

    private PixPayload(Builder b) {
        if (b.key == null || b.key.isBlank()) {
            throw new IllegalArgumentException("Pix key is required");
        }
        this.key = b.key.trim();
        this.merchantName = ascii(b.merchantName, 25);
        this.merchantCity = ascii(b.merchantCity, 15);
        this.amount = b.amount;
        this.txid = b.txid;
        this.description = b.description;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** TLV encode: id + 2-digit length + value. */
    static String emv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    public String toBrCode() {
        String merchantAccount = emv("00", "br.gov.bcb.pix") + emv("01", key);
        if (description != null && !description.isBlank()) {
            merchantAccount += emv("02", description.trim());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(emv("00", "01"));               // Payload Format Indicator
        sb.append(emv("26", merchantAccount));    // Merchant Account Information (Pix)
        sb.append(emv("52", "0000"));             // Merchant Category Code
        sb.append(emv("53", "986"));              // Transaction Currency (BRL)
        if (amount != null) {
            sb.append(emv("54", amount.setScale(2, RoundingMode.HALF_UP).toPlainString()));
        }
        sb.append(emv("58", "BR"));               // Country Code
        sb.append(emv("59", merchantName));       // Merchant Name
        sb.append(emv("60", merchantCity));       // Merchant City
        String ref = (txid == null || txid.isBlank()) ? "***" : txid.trim();
        sb.append(emv("62", emv("05", ref)));     // Additional Data Field (reference label)
        sb.append("6304");                         // CRC tag id + length
        sb.append(Crc16.hex4(sb.toString().getBytes(StandardCharsets.UTF_8)));
        return sb.toString();
    }

    /**
     * Recorta e dobra o texto para ASCII puro: remove acentos (NFD + marcas de combinação) e
     * qualquer caractere não-ASCII restante. Garante que o comprimento em caracteres do campo
     * TLV coincida com o comprimento em bytes — do contrário o app pagador desalinha o parse e
     * o Pix fica inválido (ex.: "Goiânia" tem 7 chars mas 8 bytes em UTF-8).
     */
    private static String ascii(String s, int max) {
        String v = (s == null) ? "" : s.trim();
        v = java.text.Normalizer.normalize(v, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replaceAll("[^\\x20-\\x7E]", "");
        return v.length() > max ? v.substring(0, max) : v;
    }

    public static final class Builder {
        private String key;
        private String merchantName = "PIX";
        private String merchantCity = "BRASIL";
        private BigDecimal amount;
        private String txid;
        private String description;

        public Builder key(String v) { this.key = v; return this; }
        public Builder merchantName(String v) { this.merchantName = v; return this; }
        public Builder merchantCity(String v) { this.merchantCity = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder txid(String v) { this.txid = v; return this; }
        public Builder description(String v) { this.description = v; return this; }

        public PixPayload build() { return new PixPayload(this); }
    }
}
