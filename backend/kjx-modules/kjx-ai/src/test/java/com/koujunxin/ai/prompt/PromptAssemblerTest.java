package com.koujunxin.ai.prompt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.koujunxin.ai.openai.OpenAiMessage;
import com.koujunxin.ai.session.ChatMessage;
import com.koujunxin.ai.session.ConversationSummary;

class PromptAssemblerTest {

    private static final String METHOD_SECRET = "METHOD_NEVER_LEAVE_SERVER";

    private final PromptAssembler assembler = new PromptAssembler();

    @Test
    void stablePrefixStaysIdenticalWhenSuffixChanges() {
        PromptRecord work = work("你是编辑。规则保持稳定。", "1.0.0");
        AssembledPrompt first = assembler.assemble(work, "{\"stage\":\"a\"}", null, List.of(), null);
        AssembledPrompt second = assembler.assemble(
                work,
                "{\"stage\":\"b\"}",
                new ConversationSummary("s1", 4, "昨天吵过一架", "local"),
                List.of(ChatMessage.fromUser("s1", "t1", "你好")),
                null);
        assertEquals(first.stablePrefix(), second.stablePrefix());
        assertFalse(first.variableSuffix().equals(second.variableSuffix()));
        assertEquals("1.0.0", first.promptVersion());
        List<OpenAiMessage> messages = second.toMessages();
        assertEquals("system", messages.get(0).role());
        assertEquals(first.stablePrefix(), messages.get(0).content());
        assertTrue(messages.get(1).content().contains("昨天吵过一架"));
        assertTrue(messages.get(1).content().contains("\"stage\":\"b\""));
    }

    @Test
    void methodPromptIsRejectedAndNeverCopiedIntoMessages() {
        PromptRecord method = new PromptRecord(
                "platform",
                PromptKind.METHOD,
                "SERVER",
                "0.1.0",
                PromptStatus.ACTIVE,
                METHOD_SECRET,
                "[]",
                "{}");
        assertThrows(IllegalArgumentException.class, () -> assembler.assemble(method, "{}", null, List.of(), null));
        PromptRecord work = work("作品人设。", "1.0.0");
        AssembledPrompt assembled = assembler.assemble(work, "{}", null, List.of(), null);
        String joined = assembled.toMessages().stream().map(OpenAiMessage::content).reduce("", String::concat);
        assertFalse(joined.contains(METHOD_SECRET));
        assertFalse(assembled.toString().contains("作品人设"));
    }

    @Test
    void breakupEditorUsesPersonaFragmentAndOmitsApiKey() {
        PromptRecord work = work("规则保持稳定。", "1.0.0");
        String state = """
                {"prompt":{"persona":"outer: 话少。\\ninner: 不会说。\\norigin: 自己扛。\\nspeech: 极简。"},\
                "persona":{"outer":"话少。","inner":"不会说。","origin":"自己扛。","speech":"极简。"},\
                "bp":88,"lm":30,"lf":70,"side":"a","worldline":"rewritten","apiKey":"sk-never-leave",\
                "coupleLabel":"\u6C5F\u96EA \u00D7 \u5F20\u65F8\u5955"}
                """;
        AssembledPrompt assembled = assembler.assemble(work, state, null, List.of(), null);
        assertTrue(assembled.variableSuffix().contains("[persona]"));
        assertTrue(assembled.variableSuffix().contains("outer: 话少。"));
        assertTrue(assembled.variableSuffix().contains("\"bp\":88"));
        assertFalse(assembled.variableSuffix().contains("sk-never-leave"));
        assertFalse(assembled.variableSuffix().contains("apiKey"));
        assertTrue(assembled.variableSuffix().contains("\u65F8"));
        assertFalse(assembled.variableSuffix().contains("\u6600"));
    }

    @Test
    void toStringDoesNotLeakPrefixBody() {
        PromptRecord work = work("机密前缀正文应当被省略", "2.0.0");
        AssembledPrompt assembled = assembler.assemble(work, "{\"x\":1}", null, List.of(), null);
        assertFalse(assembled.toString().contains("机密前缀正文应当被省略"));
        assertTrue(assembled.toString().contains("prefixChars="));
    }

    private static PromptRecord work(String prefix, String version) {
        return new PromptRecord("breakup-editor", PromptKind.WORK, "SERVER", version, PromptStatus.ACTIVE, prefix, "[]", "{}");
    }
}
