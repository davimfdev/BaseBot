// [OUTLINE START]
// Package: dev.davimf.basebot.modules.facs.commands
// 
// Class: ProduzirCommand
// 
// Constructors:
//   - `Constructor` : `public ProduzirCommand(EconomyRepository economy, RecipeRepository recipes)`
// 
// Methods:
//   - `Method` : `public String name()`
//   - `Method` : `public SlashCommandData data()`
// 
// Fields:
//   - `Field` : `private final EconomyRepository economy`
//   - `Field` : `private final RecipeRepository recipes`
// [OUTLINE END]



package dev.davimf.basebot.modules.facs.commands;

import dev.davimf.basebot.util.Emojis;

import dev.davimf.basebot.core.BotContext;
import dev.davimf.basebot.core.command.AutocompleteCommand;
import dev.davimf.basebot.core.command.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import dev.davimf.basebot.modules.facs.FacsLog;
import dev.davimf.basebot.modules.facs.economy.EconomyRepository;
import dev.davimf.basebot.modules.facs.economy.FarmItems;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository.Input;
import dev.davimf.basebot.modules.facs.economy.RecipeRepository.Recipe;
import dev.davimf.basebot.util.Replies;
import dev.davimf.basebot.database.model.GuildConfig;
import dev.davimf.basebot.modules.facs.perms.ManagerPermissions;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * /produzir — defines recipes and crafts products by consuming raw materials from stock
 * (BOTSPECS Module 4). {@code receita} (manager) defines a recipe of up to 3 inputs;
 * {@code fazer} crafts it; {@code lista} shows all recipes.
 */
