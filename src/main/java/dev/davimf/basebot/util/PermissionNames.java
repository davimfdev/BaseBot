package dev.davimf.basebot.util;

import net.dv8tion.jda.api.Permission;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Portuguese (pt-BR) labels for Discord permissions, used in role/channel permission logs.
 *  Falls back to JDA's English {@link Permission#getName()} for anything not mapped. */
public final class PermissionNames {

    private static final Map<Permission, String> PT = new EnumMap<>(Permission.class);

    static {
        PT.put(Permission.ADMINISTRATOR, "Administrador");
        PT.put(Permission.MANAGE_CHANNEL, "Gerenciar Canais");
        PT.put(Permission.MANAGE_SERVER, "Gerenciar Servidor");
        PT.put(Permission.VIEW_AUDIT_LOGS, "Ver Registro de Auditoria");
        PT.put(Permission.VIEW_CHANNEL, "Ver Canal");
        PT.put(Permission.VIEW_GUILD_INSIGHTS, "Ver Análises do Servidor");
        PT.put(Permission.MANAGE_ROLES, "Gerenciar Cargos");
        PT.put(Permission.MANAGE_PERMISSIONS, "Gerenciar Permissões");
        PT.put(Permission.MANAGE_WEBHOOKS, "Gerenciar Webhooks");
        PT.put(Permission.MANAGE_GUILD_EXPRESSIONS, "Gerenciar Expressões");
        PT.put(Permission.CREATE_GUILD_EXPRESSIONS, "Criar Expressões");
        PT.put(Permission.MANAGE_EVENTS, "Gerenciar Eventos");
        PT.put(Permission.CREATE_SCHEDULED_EVENTS, "Criar Eventos Agendados");
        PT.put(Permission.USE_EMBEDDED_ACTIVITIES, "Usar Atividades");
        PT.put(Permission.VIEW_CREATOR_MONETIZATION_ANALYTICS, "Ver Análises de Monetização");
        PT.put(Permission.CREATE_INSTANT_INVITE, "Criar Convite");
        PT.put(Permission.KICK_MEMBERS, "Expulsar Membros");
        PT.put(Permission.BAN_MEMBERS, "Banir Membros");
        PT.put(Permission.NICKNAME_CHANGE, "Mudar o Próprio Apelido");
        PT.put(Permission.NICKNAME_MANAGE, "Gerenciar Apelidos");
        PT.put(Permission.MODERATE_MEMBERS, "Moderar Membros (timeout)");
        PT.put(Permission.MESSAGE_ADD_REACTION, "Adicionar Reações");
        PT.put(Permission.MESSAGE_SEND, "Enviar Mensagens");
        PT.put(Permission.MESSAGE_TTS, "Enviar Mensagens em Texto-para-Voz");
        PT.put(Permission.MESSAGE_MANAGE, "Gerenciar Mensagens");
        PT.put(Permission.MESSAGE_EMBED_LINKS, "Inserir Links");
        PT.put(Permission.MESSAGE_ATTACH_FILES, "Anexar Arquivos");
        PT.put(Permission.MESSAGE_HISTORY, "Ver Histórico de Mensagens");
        PT.put(Permission.MESSAGE_MENTION_EVERYONE, "Mencionar @everyone, @here e Cargos");
        PT.put(Permission.MESSAGE_EXT_EMOJI, "Usar Emojis Externos");
        PT.put(Permission.MESSAGE_EXT_STICKER, "Usar Figurinhas Externas");
        PT.put(Permission.USE_APPLICATION_COMMANDS, "Usar Comandos de Aplicativo");
        PT.put(Permission.MESSAGE_ATTACH_VOICE_MESSAGE, "Enviar Mensagens de Voz");
        PT.put(Permission.MESSAGE_SEND_POLLS, "Criar Enquetes");
        PT.put(Permission.USE_EXTERNAL_APPLICATIONS, "Usar Aplicativos Externos");
        PT.put(Permission.PIN_MESSAGES, "Fixar Mensagens");
        PT.put(Permission.BYPASS_SLOWMODE, "Ignorar Modo Lento");
        PT.put(Permission.MANAGE_THREADS, "Gerenciar Tópicos");
        PT.put(Permission.CREATE_PUBLIC_THREADS, "Criar Tópicos Públicos");
        PT.put(Permission.CREATE_PRIVATE_THREADS, "Criar Tópicos Privados");
        PT.put(Permission.MESSAGE_SEND_IN_THREADS, "Enviar Mensagens em Tópicos");
        PT.put(Permission.PRIORITY_SPEAKER, "Orador Prioritário");
        PT.put(Permission.VOICE_STREAM, "Transmitir (Vídeo)");
        PT.put(Permission.VOICE_CONNECT, "Conectar");
        PT.put(Permission.VOICE_SPEAK, "Falar");
        PT.put(Permission.VOICE_MUTE_OTHERS, "Silenciar Membros");
        PT.put(Permission.VOICE_DEAF_OTHERS, "Ensurdecer Membros");
        PT.put(Permission.VOICE_MOVE_OTHERS, "Mover Membros");
        PT.put(Permission.VOICE_USE_VAD, "Usar Detecção de Voz");
        PT.put(Permission.VOICE_USE_SOUNDBOARD, "Usar Soundboard");
        PT.put(Permission.VOICE_USE_EXTERNAL_SOUNDS, "Usar Sons Externos");
        PT.put(Permission.VOICE_SET_STATUS, "Definir Status de Voz");
        PT.put(Permission.REQUEST_TO_SPEAK, "Pedir para Falar");
    }

    private PermissionNames() {}

    /** Portuguese label for a permission (English fallback if unmapped). */
    public static String pt(Permission p) {
        return PT.getOrDefault(p, p.getName());
    }

    /** Comma-joined Portuguese labels for a set of permissions. */
    public static String names(Collection<Permission> perms) {
        return perms.stream().map(PermissionNames::pt).collect(Collectors.joining(", "));
    }
}
