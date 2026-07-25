package whatsapp.configuration;

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;
import org.springframework.stereotype.Component;

@Component
public class LuceneAnalysisConfig implements LuceneAnalysisConfigurer {

    @Override
    public void configure(LuceneAnalysisConfigurationContext context) {
        context.analyzer("autocomplete_indexing").custom()
                .tokenizer("standard")
                .tokenFilter("lowercase")
                .tokenFilter("edgeNGram")
                .param("minGramSize", "2")
                .param("maxGramSize", "15");

        context.analyzer("autocomplete_search").custom()
                .tokenizer("standard")
                .tokenFilter("lowercase");
    }
}