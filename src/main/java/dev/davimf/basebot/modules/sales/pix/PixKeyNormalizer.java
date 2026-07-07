package dev.davimf.basebot.modules.sales.pix;

import java.util.regex.Pattern;

/** Normaliza e valida o valor de uma chave Pix conforme o tipo. Puro/testável. */
public final class PixKeyNormalizer {

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private PixKeyNormalizer() {}

    /** Resultado da normalização: {@code ok=true} traz {@code value}; senão traz {@code error} (PT-BR). */
    public record Result(boolean ok, String value, String error) {
        static Result ok(String value) { return new Result(true, value, null); }
        static Result fail(String error) { return new Result(false, null, error); }
    }

    public static Result normalize(String tipo, String valor) {
        if (tipo == null) {
            return Result.fail("Tipo de chave ausente.");
        }
        String raw = valor == null ? "" : valor.trim();
        return switch (tipo) {
            case "CPF" -> cpf(raw);
            case "CNPJ" -> cnpj(raw);
            case "PHONE" -> phone(raw);
            case "EMAIL" -> email(raw);
            case "RANDOM" -> random(raw);
            default -> Result.fail("Tipo de chave desconhecido.");
        };
    }

    private static Result cpf(String raw) {
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 11 || !validCpf(d)) {
            return Result.fail("CPF inválido. Confira os 11 dígitos.");
        }
        return Result.ok(d);
    }

    private static Result cnpj(String raw) {
        String d = raw.replaceAll("\\D", "");
        if (d.length() != 14 || !validCnpj(d)) {
            return Result.fail("CNPJ inválido. Confira os 14 dígitos.");
        }
        return Result.ok(d);
    }

    private static Result phone(String raw) {
        String d = raw.replaceAll("\\D", "");
        String full;
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
            full = d;
        } else if (d.length() == 10 || d.length() == 11) {
            full = "55" + d;
        } else {
            return Result.fail("Telefone inválido. Use DDD + número (ex.: 62986089609).");
        }
        if (full.length() != 12 && full.length() != 13) {
            return Result.fail("Telefone inválido. Use DDD + número (ex.: 62986089609).");
        }
        return Result.ok("+" + full);
    }

    private static Result email(String raw) {
        String v = raw.toLowerCase();
        if (!EMAIL.matcher(v).matches()) {
            return Result.fail("E-mail inválido.");
        }
        return Result.ok(v);
    }

    private static Result random(String raw) {
        String v = raw.toLowerCase();
        if (!UUID.matcher(v).matches()) {
            return Result.fail("Chave aleatória inválida (esperado formato UUID).");
        }
        return Result.ok(v);
    }

    private static boolean validCpf(String d) {
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        int c1 = checkDigit(d, 9, 10);
        int c2 = checkDigit(d, 10, 11);
        return c1 == (d.charAt(9) - '0') && c2 == (d.charAt(10) - '0');
    }

    private static int checkDigit(String d, int len, int startWeight) {
        int sum = 0;
        for (int i = 0; i < len; i++) {
            sum += (d.charAt(i) - '0') * (startWeight - i);
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }

    private static boolean validCnpj(String d) {
        if (d.chars().distinct().count() == 1) {
            return false;
        }
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int c1 = cnpjDigit(d, w1);
        int c2 = cnpjDigit(d, w2);
        return c1 == (d.charAt(12) - '0') && c2 == (d.charAt(13) - '0');
    }

    private static int cnpjDigit(String d, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (d.charAt(i) - '0') * weights[i];
        }
        int mod = sum % 11;
        return mod < 2 ? 0 : 11 - mod;
    }
}
