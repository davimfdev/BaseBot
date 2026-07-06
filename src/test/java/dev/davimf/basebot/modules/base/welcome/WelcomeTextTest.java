package dev.davimf.basebot.modules.base.welcome;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;

class WelcomeTextTest {

    private Member member(String name, String mention) {
        return (Member) Proxy.newProxyInstance(
                Member.class.getClassLoader(),
                new Class[]{Member.class},
                (proxy, method, args) -> {
                    if ("getEffectiveName".equals(method.getName())) return name;
                    if ("getAsMention".equals(method.getName())) return mention;
                    return null;
                }
        );
    }

    private Guild guild(String name, int count) {
        return (Guild) Proxy.newProxyInstance(
                Guild.class.getClassLoader(),
                new Class[]{Guild.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return name;
                    if ("getMemberCount".equals(method.getName())) return count;
                    return null;
                }
        );
    }

    @Test
    void substitutesAllTokens() {
        String out = WelcomeText.render("{user}/{mention}/{server}/{count}",
                member("Davi", "<@1>"), guild("Casa", 42));
        assertEquals("Davi/<@1>/Casa/42", out);
    }

    @Test
    void leavesLiteralTextUntouched() {
        assertEquals("Olá!", WelcomeText.render("Olá!", member("x", "<@1>"), guild("g", 1)));
    }

    @Test
    void nullTemplateBecomesEmpty() {
        assertEquals("", WelcomeText.render(null, member("x", "<@1>"), guild("g", 1)));
    }
}
