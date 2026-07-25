package whatsapp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "message", indexes = {
        @Index(name = "idx_msg_sender_receiver_date_time", columnList = "sender, receiver, date_time"),
        @Index(name = "idx_msg_receiver_date_time", columnList = "receiver, date_time")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender", nullable = false)
    private String sender;

    @Column(name = "receiver", nullable = false)
    private String receiver;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "text", nullable = false, columnDefinition = "TEXT")
    private String text;

    @Builder.Default
    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime = LocalDateTime.now();

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;
}