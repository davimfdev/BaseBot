package dev.davimf.basebot.modules.base.leveling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Funde o tempo salvo no banco com o pendente de quem está em call agora, descarta totais zerados e
 * ordena. Puro, para o ranking ser testável sem JDA.
 *
 * <p>Ordenar aqui — e não no SQL — é o que permite o pendente mudar posições. Também torna a
 * contagem total e a página exibida coerentes: ambas saem da MESMA lista, e não de duas queries
 * independentes que podem discordar.
 */
public final class VoiceRanking {

    private VoiceRanking() {}

    public static List<VoiceTimeRepository.Entry> merge(List<VoiceTimeRepository.Entry> saved,
                                                        Map<String, Long> pending) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (VoiceTimeRepository.Entry e : saved) {
            totals.merge(e.userId(), e.ms(), Long::sum);
        }
        pending.forEach((userId, ms) -> totals.merge(userId, ms, Long::sum));

        List<VoiceTimeRepository.Entry> out = new ArrayList<>();
        totals.forEach((userId, ms) -> {
            if (ms > 0) {
                out.add(new VoiceTimeRepository.Entry(userId, ms));
            }
        });
        out.sort(Comparator.comparingLong(VoiceTimeRepository.Entry::ms).reversed()
                .thenComparing(VoiceTimeRepository.Entry::userId));
        return out;
    }
}
