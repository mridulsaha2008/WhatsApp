package whatsapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import whatsapp.entity.Message;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query(value = """
             WITH RankedMessages AS (
                 SELECT id, sender, receiver, type, text, date_time, is_read,
                        ROW_NUMBER() OVER (
                            PARTITION BY 
                                CASE 
                                    WHEN sender = :currentUser THEN receiver 
                                    ELSE sender 
                                END 
                            ORDER BY date_time DESC, id DESC
                        ) as rn
                 FROM message
                 WHERE sender = :currentUser OR receiver = :currentUser
             )
             SELECT id, sender, receiver, type, text, date_time, is_read
             FROM RankedMessages
             WHERE rn = 1
             ORDER BY date_time DESC, id DESC
            """, nativeQuery = true)
    List<Message> findHomepageConversations(@Param("currentUser") String currentUser);

    @Query("""
             SELECT m FROM Message m 
             WHERE (m.sender = :user1 AND m.receiver = :user2) 
                OR (m.sender = :user2 AND m.receiver = :user1)
             ORDER BY m.dateTime DESC, m.id DESC
            """)
    List<Message> findConversations(@Param("user1") String user1, @Param("user2") String user2);

    @Query("""
             SELECT m FROM Message m 
             WHERE ((m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1))
               AND m.dateTime > :dateTime1 AND m.dateTime < :dateTime2
             ORDER BY m.dateTime DESC, m.id DESC 
            """)
    List<Message> findBetween(@Param("user1") String user1, @Param("user2") String user2,
                              @Param("dateTime1") LocalDateTime dateTime1, @Param("dateTime2") LocalDateTime dateTime2);

    @Query("""
             SELECT m FROM Message m 
             WHERE ((m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1))
               AND m.dateTime >= :dateTime1 AND m.dateTime <= :dateTime2
             ORDER BY m.dateTime DESC, m.id DESC 
            """)
    List<Message> findBetweenInclusive(@Param("user1") String user1, @Param("user2") String user2,
                                       @Param("dateTime1") LocalDateTime dateTime1, @Param("dateTime2") LocalDateTime dateTime2);

    @Query("""
             SELECT m FROM Message m 
             WHERE ((m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1))
               AND m.dateTime >= :dateTime1 AND m.dateTime < :dateTime2
             ORDER BY m.dateTime DESC, m.id DESC 
            """)
    List<Message> findBetweenInclusiveLeft(@Param("user1") String user1, @Param("user2") String user2,
                                           @Param("dateTime1") LocalDateTime dateTime1, @Param("dateTime2") LocalDateTime dateTime2);

    @Query("""
             SELECT m FROM Message m 
             WHERE ((m.sender = :user1 AND m.receiver = :user2) OR (m.sender = :user2 AND m.receiver = :user1))
               AND m.dateTime > :dateTime1 AND m.dateTime <= :dateTime2
             ORDER BY m.dateTime DESC, m.id DESC 
            """)
    List<Message> findBetweenInclusiveRight(@Param("user1") String user1, @Param("user2") String user2,
                                            @Param("dateTime1") LocalDateTime dateTime1, @Param("dateTime2") LocalDateTime dateTime2);

    @Query(value = """
             SELECT * FROM message 
             WHERE ((sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1))
               AND date_time < :dateTime
             ORDER BY date_time DESC, id DESC 
             LIMIT 50
            """, nativeQuery = true)
    List<Message> findTop50Before(@Param("user1") String user1, @Param("user2") String user2, @Param("dateTime") LocalDateTime dateTime);

    @Query(value = """
             SELECT * FROM message 
             WHERE ((sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1))
               AND date_time <= :dateTime
             ORDER BY date_time DESC, id DESC 
             LIMIT 50
            """, nativeQuery = true)
    List<Message> findTop50BeforeInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("dateTime") LocalDateTime dateTime);

    @Query(value = """
             SELECT * FROM message 
             WHERE ((sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1))
               AND date_time > :dateTime
             ORDER BY date_time DESC, id DESC 
             LIMIT 50
            """, nativeQuery = true)
    List<Message> findTop50After(@Param("user1") String user1, @Param("user2") String user2, @Param("dateTime") LocalDateTime dateTime);

    @Query(value = """
             SELECT * FROM message 
             WHERE ((sender = :user1 AND receiver = :user2) OR (sender = :user2 AND receiver = :user1))
               AND date_time >= :dateTime
             ORDER BY date_time DESC, id DESC 
             LIMIT 50
            """, nativeQuery = true)
    List<Message> findTop50AfterInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("dateTime") LocalDateTime dateTime);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Message m
        SET m.isRead = true
        WHERE m.sender = :sender AND m.receiver = :receiver AND m.isRead = false
        """)
    int markAsRead(@Param("sender") String sender, @Param("receiver") String receiver);
}