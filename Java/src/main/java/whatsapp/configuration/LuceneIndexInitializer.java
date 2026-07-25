package whatsapp.configuration;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import whatsapp.entity.User;

@Slf4j
@Component
@RequiredArgsConstructor
public class LuceneIndexInitializer implements ApplicationRunner {

    private final EntityManager entityManager;

    @Value("${hibernate.search.reindex-on-startup:false}")
    private boolean reindexOnStartup;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!reindexOnStartup) {
            log.info("Skipping Lucene mass indexing (reindex-on-startup is false)");
            return;
        }
        try {
            log.info("Starting Lucene mass indexing for User entity...");
            SearchSession searchSession = Search.session(entityManager);

            searchSession.massIndexer(User.class)
                    .threadsToLoadObjects(2)
                    .batchSizeToLoadObjects(25)
                    .startAndWait();

            log.info("Lucene mass indexing completed successfully!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lucene mass indexing was interrupted", e);
        } catch (Exception e) {
            log.error("Failed to execute Lucene mass indexing", e);
        }
    }
}