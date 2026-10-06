package com.pengunie.ai.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Extractive stand-in for an LLM that speaks the RAG answer contract: it picks the source sharing
 * the most terms with the question and quotes its best sentence. Lets the full chat pipeline,
 * including citation validation, run without an API key.
 */
public class FakeChatModel implements ChatModel {

	public static final String MODEL = "fake-chat";

	private static final Pattern SOURCE = Pattern.compile("<source id=\"(\\d+)\"[^>]*>(.*?)</source>", Pattern.DOTALL);

	// Sentence ends, except after common abbreviations ("Dr. Mehta").
	private static final Pattern SENTENCE_BREAK = Pattern
		.compile("(?<!\\b(?:Dr|Mr|Mrs|Ms|St|No|Rs|vs|etc)\\.)(?<=[.!?])\\s+|\\n\\s*\\n");

	private static final Pattern QUESTION = Pattern.compile("<question>(.*?)</question>", Pattern.DOTALL);

	@Override
	public ChatResponse call(Prompt prompt) {
		String userText = prompt.getInstructions()
			.stream()
			.filter(m -> m.getMessageType() == MessageType.USER)
			.map(Message::getText)
			.reduce((a, b) -> b)
			.orElse("");
		String json = answer(userText);
		int inputTokens = prompt.getInstructions().stream().mapToInt(m -> m.getText().length() / 4).sum();
		var metadata = ChatResponseMetadata.builder()
			.model(MODEL)
			.usage(new DefaultUsage(inputTokens, json.length() / 4))
			.build();
		return new ChatResponse(List.of(new Generation(new AssistantMessage(json))), metadata);
	}

	private String answer(String userText) {
		Matcher q = QUESTION.matcher(userText);
		Set<String> questionTerms = FakeText.terms(q.find() ? q.group(1) : userText);
		String bestId = null;
		String bestSentence = null;
		int bestOverlap = 0;
		Matcher sources = SOURCE.matcher(userText);
		while (sources.find()) {
			for (String sentence : sentences(sources.group(2))) {
				Set<String> terms = FakeText.terms(sentence);
				terms.retainAll(questionTerms);
				if (terms.size() > bestOverlap) {
					bestOverlap = terms.size();
					bestId = sources.group(1);
					bestSentence = sentence;
				}
			}
		}
		if (bestId == null || bestOverlap < Math.min(2, questionTerms.size())) {
			return "{\"answerable\":false,\"answer\":\"I could not find this in your documents.\",\"citations\":[]}";
		}
		String quote = escape(bestSentence);
		return "{\"answerable\":true,\"answer\":\"" + quote + " [" + bestId + "]\",\"citations\":[{\"sourceId\":"
				+ bestId + ",\"quote\":\"" + quote + "\"}]}";
	}

	private static List<String> sentences(String text) {
		List<String> result = new ArrayList<>();
		for (String s : SENTENCE_BREAK.split(text.strip())) {
			if (!s.isBlank() && !s.strip().startsWith("#")) {
				result.add(s.strip().replaceAll("\\s+", " "));
			}
		}
		return result;
	}

	private static String escape(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

}
