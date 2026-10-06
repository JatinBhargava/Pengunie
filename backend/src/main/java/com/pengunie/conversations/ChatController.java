package com.pengunie.conversations;

import java.util.List;
import java.util.UUID;

import com.pengunie.common.AccessScope;
import com.pengunie.common.ApiException;
import com.pengunie.conversations.ConversationRepository.ConversationView;
import com.pengunie.conversations.RagChatService.CitationView;
import com.pengunie.conversations.RagChatService.MessageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ChatController {

	private static final TypeReference<List<CitationView>> CITATIONS = new TypeReference<>() {
	};

	private final RagChatService rag;

	private final ConversationRepository conversations;

	private final ObjectMapper json;

	ChatController(RagChatService rag, ConversationRepository conversations, ObjectMapper json) {
		this.rag = rag;
		this.conversations = conversations;
		this.json = json;
	}

	record ChatRequest(UUID conversationId, @NotBlank @Size(max = 4000) String message, UUID collectionId) {
	}

	record ConversationDetail(UUID id, String title, List<MessageView> messages) {
	}

	@PostMapping("/api/v1/chat")
	RagChatService.ChatTurn chat(AccessScope scope, @Valid @RequestBody ChatRequest request) {
		return rag.chat(scope, request.conversationId(), request.message().strip(), request.collectionId());
	}

	@GetMapping("/api/v1/conversations")
	List<ConversationView> list(AccessScope scope) {
		return conversations.list(scope);
	}

	@GetMapping("/api/v1/conversations/{id}")
	ConversationDetail get(AccessScope scope, @PathVariable UUID id) {
		ConversationView conversation = conversations.find(scope, id)
			.orElseThrow(() -> ApiException.notFound("Conversation"));
		List<MessageView> messages = conversations.messages(id, 500)
			.stream()
			.map(m -> new MessageView(m.id(), m.role(), m.content(),
					m.grounding() == null ? null : Grounding.valueOf(m.grounding()),
					json.readValue(m.citations(), CITATIONS), m.createdAt()))
			.toList();
		return new ConversationDetail(conversation.id(), conversation.title(), messages);
	}

	@DeleteMapping("/api/v1/conversations/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(AccessScope scope, @PathVariable UUID id) {
		if (!conversations.delete(scope, id)) {
			throw ApiException.notFound("Conversation");
		}
	}

}
