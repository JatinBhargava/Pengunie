package com.pengunie.ai.fake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("fake")
class FakeModelsConfig {

	@Bean
	FakeEmbeddingModel fakeEmbeddingModel(@Value("${app.embedding.dimensions}") int dimensions) {
		return new FakeEmbeddingModel(dimensions);
	}

	@Bean
	FakeChatModel fakeChatModel() {
		return new FakeChatModel();
	}

}