public final class ProduzirCommand implements SlashCommand, AutocompleteCommand {

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
        // Discord requires all required options before optional ones.
        SubcommandData receita = new SubcommandData("receita", "Define/atualiza uma receita de produção.")
                .addOptions(new OptionData(OptionType.STRING, "produto", "Produto final", true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.STRING, "entrada1", "Material 1", true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.INTEGER, "qtd1", "Qtd do material 1", true).setMinValue(1))
                .addOptions(new OptionData(OptionType.INTEGER, "saida", "Qtd produzida por fabricação", false)
                        .setMinValue(1))
                .addOptions(new OptionData(OptionType.STRING, "entrada2", "Material 2 (opcional)", false).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.INTEGER, "qtd2", "Qtd do material 2", false).setMinValue(1))
                .addOptions(new OptionData(OptionType.STRING, "entrada3", "Material 3 (opcional)", false).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.INTEGER, "qtd3", "Qtd do material 3", false).setMinValue(1));
        SubcommandData fazer = new SubcommandData("fazer", "Fabrica um produto consumindo o estoque.")
                .addOptions(new OptionData(OptionType.STRING, "produto", "Produto a fabricar", true).setAutoComplete(true))
                .addOptions(new OptionData(OptionType.INTEGER, "vezes", "Quantas fabricações", false)
                        .setMinValue(1).setMaxValue(10_000));
        SubcommandData lista = new SubcommandData("lista", "Lista as receitas cadastradas.");
        return Commands.slash("produzir", "Produção: receitas e fabricação a partir do estoque.")
                .addSubcommands(receita, fazer, lista);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            Replies.ephemeral(event, ctx, "Use este comando em um servidor.");
            return;
        }
        switch (event.getSubcommandName() == null ? "" : event.getSubcommandName()) {
            case "receita" -> receita(event, ctx);
            case "fazer" -> fazer(event, ctx);
            case "lista" -> lista(event, ctx);
            default -> Replies.ephemeral(event, ctx, "Subcomando inválido.");
        }
    }

    @Override
    public void onAutocomplete(CommandAutoCompleteInteractionEvent event, BotContext ctx) {
        if (event.getGuild() == null) {
            event.replyChoiceStrings(List.of()).queue();
            return;
        }
        String focused = event.getFocusedOption().getValue().toLowerCase(Locale.ROOT);
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        List<String> choices = FarmItems.list(cfg).stream()
                .filter(i -> i.toLowerCase(Locale.ROOT).contains(focused))
                .limit(25)
                .toList();
        event.replyChoiceStrings(choices).queue();
    }

    private void receita(SlashCommandInteractionEvent event, BotContext ctx) {
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(event.getGuild().getId());
        if (!ManagerPermissions.can(event.getMember(), cfg, ManagerPermissions.Capability.FARM)) {
            Replies.ephemeral(event, ctx, "Apenas a gerência de **Farm** pode definir receitas.");
            return;
        }
        String produto = event.getOption("produto", OptionMapping::getAsString).trim();
        long saida = event.getOption("saida", 1L, OptionMapping::getAsLong);
        List<Input> inputs = new ArrayList<>();
        addInput(inputs, event, "entrada1", "qtd1");
        addInput(inputs, event, "entrada2", "qtd2");
        addInput(inputs, event, "entrada3", "qtd3");
        if (inputs.isEmpty()) {
            Replies.ephemeral(event, ctx, "Informe ao menos um material de entrada.");
            return;
        }
        // Items must be pre-configured (/setup → Farm) so the stock stays consistent.
        List<String> unknown = new ArrayList<>();
        if (!FarmItems.contains(cfg, produto)) {
            unknown.add(produto);
        }
        for (Input in : inputs) {
            if (!FarmItems.contains(cfg, in.item())) {
                unknown.add(in.item());
            }
        }
        if (!unknown.isEmpty()) {
            Replies.ephemeral(event, ctx, "Itens não configurados em `/setup → Farm`: "
                    + String.join(", ", unknown) + ". Cadastre-os lá primeiro.");
            return;
        }
        produto = FarmItems.canonical(cfg, produto);
        List<Input> canon = new ArrayList<>();
        for (Input in : inputs) {
            canon.add(new Input(FarmItems.canonical(cfg, in.item()), in.qty()));
        }
        inputs = canon;
        recipes.upsert(event.getGuild().getId(), produto, saida, inputs);
        String list = inputs.stream().map(i -> i.qty() + "x " + i.item()).reduce((a, b) -> a + ", " + b).orElse("");
        Replies.ephemeral(event, ctx, "" + Emojis.of(Emojis.WRENCH, "🛠️") + " Receita salva: **" + saida + "x " + produto + "** ← " + list);
    }

    private void fazer(SlashCommandInteractionEvent event, BotContext ctx) {
        String guildId = event.getGuild().getId();
        GuildConfig cfg = ctx.database().guildConfig().findOrEmpty(guildId);
        String produto = FarmItems.canonical(cfg, event.getOption("produto", OptionMapping::getAsString));
        long vezes = event.getOption("vezes", 1L, OptionMapping::getAsLong);
        Optional<Recipe> maybe = recipes.findByProduct(guildId, produto);
        if (maybe.isEmpty()) {
            Replies.ephemeral(event, ctx,
                    "Receita não encontrada para **" + produto + "**. Crie com /produzir receita.");
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
            Replies.ephemeral(event, ctx, "Estoque insuficiente:\n• " + String.join("\n• ", missing));
            return;
        }
        for (Input in : recipe.inputs()) {
            economy.deductStock(guildId, in.item(), in.qty() * vezes);
        }
        long produced = recipe.outputQty() * vezes;
        long newStock = economy.addStock(guildId, produto, produced);

        ctx.database().actionLogs().log(guildId, event.getUser().getId(), null, "PRODUCE",
                produced + "x " + produto);
        FacsLog.post(ctx, guildId, "log-farm", "## " + Emojis.of(Emojis.WRENCH, "🛠️") + " Produção\n---\n"
                + "" + Emojis.of(Emojis.MEMBER, "👤") + " **Membro** · <@" + event.getUser().getId() + ">\n"
                + "" + Emojis.of(Emojis.WRENCH, "🛠️") + " **Produzido** · `" + produced + "x` " + produto + "\n"
                + "" + Emojis.of(Emojis.STATS, "📊") + " **Estoque** · `" + newStock + "`");
        Replies.reply(event, ctx, "" + Emojis.of(Emojis.WRENCH, "🛠️") + " Fabricado **" + produced + "x " + produto + "**. Estoque: " + newStock);
    }

    private void lista(SlashCommandInteractionEvent event, BotContext ctx) {
        List<Recipe> all = recipes.list(event.getGuild().getId());
        if (all.isEmpty()) {
            Replies.ephemeral(event, ctx, "Nenhuma receita cadastrada.");
            return;
        }
        StringBuilder sb = new StringBuilder("## " + Emojis.of(Emojis.WRENCH, "🛠️") + " Receitas\n");
        for (Recipe r : all) {
            String ins = r.inputs().stream().map(i -> i.qty() + "x " + i.item())
                    .reduce((a, b) -> a + ", " + b).orElse("—");
            sb.append("\n**").append(r.outputQty()).append("x ").append(r.product()).append("** ← ").append(ins);
        }
        Replies.ephemeral(event, ctx, sb.toString());
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
