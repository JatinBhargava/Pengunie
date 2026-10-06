package com.pengunie.search;

import java.util.List;
import java.util.UUID;

import com.pengunie.common.AccessScope;
import com.pengunie.search.ChunkSearchRepository.ChunkHit;
import com.pengunie.search.ChunkSearchRepository.ChunkPreview;
import com.pengunie.search.ChunkSearchRepository.SearchFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SearchController {

	enum Mode {

		SEMANTIC, KEYWORD

	}

	record SearchRequest(@NotBlank @Size(max = 1000) String query, Mode mode, UUID collectionId, UUID documentId,
			@Min(1) @Max(50) Integer topK) {
	}

	record SearchResult(UUID chunkId, UUID documentId, String documentTitle, int pageStart, int pageEnd,
			String heading, String snippet, double score) {

		static SearchResult of(ChunkHit hit) {
			return new SearchResult(hit.chunkId(), hit.documentId(), hit.documentTitle(), hit.pageStart(),
					hit.pageEnd(), hit.heading(), hit.snippet(), hit.score());
		}

	}

	record SearchResponse(Mode mode, List<SearchResult> results, long tookMs) {
	}

	private final ChunkSearchRepository chunks;

	private final EmbeddingService embeddings;

	SearchController(ChunkSearchRepository chunks, EmbeddingService embeddings) {
		this.chunks = chunks;
		this.embeddings = embeddings;
	}

	@PostMapping("/api/v1/search")
	SearchResponse search(AccessScope scope, @Valid @RequestBody SearchRequest request) {
		long started = System.currentTimeMillis();
		Mode mode = request.mode() == null ? Mode.SEMANTIC : request.mode();
		int topK = request.topK() == null ? 10 : request.topK();
		var filter = new SearchFilter(request.collectionId(), request.documentId());
		List<ChunkHit> hits = switch (mode) {
			case SEMANTIC -> chunks.semantic(scope,
					embeddings.embedQuery(request.query(), scope.userId(), "embed_query", null, null), filter, topK, 0);
			case KEYWORD -> chunks.keyword(scope, request.query(), filter, topK);
		};
		return new SearchResponse(mode, hits.stream().map(SearchResult::of).toList(),
				System.currentTimeMillis() - started);
	}

	@GetMapping("/api/v1/documents/{id}/chunks")
	List<ChunkPreview> documentChunks(AccessScope scope, @PathVariable UUID id) {
		return chunks.listForDocument(scope, id);
	}

}
