package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.SlashCommand;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.economy.EconomyRepository;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository.Input;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository.Recipe;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * /produzir — defines recipes and crafts products by consuming raw materials from stock
 * (BOTSPECS Module 4). {@code receita} (manager) defines a recipe of up to 3 inputs;
 * {@code fazer} crafts it; {@code lista} shows all recipes.
 */
public final class ProduzirCommand implements SlashCommand {

    private final EconomyRepository economy;
    private final RecipeRepository recipes;

    public ProduzirCommand(EconomyRepository economy, RecipeRepository recipes) {
        this.economy = economy;
        this.recipes = recipes;
    }

    @Override
    public String name() {
        return "produzir";
    }

    @Override
    public SlashCommandData data() {
        SubcommandData receita = new SubcommandData("receita", "Define/atualiza uma receita de produção.")
                .addOption(OptionType.STRING, "produto", "Produto final", true)
                .addOptions(new OptionData(OptionType.INTEGER, "saida", "Qtd produzida por fabricação", false)
                        .setMinValue(1))
                .addOption(OptionType.STRING, "entrada1", "Material 1", true)
                .addOptions(new OptionData(OptionType.INTEGER, "qtd1", "Qtd do material 1", true).setMinValue(1))
                .addOption(OptionType.STRING, "entrada2", "Material 2 (opcional)", false)
                .addOptions(new OptionData(OptionType.INTEGER, "qtd2", "Qtd do material 2", false).setMinValue(1))
                .addOption(OptionType.STRING, "entrada3", "Material 3 (opcional)", false)
                .addOptions(new OptionData(OptionType.INTEGER, "qtd3", "Qtd do material 3", false).setMinValue(1));
        SubcommandData fazer = new SubcommandData("fazer", "Fabrica um produto consumindo o estoque.")
                .addOption(OptionType.STRING, "produto", "Produto a fabricar", true)
                .addOptions(new OptionData(OptionType.INTEGER, "vezes", "Quantas fabricações", false)
                        .setMinValue(1).setMaxValue(10_000));
        SubcommandData lista = new SubcommandData("lista", "Lista as receitas cadastradas.");
        return Commands.slash("produzir", "Produção: receitas e fabricação a partir do estoque.")
                .addSubcommands(receita, fazer, lista);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.reply("Use este comando em um servidor.").setEphemeral(true).queue();
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "receita" -> receita(event, ctx);
            case "fazer" -> fazer(event, ctx);
            case "lista" -> lista(event);
            default -> event.reply("Subcomando inválido.").setEphemeral(true).queue();
        }
    }

    private void receita(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("Apenas a gerência pode definir receitas.").setEphemeral(true).queue();
            return;
        }
        String produto = event.getOption("produto", OptionMapping::getAsString).trim();
        long saida = event.getOption("saida", 1L, OptionMapping::getAsLong);
        List<Input> inputs = new ArrayList<>();
        addInput(inputs, event, "entrada1", "qtd1");
        addInput(inputs, event, "entrada2", "qtd2");
        addInput(inputs, event, "entrada3", "qtd3");
        if (inputs.isEmpty()) {
            event.reply("Informe ao menos um material de entrada.").setEphemeral(true).queue();
            return;
        }
        recipes.upsert(event.getGuild().getId(), produto, saida, inputs);
        String list = inputs.stream().map(i -> i.qty() + "x " + i.item()).reduce((a, b) -> a + ", " + b).orElse("");
        event.reply("🛠️ Receita salva: **" + saida + "x " + produto + "** ← " + list).setEphemeral(true).queue();
    }

    private void fazer(SlashCommandInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        String produto = event.getOption("produto", OptionMapping::getAsString).trim();
        long vezes = event.getOption("vezes", 1L, OptionMapping::getAsLong);
        Optional<Recipe> maybe = recipes.findByProduct(guildId, produto);
        if (maybe.isEmpty()) {
            event.reply("Receita não encontrada para **" + produto + "**. Crie com /produzir receita.")
                    .setEphemeral(true).queue();
            return;
        }
        Recipe recipe = maybe.get();
        List<String> missing = new ArrayList<>();
        for (Input in : recipe.inputs()) {
            long need = in.qty() * vezes;
            if (economy.getStock(guildId, in.item()) < need) {
                missing.add(need + "x " + in.item() + " (tem " + economy.getStock(guildId, in.item()) + ")");
            }
        }
        if (!missing.isEmpty()) {
            event.reply("Estoque insuficiente:\n• " + String.join("\n• ", missing)).setEphemeral(true).queue();
            return;
        }
        for (Input in : recipe.inputs()) {
            economy.deductStock(guildId, in.item(), in.qty() * vezes);
        }
        long produced = recipe.outputQty() * vezes;
        long newStock = economy.addStock(guildId, produto, produced);

        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null, "PRODUCE",
                produced + "x " + produto);
        FacsLog.post(ctx, guildId, "log-farm", "## 🛠️ Produção\n<@" + event.getUser().getId()
                + "> fabricou **" + produced + "x " + produto + "** · estoque agora " + newStock);
        event.reply("🛠️ Fabricado **" + produced + "x " + produto + "**. Estoque: " + newStock).queue();
    }

    private void lista(SlashCommandInteractionEvent event) {
        List<Recipe> all = recipes.list(event.getGuild().getId());
        if (all.isEmpty()) {
            event.reply("Nenhuma receita cadastrada.").setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder("## 🛠️ Receitas\n");
        for (Recipe r : all) {
            String ins = r.inputs().stream().map(i -> i.qty() + "x " + i.item())
                    .reduce((a, b) -> a + ", " + b).orElse("—");
            sb.append("\n**").append(r.outputQty()).append("x ").append(r.product()).append("** ← ").append(ins);
        }
        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    private static void addInput(List<Input> inputs, SlashCommandInteractionEvent event,
                                 String itemOpt, String qtyOpt) {
        String item = event.getOption(itemOpt, OptionMapping::getAsString);
        Long qty = event.getOption(qtyOpt, OptionMapping::getAsLong);
        if (item != null && !item.isBlank() && qty != null && qty > 0) {
            inputs.add(new Input(item.trim(), qty));
        }
    }
}
