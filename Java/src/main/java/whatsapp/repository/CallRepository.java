package whatsapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import whatsapp.entity.Call;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {
    @Query("SELECT c FROM Call c WHERE c.caller = :username OR c.receiver = :username ORDER BY c.startTime DESC")
    List<Call> findAllCalls(@Param("username") String username);

    @Query("SELECT c FROM Call c WHERE (c.caller = :username OR c.receiver = :username) AND c.startTime > :after AND c.startTime < :before ORDER BY c.startTime DESC")
    List<Call> findAllCallsBetween(@Param("username") String username, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE (c.caller = :username OR c.receiver = :username) AND c.startTime >= :after AND c.startTime <= :before ORDER BY c.startTime DESC")
    List<Call> findAllCallsBetweenInclusive(@Param("username") String username, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE (c.caller = :username OR c.receiver = :username) AND c.startTime >= :after AND c.startTime < :before ORDER BY c.startTime DESC")
    List<Call> findAllCallsBetweenAfterInclusive(@Param("username") String username, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE (c.caller = :username OR c.receiver = :username) AND c.startTime > :after AND c.startTime <= :before ORDER BY c.startTime DESC")
    List<Call> findAllCallsBetweenBeforeInclusive(@Param("username") String username, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE (c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1) ORDER BY c.startTime DESC")
    List<Call> findCallBetweenUsers(@Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT c FROM Call c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.startTime > :after AND c.startTime < :before ORDER BY c.startTime DESC")
    List<Call> findCallBetweenUsersBetween(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.startTime >= :after AND c.startTime <= :before ORDER BY c.startTime DESC")
    List<Call> findCallBetweenUsersBetweenInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.startTime >= :after AND c.startTime < :before ORDER BY c.startTime DESC")
    List<Call> findCallBetweenUsersBetweenAfterInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query("SELECT c FROM Call c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.startTime > :after AND c.startTime <= :before ORDER BY c.startTime DESC")
    List<Call> findCallBetweenUsersBetweenBeforeInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after, @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM calls c WHERE (c.caller = :username OR c.receiver = :username) AND c.start_time < :before ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findAllCallsBefore(@Param("username") String username, @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM calls c WHERE (c.caller = :username OR c.receiver = :username) AND c.start_time <= :before ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findAllCallsBeforeInclusive(@Param("username") String username, @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM calls c WHERE (c.caller = :username OR c.receiver = :username) AND c.start_time > :after ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findAllCallsAfter(@Param("username") String username, @Param("after") LocalDateTime after);

    @Query(value = "SELECT * FROM calls c WHERE (c.caller = :username OR c.receiver = :username) AND c.start_time >= :after ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findAllCallsAfterInclusive(@Param("username") String username, @Param("after") LocalDateTime after);

    @Query(value = "SELECT * FROM calls c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.start_time < :before ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findCallBetweenUsersBefore(@Param("user1") String user1, @Param("user2") String user2, @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM calls c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.start_time <= :before ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findCallBetweenUsersBeforeInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("before") LocalDateTime before);

    @Query(value = "SELECT * FROM calls c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.start_time > :after ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findCallBetweenUsersAfter(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after);

    @Query(value = "SELECT * FROM calls c WHERE ((c.caller = :user1 AND c.receiver = :user2) OR (c.caller = :user2 AND c.receiver = :user1)) AND c.start_time >= :after ORDER BY c.start_time DESC LIMIT 50", nativeQuery = true)
    List<Call> findCallBetweenUsersAfterInclusive(@Param("user1") String user1, @Param("user2") String user2, @Param("after") LocalDateTime after);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Call c SET c.status = :status WHERE c.id = :id")
    void updateCallStatus(@Param("id") Long id, @Param("status") String status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Call c SET c.endTime = :endTime, c.status = :status, c.durationInSeconds = :duration WHERE c.id = :id")
    void finalizeCall(@Param("id") Long id, @Param("status") String status, @Param("endTime") LocalDateTime endTime, @Param("duration") Long duration);
}